package com.opentokreactnative.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.opentok.android.BaseVideoCapturer;
import com.opentokreactnative.mlkit.camera.CameraSource;
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener;
import com.opentokreactnative.mlkit.camera.CameraSourcePreview;
import com.opentokreactnative.mlkit.graphics.GraphicOverlay;
import com.opentokreactnative.mlkit.processors.MultiProcessor;

import java.io.IOException;
import java.lang.ref.WeakReference;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch, ProcessorFrameListener {

    final private String TAG = "CustomVideoCapturer";
    final private WeakReference<Activity> activityRef;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;

    private MultiProcessor multiProcessor;
    private boolean backgroundBlurEnabled;
    private boolean pixelatedFaceEnabled;
    private Canvas canvas;
    private Bitmap bitmap;
    private CameraSource cameraSource;
    private boolean isCapturing = false;
    private boolean capturingRequested = false;
    final private int fps = 30;
    private int width = 0;
    private int height = 0;
    private int[] frame;


    final private Handler handler = new Handler(Looper.myLooper());

    final private Runnable screenShareTask = new Runnable() {
        @Override
        public void run() {
            if (isCapturing) {
                int width = graphicOverlay.getWidth();
                int height = graphicOverlay.getHeight();
                if (frame == null || CustomVideoCapturer.this.width != width || CustomVideoCapturer.this.height != height) {
                    CustomVideoCapturer.this.width = width;
                    CustomVideoCapturer.this.height = height;

                    if (width <= 0 || height <= 0) {
                        handler.postDelayed(screenShareTask, 1000 / fps);
                        return;
                    }

                    if (bitmap != null) {
                        bitmap.recycle();
                        bitmap = null;
                    }

                    bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(bitmap);

                    frame = new int[width * height];
                }

                if (graphicOverlay != null) {
                    graphicOverlay.draw(canvas);
                    bitmap.getPixels(frame, 0, width, 0, 0, width, height);

                    provideIntArrayFrame(frame, ARGB, width, height, 0, false);
                }

                handler.postDelayed(screenShareTask, 1000 / fps);
            }
        }
    };

    public CustomVideoCapturer(Activity activity) {
        activityRef = new WeakReference<>(activity);
    }

    public void initCamera(CameraSourcePreview sourcePreview, GraphicOverlay overlay) {
        this.preview = sourcePreview;
        this.graphicOverlay = overlay;

        Activity activity = activityRef.get();
        cameraSource = new CameraSource(activity, graphicOverlay);
        cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);

        multiProcessor = new MultiProcessor(activity);
        multiProcessor.enableBackgroundBlur = backgroundBlurEnabled;
        multiProcessor.enablePixelatedFace = pixelatedFaceEnabled;

        cameraSource.setMachineLearningFrameProcessor(multiProcessor);
        cameraSource.setFrameListener(this);
        if (capturingRequested) {
            startCapture();
        }
    }

    public void enableBackgroundBlur(boolean enable) {
        backgroundBlurEnabled = enable;
        if (multiProcessor != null) {
            startScreenSharingIfNeeded();
            multiProcessor.enableBackgroundBlur = enable;
        }
    }

    public void enablePixelatedFace(boolean enable) {
        pixelatedFaceEnabled = enable;
        if (multiProcessor != null) {
            startScreenSharingIfNeeded();
            multiProcessor.enablePixelatedFace = enable;
        }
    }

    @Override
    public void init() {
    }

    @SuppressLint("MissingPermission")
    @Override
    public int startCapture() {
        if (preview != null && graphicOverlay != null) {
            isCapturing = true;
            capturingRequested = false;
            preview.stop();
            startCameraSource();
            startScreenSharingIfNeeded();
        } else {
            capturingRequested = true;
        }
        return 0;
    }

    @Override
    public int stopCapture() {
        isCapturing = false;
        capturingRequested = false;
        handler.removeCallbacks(screenShareTask);
        preview.stop();
        return 0;
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    private void startScreenSharingIfNeeded() {
        handler.removeCallbacks(screenShareTask);

        if (isCapturing && (pixelatedFaceEnabled || backgroundBlurEnabled)) {
            handler.postDelayed(screenShareTask, 0);
        }
    }

    private int currentVideFormat() {
        return (pixelatedFaceEnabled || backgroundBlurEnabled) ? ARGB : NV21;
    }

    @Override
    public boolean isCaptureStarted() {
        return isCapturing;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        BaseVideoCapturer.CaptureSettings settings = new BaseVideoCapturer.CaptureSettings();

        settings.fps = fps;
        settings.width = width;
        settings.height = height;
        settings.format = currentVideFormat();
        settings.expectedDelay = 0;

        return settings;
    }

    @Override
    public void onPause() {
    }

    @Override
    public void onResume() {
    }

    @Override
    public void cycleCamera() {

        int current = cameraSource.getCameraFacing();
        int next = current == CameraSource.CAMERA_FACING_FRONT
                ? CameraSource.CAMERA_FACING_BACK
                : CameraSource.CAMERA_FACING_FRONT;

        cameraSource.setFacing(next);
        preview.stop();
        startCameraSource();
    }

    @Override
    public int getCameraIndex() {
        return cameraSource.getCameraFacing();
    }

    @Override
    public void swapCamera(int i) {
        cameraSource.setFacing(i);
    }

    @Override
    public void destroy() {
    }

    @Override
    public void onFrame(byte[] frame, int width, int height, int rotation) {
        this.width = width;
        this.height = height;

        provideByteArrayFrame(
                frame,
                NV21,
                width,
                height,
                rotation,
                getCameraIndex() == CameraSource.CAMERA_FACING_FRONT
        );
    }
}
