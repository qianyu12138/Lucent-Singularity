package com.syl.lux;

import android.Manifest;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import androidx.camera.camera2.interop.Camera2Interop;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.activity.ComponentActivity;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends ComponentActivity {
    private static final int FG=0xfff1f3eb, MUTED=0xff9aa397, BG=0xff141716;
    private static final double[] ALL_F={0.5,0.7,0.8,0.9,1.0,1.1,1.2,1.4,1.6,1.7,1.8,2.0,2.2,2.4,2.5,2.8,3.2,3.3,3.5,4.0,4.5,4.8,5.0,5.5,5.6,6.3,6.7,7.1,8.0,9.0,9.5,10.0,11.0,13.0,14.0,16.0,18.0,19.0,20.0,22.0,25.0,27.0,29.0,32.0,36.0,38.0,40.0,45.0,51.0,54.0,57.0,64.0,72.0,76.0,80.0,90.0,107.0,128.0,180.0,256.0};
    private static final double[] ALL_T={172800,144000,129600,115200,86400,43200,39600,36000,32400,28800,25200,21600,18000,14400,10800,7200,6300,5400,4500,3600,2700,1800,900,480,240,120,60,30,15,13,12,10,8,6,5,4,3,2.5,2,1.6,1.5,1.3,1,1.0/1.3,1.0/1.5,1.0/1.6,1.0/2,1.0/2.5,1.0/3,1.0/4,1.0/5,1.0/6,1.0/8,1.0/10,1.0/13,1.0/15,1.0/20,1.0/25,1.0/30,1.0/40,1.0/45,1.0/50,1.0/60,1.0/80,1.0/90,1.0/100,1.0/125,1.0/160,1.0/180,1.0/200,1.0/250,1.0/320,1.0/350,1.0/400,1.0/500,1.0/640,1.0/750,1.0/800,1.0/1000,1.0/1250,1.0/1500,1.0/1600,1.0/2000,1.0/2500,1.0/3000,1.0/3200,1.0/4000,1.0/5000,1.0/6000,1.0/6400,1.0/8000,1.0/16000,1.0/32000,1.0/64000};
    private static final String[] ISOS={"1","3","5","6","25","32","40","50","64","80","100","125","160","200","250","320","400","500","640","800","1000","1250","1600","2000","2500","3000","3200","4000","5000","6400","8000","10000","12800","25600","51200","102400","204800","509600"};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private ThemeColors theme=ThemeColors.OPTIONS[0];
    private AlertDialog settingsDialog;
    private AlertDialog informationDialog;
    private ScrollView settingsScroll;
    private int settingsScrollY;
    private boolean reopenSettings;
    private double[] fs,ts;
    private double aperture=4,seconds=1.0/60;
    private double displayedAperture=Double.NaN,displayedShutter=Double.NaN;
    private int isoIndex=2,nd=0,interval=1000;
    private boolean manualF=true,paused,foreground,cameraStarting,regionSupported,showZoom=true;
    private float current=Float.NaN,maximum,zoom=1,minZoom=1,maxZoom=1;
    private ImageCapture jpegCapture; private boolean jpegBusy; private double jpegEv=Double.NaN; private volatile double latestEv=Double.NaN; private volatile int captureGeneration; private double currentEv=Double.NaN; private long lastFrameTime; private String meterStatus="等待相机测光";
    private boolean rawEnabled,rawSupported,rawActive,rawBusy;
    private int rawRevision;
    private ImageCapture rawCapture;
    private RawMeter rawMeter;
    private final ExecutorService rawExecutor=Executors.newSingleThreadExecutor();
    private LinearLayout settingsRawSection;
    private Switch settingsRawSwitch;
    private ProcessCameraProvider provider;private Camera camera;
    private PreviewView preview; private View spotMarker; private float spotX=.5f,spotY=.5f; private int spotRequest;
    private TextView status,luxText,evText,maxText,cameraMessage,rawBadge,sourceText;
    private Button hold;
    private DialView shutterDial,apertureDial,zoomDial;
    private SeekBar settingsZoom;private TextView settingsZoomLabel,settingsCameraNote;
    private final Runnable sample=new Runnable(){public void run(){if(!foreground)return;if(!paused){if(rawActive)requestRawReading();else{double ev=latestEv;if(!Double.isFinite(ev)||ev<3.5){requestJpegReading();if(Double.isFinite(jpegEv)&&(!Double.isFinite(ev)||jpegEv<3.5))ev=jpegEv;}if(Double.isFinite(ev)&&SystemClock.elapsedRealtime()-lastFrameTime<3000){currentEv=ev;current=(float)(Math.pow(2,ev)*2.5);maximum=Math.max(maximum,current);meterStatus="相机测光";}else{currentEv=Double.NaN;current=Float.NaN;}}render();}handler.postDelayed(this,interval);}};

    @Override public void onCreate(Bundle state){super.onCreate(state);
        prefs=getSharedPreferences("meter",MODE_PRIVATE);theme=ThemeColors.OPTIONS[prefs.getInt(ThemeColors.PREFERENCE,0)];migrateStops();fs=enabled(ALL_F,"f");ts=enabled(ALL_T,"t");
        aperture=nearest(fs,prefs.getFloat("aperture",4));seconds=nearest(ts,prefs.getFloat("seconds",1f/60));
        isoIndex=prefs.getInt("iso",2);nd=prefs.getInt("nd",0);interval=prefs.getInt("interval",1000);manualF=prefs.getBoolean("manualF",true);showZoom=prefs.getBoolean("showZoom",true);zoom=prefs.getFloat("zoom",1);
        rawEnabled=prefs.getBoolean("rawMetering",false);
        if(state!=null){currentEv=state.getDouble("ev",Double.NaN);current=state.getFloat("current",Float.NaN);maximum=state.getFloat("maximum");paused=state.getBoolean("paused");}

        buildPage();render();
        if(state!=null&&state.getBoolean("settingsOpen")){settingsScrollY=state.getInt("settingsScrollY");handler.post(this::showSettings);}
        else if(state!=null&&state.getBoolean("informationOpen"))handler.post(this::showInformation);
    }
private void migrateStops(){
    if(prefs.getInt("stopVersion",0)>=1)return;
    double[] oldF={1,1.4,2,2.8,4,5.6,8,11,16,22,32};
    double[] oldT={30,15,8,4,2,1,.5,.25,.125,1.0/15,1.0/30,1.0/60,1.0/125,1.0/250,1.0/500,1.0/1000,1.0/2000,1.0/4000,1.0/8000};
    String[] oldISO={"25","50","100","200","400","800","1600","3200","6400"};
    SharedPreferences.Editor edit=prefs.edit();
    for(String key:new String[]{"f","t"}){double[] old=key.equals("f")?oldF:oldT,all=key.equals("f")?ALL_F:ALL_T;for(int i=0;i<all.length;i++){boolean on=true;for(int j=0;j<old.length;j++)if(Math.abs(all[i]-old[j])<1e-12){on=prefs.getBoolean(key+j,true);break;}edit.putBoolean(key+i,on);}}
    int oldIndex=prefs.getInt("iso",2);String previous=oldISO[Math.max(0,Math.min(oldISO.length-1,oldIndex))];edit.putInt("iso",Arrays.asList(ISOS).indexOf(previous));edit.putInt("stopVersion",1).apply();
}
private double[] enabled
(double[] all,String key){key=activeKey(key);ArrayList<Double> result=new ArrayList<>();for(int i=0;i<all.length;i++)if(prefs.getBoolean(key+i,true))result.add(all[i]);if(result.isEmpty())return new double[]{all[0]};double[] out=new double[result.size()];for(int i=0;i<out.length;i++)out[i]=result.get(i);return out;}
    private static double nearest(double[] values,double target){double best=values[0];for(double v:values)if(Math.abs(Math.log(v/target))<Math.abs(Math.log(best/target)))best=v;return best;}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private int color(int original){return theme.color(original);}
    private TextView text(String value,int size,int baseColor){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color(baseColor));return v;}
    private TextView cameraText(String value,int size,int baseColor){TextView v=text(value,size,baseColor);v.setTextColor(baseColor);return v;}
    private TextView dialogTitle(String value){TextView v=text(value,20,FG);v.setPadding(dp(24),dp(20),dp(24),dp(12));return v;}
    private android.content.res.ColorStateList controlTint(){return new android.content.res.ColorStateList(new int[][]{{-android.R.attr.state_enabled},{android.R.attr.state_checked},{android.R.attr.state_focused},{}},new int[]{color(0x559aa397),theme.accent,theme.accent,color(MUTED)});}
    private LinearLayout column(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private Button button(String value){Button b=new Button(this);b.setText(value);b.setTextSize(12);b.setTextColor(color(FG));b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(color(0xff292e28)));return b;}
    private void buildPage(){
        androidx.core.view.WindowInsetsControllerCompat bars=androidx.core.view.WindowCompat.getInsetsController(getWindow(),getWindow().getDecorView());bars.setAppearanceLightStatusBars(theme.light);bars.setAppearanceLightNavigationBars(theme.light);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(color(BG)));
        getWindow().setStatusBarColor(color(BG));getWindow().setNavigationBarColor(color(BG));
        LinearLayout root=column();root.setBackgroundColor(color(BG));root.setClipToPadding(false);root.setClipChildren(false);root.setPadding(dp(12),dp(6),dp(12),dp(6));setContentView(root);
        root.setOnApplyWindowInsetsListener((v,i)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets b=i.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(dp(12)+b.left,dp(6)+b.top,dp(12)+b.right,dp(6)+b.bottom);}else v.setPadding(dp(12)+i.getSystemWindowInsetLeft(),dp(6)+i.getSystemWindowInsetTop(),dp(12)+i.getSystemWindowInsetRight(),dp(6)+i.getSystemWindowInsetBottom());return i;});root.requestApplyInsets();
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.singularity_header);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(28),dp(28));iconLp.setMarginEnd(dp(8));header.addView(icon,iconLp);
        TextView brand=text(getString(R.string.app_name),17,FG);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setSingleLine(true);brand.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(brand,new LinearLayout.LayoutParams(0,dp(30),1));status=text("等待测光",12,MUTED);status.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);header.addView(status);root.addView(header);
LinearLayout options=new LinearLayout(this);options.setGravity(Gravity.CENTER_VERTICAL);options.addView(text("ISO  ",12,MUTED));TextView iso=groupOption(ISOS[isoIndex]+"  ▾",false,false);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(0,dp(38),1);ip.setMarginEnd(dp(12));options.addView(iso,ip);options.addView(text("ND  ",12,MUTED));String[] nds=new String[21];nds[0]="无";for(int i=1;i<21;i++)nds[i]=i+" 档";TextView filter=groupOption(nds[nd]+"  ▾",false,false);options.addView(filter,new LinearLayout.LayoutParams(0,dp(38),1));LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,dp(48));root.addView(options,op);iso.setOnClickListener(v->selectValue("ISO 感光度",ISOS,isoIndex,i->{isoIndex=i;iso.setText(ISOS[i]+"  ▾");save();render();}));filter.setOnClickListener(v->selectValue("ND 减光镜",nds,nd,i->{nd=i;filter.setText(nds[i]+"  ▾");save();render();}));
        FrameLayout square=new FrameLayout(this);square.setBackgroundColor(BG);
        preview=new PreviewView(this);preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);preview.setScaleType(PreviewView.ScaleType.FILL_CENTER);square.addView(preview,new FrameLayout.LayoutParams(-1,-1));
        spotMarker=new View(this){private final android.graphics.Paint p=new android.graphics.Paint(3);@Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);if(!regionSupported&&!rawActive)return;p.setColor(theme.accent);p.setStyle(android.graphics.Paint.Style.STROKE);p.setStrokeWidth(dp(1));float x=spotX*getWidth(),y=spotY*getHeight(),r=dp(18);c.drawRect(x-r,y-r,x+r,y+r,p);c.drawLine(x-dp(5),y,x+dp(5),y,p);c.drawLine(x,y-dp(5),x,y+dp(5),p);}};
        square.addView(spotMarker,new FrameLayout.LayoutParams(-1,-1));
        GestureDetector taps=new GestureDetector(this,new GestureDetector.SimpleOnGestureListener(){@Override public boolean onDown(MotionEvent e){return true;}@Override public boolean onSingleTapUp(MotionEvent e){meterAt(e.getX(),e.getY(),true);return true;}});
        square.setOnTouchListener((v,event)->{boolean handled=taps.onTouchEvent(event);if(event.getActionMasked()==MotionEvent.ACTION_UP)v.performClick();return handled;});
        LinearLayout readouts=new LinearLayout(this);readouts.setPadding(dp(16),dp(12),dp(16),dp(12));readouts.setBackgroundColor(0xbb141716);LinearLayout l=column();l.addView(cameraText("光照强度 · lx",11,MUTED));luxText=cameraText("—",28,FG);l.addView(luxText);LinearLayout e=column();e.addView(cameraText("曝光值 · EV100",11,MUTED));evText=cameraText("—",28,FG);e.addView(evText);readouts.addView(l,new LinearLayout.LayoutParams(0,-2,1));readouts.addView(e,new LinearLayout.LayoutParams(0,-2,1));sourceText=cameraText("相机测光\n换算 Lux",11,MUTED);readouts.addView(sourceText);square.addView(readouts,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        cameraMessage=cameraText("点击开启相机预览",14,FG);cameraMessage.setGravity(Gravity.CENTER);square.addView(cameraMessage,new FrameLayout.LayoutParams(-1,-1));cameraMessage.setOnClickListener(v->{if(checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)startCamera();else requestPermissions(new String[]{Manifest.permission.CAMERA},10);});LinearLayout.LayoutParams picture=new LinearLayout.LayoutParams(-1,0,1);root.addView(square,picture);
        rawBadge=cameraText("RAW",11,FG);rawBadge.setGravity(Gravity.CENTER);rawBadge.setTypeface(null,android.graphics.Typeface.BOLD);rawBadge.setContentDescription("RAW 测光已开启");rawBadge.setPadding(dp(10),dp(5),dp(10),dp(5));GradientDrawable badgeBackground=new GradientDrawable();badgeBackground.setColor(0xcc141716);badgeBackground.setCornerRadius(dp(6));badgeBackground.setStroke(dp(1),0x99ffffff);rawBadge.setBackground(badgeBackground);rawBadge.setVisibility(View.GONE);FrameLayout.LayoutParams badgeLp=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END);badgeLp.topMargin=dp(12);badgeLp.setMarginEnd(dp(12));square.addView(rawBadge,badgeLp);
        zoomDial=new DialView(this);zoomDial.theme(theme);LinearLayout.LayoutParams zp=new LinearLayout.LayoutParams(-1,dp(78));zp.topMargin=dp(6);root.addView(zoomDial,zp);zoomDial.listener(new DialView.Listener(){public void manual(){}public void changed(int i){applyZoom(minZoom+i*.1f);}});
        LinearLayout dials=new LinearLayout(this);shutterDial=new DialView(this);shutterDial.theme(theme);apertureDial=new DialView(this);apertureDial.theme(theme);LinearLayout.LayoutParams left=new LinearLayout.LayoutParams(0,dp(140),1);left.setMarginEnd(dp(10));dials.addView(shutterDial,left);dials.addView(apertureDial,new LinearLayout.LayoutParams(0,dp(140),1));
        LinearLayout.LayoutParams dialsLp=new LinearLayout.LayoutParams(-1,-2);dialsLp.topMargin=dp(8);root.addView(dials,dialsLp);
        shutterDial.listener(new DialView.Listener(){public void selectValue(){selectExposure(false);}public void groups(){chooseGroup("t",null);}public void manual(){switchPriority(false);}public void changed(int i){seconds=ts[i];save();render();}});apertureDial.listener(new DialView.Listener(){public void selectValue(){selectExposure(true);}public void groups(){chooseGroup("f",null);}public void manual(){switchPriority(true);}public void changed(int i){aperture=fs[i];save();render();}});
        TextView hint=text("短按切换优先 · 长按数值 5 秒切换组",11,MUTED);hint.setGravity(Gravity.CENTER);hint.setPadding(0,dp(4),0,dp(4));root.addView(hint);
        LinearLayout footer=new LinearLayout(this);footer.setGravity(Gravity.CENTER_VERTICAL);LinearLayout peak=column();maxText=text("最高照度 — lx",11,MUTED);peak.addView(maxText);TextView reset=text("重置",11,MUTED);reset.setPadding(0,dp(8),dp(16),dp(8));reset.setOnClickListener(v->{maximum=Float.isFinite(current)?current:0;render();});peak.addView(reset);footer.addView(peak,new LinearLayout.LayoutParams(0,-2,1));Button settings=button("⚙ 设置");settings.setOnClickListener(v->showSettings());footer.addView(settings,new LinearLayout.LayoutParams(0,dp(48),1));hold=button("暂停测光");hold.setOnClickListener(v->{paused=!paused;if(paused)rawRevision++;else if(rawActive)requestRawReading();render();});footer.addView(hold,new LinearLayout.LayoutParams(0,dp(48),1));root.addView(footer);
    }
private void selectExposure(boolean isF){double[] stops=isF?fs:ts;String[] labels=new String[stops.length];double currentValue=isF?aperture:seconds;int selected=0;for(int i=0;i<stops.length;i++){labels[i]=isF?apertureLabel(stops[i]):shutterLabel(stops[i]);if(stops[i]==currentValue)selected=i;}selectValue(isF?"选择光圈":"选择快门速度",labels,selected,index->{if(isF)aperture=stops[index];else seconds=stops[index];save();render();});}
private void selectValue(String title,String[] values,int selected,Choice choice){
    android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout shell=column();shell.setPadding(dp(20),dp(16),dp(20),dp(16));LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(title,18,FG),new LinearLayout.LayoutParams(0,dp(48),1));TextView close=text("✕",20,MUTED);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭选择");header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));close.setOnClickListener(v->dialog.dismiss());shell.addView(header);ScrollView scroll=new ScrollView(this);LinearLayout rows=column();scroll.addView(rows);int rowHeight=dp(52),gap=dp(8);shell.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min((rowHeight+gap)*values.length,getResources().getDisplayMetrics().heightPixels/2)));for(int i=0;i<values.length;i++){final int index=i;TextView row=groupOption(values[i]+(i==selected?"     ✓":""),i==selected,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,rowHeight);rp.topMargin=gap;rows.addView(row,rp);row.setOnClickListener(v->{choice.changed(index);dialog.dismiss();});}dialog.setContentView(shell);android.view.Window window=dialog.getWindow();if(window!=null){GradientDrawable bg=new GradientDrawable();bg.setColor(color(BG));bg.setCornerRadius(dp(20));bg.setStroke(dp(1),color(0xff363c36));window.setBackgroundDrawable(bg);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.65f;window.setAttributes(attrs);}dialog.show();if(window!=null)window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(420)),-2);scroll.post(()->scroll.scrollTo(0,Math.max(0,(rowHeight+gap)*selected-scroll.getHeight()/2+rowHeight/2)));
}
private interface Choice
{void changed(int i);}
    private String apertureLabel(double n){return "f / "+(n>=10?decimal(n):String.format(Locale.US,"%.1f",n));}
    private String decimal(double n){return java.math.BigDecimal.valueOf(n).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();} private String shutterLabel(double n){return n<1?"1/"+decimal(1/n)+"s":java.math.BigDecimal.valueOf(n).setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()+"s";}
private void switchPriority(boolean aperturePriority){
    if(manualF!=aperturePriority){
        if(aperturePriority&&Double.isFinite(displayedAperture))aperture=displayedAperture;
        if(!aperturePriority&&Double.isFinite(displayedShutter))seconds=displayedShutter;
        manualF=aperturePriority;save();
    }
    render();
}
private void updateScreenAwake(){
    if(foreground&&!paused&&camera!=null)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
}
private void render(){
updateScreenAwake();
if(rawBadge!=null){rawBadge.setVisibility(rawActive?View.VISIBLE:View.GONE);sourceText.setText(rawActive?"RAW 测光\n换算 Lux":"相机测光\n换算 Lux");}
if(shutterDial==null)return;status.setText(paused?"读数已保持":meterStatus);hold.setText(paused?"继续测光":"暂停测光");hold.setEnabled(camera!=null);luxText.setText(Float.isFinite(current)?String.format(Locale.US,"%d",(int)current):"—");evText.setText(current>0?String.format(Locale.US,"%.1f",currentEv):"—");maxText.setText(String.format(Locale.US,"最高 %.1f lx",maximum));double a=aperture,t=seconds;boolean valid=Double.isFinite(currentEv);double effective=Math.pow(2,currentEv)*2.5/Math.pow(2,nd);if(valid){if(manualF)t=ExposureCalculator.shutterSeconds(effective,Double.parseDouble(ISOS[isoIndex]),a);else a=ExposureCalculator.aperture(effective,Double.parseDouble(ISOS[isoIndex]),t);}renderDial(apertureDial,"光圈",fs,a,manualF,valid,true);renderDial(shutterDial,"快门速度",ts,t,!manualF,valid,false);renderZoom();}
    private void renderDial(DialView dial,String title,double[] stops,double raw,boolean manual,boolean valid,boolean isF){double min=Arrays.stream(stops).min().getAsDouble(),max=Arrays.stream(stops).max().getAsDouble();boolean low=raw<min*(1-1e-9),high=raw>max*(1+1e-9),warning=valid&&!manual&&(low||high);double value=warning?Math.max(min,Math.min(max,raw)):valid&&!manual?nearest(stops,raw):raw;if(isF)displayedAperture=valid||manual?value:Double.NaN;else displayedShutter=valid||manual?value:Double.NaN;String label=!valid&&!manual?"—":isF?apertureLabel(value):shutterLabel(value);String hint=warning?(isF?(low?"高于上限":"低于下限"):(low?"低于下限":"超过上限")):manual?"‹ 左右拨动 ›":valid?"自动跟随":"等待有效照度";dial.display(title,label,stops,value,manual,warning,hint,false);}
    private void save(){prefs.edit().putFloat("aperture",(float)aperture).putFloat("seconds",(float)seconds).putInt("iso",isoIndex).putInt("nd",nd).putInt("interval",interval).putBoolean("manualF",manualF).putBoolean("showZoom",showZoom).putFloat("zoom",zoom).apply();}
    private void showSettings(){
        if(settingsDialog!=null&&settingsDialog.isShowing())return;
        LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),dp(20));
        LinearLayout appearance=card();LinearLayout.LayoutParams appearanceLp=new LinearLayout.LayoutParams(-1,-2);appearanceLp.bottomMargin=dp(16);panel.addView(appearance,appearanceLp);
        appearance.addView(text("主题色",14,FG));TextView themeNote=text("切换界面配色，自动保存",11,MUTED);themeNote.setPadding(0,dp(8),0,dp(12));appearance.addView(themeNote);
        TextView themeChoice=groupOption(theme.name+"  ▾",true,false);appearance.addView(themeChoice,new LinearLayout.LayoutParams(-1,dp(48)));themeChoice.setOnClickListener(v->chooseTheme());
        TextView caption=text("拨轮与档位 · 按器材保存常用组合",12,MUTED);caption.setPadding(0,0,0,dp(12));panel.addView(caption);
        groupSection(panel,"光圈",ALL_F,"f",true);groupSection(panel,"快门速度",ALL_T,"t",false);
        LinearLayout measurement=card();panel.addView(measurement);measurement.addView(text("测量 · 识别间隔",14,FG));
        TextView intervalNote=text("按此间隔更新曝光值和照度",11,MUTED);intervalNote.setPadding(0,dp(8),0,dp(12));measurement.addView(intervalNote);
        int[] periods={500,1000,2000,5000};String[] labels={"每 0.5 秒","每 1 秒","每 2 秒","每 5 秒"};int selected=1;for(int i=0;i<periods.length;i++)if(periods[i]==interval)selected=i;
        TextView timing=groupOption(labels[selected]+"  ▾",false,false);measurement.addView(timing,new LinearLayout.LayoutParams(-1,dp(48)));
        timing.setOnClickListener(v->{int current=0;for(int i=0;i<periods.length;i++)if(periods[i]==interval)current=i;selectValue("识别间隔",labels,current,i->{interval=periods[i];timing.setText(labels[i]+"  ▾");save();handler.removeCallbacks(sample);if(foreground)handler.postDelayed(sample,interval);});});
        settingsRawSection=column();settingsRawSection.setPadding(0,dp(12),0,0);measurement.addView(settingsRawSection);
        settingsRawSwitch=new Switch(this);settingsRawSwitch.setText("RAW 测光");settingsRawSwitch.setTextSize(14);settingsRawSwitch.setTextColor(color(FG));settingsRawSwitch.setPadding(0,dp(8),0,dp(8));settingsRawSwitch.setThumbTintList(controlTint());settingsRawSwitch.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{0x55000000|(theme.accent&0x00ffffff),color(0x559aa397)}));settingsRawSwitch.setChecked(rawEnabled);settingsRawSection.addView(settingsRawSwitch,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView rawNote=text("读取选区原始像素，以相机测光为基准校正。关闭后使用普通相机测光，Lux 均为估算值。",11,MUTED);rawNote.setPadding(0,dp(4),0,dp(4));rawNote.setLineSpacing(dp(3),1);settingsRawSection.addView(rawNote);updateRawControls();settingsRawSwitch.setOnCheckedChangeListener((v,on)->{if(on!=rawEnabled)setRawMode(on);});
        LinearLayout cameraSettings=card();LinearLayout.LayoutParams cameraLp=new LinearLayout.LayoutParams(-1,-2);cameraLp.topMargin=dp(14);panel.addView(cameraSettings,cameraLp);cameraSettings.addView(text("相机",14,FG));
        Switch visible=new Switch(this);visible.setText("在主页面显示焦距拨轮");visible.setTextSize(13);visible.setTextColor(color(FG));visible.setPadding(0,dp(12),0,dp(8));visible.setThumbTintList(controlTint());visible.setTrackTintList(new android.content.res.ColorStateList(new int[][]{{android.R.attr.state_checked},{}},new int[]{0x55000000|(theme.accent&0x00ffffff),color(0x559aa397)}));visible.setChecked(showZoom);cameraSettings.addView(visible);
        TextView zoomNote=text("关闭后，仅在此处调节焦距",11,MUTED);zoomNote.setPadding(0,dp(4),0,dp(12));cameraSettings.addView(zoomNote);visible.setOnCheckedChangeListener((v,on)->{showZoom=on;save();renderZoom();});
        settingsZoomLabel=text("",14,FG);settingsZoomLabel.setPadding(0,dp(4),0,dp(8));cameraSettings.addView(settingsZoomLabel);
        settingsZoom=new SeekBar(this);settingsZoom.setMax(Math.max(0,Math.round((maxZoom-minZoom)*10)));settingsZoom.setProgress(Math.round((zoom-minZoom)*10));settingsZoom.setEnabled(regionSupported&&maxZoom>minZoom);settingsZoom.setThumbTintList(controlTint());settingsZoom.setProgressTintList(android.content.res.ColorStateList.valueOf(theme.accent));settingsZoom.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(color(0xff363c36)));cameraSettings.addView(settingsZoom,new LinearLayout.LayoutParams(-1,dp(44)));
        settingsZoom.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean user){if(user)applyZoom(minZoom+p*.1f);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        settingsCameraNote=text("",12,MUTED);settingsCameraNote.setPadding(0,dp(8),0,0);cameraSettings.addView(settingsCameraNote);renderZoom();
        TextView information=groupOption("信息  ›",false,false);LinearLayout.LayoutParams informationLp=new LinearLayout.LayoutParams(-1,dp(52));informationLp.topMargin=dp(14);panel.addView(information,informationLp);
        information.setOnClickListener(v->{settingsDialog.dismiss();showInformation();});
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.addView(panel);settingsScroll=scroll;
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogTitle("测光设置")).setView(scroll).setPositiveButton("完成",null).create();settingsDialog=dialog;
        dialog.setOnDismissListener(v->{settingsScrollY=scroll.getScrollY();settingsZoom=null;settingsZoomLabel=null;settingsCameraNote=null;settingsRawSection=null;settingsRawSwitch=null;settingsScroll=null;settingsDialog=null;});dialog.show();styleDialog(dialog);scroll.post(()->scroll.scrollTo(0,settingsScrollY));
    }
    private void showInformation(){
        if(informationDialog!=null&&informationDialog.isShowing())return;
        String[][] sections={
            {"符号与单位","N：光圈 f 值\nt：曝光时间（秒）\nS：ISO 感光度\nd：ND 减光档数\nL：滤镜前照度（lx）","log₂ 表示以 2 为底的对数，2ᵈ 表示 2 的 d 次方。无 ND 时，d = 0。"},
            {"Lux 与 EV100","L = 2.5 × 2^EV100\nEV100 = log₂(L / 2.5)","EV100 是 ISO 100 下的曝光值。本 App 的 Lux 为相机测光换算的照度估算值，换算系数为 2.5。"},
            {"相机测光","EV100 = log₂(Nc² / tc) − log₂(Sc / 100)","Nc、tc、Sc 分别是手机相机的实际光圈 f 值、曝光时间和 ISO。主页选择的 ISO、ND 用于计算目标曝光组合，不改变测得的 EV100 和 Lux。"},
            {"RAW 测光","EVraw = EV相机 + log₂(G选区 / G参考)","RAW 模式读取同一帧的原始像素和曝光参数，扣除黑电平并按饱和值归一化，用绿色通道估计亮度。G参考来自相机实际自动曝光区域，无区域时使用画面平均。它沿用相机测光基准，未进行独立照度标定，Lux 仍为估算值。选区过暗、饱和或曝光尚未稳定时不输出读数。"},
            {"ND 减光","L' = L / 2ᵈ","L' 是经过 ND 后的有效照度。ND 每增加 1 档，通光量减半。下面的曝光公式均已计入 ND 影响。"},
            {"快门 · 曝光时间","t = (250 × N² × 2ᵈ) / (L × S)","光圈优先时，根据光圈、ISO 和 ND 计算曝光时间。t 的单位是秒，例如 1/125 秒。"},
            {"光圈","N = √[(L × S × t) / (250 × 2ᵈ)]","快门优先时，根据曝光时间、ISO 和 ND 计算光圈 f 值。f 值越小，实际光圈越大。"},
            {"ISO 感光度","S = (250 × N² × 2ᵈ) / (L × t)","固定照度、光圈、曝光时间和 ND 时，可据此反推 ISO。App 内的 ISO 由你手动选择。"},
            {"ND 档数","d = log₂[(L × S × t) / (250 × N²)]","固定照度、光圈、曝光时间和 ISO 时，可据此反推 ND 档数。App 内的 ND 由你手动选择。"},
            {"计算示例与档位","2000 lx · ISO 100 · f/4\n无 ND → 1/50 秒\nND 1 档 → 1/25 秒","自动计算结果会匹配当前组已启用的最近档位。超出范围时，显示红色边界值和刻度警告。"}
        };
        LinearLayout panel=column();panel.setPadding(dp(20),dp(8),dp(20),dp(8));
        LinearLayout author=card();author.setGravity(Gravity.CENTER_HORIZONTAL);author.setPadding(dp(14),dp(20),dp(14),dp(20));LinearLayout.LayoutParams authorLp=new LinearLayout.LayoutParams(-1,-2);authorLp.bottomMargin=dp(12);panel.addView(author,authorLp);
        TextView authorTitle=text("作者声明",14,MUTED);authorTitle.setGravity(Gravity.CENTER);author.addView(authorTitle);
        ImageView avatar=new ImageView(this);avatar.setImageResource(R.drawable.author_avatar);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setContentDescription("作者头像：吐着泡泡说爱你");
        avatar.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,android.graphics.Outline outline){outline.setOval(0,0,view.getWidth(),view.getHeight());}});avatar.setClipToOutline(true);
        GradientDrawable avatarBorder=new GradientDrawable();avatarBorder.setShape(GradientDrawable.OVAL);avatarBorder.setColor(Color.TRANSPARENT);avatarBorder.setStroke(dp(1),theme.border);avatar.setForeground(avatarBorder);
        LinearLayout.LayoutParams avatarLp=new LinearLayout.LayoutParams(dp(96),dp(96));avatarLp.topMargin=dp(14);avatarLp.bottomMargin=dp(12);author.addView(avatar,avatarLp);
        TextView authorName=text("吐着泡泡说爱你",17,FG);authorName.setGravity(Gravity.CENTER);author.addView(authorName);
        for(String[] section:sections){
            LinearLayout block=card();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);panel.addView(block,lp);
            block.addView(text(section[0],15,FG));
            TextView formula=text(section[1],14,0xffe3c17d);formula.setPadding(0,dp(10),0,dp(8));formula.setLineSpacing(dp(5),1);block.addView(formula);
            TextView explanation=text(section[2],12,MUTED);explanation.setLineSpacing(dp(4),1);block.addView(explanation);
        }
        ScrollView scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.addView(panel);
        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogTitle("信息")).setView(scroll).setPositiveButton("关闭",null).create();informationDialog=dialog;
        dialog.setOnDismissListener(v->informationDialog=null);dialog.show();styleDialog(dialog);
    }
    private void chooseTheme(){
        String[] names=new String[ThemeColors.OPTIONS.length];int selected=0;for(int i=0;i<names.length;i++){names[i]=ThemeColors.OPTIONS[i].name;if(ThemeColors.OPTIONS[i]==theme)selected=i;}
        selectValue("主题色",names,selected,i->{ThemeColors next=ThemeColors.OPTIONS[i];if(next==theme)return;prefs.edit().putInt(ThemeColors.PREFERENCE,i).apply();reopenSettings=true;if(settingsDialog!=null)settingsDialog.dismiss();recreate();});
    }
    private void styleDialog(AlertDialog dialog){
        if(dialog.getWindow()!=null){GradientDrawable bg=new GradientDrawable();bg.setColor(color(BG));bg.setCornerRadius(dp(20));dialog.getWindow().setBackgroundDrawable(bg);}
        for(int which:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button button=dialog.getButton(which);if(button!=null)button.setTextColor(theme.accentInk);}
    }
private String activeKey(String kind){String id=prefs.getString(kind+".active","");return id.isEmpty()?kind:kind+".group."+id+".";}
private ArrayList<String> groupIds(String kind){ArrayList<String> ids=new ArrayList<>(prefs.getStringSet(kind+".groups",Collections.emptySet()));Collections.sort(ids);ids.add(0,"");return ids;}
private String groupName(String kind,String id){return prefs.getString(kind+".name."+id,id.isEmpty()?"默认组":"未命名组");}
private void refreshStops(){fs=enabled(ALL_F,"f");ts=enabled(ALL_T,"t");aperture=nearest(fs,aperture);seconds=nearest(ts,seconds);save();render();}
private void createGroup(String kind){String prefix=kind.equals("f")?"镜头 ":"相机 ";Set<String> names=new HashSet<>();for(String id:groupIds(kind))names.add(groupName(kind,id));int n=1;while(names.contains(prefix+n))n++;String id=UUID.randomUUID().toString(),source=activeKey(kind);Set<String> ids=new HashSet<>(prefs.getStringSet(kind+".groups",Collections.emptySet()));ids.add(id);SharedPreferences.Editor edit=prefs.edit().putStringSet(kind+".groups",ids).putString(kind+".active",id).putString(kind+".name."+id,prefix+n);double[] all=kind.equals("f")?ALL_F:ALL_T;for(int i=0;i<all.length;i++)edit.putBoolean(kind+".group."+id+"."+i,prefs.getBoolean(source+i,true));edit.apply();refreshStops();}
private void chooseGroup(String kind,Runnable after){
    android.app.Dialog dialog=new android.app.Dialog(this);LinearLayout shell=column();shell.setPadding(dp(20),dp(20),dp(20),dp(16));
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text(kind.equals("f")?"选择镜头组":"选择相机组",19,FG),new LinearLayout.LayoutParams(0,dp(44),1));TextView close=text("✕",20,MUTED);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭组选择");header.addView(close,new LinearLayout.LayoutParams(dp(44),dp(44)));close.setOnClickListener(v->dialog.dismiss());shell.addView(header);
    TextView caption=text("选择常用器材的档位组合",12,MUTED);caption.setPadding(0,0,0,dp(16));shell.addView(caption);
    ScrollView scroll=new ScrollView(this);LinearLayout rows=column();scroll.addView(rows);shell.addView(scroll,new LinearLayout.LayoutParams(-1,-2));
    Runnable done=()->{dialog.dismiss();if(after!=null)after.run();};
    TextView create=groupOption("＋  新建组",false,true);rows.addView(create,new LinearLayout.LayoutParams(-1,dp(52)));create.setOnClickListener(v->{createGroup(kind);done.run();});
    String current=prefs.getString(kind+".active","");for(String id:groupIds(kind)){boolean selected=id.equals(current);TextView row=groupOption(groupName(kind,id)+(selected?"     ✓":""),selected,false);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,dp(52));rp.topMargin=dp(8);rows.addView(row,rp);row.setOnClickListener(v->{prefs.edit().putString(kind+".active",id).apply();refreshStops();done.run();});}
    dialog.setContentView(shell);android.view.Window window=dialog.getWindow();if(window!=null){GradientDrawable bg=new GradientDrawable();bg.setColor(color(BG));bg.setCornerRadius(dp(20));bg.setStroke(dp(1),color(0xff363c36));window.setBackgroundDrawable(bg);window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=window.getAttributes();attrs.dimAmount=.65f;window.setAttributes(attrs);}dialog.show();if(window!=null){int width=Math.min(getResources().getDisplayMetrics().widthPixels-dp(40),dp(420));window.setLayout(width,-2);scroll.getLayoutParams().height=Math.min(dp(60)*(groupIds(kind).size()+1),getResources().getDisplayMetrics().heightPixels/2);scroll.requestLayout();}
}
private TextView groupOption(String label,boolean selected,boolean create){TextView row=text(label,14,selected||create?0xffe3c17d:FG);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(14),0,dp(14),0);row.setSingleLine(true);row.setEllipsize(android.text.TextUtils.TruncateAt.END);GradientDrawable bg=new GradientDrawable();bg.setColor(color(selected?0xff302c22:0xff1c211d));bg.setCornerRadius(dp(8));bg.setStroke(dp(1),color(selected?0xff64563b:0xff363c36));row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color(0x33e3c17d)),bg,null));row.setClickable(true);row.setFocusable(true);row.setSelected(selected);return row;}
private void nameGroup(String kind,Runnable after){String currentId=prefs.getString(kind+".active","");EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(color(FG));input.setHintTextColor(color(MUTED));input.setBackgroundTintList(controlTint());input.setText(groupName(kind,currentId));AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogTitle("重命名档位组")).setView(input).setPositiveButton("保存",null).setNegativeButton("取消",null).create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(b->{String name=input.getText().toString().trim();if(name.isEmpty()){input.setError("请输入组名称");return;}for(String id:groupIds(kind))if(!id.equals(currentId)&&groupName(kind,id).equals(name)){input.setError("组名称已存在");return;}prefs.edit().putString(kind+".name."+currentId,name).apply();dialog.dismiss();after.run();}));dialog.show();styleDialog(dialog);}
private void groupSection(LinearLayout panel,String title,double[] all,String kind,boolean isF){LinearLayout section=card();LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);panel.addView(section,lp);populateGroupSection(section,title,all,kind,isF);}
private LinearLayout card(){LinearLayout c=column();c.setPadding(dp(14),dp(14),dp(14),dp(14));GradientDrawable bg=new GradientDrawable();bg.setColor(color(0xff1c211d));bg.setCornerRadius(dp(12));bg.setStroke(dp(1),color(0xff363c36));c.setBackground(bg);return c;}
private void styleFold(Button b,boolean open){b.setText(open?"收起档位  ▴":"展开编辑档位  ▾");b.setTextColor(color(0xffe3c17d));b.setBackgroundTintList(null);GradientDrawable bg=new GradientDrawable();bg.setColor(color(open?0xff25291f:0xff302c22));bg.setCornerRadius(dp(7));bg.setStroke(dp(1),color(open?0xff363c36:0xff64563b));b.setBackground(bg);}
private void populateGroupSection(LinearLayout section,String title,double[] all,String kind,boolean isF){section.removeAllViews();Runnable refresh=()->populateGroupSection(section,title,all,kind,isF);TextView heading=text(title,16,FG);section.addView(heading);TextView count=text(enabled(all,kind).length+" 档已启用 / 共 "+all.length+" 档",11,MUTED);count.setPadding(0,dp(5),0,dp(10));section.addView(count);LinearLayout actions=new LinearLayout(this);Button choose=button(groupName(kind,prefs.getString(kind+".active",""))+"  ▾");choose.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);choose.setBackgroundTintList(null);GradientDrawable selectorBg=new GradientDrawable();selectorBg.setColor(color(0xff141915));selectorBg.setCornerRadius(dp(7));selectorBg.setStroke(dp(1),color(0xff394236));choose.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(color(0x33e3c17d)),selectorBg,null));choose.setPadding(dp(12),0,dp(12),0);choose.setOnClickListener(v->chooseGroup(kind,refresh));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(46),1);cp.setMarginEnd(dp(8));actions.addView(choose,cp);Button rename=button("重命名");rename.setOnClickListener(v->nameGroup(kind,refresh));actions.addView(rename,new LinearLayout.LayoutParams(dp(80),dp(46)));section.addView(actions);LinearLayout list=column();stopChoices(list,title,all,kind,isF);boolean expanded=prefs.getBoolean(kind+".expanded",false);list.setVisibility(expanded?View.VISIBLE:View.GONE);Button fold=button("");styleFold(fold,expanded);fold.setOnClickListener(v->{boolean open=list.getVisibility()!=View.VISIBLE;list.setVisibility(open?View.VISIBLE:View.GONE);styleFold(fold,open);count.setText(enabled(all,kind).length+" 档已启用 / 共 "+all.length+" 档");prefs.edit().putBoolean(kind+".expanded",open).apply();});LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,dp(44));fp.topMargin=dp(10);section.addView(fold,fp);section.addView(list);}
private void stopChoices
(LinearLayout panel,String title,double[] all,String key,boolean isF){panel.addView(text(title,13,MUTED));for(int row=0;row<all.length;row+=3){LinearLayout line=new LinearLayout(this);for(int j=row;j<Math.min(row+3,all.length);j++){int index=j;CheckBox b=new CheckBox(this);b.setText(isF?apertureLabel(all[j]):shutterLabel(all[j]));b.setTextSize(11);b.setTextColor(color(FG));b.setButtonTintList(controlTint());b.setChecked(prefs.getBoolean(activeKey(key)+j,true));line.addView(b,new LinearLayout.LayoutParams(0,dp(44),1));b.setOnCheckedChangeListener((v,on)->{int count=0;for(int k=0;k<all.length;k++)if(k==index?on:prefs.getBoolean(activeKey(key)+k,true))count++;if(count==0){b.setChecked(true);Toast.makeText(this,"至少保留一个档位",Toast.LENGTH_SHORT).show();return;}prefs.edit().putBoolean(activeKey(key)+index,on).apply();fs=enabled(ALL_F,"f");ts=enabled(ALL_T,"t");aperture=nearest(fs,aperture);seconds=nearest(ts,seconds);save();render();});}panel.addView(line);}}
    private double[] zoomStops(){int count=Math.max(1,(int)Math.ceil((maxZoom-minZoom)*10)+1);double[] out=new double[count];for(int i=0;i<count;i++)out[i]=Math.min(maxZoom,minZoom+i*.1);return out;}
    private void renderZoom(){if(zoomDial==null)return;zoomDial.setVisibility(showZoom?View.VISIBLE:View.GONE);zoomDial.setEnabled(regionSupported&&maxZoom>minZoom);zoomDial.display("焦距 / 变焦",String.format(Locale.US,"%.1f×",zoom),zoomStops(),zoom,true,false,regionSupported?"左右拨动":rawActive?"焦距调节不可用":"不支持区域测光 · 调节已禁用",true);if(settingsCameraNote!=null)settingsCameraNote.setText(regionSupported?"与主页面实时同步":rawActive?"可点击预览选择 RAW 测光区域，焦距调节不可用":"此设备不支持区域测光，焦距调节已禁用");if(settingsZoomLabel!=null)settingsZoomLabel.setText(String.format(Locale.US,"焦距 / 变焦  %.1f×",zoom));if(settingsZoom!=null){settingsZoom.setEnabled(regionSupported&&maxZoom>minZoom);settingsZoom.setMax(Math.max(0,Math.round((maxZoom-minZoom)*10)));settingsZoom.setProgress(Math.round((zoom-minZoom)*10));}}
private void requestJpegReading(){
    if(jpegCapture==null||jpegBusy||!foreground)return;
    jpegBusy=true;int generation=captureGeneration;
    jpegCapture.takePicture(ContextCompat.getMainExecutor(this),new ImageCapture.OnImageCapturedCallback(){
        @Override public void onCaptureSuccess(ImageProxy image){
            try{if(generation!=captureGeneration)return;java.nio.ByteBuffer buffer=image.getPlanes()[0].getBuffer();byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);
                android.media.ExifInterface exif=new android.media.ExifInterface(new java.io.ByteArrayInputStream(bytes));
                double f=exif.getAttributeDouble(android.media.ExifInterface.TAG_F_NUMBER,0),time=exif.getAttributeDouble(android.media.ExifInterface.TAG_EXPOSURE_TIME,0);int iso=exif.getAttributeInt(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS,0);
                double ev=ExposureCalculator.cameraEv((float)f,iso,(long)(time*1e9));
                jpegEv=Double.isFinite(ev)?Math.round(ev*10)/10.0:Double.NaN;
                if(Double.isFinite(jpegEv))lastFrameTime=SystemClock.elapsedRealtime();
            }catch(java.io.IOException|RuntimeException error){jpegEv=Double.NaN;android.util.Log.w("Lux","JPEG metering failed",error);}finally{image.close();if(generation==captureGeneration)jpegBusy=false;}
        }
        @Override public void onError(ImageCaptureException error){if(generation==captureGeneration){jpegBusy=false;jpegEv=Double.NaN;}android.util.Log.w("Lux","JPEG capture failed",error);}
    });
}
private void updateRawControls(){
    if(settingsRawSection==null)return;
    settingsRawSection.setVisibility(rawSupported?View.VISIBLE:View.GONE);
    settingsRawSwitch.setEnabled(camera!=null&&!cameraStarting);
    settingsRawSwitch.setChecked(rawEnabled);
}
private void setRawMode(boolean enabled){
    if(enabled&&!rawSupported)return;
    rawEnabled=enabled;prefs.edit().putBoolean("rawMetering",enabled).apply();
    releaseCamera();currentEv=Double.NaN;current=Float.NaN;maximum=0;
    meterStatus=enabled?"正在开启 RAW":"等待相机测光";
    startCamera();updateRawControls();render();
}
private void requestRawReading(){
    if(rawCapture==null||rawMeter==null||rawBusy||!rawActive||!foreground||paused)return;
    Matrix sensorToView=preview.getSensorToViewTransform(),viewToSensor=new Matrix();
    if(sensorToView==null||!sensorToView.invert(viewToSensor)){meterStatus="RAW 等待预览就绪";return;}
    float x=spotX*preview.getWidth(),y=spotY*preview.getHeight(),r=dp(18);
    RectF selected=new RectF(Math.max(0,x-r),Math.max(0,y-r),Math.min(preview.getWidth(),x+r),Math.min(preview.getHeight(),y+r));
    viewToSensor.mapRect(selected);
    int generation=captureGeneration,revision=rawRevision;RawMeter meter=rawMeter;rawBusy=true;
    rawCapture.takePicture(ContextCompat.getMainExecutor(this),new ImageCapture.OnImageCapturedCallback(){
        @Override public void onCaptureSuccess(ImageProxy image){
            if(generation!=captureGeneration||!foreground){image.close();return;}
            rawExecutor.execute(()->{
                RawMeter.Reading reading;
                try{reading=meter.read(image,selected);}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();reading=RawMeter.Reading.invalid("RAW 测光已停止");}
                catch(RuntimeException error){android.util.Log.w("Lux","RAW processing failed",error);reading=RawMeter.Reading.invalid("RAW 数据处理失败");}
                finally{image.close();}
                RawMeter.Reading result=reading;
                handler.post(()->{
                    if(generation!=captureGeneration||!foreground||!rawActive)return;
                    rawBusy=false;
                    if(revision!=rawRevision){if(!paused)requestRawReading();return;}
                    if(paused)return;
                    currentEv=result.ev();current=Double.isFinite(currentEv)?(float)(Math.pow(2,currentEv)*2.5):Float.NaN;
                    if(Float.isFinite(current))maximum=Math.max(maximum,current);
                    meterStatus=result.status();render();
                });
            });
        }
        @Override public void onError(ImageCaptureException error){
            android.util.Log.w("Lux","RAW capture failed",error);
            if(generation!=captureGeneration||!foreground||!rawActive)return;
            rawBusy=false;if(paused||revision!=rawRevision)return;
            currentEv=Double.NaN;current=Float.NaN;meterStatus="RAW 采集失败";render();
        }
    });
}
private void meterAt(float x,float y,boolean user){
    if(camera==null)return;
    if(rawActive){
        spotX=Math.max(0,Math.min(1,x/preview.getWidth()));spotY=Math.max(0,Math.min(1,y/preview.getHeight()));
        rawRevision++;spotMarker.invalidate();
        if(!paused){currentEv=Double.NaN;current=Float.NaN;meterStatus="RAW 测光中";requestRawReading();render();}
        return;
    }
    if(!regionSupported){if(user)Toast.makeText(this,"此摄像头不支持区域测光",Toast.LENGTH_SHORT).show();return;}
    MeteringPoint point=preview.getMeteringPointFactory().createPoint(x,y,.1f);
    FocusMeteringAction action=new FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AE|FocusMeteringAction.FLAG_AF).disableAutoCancel().build();
    if(!camera.getCameraInfo().isFocusMeteringSupported(action)){if(user)Toast.makeText(this,"此位置不支持区域测光",Toast.LENGTH_SHORT).show();return;}
    int request=++spotRequest,generation=captureGeneration;
    ListenableFuture<FocusMeteringResult> future=camera.getCameraControl().startFocusAndMetering(action);
    future.addListener(()->{if(request!=spotRequest||generation!=captureGeneration||!foreground)return;try{future.get();spotX=Math.max(0,Math.min(1,x/preview.getWidth()));spotY=Math.max(0,Math.min(1,y/preview.getHeight()));spotMarker.invalidate();jpegEv=Double.NaN;}catch(Exception e){android.util.Log.w("Lux","Region metering failed",e);if(user)Toast.makeText(this,"区域测光失败，请重试",Toast.LENGTH_SHORT).show();}},ContextCompat.getMainExecutor(this));
}
private void applyZoom

(float target){if(camera==null||!regionSupported)return;int generation=captureGeneration;float next=Math.max(minZoom,Math.min(maxZoom,target));ListenableFuture<Void> result=camera.getCameraControl().setZoomRatio(next);result.addListener(()->{if(generation!=captureGeneration||!foreground)return;try{result.get();zoom=next;save();renderZoom();if(rawActive){rawRevision++;if(!paused){currentEv=Double.NaN;current=Float.NaN;requestRawReading();render();}}}catch(Exception e){android.util.Log.w("Lux","Zoom update failed",e);}},ContextCompat.getMainExecutor(this));}
    @androidx.annotation.OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
    private void startCamera(){
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED||cameraStarting||camera!=null)return;
        cameraStarting=true;updateRawControls();ListenableFuture<ProcessCameraProvider> future=ProcessCameraProvider.getInstance(this);
        future.addListener(()->{cameraStarting=false;if(!foreground)return;try{
            provider=future.get();CameraSelector selector=CameraSelector.DEFAULT_BACK_CAMERA;
            if(!provider.hasCamera(selector))selector=CameraSelector.DEFAULT_FRONT_CAMERA;
            if(!provider.hasCamera(selector)){cameraMessage.setText("此设备没有可用相机");return;}
            CameraInfo info=provider.getCameraInfo(selector);
            CameraCharacteristics characteristics=getSystemService(CameraManager.class).getCameraCharacteristics(Camera2CameraInfo.from(info).getCameraId());
            rawSupported=ImageCapture.getImageCaptureCapabilities(info).getSupportedOutputFormats().contains(ImageCapture.OUTPUT_FORMAT_RAW)&&RawMeter.supports(characteristics);
            if(rawEnabled&&!rawSupported){rawEnabled=false;prefs.edit().putBoolean("rawMetering",false).apply();}
            boolean useRaw=rawEnabled;RawMeter meter=useRaw?new RawMeter(characteristics):null;rawMeter=meter;
            int generation=++captureGeneration;Preview.Builder builder=new Preview.Builder();
            ImageCapture.Builder captureBuilder=new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setOutputFormat(useRaw?ImageCapture.OUTPUT_FORMAT_RAW:ImageCapture.OUTPUT_FORMAT_JPEG);
            CameraCaptureSession.CaptureCallback captureCallback=new CameraCaptureSession.CaptureCallback(){
            @Override public void onCaptureCompleted(CameraCaptureSession session,CaptureRequest request,TotalCaptureResult result){
                if(generation!=captureGeneration)return;
                if(meter!=null){meter.capture(result);return;}
                Integer sensitivity=result.get(CaptureResult.SENSOR_SENSITIVITY);Float lens=result.get(CaptureResult.LENS_APERTURE);Long time=result.get(CaptureResult.SENSOR_EXPOSURE_TIME);
                double ev=ExposureCalculator.cameraEv(lens,sensitivity,time);
                latestEv=ev;
                handler.post(()->{if(!foreground||generation!=captureGeneration)return;lastFrameTime=SystemClock.elapsedRealtime();meterStatus=Double.isFinite(ev)?"相机测光":"相机未提供有效曝光参数";});
            }
            };
            Camera2Interop.Extender<Preview> previewOptions=new Camera2Interop.Extender<>(builder);previewOptions.setSessionCaptureCallback(captureCallback);
            if(useRaw){
                Camera2Interop.Extender<ImageCapture> captureOptions=new Camera2Interop.Extender<>(captureBuilder);captureOptions.setSessionCaptureCallback(captureCallback);
                if(Build.VERSION.SDK_INT>=28){
                    int[] distortion=characteristics.get(CameraCharacteristics.DISTORTION_CORRECTION_AVAILABLE_MODES);
                    if(distortion!=null)for(int mode:distortion)if(mode==CaptureRequest.DISTORTION_CORRECTION_MODE_OFF){previewOptions.setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE,mode);captureOptions.setCaptureRequestOption(CaptureRequest.DISTORTION_CORRECTION_MODE,mode);break;}
                }
                int[] shading=characteristics.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_LENS_SHADING_MAP_MODES);
                if(shading!=null)for(int mode:shading)if(mode==CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE_ON){captureOptions.setCaptureRequestOption(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE,mode);break;}
            }
            Preview useCase=builder.build();useCase.setSurfaceProvider(preview.getSurfaceProvider());provider.unbindAll();ImageCapture capture=captureBuilder.build();
            camera=provider.bindToLifecycle(this,selector,useCase,capture);rawActive=useRaw;
            if(useRaw){rawCapture=capture;jpegCapture=null;meterStatus="RAW 等待曝光稳定";}else{jpegCapture=capture;rawCapture=null;}
            Integer regions=characteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE);regionSupported=regions!=null&&regions>0;
            ZoomState z=camera.getCameraInfo().getZoomState().getValue();if(z!=null){minZoom=z.getMinZoomRatio();maxZoom=z.getMaxZoomRatio();}
            if(regionSupported)applyZoom(zoom);else zoom=z==null?1:z.getZoomRatio();
            cameraMessage.setVisibility(View.GONE);spotMarker.invalidate();updateRawControls();
            preview.post(()->{if(generation!=captureGeneration||!foreground||camera==null)return;if(rawActive)startRawReference();else meterAt(preview.getWidth()*.5f,preview.getHeight()*.5f,false);});
            render();
        }catch(Exception e){
            android.util.Log.e("Lux","Camera start failed",e);boolean failedRaw=rawEnabled;releaseCamera();
            if(failedRaw){rawEnabled=false;prefs.edit().putBoolean("rawMetering",false).apply();Toast.makeText(this,"RAW 启用失败，正在切回普通测光",Toast.LENGTH_SHORT).show();startCamera();}
            else{cameraMessage.setVisibility(View.VISIBLE);cameraMessage.setText("相机启动失败，点击重试");meterStatus="相机启动失败";}
            updateRawControls();render();
        }},ContextCompat.getMainExecutor(this));
    }
    private void startRawReference(){
        if(regionSupported){
            MeteringPoint point=preview.getMeteringPointFactory().createPoint(preview.getWidth()*.5f,preview.getHeight()*.5f,.1f);
            FocusMeteringAction action=new FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AE).disableAutoCancel().build();
            if(camera.getCameraInfo().isFocusMeteringSupported(action)){
                ListenableFuture<FocusMeteringResult> result=camera.getCameraControl().startFocusAndMetering(action);
                result.addListener(()->{try{result.get();}catch(Exception error){android.util.Log.w("Lux","RAW reference region unavailable",error);}},ContextCompat.getMainExecutor(this));
            }
        }
        requestRawReading();
    }
    private void releaseCamera(){
        captureGeneration++;rawRevision++;rawActive=false;rawBusy=false;rawCapture=null;
        if(rawMeter!=null){rawMeter.close();rawMeter=null;}
        jpegCapture=null;jpegBusy=false;jpegEv=Double.NaN;latestEv=Double.NaN;
        if(provider!=null)provider.unbindAll();camera=null;regionSupported=false;
    }
    @Override protected void onDestroy(){if(settingsDialog!=null)settingsDialog.dismiss();if(informationDialog!=null)informationDialog.dismiss();rawExecutor.shutdown();super.onDestroy();}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){super.onRequestPermissionsResult(code,permissions,results);if(code==10){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)startCamera();else cameraMessage.setText("未授权相机 · 点击重试或在系统设置开启");}}
    @Override protected void onResume(){super.onResume();foreground=true;latestEv=Double.NaN;meterStatus="等待相机测光";handler.post(sample);startCamera();}
    @Override protected void onPause(){foreground=false;updateScreenAwake();handler.removeCallbacks(sample);releaseCamera();super.onPause();}
    @Override protected void onSaveInstanceState(Bundle b){b.putBoolean("settingsOpen",reopenSettings||(settingsDialog!=null&&settingsDialog.isShowing()));b.putBoolean("informationOpen",informationDialog!=null&&informationDialog.isShowing());b.putInt("settingsScrollY",settingsScroll==null?settingsScrollY:settingsScroll.getScrollY());b.putDouble("ev",currentEv);b.putFloat("current",current);b.putFloat("maximum",maximum);b.putBoolean("paused",paused);super.onSaveInstanceState(b);}
}
