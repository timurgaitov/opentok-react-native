package com.opentokreactnative.utils;

import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.BaseVideoCapturer;
import com.serenegiant.usb.USBMonitor;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch {

    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean isCapturePaused = false;

    private final UvcVideoCapturer uvcVideoCapturer;
    private USBMonitor.UsbControlBlock controlBlock;

    public CustomVideoCapturer(ReactApplicationContext context) {
        uvcVideoCapturer = new UvcVideoCapturer(context, this);
    }

    public synchronized void init() {
    }

    public void setControlBlock(USBMonitor.UsbControlBlock controlBlock) {
        this.controlBlock = controlBlock;
    }

    @Override
    public synchronized int startCapture() {
        if (isCaptureStarted) {
            return -1;
        }

        uvcVideoCapturer.openCamera(controlBlock);
        int res = uvcVideoCapturer.startCapture();
        if (res == 0) {
            isCaptureRunning = true;
            isCaptureStarted = true;
        }

        return res;
    }

    @Override
    public synchronized int stopCapture() {
        isCaptureStarted = false;
        uvcVideoCapturer.destroy();
        return 0;
    }

    @Override
    public void destroy() {
        uvcVideoCapturer.destroy();
    }

    @Override
    public boolean isCaptureStarted() {
        return isCaptureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        return uvcVideoCapturer.getCaptureSettings();
    }

    @Override
    public synchronized void onPause() {
        if (isCaptureStarted) {
            isCapturePaused = true;
            stopCapture();
        }
    }

    @Override
    public void onResume() {
        if (isCapturePaused) {
            init();
            startCapture();
            isCapturePaused = false;
        }
    }

    @Override
    public synchronized void cycleCamera() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getCameraIndex() {
        return CameraIndex.External;
    }

    public void closeCamera() {
        uvcVideoCapturer.closeCamera();
    }

    public synchronized void swapCamera(int index) {
    }


    public boolean getIsCaptureRunning() {
        return isCaptureRunning;
    }

}