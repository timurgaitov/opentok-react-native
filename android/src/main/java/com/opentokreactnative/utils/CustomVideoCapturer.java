package com.opentokreactnative.utils;

import android.hardware.usb.UsbDevice;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.BaseVideoCapturer;
import com.serenegiant.usb.USBMonitor;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch {
    private final static CameraType fallbackCameraType = CameraType.AndroidBack;
    private final static int fallbackCameraIndex = CameraIndex.Back;

    private CameraType cameraType = fallbackCameraType;
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean isCapturePaused = false;

//    private final USBMonitor usbMonitor;
    private final UvcVideoCapturer uvcVideoCapturer;
    //private final AndroidVideoCapturer androidVideoCapturer;

    private UsbDevice mDevice;
    private USBMonitor.UsbControlBlock mCtrlBlock;

    private boolean permissionRequested = false;

    public CustomVideoCapturer(ReactApplicationContext context, USBMonitor.UsbControlBlock controlBlock) {
        uvcVideoCapturer = new UvcVideoCapturer(context, this);
        mCtrlBlock = controlBlock;
        uvcVideoCapturer.openCamera(mCtrlBlock);
        isCaptureStarted = true;
        //       androidVideoCapturer = new AndroidVideoCapturer(context, resolution, fps, this);
//        usbMonitor = new USBMonitor(context, deviceConnectListener);
    }

    public synchronized void init() {
//        usbMonitor.register();
    }

    @Override
    public synchronized int startCapture() {
        Log.i("OTRN", "CustomVideoCapturer startCapture");
        if (isCaptureStarted) {
            return -1;
        }

        int res = uvcVideoCapturer.startCapture();
        // int res = cameraType == CameraType.External ?  : androidVideoCapturer.startCapture(cameraType);
        if (res == 0) {
            isCaptureRunning = true;
            isCaptureStarted = true;
        }

        return res;
    }

    @Override
    public synchronized int stopCapture() {
        isCaptureStarted = false;
//        if (usbMonitor != null) {
//            usbMonitor.unregister();
//            usbMonitor.destroy();
//        }
        return uvcVideoCapturer.releaseCamera();
    }

    @Override
    public void destroy() {
        uvcVideoCapturer.destroy();
//        if (usbMonitor != null) {
//            usbMonitor.unregister();
//            usbMonitor.destroy();
//        }
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
    public synchronized  void onPause() {
        Log.i("OTRN", "CustomVideoCapturer onPause");

        if (isCaptureStarted) {
            isCapturePaused = true;
            stopCapture();
        }
    }

    @Override
    public void onResume() {
        Log.i("OTRN", "CustomVideoCapturer onResume");
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

    public void closeCamera() {
        uvcVideoCapturer.closeCamera();
    }

    public synchronized void swapCamera(int index) {
        Log.i("OTRN", "CustomVideoCapturer swapCamera");
        cameraType = CameraType.External;

//
//        CameraType swapToCameraType;
//
//        switch (index) {
//            case CameraIndex.External:
//                swapToCameraType = CameraType.External;
//                break;
//            case CameraIndex.Back:
//                swapToCameraType = CameraType.AndroidBack;
//                break;
//            case CameraIndex.Front:
//                swapToCameraType = CameraType.AndroidFront;
//                break;
//            default:
//                return;
//        }
//
//        if (swapToCameraType == cameraType) {
//            return;
//        }
//
//        if (swapToCameraType == CameraType.External) {
//            if (mDevice == null || mCtrlBlock == null || !hasPermission(mDevice)) {
//                swapToCameraType = fallbackCameraType;
//            }
//        }
//
//        if (cameraType == CameraType.AndroidFront && swapToCameraType == CameraType.AndroidBack
//            || cameraType == CameraType.AndroidBack && swapToCameraType == CameraType.AndroidFront) {
//            androidVideoCapturer.swapCamera(swapToCameraType, this.isCaptureStarted);
//        } else if (swapToCameraType == CameraType.External) {
//            androidVideoCapturer.stopCapture();
//            uvcVideoCapturer.openCamera(mCtrlBlock);
//        } else {
//            uvcVideoCapturer.stopCapture();
//            androidVideoCapturer.startCapture(swapToCameraType);
//        }

    }

    public boolean getIsCaptureRunning() {
        return  isCaptureRunning;
    }

//    private boolean hasPermission(UsbDevice device) {
//        try {
//            return usbMonitor.hasPermission(device);
//        } catch (SecurityException ex) {
//            // ignore
//        }
//        return false;
//    }
//
//    private void requestPermission() {
//        if (permissionRequested) {
//            return;
//        }
//        permissionRequested = true;
//
//        for (UsbDevice device : usbMonitor.getDeviceList()) {
//            try {
//                usbMonitor.requestPermission(device);
//            } catch (SecurityException ex) {
//                // ignore
//            }
//        }
//    }

    private final USBMonitor.OnDeviceConnectListener deviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
          //  requestPermission();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            mDevice = device;
            mCtrlBlock = ctrlBlock;

            swapCamera(CameraIndex.External);
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        }

        @Override
        public void onDettach(final UsbDevice device) {
            mDevice = null;
            mCtrlBlock = null;

            permissionRequested = false;
            uvcVideoCapturer.closeCamera();

            swapCamera(fallbackCameraIndex);
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };
}