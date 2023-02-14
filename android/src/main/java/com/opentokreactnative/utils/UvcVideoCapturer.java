package com.opentokreactnative.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.BaseVideoCapturer;
import com.opentokreactnative.mlkit.camera.FrameMetadata;
import com.opentokreactnative.mlkit.processors.FrameProcessingRunnable;
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener;
import com.opentokreactnative.mlkit.processors.base.VisionImageProcessor;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.common.UVCCameraHandler;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.LinkedList;

public class UvcVideoCapturer {
    private static final String TAG = "UvcVideoCapturer";

    public final int LOW_FPS = 20;
    public final int HIGH_FPS = 30;

    public final int LOW_WIDTH = 480;
    public final int LOW_HEIGHT = 360;

    public final int HIGH_WIDTH = 1920;
    public final int HIGH_HEIGHT = 1080;

    public int previewWidth = HIGH_WIDTH;
    public int previewHeight = HIGH_HEIGHT;

    private boolean isFrameProcessorActive = false;

    private final Object mSync = new Object();
    private UVCCameraHandler cameraHandler;
    private SurfaceTexture mSurfaceTexture;
    final private UVCCameraTextureView uvcCameraTextureView;
    final private WeakReference<Activity> activityRef;
    private final ProcessorFrameListener frameListener;

    private Thread processingThread;

    private final FrameProcessingRunnable processingRunnable;

    public UvcVideoCapturer(ReactApplicationContext context,
                            ProcessorFrameListener frameListener,
                            FrameProcessingRunnable frameProcessingRunnable) {
        this.frameListener = frameListener;

        activityRef = new WeakReference<>(context.getCurrentActivity());
        uvcCameraTextureView = new UVCCameraTextureView(context);
        processingRunnable = frameProcessingRunnable;
    }

    public int startCapture() {
        synchronized (mSync) {
            if (cameraHandler != null) {
                cameraHandler.startPreview(mSurfaceTexture);

                processingThread = new Thread(processingRunnable);
                processingRunnable.setActive(true);
                processingThread.start();
            }
        }
        return 0;
    }

    public int stopCapture() {
        synchronized (mSync) {
            processingRunnable.setActive(false);
            if (processingThread != null) {
                try {
                    // Wait for the thread to complete to ensure that we can't have multiple threads
                    // executing at the same time (i.e., which would happen if we called start too
                    // quickly after stop).
                    processingThread.join();
                } catch (InterruptedException e) {
                    Log.d(TAG, "Frame processing thread interrupted on release.");
                }
                processingThread = null;
            }

            if (cameraHandler != null) {
                cameraHandler.stopPreview();
            }
        }
        return 0;
    }

    public void destroy() {
        synchronized (mSync) {
            releaseCamera();
        }
    }

    public BaseVideoCapturer.CaptureSettings getCaptureSettings() {
        BaseVideoCapturer.CaptureSettings settings = new BaseVideoCapturer.CaptureSettings();

        settings.fps = isFrameProcessorActive ? LOW_FPS : HIGH_FPS;
        settings.width = previewWidth;
        settings.height = previewHeight;
        settings.format = isFrameProcessorActive ? BaseVideoCapturer.ARGB : BaseVideoCapturer.NV21;
        settings.expectedDelay = 0;

        return settings;
    }


    public void openCamera(final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
        synchronized (mSync) {
            if (cameraHandler == null) {
                cameraHandler = UVCCameraHandler.createHandler(activityRef.get(), uvcCameraTextureView, previewWidth, previewHeight);
                cameraHandler.setOnFrameListener(frameCallback);

                processingRunnable.setPreviewSize(previewWidth, previewHeight);
                processingRunnable.setRotation(0);
            }
            cameraHandler.open(ctrlBlock);
            mSurfaceTexture = new SurfaceTexture(42);
            startCapture();
        }
    }

    public void closeCamera() {
        synchronized (mSync) {
            if (cameraHandler != null) {
                cameraHandler.close();
            }
            if (mSurfaceTexture != null) {
                mSurfaceTexture.release();
            }
            mSurfaceTexture = null;
        }

    }

    public synchronized int releaseCamera() {
        synchronized (mSync) {
            try {
                stopCapture();
                cameraHandler.release();
            } catch (Exception e) {
                // ignore
            }
            cameraHandler = null;
            try {
                mSurfaceTexture.release();
            } catch (Exception e) {
                // ignore
            }
            mSurfaceTexture = null;
        }
        return 0;
    }

    public void onFrameProcessorEnabled(boolean enabled, final USBMonitor.UsbControlBlock ctrlBlock) {
        if (isFrameProcessorActive != enabled && cameraHandler != null) {
            isFrameProcessorActive = enabled;
            releaseCamera();
            previewWidth = enabled ? LOW_WIDTH : HIGH_WIDTH;
            previewHeight = enabled ? LOW_HEIGHT : HIGH_HEIGHT;
            openCamera(ctrlBlock, false);
        }
    }

    private final AbstractUVCCameraHandler.OnFrameListener frameCallback = new AbstractUVCCameraHandler.OnFrameListener() {

        @Override
        public void onFrame(ByteBuffer frame) {
            if (isFrameProcessorActive) {
                processingRunnable.setNextFrame(frame);
            } else {
                frame.clear();
                frameListener.onFrame(frame, previewWidth, previewHeight, 0);
            }
        }

        @Override
        public void onPreviewSizeChanged(int width, int height) {
            previewWidth = width;
            previewHeight = height;

            processingRunnable.setPreviewSize(previewWidth, previewHeight);
            processingRunnable.setRotation(0);
        }
    };
}
