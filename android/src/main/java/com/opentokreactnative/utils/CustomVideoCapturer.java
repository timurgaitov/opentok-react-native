package com.opentokreactnative.utils;

import android.annotation.SuppressLint;
import android.app.Activity;

import com.opentok.android.BaseVideoCapturer;
import com.opentokreactnative.mlkit.camera.CameraSource;
import com.opentokreactnative.mlkit.processors.VideoFiltersProcessor;
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch, ProcessorFrameListener {

    final private String TAG = "CustomVideoCapturer";

    private final VideoFiltersProcessor videoFiltersProcessor;
    private CameraSource cameraSource;
    private boolean isCapturing = false;
    private final int fps = 30;
    private int width = 0;
    private int height = 0;

    public CustomVideoCapturer(Activity activity) {
        cameraSource = new CameraSource(activity);
        cameraSource.setFacing(CameraSource.CAMERA_FACING_FRONT);

        videoFiltersProcessor = new VideoFiltersProcessor(activity, this);

        cameraSource.setMachineLearningFrameProcessor(videoFiltersProcessor);
        cameraSource.setFrameListener(this);
    }

    public void enableBackgroundBlur(boolean enable) {
        videoFiltersProcessor.enableBackgroundBlur = enable;
    }

    public void enablePixelatedFace(boolean enable) {
        videoFiltersProcessor.enablePixelatedFace = enable;
    }

    @Override
    public void init() {
    }

    @Override
    public int startCapture() {
        isCapturing = true;
        cameraSource.stop();
        startCameraSource();
        return 0;
    }

    @Override
    public int stopCapture() {
        isCapturing = false;
        cameraSource.stop();
        return 0;
    }

    @SuppressLint("MissingPermission")
    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                cameraSource.start();
            } catch (Exception e) {
                cameraSource.release();
                cameraSource = null;
            }
        }
    }

    private int currentVideFormat() {
        return videoFiltersProcessor.active() ? ARGB : NV21;
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

        swapCamera(next);
    }

    @Override
    public int getCameraIndex() {
        return cameraSource.getCameraFacing();
    }

    @Override
    public void swapCamera(int i) {
        cameraSource.setFacing(i);
        cameraSource.stop();
        startCameraSource();
    }

    @Override
    public void destroy() {
        cameraSource.stop();
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

    @Override
    public void onFrame(int[] frame, int width, int height, int rotation) {
        this.width = width;
        this.height = height;

        provideIntArrayFrame(
                frame,
                ARGB,
                width,
                height,
                0,
                getCameraIndex() == CameraSource.CAMERA_FACING_FRONT
        );
    }
}
