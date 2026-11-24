package com.syl.lux;

import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.BlackLevelPattern;
import android.hardware.camera2.params.LensShadingMap;
import android.hardware.camera2.params.MeteringRectangle;
import android.os.Build;
import android.os.SystemClock;
import android.util.Size;
import androidx.camera.core.ImageProxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashMap;

/** RAW spot readings use the camera's AE reading as their reference, not a fixed RAW gray value. */
final class RawMeter implements AutoCloseable {
    static final String[] MODES={"点测光","中央重点测光","平均测光"};
    record Reading(double ev,String status) {
        static Reading invalid(String status){return new Reading(Double.NaN,status);}
    }
    record Frame(long timestamp,double ev,Integer aeState,float[] black,int white,
                 Rect crop,float zoom,MeteringRectangle[] regions,LensShadingMap shading,boolean uncorrected) { }
    private final CameraCharacteristics characteristics;
    private final LinkedHashMap<Long,Frame> frames=new LinkedHashMap<>();
    private boolean closed;

    RawMeter(CameraCharacteristics characteristics){this.characteristics=characteristics;}

    static boolean supports(CameraCharacteristics c){
        int[] caps=c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        boolean raw=false;if(caps!=null)for(int cap:caps)if(cap==CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)raw=true;
        Integer cfa=c.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        return raw&&cfa!=null&&(cfa>=CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB&&cfa<=CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR||Build.VERSION.SDK_INT>=29&&cfa==CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO)
            &&c.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN)!=null&&c.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL)!=null
            &&c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)!=null&&c.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE)!=null;
    }

    synchronized void capture(CaptureResult result){
        if(closed)return;
        Long timestamp=result.get(CaptureResult.SENSOR_TIMESTAMP);if(timestamp==null)return;
        float[] black=result.get(CaptureResult.SENSOR_DYNAMIC_BLACK_LEVEL);
        if(black==null){
            BlackLevelPattern pattern=characteristics.get(CameraCharacteristics.SENSOR_BLACK_LEVEL_PATTERN);
            black=new float[4];for(int i=0;i<4;i++)black[i]=pattern.getOffsetForIndex(i%2,i/2);
        }
        Integer white=result.get(CaptureResult.SENSOR_DYNAMIC_WHITE_LEVEL);
        if(white==null)white=characteristics.get(CameraCharacteristics.SENSOR_INFO_WHITE_LEVEL);
        Float ratio=Build.VERSION.SDK_INT>=30?result.get(CaptureResult.CONTROL_ZOOM_RATIO):null;
        Integer distortion=Build.VERSION.SDK_INT>=28?result.get(CaptureResult.DISTORTION_CORRECTION_MODE):null;
        double ev=ExposureCalculator.cameraEv(result.get(CaptureResult.LENS_APERTURE),result.get(CaptureResult.SENSOR_SENSITIVITY),result.get(CaptureResult.SENSOR_EXPOSURE_TIME));
        Frame frame=new Frame(timestamp,ev,result.get(CaptureResult.CONTROL_AE_STATE),black,white,
            result.get(CaptureResult.SCALER_CROP_REGION),ratio==null?1:ratio,result.get(CaptureResult.CONTROL_AE_REGIONS),
            result.get(CaptureResult.STATISTICS_LENS_SHADING_CORRECTION_MAP),Build.VERSION.SDK_INT>=28&&distortion!=null&&distortion==CaptureResult.DISTORTION_CORRECTION_MODE_OFF);
        frames.put(timestamp,frame);
        while(frames.size()>64)frames.remove(frames.keySet().iterator().next());
        notifyAll();
    }

    private synchronized Frame frame(long timestamp) throws InterruptedException {
        long deadline=SystemClock.elapsedRealtime()+1500;
        while(!closed&&!frames.containsKey(timestamp)){
            long remaining=deadline-SystemClock.elapsedRealtime();if(remaining<=0)return null;wait(remaining);
        }
        return frames.remove(timestamp);
    }

    Reading read(ImageProxy image,RectF selection,RectF visible,int mode) throws InterruptedException {
        if(image.getFormat()!=ImageFormat.RAW_SENSOR)return Reading.invalid("RAW 图像格式不可用");
        Frame frame=frame(image.getImageInfo().getTimestamp());
        if(frame==null||!Double.isFinite(frame.ev))return Reading.invalid("RAW 曝光参数未就绪");
        if(frame.aeState==null||frame.aeState!=CaptureResult.CONTROL_AE_STATE_CONVERGED&&frame.aeState!=CaptureResult.CONTROL_AE_STATE_LOCKED)
            return Reading.invalid("RAW 等待曝光稳定");
        Rect active=characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        Rect pre=characteristics.get(CameraCharacteristics.SENSOR_INFO_PRE_CORRECTION_ACTIVE_ARRAY_SIZE);
        Size pixels=characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
        Rect coordinates=frame.uncorrected?pre:active;
        RectF crop=frame.crop==null?new RectF(coordinates):new RectF(frame.crop);
        RectF physicalCrop=unzoom(crop,coordinates,frame.zoom);
        RectF spot=mapSelection(selection,active,physicalCrop);
        RectF visibleRaw=mapSelection(visible,active,physicalCrop);
        RectF rawBounds=pixels!=null&&image.getWidth()==pixels.getWidth()&&image.getHeight()==pixels.getHeight()
            ?new RectF(0,0,pixels.getWidth(),pixels.getHeight()):new RectF(pre);
        Matrix sensorToRaw=new Matrix();sensorToRaw.setRectToRect(rawBounds,new RectF(0,0,image.getWidth(),image.getHeight()),Matrix.ScaleToFit.FILL);
        ImageProxy.PlaneProxy plane=image.getPlanes()[0];
        int cfa=characteristics.get(CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT);
        Stats selected=modeSample(plane.getBuffer(),image.getWidth(),image.getHeight(),plane.getRowStride(),plane.getPixelStride(),
            bufferRect(spot,sensorToRaw),bufferRect(visibleRaw,sensorToRaw),mode,frame.black,frame.white,cfa,frame.shading,bufferRect(new RectF(pre),sensorToRaw));
        if(!selected.valid())return Reading.invalid(selected.clipped>.02?"RAW 选区过亮":"RAW 选区光线不足");
        double total=0,weights=0;
        if(frame.regions!=null)for(MeteringRectangle region:frame.regions){
            if(region.getMeteringWeight()==0)continue;
            RectF reference=unzoom(new RectF(region.getRect()),coordinates,frame.zoom);
            if(!reference.intersect(physicalCrop))continue;
            Stats s=sample(plane.getBuffer(),image.getWidth(),image.getHeight(),plane.getRowStride(),plane.getPixelStride(),
                bufferRect(reference,sensorToRaw),frame.black,frame.white,cfa,frame.shading,bufferRect(new RectF(pre),sensorToRaw));
            if(!s.valid())return Reading.invalid("RAW 参考区域超出测量范围");
            double weight=reference.width()*reference.height()*region.getMeteringWeight();total+=s.mean*weight;weights+=weight;
        }
        if(weights==0){
            Stats reference=sample(plane.getBuffer(),image.getWidth(),image.getHeight(),plane.getRowStride(),plane.getPixelStride(),
                bufferRect(physicalCrop,sensorToRaw),frame.black,frame.white,cfa,frame.shading,bufferRect(new RectF(pre),sensorToRaw));
            if(!reference.valid())return Reading.invalid("RAW 参考区域超出测量范围");
            total=reference.mean;weights=1;
        }
        return new Reading(correctedEv(frame.ev,selected.mean,total/weights),"RAW · "+MODES[mode]);
    }

    static double correctedEv(double referenceEv,double selected,double reference){
        if(!Double.isFinite(referenceEv)||!Double.isFinite(selected)||!Double.isFinite(reference)||selected<=0||reference<=0)return Double.NaN;
        return referenceEv+Math.log(selected/reference)/Math.log(2);
    }
    static RectF unzoom(RectF rect,Rect array,float zoom){
        float cx=array.exactCenterX(),cy=array.exactCenterY();
        return new RectF(cx+(rect.left-cx)/zoom,cy+(rect.top-cy)/zoom,cx+(rect.right-cx)/zoom,cy+(rect.bottom-cy)/zoom);
    }
    static RectF mapSelection(RectF nominal,Rect active,RectF crop){
        return new RectF(crop.left+(nominal.left-active.left)/active.width()*crop.width(),
            crop.top+(nominal.top-active.top)/active.height()*crop.height(),
            crop.left+(nominal.right-active.left)/active.width()*crop.width(),
            crop.top+(nominal.bottom-active.top)/active.height()*crop.height());
    }
    private static RectF bufferRect(RectF rect,Matrix transform){RectF out=new RectF(rect);transform.mapRect(out);return out;}

    record Stats(double mean,double clipped,int count) {
        boolean valid(){return count>=16&&Double.isFinite(mean)&&mean>1e-4&&clipped<=.02;}
    }
    static Stats modeSample(ByteBuffer buffer,int width,int height,int rowStride,int pixelStride,RectF spot,RectF visible,int mode,
                            float[] black,int white,int cfa,LensShadingMap shading,RectF active){
        if(mode==2)return sample(buffer,width,height,rowStride,pixelStride,visible,black,white,cfa,shading,active);
        Stats center=sampleMasked(buffer,width,height,rowStride,pixelStride,spot,black,white,cfa,shading,active,spot,false);
        if(mode==0)return center;
        Stats outer=sampleMasked(buffer,width,height,rowStride,pixelStride,visible,black,white,cfa,shading,active,spot,true);
        return new Stats(.75*center.mean+.25*outer.mean,.75*center.clipped+.25*outer.clipped,Math.min(center.count,outer.count));
    }
    static Stats sample(ByteBuffer buffer,int width,int height,int rowStride,int pixelStride,RectF region,
                        float[] black,int white,int cfa,LensShadingMap shading,RectF active){
        return sampleMasked(buffer,width,height,rowStride,pixelStride,region,black,white,cfa,shading,active,null,false);
    }
    private static Stats sampleMasked(ByteBuffer buffer,int width,int height,int rowStride,int pixelStride,RectF region,
                        float[] black,int white,int cfa,LensShadingMap shading,RectF active,RectF ellipse,boolean outside){
        RectF r=new RectF(region);
        if(!r.intersect(0,0,width,height))return new Stats(Double.NaN,0,0);
        int left=((int)Math.ceil(r.left)+1)&~1,top=((int)Math.ceil(r.top)+1)&~1;
        int right=Math.min(width,(int)Math.floor(r.right)),bottom=Math.min(height,(int)Math.floor(r.bottom));
        int step=Math.max(2,(int)Math.ceil(Math.max(r.width(),r.height())/128)*2);
        ByteBuffer data=buffer.duplicate().order(ByteOrder.nativeOrder());int base=data.position();
        if(pixelStride<2||rowStride<width*pixelStride||black.length!=4||base+(long)(height-1)*rowStride+(long)(width-1)*pixelStride+2>data.limit())
            throw new IllegalArgumentException("Invalid RAW plane layout");
        double sum=0;int count=0,clipped=0;
        boolean mono=Build.VERSION.SDK_INT>=29&&cfa==CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_MONO;
        boolean greenOdd=cfa==CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_RGGB||cfa==CameraCharacteristics.SENSOR_INFO_COLOR_FILTER_ARRANGEMENT_BGGR;
        for(int y=top;y+1<bottom;y+=step)for(int x=left;x+1<right;x+=step)for(int i=0;i<4;i++){
            int col=i%2,row=i/2;if(!mono&&((col+row)%2==1)!=greenOdd)continue;
            if(ellipse!=null){double nx=(x+col+.5-ellipse.centerX())/(ellipse.width()/2),ny=(y+row+.5-ellipse.centerY())/(ellipse.height()/2);if((nx*nx+ny*ny<=1)==outside)continue;}
            if(!Float.isFinite(black[i])||white<=black[i])throw new IllegalArgumentException("Invalid RAW black/white levels");
            int raw=data.getShort(base+(y+row)*rowStride+(x+col)*pixelStride)&0xffff;
            double level=(raw-black[i])/(white-black[i]);
            if(level>=.98)clipped++;
            double gain=shading==null||mono?1:shadingGain(shading,row==0?1:2,x+col,y+row,active);
            sum+=Math.max(0,level)*gain;count++;
        }
        return new Stats(count==0?Double.NaN:sum/count,count==0?0:clipped/(double)count,count);
    }
    private static double shadingGain(LensShadingMap map,int channel,float x,float y,RectF active){
        float gx=Math.max(0,Math.min(map.getColumnCount()-1,(x-active.left)/active.width()*(map.getColumnCount()-1)));
        float gy=Math.max(0,Math.min(map.getRowCount()-1,(y-active.top)/active.height()*(map.getRowCount()-1)));
        int x0=(int)gx,y0=(int)gy,x1=Math.min(x0+1,map.getColumnCount()-1),y1=Math.min(y0+1,map.getRowCount()-1);
        double a=map.getGainFactor(channel,x0,y0)*(1-(gx-x0))+map.getGainFactor(channel,x1,y0)*(gx-x0);
        double b=map.getGainFactor(channel,x0,y1)*(1-(gx-x0))+map.getGainFactor(channel,x1,y1)*(gx-x0);
        return a*(1-(gy-y0))+b*(gy-y0);
    }
    @Override public synchronized void close(){closed=true;frames.clear();notifyAll();}
}
