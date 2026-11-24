package com.syl.lux;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

/** Finite camera-style dial. Position is fractional for automatic readings. */
final class DialView extends View {
    interface Listener { void manual(); void changed(int index); default void groups() {} default void selectValue() {} }
    private final Paint paint = new Paint(3);
    private ThemeColors theme = ThemeColors.OPTIONS[0];
    private double[] values = {1};
    private String title = "", value = "—", hint = "";
    private boolean manual, warning, zoom, dragging, longTriggered;
    private float position, startX, startY, startPosition;
    private int lastHapticIndex;
    private boolean holding;
    private long holdStarted;
    private Listener listener;
    private final Runnable longPress = () -> { if (holding && !dragging && listener != null) { holding=false;longTriggered=true;listener.groups();performHapticFeedback(0); } };
    DialView(Context context) { super(context); setFocusable(true); setClickable(true); }
    void listener(Listener value) { listener = value; }
    void theme(ThemeColors value) { theme = value; invalidate(); }
    void display(String title, String value, double[] stops, double actual, boolean manual, boolean warning, String hint, boolean zoom) {
        this.title=title;this.value=value;this.values=stops;this.position=(float) position(stops,actual);this.manual=manual;this.warning=warning;this.hint=hint;this.zoom=zoom;
        setContentDescription(title+" "+value+" "+hint); invalidate();
    }
    static double position(double[] values, double value) {
        if(values.length<2)return 0;
        boolean asc=values[values.length-1]>values[0];
        if(asc?value<=values[0]:value>=values[0])return 0;
        int last=values.length-1;
        if(asc?value>=values[last]:value<=values[last])return last;
        for(int i=0;i<last;i++)if(asc?value<=values[i+1]:value>=values[i+1])return i+Math.log(value/values[i])/Math.log(values[i+1]/values[i]);
        return last;
    }
    private float d(float n){return n*getResources().getDisplayMetrics().density;}
    private void text(Canvas c,String s,float x,float y,int size,int color){paint.setColor(color);paint.setTextSize(d(size));paint.setTextAlign(Paint.Align.LEFT);c.drawText(s,x,y,paint);}
    private void centerText(Canvas c,String s,float x,float y,int size,int color){paint.setColor(color);paint.setTextSize(d(size));paint.setTextAlign(Paint.Align.CENTER);c.drawText(s,x,y,paint);}
    private void tickFade(float width, int color) {
        int rgb = color & 0x00ffffff;
        // Fade across two tick gaps, with a much fainter outer level.
        float edge = Math.min(d(44)/(width-d(20)), .45f);
        paint.setShader(new LinearGradient(d(10), 0, width-d(10), 0,
            new int[]{rgb, rgb | 0x18000000, color, color, rgb | 0x18000000, rgb},
            new float[]{0, edge*.5f, edge, 1-edge, 1-edge*.5f, 1}, Shader.TileMode.CLAMP));
    }
@Override protected void onDraw(Canvas c){
        super.onDraw(c);int gold=theme.accent,muted=theme.color(0xff9aa397),red=theme.warning,fg=theme.color(0xfff1f3eb);
        float w=getWidth(),h=getHeight();paint.setColor(warning?theme.warningBackground:theme.color(manual&&!zoom?0xff302c22:0xff202421));c.drawRoundRect(0,0,w,h,d(9),d(9),paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(d(1));paint.setColor(warning?red:manual?gold:theme.color(0xff363c36));c.drawRoundRect(d(1),d(1),w-d(1),h-d(1),d(9),d(9),paint);paint.setStyle(Paint.Style.FILL);
if(zoom){
    text(c,title,d(12),d(19),11,muted);text(c,value,w-d(65),d(19),16,theme.accentInk);
    c.save();c.clipRect(d(10),d(27),w-d(10),d(53));paint.setColor(isEnabled()?gold:muted);paint.setStrokeWidth(d(1));
    tickFade(w,isEnabled()?gold:muted);for(int i=0;i<values.length;i++){float x=w/2+(i-position)*d(22);c.drawLine(x,d(28),x,d(49),paint);}paint.setShader(null);c.restore();
    paint.setColor(red);Path pointer=new Path();pointer.moveTo(w/2,d(54));pointer.lineTo(w/2-d(4),d(60));pointer.lineTo(w/2+d(4),d(60));pointer.close();c.drawPath(pointer,paint);centerText(c,hint,w/2,d(72),10,muted);
    if(!isEnabled()){paint.setColor(theme.color(0x99141716));c.drawRoundRect(0,0,w,h,d(9),d(9),paint);}return;
}
text(c,title,d(13),d(22),12,muted);

        if(!zoom)text(c,manual?"MANUAL":"AUTO",w-d(manual?67:48),d(22),10,manual?theme.accentInk:muted);
        text(c,value,d(13),d(62),zoom?21:29,warning?red:manual?theme.manualText:fg);
        c.save();c.clipRect(d(10),d(73),w-d(10),h-d(30));
        paint.setStrokeWidth(d(1));paint.setColor(warning?red:manual?gold:muted);
        tickFade(w,warning?red:manual?gold:muted);for(int i=0;i<values.length;i++){float x=w/2+(i-position)*d(22);c.drawLine(x,d(78),x,h-d(34),paint);}paint.setShader(null);c.restore();
        if(manual){paint.setColor(red);Path p=new Path();p.moveTo(w/2,h-d(29));p.lineTo(w/2-d(5),h-d(22));p.lineTo(w/2+d(5),h-d(22));p.close();c.drawPath(p,paint);}
        centerText(c,hint,w/2,h-d(7),10,warning?red:muted);
if(holding&&!zoom){float progress=Math.min(1f,(android.os.SystemClock.uptimeMillis()-holdStarted)/5000f);paint.setColor(gold);c.drawRoundRect(d(10),h-d(4),d(10)+(w-d(20))*progress,h-d(1),d(1),d(1),paint);postInvalidateOnAnimation();}
if(!isEnabled()){paint.setColor(theme.color(0xaa141716));c.drawRoundRect(0,0,w,h,d(9),d(9),paint);}
    }
    @Override public boolean onTouchEvent(MotionEvent e){
        if(!isEnabled())return false;
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:startX=e.getX();startY=e.getY();startPosition=position;lastHapticIndex=Math.round(position);dragging=false;longTriggered=false;holding=!zoom;holdStarted=android.os.SystemClock.uptimeMillis();if(!zoom)postDelayed(longPress,5000);invalidate();return true;
            case MotionEvent.ACTION_MOVE:
                float dx=e.getX()-startX,dy=e.getY()-startY;
                if(longTriggered)return true;float slop=Math.max(d(16),ViewConfiguration.get(getContext()).getScaledTouchSlop()*2f);if(!dragging&&Math.abs(dy)>Math.max(slop,Math.abs(dx))){holding=false;removeCallbacks(longPress);invalidate();return true;}
                if(dragging||Math.abs(dx)>slop){holding=false;dragging=true;removeCallbacks(longPress);if(manual){dragging=true;getParent().requestDisallowInterceptTouchEvent(true);float next=Math.max(0,Math.min(values.length-1,startPosition-dx/d(22)));int index=Math.round(next);if(index!=lastHapticIndex){lastHapticIndex=index;performHapticFeedback(android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.R?android.view.HapticFeedbackConstants.CONFIRM:android.view.HapticFeedbackConstants.LONG_PRESS);}if(listener!=null)listener.changed(index);position=next;invalidate();}}return true;
            case MotionEvent.ACTION_UP:holding=false;removeCallbacks(longPress);getParent().requestDisallowInterceptTouchEvent(false);if(listener!=null&&manual&&dragging)listener.changed(Math.round(position));if(!dragging&&!longTriggered&&!zoom&&listener!=null){if(manual&&startY>=d(28)&&startY<=d(70)&&e.getY()>=d(28)&&e.getY()<=d(70))listener.selectValue();else listener.manual();}performClick();invalidate();return true;
            case MotionEvent.ACTION_CANCEL:holding=false;invalidate();removeCallbacks(longPress);getParent().requestDisallowInterceptTouchEvent(false);return true;
            default:return true;
        }
    }
    @Override protected void onDetachedFromWindow(){holding=false;removeCallbacks(longPress);super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
}
