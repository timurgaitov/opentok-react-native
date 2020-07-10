package com.opentokreactnative.utils;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.Publisher;
import com.opentokreactnative.R;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import java.util.List;

enum CameraType {
    AndroidBack,
    AndroidFront,
    External
}

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch {
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean isCapturePaused = false;
    private CameraType cameraType = CameraType.AndroidFront;
    private USBMonitor mUSBMonitor;
    private UvcVideoCapturer uvcVideoCapturer;
    private AndroidVideoCapturer androidVideoCapturer;
    private Context mContext;
    private final Object mSync = new Object();

    public CustomVideoCapturer(Context context, Publisher.CameraCaptureResolution resolution, Publisher.CameraCaptureFrameRate fps) {
        mContext = context;
        uvcVideoCapturer = new UvcVideoCapturer(context, this);
        androidVideoCapturer = new AndroidVideoCapturer(context, resolution, fps, this);
        mUSBMonitor = new USBMonitor(context, mOnDeviceConnectListener);
    }

    public synchronized void init() {
        mUSBMonitor.register();
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
        if (mUSBMonitor != null) {
            mUSBMonitor.unregister();
            mUSBMonitor.destroy();
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
        cameraType = getNextCameraType();
        if (cameraType == CameraType.External) {
            if (!handleAttach()) {
                cameraType = getNextCameraType();
                androidVideoCapturer.swapCamera(cameraType, this.isCaptureStarted);
            }
        } else {
            uvcVideoCapturer.stopCapture();
            androidVideoCapturer.swapCamera(cameraType, this.isCaptureStarted);
        }
    }

    private CameraType getNextCameraType() {
        return CameraType.values()[(cameraType.ordinal() + 1) % 3];
    }

    // deprecated
    public int getCameraIndex() {
        return 0;
    }

    // deprecated
    public synchronized void swapCamera(int index) {
    }

    public boolean getIsCaptureRunning() {
        return  isCaptureRunning;
    }

    private boolean handleAttach() {
        synchronized (mSync) {
            List<UsbDevice> usbDeviceList = mUSBMonitor.getDeviceList();
            if (usbDeviceList.size() > 0) {
                mUSBMonitor.requestPermission(usbDeviceList.get(0));
                return true;
            }
        }
        return false;
    }

    private final USBMonitor.OnDeviceConnectListener mOnDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            handleAttach();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            androidVideoCapturer.stopCapture();
            uvcVideoCapturer.onConnectCamera(device, ctrlBlock, createNew);
            cameraType = CameraType.External;
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
            uvcVideoCapturer.onDisconnectCamera();
        }

        @Override
        public void onDettach(final UsbDevice device) {
            if (cameraType == CameraType.External) {
                androidVideoCapturer.startCapture(CameraType.AndroidFront);
                cameraType = CameraType.AndroidFront;
            }
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };
}