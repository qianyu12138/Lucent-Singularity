package com.qianyu.lux;

import android.graphics.SurfaceTexture;
import android.opengl.*;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.ProcessingException;
import androidx.camera.core.SurfaceOutput;
import androidx.camera.core.SurfaceProcessor;
import androidx.camera.core.SurfaceRequest;
import androidx.core.util.Consumer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.Executor;

/** Preview-only exposure simulation; capture and metering retain the unmodified camera data. */
final class ExposurePreview implements SurfaceProcessor,AutoCloseable {
    private static final String VERTEX="attribute vec2 pos; attribute vec2 uv; uniform mat4 transform; varying vec2 tex; void main(){gl_Position=vec4(pos,0.,1.);tex=(transform*vec4(uv,0.,1.)).xy;}";
    private static final String FRAGMENT="""
        #extension GL_OES_EGL_image_external : require
        precision highp float;
        uniform samplerExternalOES image;
        uniform float gain;
        varying vec2 tex;
        vec3 linearize(vec3 c){return mix(c/12.92,pow((c+0.055)/1.055,vec3(2.4)),step(vec3(0.04045),c));}
        vec3 encode(vec3 c){return mix(c*12.92,1.055*pow(c,vec3(1.0/2.4))-0.055,step(vec3(0.0031308),c));}
        void main(){vec3 c=texture2D(image,tex).rgb;gl_FragColor=vec4(encode(clamp(linearize(c)*gain,0.,1.)),1.);}
        """;
    private final HandlerThread thread=new HandlerThread("ExposurePreview");
    private final Handler handler;
    private final Executor executor;
    private final Consumer<Throwable> errors;
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(16*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final ArrayList<Input> inputs=new ArrayList<>();
    private final LinkedHashMap<SurfaceOutput,EGLSurface> outputs=new LinkedHashMap<>();
    private final LinkedHashMap<Long,Double> frameEvs=new LinkedHashMap<>();
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface parking=EGL14.EGL_NO_SURFACE;
    private EGLConfig config;
    private int program,positionLocation,uvLocation,transformLocation,gainLocation;
    private Input active;
    private double targetEv=Double.NaN,calibration;
    private boolean closed,failed;
    private final float[] original=new float[16],transformed=new float[16];

    private final class Input {
        final int texture;
        final SurfaceTexture frames;
        final Surface surface;
        boolean hasFrame;
        Input(int texture,SurfaceTexture frames){this.texture=texture;this.frames=frames;surface=new Surface(frames);}
    }

    ExposurePreview(Consumer<Throwable> errors){
        this.errors=errors;thread.start();handler=new Handler(thread.getLooper());executor=command->handler.post(command);
        vertices.put(new float[]{-1,-1,0,0, 1,-1,1,0, -1,1,0,1, 1,1,1,1}).position(0);
    }

    CameraEffect effect(){return new CameraEffect(CameraEffect.PREVIEW,executor,this,this::fail){};}

    void setTarget(double ev,double correction){handler.post(()->{targetEv=ev;calibration=correction;draw();});}

    void capture(long timestamp,double ev){handler.post(()->{
        if(closed||failed)return;
        frameEvs.put(timestamp,ev);while(frameEvs.size()>64)frameEvs.remove(frameEvs.keySet().iterator().next());draw();
    });}

    @Override public void onInputSurface(SurfaceRequest request)throws ProcessingException {
        if(closed||failed){request.willNotProvideSurface();return;}
        try{
            initialize();park();int[] texture=new int[1];GLES20.glGenTextures(1,texture,0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
            SurfaceTexture frames=new SurfaceTexture(texture[0]);frames.setDefaultBufferSize(request.getResolution().getWidth(),request.getResolution().getHeight());
            Input input=new Input(texture[0],frames);inputs.add(input);active=input;
            frames.setOnFrameAvailableListener(value->{if(closed||failed||active!=input)return;try{park();value.updateTexImage();input.hasFrame=true;draw();}catch(RuntimeException e){fail(e);}},handler);
            request.provideSurface(input.surface,executor,result->{
                frames.setOnFrameAvailableListener(null);frames.release();input.surface.release();
                if(display!=EGL14.EGL_NO_DISPLAY){park();GLES20.glDeleteTextures(1,new int[]{input.texture},0);}
                inputs.remove(input);if(active==input)active=null;finishClose();
            });
        }catch(RuntimeException e){request.willNotProvideSurface();fail(e);ProcessingException failure=new ProcessingException();failure.initCause(e);throw failure;}
    }

    @Override public void onOutputSurface(SurfaceOutput output)throws ProcessingException {
        if(closed||failed){output.close();return;}
        try{
            initialize();Surface surface=output.getSurface(executor,event->remove(output));
            EGLSurface window=EGL14.eglCreateWindowSurface(display,config,surface,new int[]{EGL14.EGL_NONE},0);
            if(window==EGL14.EGL_NO_SURFACE)throw new IllegalStateException("Cannot create exposure preview output");
            outputs.put(output,window);
        }catch(RuntimeException e){output.close();fail(e);ProcessingException failure=new ProcessingException();failure.initCause(e);throw failure;}
    }

    private void draw(){
        if(closed||failed||active==null||!active.hasFrame||outputs.isEmpty())return;
        Double cameraEv=frameEvs.get(active.frames.getTimestamp());
        if(cameraEv==null||!Double.isFinite(cameraEv)||!Double.isFinite(targetEv))return;
        try{
            float gain=(float)Math.pow(2,Math.max(-20,Math.min(20,ExposureCalculator.previewStops(cameraEv,targetEv,calibration))));
            active.frames.getTransformMatrix(original);
            for(var entry:outputs.entrySet()){
                SurfaceOutput output=entry.getKey();
                if(!EGL14.eglMakeCurrent(display,entry.getValue(),entry.getValue(),context))throw new IllegalStateException("Cannot draw exposure preview");
                GLES20.glViewport(0,0,output.getSize().getWidth(),output.getSize().getHeight());
                GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,active.texture);
                vertices.position(0);GLES20.glVertexAttribPointer(positionLocation,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(positionLocation);
                vertices.position(2);GLES20.glVertexAttribPointer(uvLocation,2,GLES20.GL_FLOAT,false,16,vertices);GLES20.glEnableVertexAttribArray(uvLocation);
                output.updateTransformMatrix(transformed,original);GLES20.glUniformMatrix4fv(transformLocation,1,false,transformed,0);GLES20.glUniform1f(gainLocation,gain);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
                if(!EGL14.eglSwapBuffers(display,entry.getValue()))throw new IllegalStateException("Cannot present exposure preview");
            }
        }catch(RuntimeException e){fail(e);}
    }

    private void initialize(){
        if(display!=EGL14.EGL_NO_DISPLAY)return;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];if(!EGL14.eglInitialize(display,version,0,version,1))throw new IllegalStateException("Cannot initialize exposure renderer");
        EGLConfig[] choices=new EGLConfig[1];int[] count=new int[1];
        int[] attributes={EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_WINDOW_BIT|EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE};
        if(!EGL14.eglChooseConfig(display,attributes,0,choices,0,1,count,0)||count[0]==0)throw new IllegalStateException("No exposure renderer configuration");config=choices[0];
        context=EGL14.eglCreateContext(display,config,EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        if(context==EGL14.EGL_NO_CONTEXT)throw new IllegalStateException("Cannot create exposure renderer context");
        parking=EGL14.eglCreatePbufferSurface(display,config,new int[]{EGL14.EGL_WIDTH,1,EGL14.EGL_HEIGHT,1,EGL14.EGL_NONE},0);park();
        int vertex=shader(GLES20.GL_VERTEX_SHADER,VERTEX),fragment=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vertex);GLES20.glAttachShader(program,fragment);GLES20.glLinkProgram(program);
        GLES20.glDeleteShader(vertex);GLES20.glDeleteShader(fragment);int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
        if(ok[0]==0)throw new IllegalStateException(GLES20.glGetProgramInfoLog(program));
        positionLocation=GLES20.glGetAttribLocation(program,"pos");uvLocation=GLES20.glGetAttribLocation(program,"uv");transformLocation=GLES20.glGetUniformLocation(program,"transform");gainLocation=GLES20.glGetUniformLocation(program,"gain");
    }

    private int shader(int kind,String source){int shader=GLES20.glCreateShader(kind);GLES20.glShaderSource(shader,source);GLES20.glCompileShader(shader);int[] ok=new int[1];GLES20.glGetShaderiv(shader,GLES20.GL_COMPILE_STATUS,ok,0);if(ok[0]==0){String error=GLES20.glGetShaderInfoLog(shader);GLES20.glDeleteShader(shader);throw new IllegalStateException(error);}return shader;}
    private void park(){if(!EGL14.eglMakeCurrent(display,parking,parking,context))throw new IllegalStateException("Cannot activate exposure renderer");}
    private void remove(SurfaceOutput output){EGLSurface window=outputs.remove(output);if(window!=null){park();EGL14.eglDestroySurface(display,window);}output.close();}
    private void fail(Throwable error){if(failed||closed)return;failed=true;errors.accept(error);}
    @Override public void close(){handler.post(()->{closed=true;for(SurfaceOutput output:new ArrayList<>(outputs.keySet()))remove(output);finishClose();});}
    private void finishClose(){
        if(!closed||!inputs.isEmpty())return;
        if(display!=EGL14.EGL_NO_DISPLAY){
            if(context!=EGL14.EGL_NO_CONTEXT&&parking!=EGL14.EGL_NO_SURFACE){park();if(program!=0)GLES20.glDeleteProgram(program);}
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(parking!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,parking);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglReleaseThread();EGL14.eglTerminate(display);display=EGL14.EGL_NO_DISPLAY;
        }
        thread.quitSafely();
    }
}
