package com.opentokreactnative.utils;

import android.app.Activity;
import android.graphics.SurfaceTexture;

import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.BaseVideoCapturer;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.common.AbstractUVCCameraHandler;
import com.serenegiant.usb.common.UVCCameraHandler;
import com.serenegiant.usb.widget.UVCCameraTextureView;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class UvcVideoCapturer {
    public int previewWidth = 0;
    public int previewHeight = 0;
    private final Object mSync = new Object();
    private UVCCameraHandler cameraHandler;
    private SurfaceTexture mSurfaceTexture;
    final private UVCCameraTextureView uvcCameraTextureView;
    final private WeakReference<Activity> activityRef;


    private final CustomVideoCapturer mCustomVideoCapturer;

    public UvcVideoCapturer(ReactApplicationContext context, CustomVideoCapturer customVideoCapturer) {
        activityRef = new WeakReference<>(context.getCurrentActivity());
        mCustomVideoCapturer = customVideoCapturer;
        uvcCameraTextureView = new UVCCameraTextureView(context);
    }

    public int startCapture() {
        synchronized (mSync) {
            if (cameraHandler != null) {
                cameraHandler.startPreview(mSurfaceTexture);
            }
        }
        return 0;
    }

    public int stopCapture() {
        synchronized (mSync) {
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

        settings.fps = 30;
        settings.width = previewWidth;
        settings.height = previewHeight;
        settings.format = BaseVideoCapturer.NV21;
        settings.expectedDelay = 0;

        return settings;
    }


    public void openCamera(final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
        synchronized (mSync) {
            if (cameraHandler == null) {
                cameraHandler = UVCCameraHandler.createHandler(activityRef.get(), uvcCameraTextureView, 0, 0);
                cameraHandler.setOnFrameListener(frameCallback);
            }
            cameraHandler.open(ctrlBlock);
            mSurfaceTexture = new SurfaceTexture(42);
            cameraHandler.startPreview(mSurfaceTexture);
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

    private final AbstractUVCCameraHandler.OnFrameListener frameCallback = new AbstractUVCCameraHandler.OnFrameListener() {

        @Override
        public void onFrame(ByteBuffer frame) {
            frame.clear();
            mCustomVideoCapturer.provideBufferFrame(frame, BaseVideoCapturer.NV21, previewWidth, previewHeight, 0, false);
        }

        @Override
        public void onPreviewSizeChanged(int width, int height) {
            previewWidth = width;
            previewHeight = height;
        }
    };
}
