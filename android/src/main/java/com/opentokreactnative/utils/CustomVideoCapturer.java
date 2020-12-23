package com.opentokreactnative.utils;

import android.content.Context;
import android.hardware.usb.UsbDevice;

import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.Publisher;
import com.serenegiant.usb.USBMonitor;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch {
    private final static CameraType fallbackCameraType = CameraType.AndroidBack;
    private final static int fallbackCameraIndex = CameraIndex.Back;

    private CameraType cameraType = fallbackCameraType;
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean isCapturePaused = false;

    private final USBMonitor usbMonitor;
    private final UvcVideoCapturer uvcVideoCapturer;
    private final AndroidVideoCapturer androidVideoCapturer;

    private UsbDevice mDevice;
    private USBMonitor.UsbControlBlock mCtrlBlock;

    private boolean permissionRequested = false;

    public CustomVideoCapturer(Context context, Publisher.CameraCaptureResolution resolution, Publisher.CameraCaptureFrameRate fps) {
        uvcVideoCapturer = new UvcVideoCapturer(this);
        androidVideoCapturer = new AndroidVideoCapturer(context, resolution, fps, this);
        usbMonitor = new USBMonitor(context, deviceConnectListener);
    }

    public synchronized void init() {
        usbMonitor.register();
    }

    @Override
    public synchronized int startCapture() {
        if (isCaptureStarted) {
            return -1;
        }

        int res = cameraType == CameraType.External ? uvcVideoCapturer.startCapture() : androidVideoCapturer.startCapture(cameraType);
        if (res == 0) {
            isCaptureRunning = true;
            isCaptureStarted = true;
        }

        return res;
    }

    @Override
    public synchronized int stopCapture() {
        isCaptureStarted = false;

        return cameraType == CameraType.External ? uvcVideoCapturer.stopCapture() : androidVideoCapturer.stopCapture();
    }

    @Override
    public void destroy() {
        uvcVideoCapturer.destroy();
        if (usbMonitor != null) {
            usbMonitor.unregister();
            usbMonitor.destroy();
        }
    }

    @Override
    public boolean isCaptureStarted() {
        return isCaptureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        return cameraType == CameraType.External ? uvcVideoCapturer.getCaptureSettings() : androidVideoCapturer.getCaptureSettings();
    }

    @Override
    public synchronized  void onPause() {
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
        if (cameraType == CameraType.AndroidFront) {
            return CameraIndex.Front;
        } else if (cameraType == CameraType.AndroidBack) {
            return CameraIndex.Back;
        } else if (cameraType == CameraType.External) {
            return CameraIndex.External;
        }

        throw new IllegalStateException();
    }

    public synchronized void swapCamera(int index) {
        CameraType swapToCameraType;

        switch (index) {
            case CameraIndex.External:
                swapToCameraType = CameraType.External;
                break;
            case CameraIndex.Back:
                swapToCameraType = CameraType.AndroidBack;
                break;
            case CameraIndex.Front:
                swapToCameraType = CameraType.AndroidFront;
                break;
            default:
                return;
        }

        if (swapToCameraType == cameraType) {
            return;
        }

        if (swapToCameraType == CameraType.External) {
            if (mDevice == null || mCtrlBlock == null || !hasPermission(mDevice)) {
                swapToCameraType = fallbackCameraType;
            }
        }

        if (cameraType == CameraType.AndroidFront && swapToCameraType == CameraType.AndroidBack
            || cameraType == CameraType.AndroidBack && swapToCameraType == CameraType.AndroidFront) {
            androidVideoCapturer.swapCamera(swapToCameraType, this.isCaptureStarted);
        } else if (swapToCameraType == CameraType.External) {
            androidVideoCapturer.stopCapture();
            final boolean doesNotMatter = false;
            uvcVideoCapturer.openCamera(mCtrlBlock, doesNotMatter);
        } else {
            uvcVideoCapturer.stopCapture();
            androidVideoCapturer.startCapture(swapToCameraType);
        }

        cameraType = swapToCameraType;
    }

    public boolean getIsCaptureRunning() {
        return  isCaptureRunning;
    }

    private boolean hasPermission(UsbDevice device) {
        try {
            return usbMonitor.hasPermission(device);
        } catch (SecurityException ex) {
            // ignore
        }
        return false;
    }

    private void requestPermission() {
        if (permissionRequested) {
            return;
        }
        permissionRequested = true;

        for (UsbDevice device : usbMonitor.getDeviceList()) {
            try {
                usbMonitor.requestPermission(device);
            } catch (SecurityException ex) {
                // ignore
            }
        }
    }

    private final USBMonitor.OnDeviceConnectListener deviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            requestPermission();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            mDevice = device;
            mCtrlBlock = ctrlBlock;

            swapCamera(CameraIndex.External);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            uvcVideoCapturer.closeCamera();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            mDevice = null;
            mCtrlBlock = null;

            permissionRequested = false;

            swapCamera(fallbackCameraIndex);
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };
}