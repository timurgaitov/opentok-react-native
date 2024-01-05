package com.opentokreactnative.utils;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDevice;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReactApplicationContext;
import com.jiangdg.ausbc.MultiCameraClient;
import com.jiangdg.ausbc.callback.IDeviceConnectCallBack;
import com.jiangdg.usb.USBMonitor;
import com.opentok.android.BaseVideoCapturer;
import com.opentokreactnative.BuildConfig;
import com.opentokreactnative.UvcVideoCapturer;
import com.opentokreactnative.mlkit.camera.AndroidCameraCapturer;
import com.opentokreactnative.mlkit.processors.FrameProcessingRunnable;
import com.opentokreactnative.mlkit.processors.VideoFiltersProcessor;
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener;

import java.nio.ByteBuffer;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch, ProcessorFrameListener {

    private final String TAG = "CustomVideoCapturer";
    private final static CameraType fallbackCameraType = CameraType.AndroidBack;
    private CameraType cameraType = fallbackCameraType;
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean isCapturePaused = false;

    private final UvcVideoCapturer uvcVideoCapturer;

    private final VideoFiltersProcessor videoFiltersProcessor;
    private AndroidCameraCapturer androidCameraCapturer;

    private UsbDevice device;
    private USBMonitor.UsbControlBlock ctrlBlock;

    private final MultiCameraClient usbCameraClient;

    private CameraEvents cameraEventsListener;
    private boolean permissionRequested = false;

    public CustomVideoCapturer(ReactApplicationContext context) {
        usbCameraClient = new MultiCameraClient(context, deviceConnectListener);

        videoFiltersProcessor = new VideoFiltersProcessor(context, this);

        FrameProcessingRunnable androidRunnable = new FrameProcessingRunnable(videoFiltersProcessor);
        androidCameraCapturer = new AndroidCameraCapturer(context.getCurrentActivity(), androidRunnable);
        androidCameraCapturer.setFacing(AndroidCameraCapturer.CAMERA_FACING_BACK);
        androidCameraCapturer.setFrameListener(this);

        FrameProcessingRunnable usbRunnable = new FrameProcessingRunnable(videoFiltersProcessor);
        uvcVideoCapturer = new UvcVideoCapturer(context, this, usbRunnable);
    }

    public synchronized void init() {
        usbCameraClient.register();
    }

    @Override
    public synchronized int startCapture() {
        if (isCaptureStarted) {
            return -1;
        }

        int res = usingExternalCamera() ? uvcVideoCapturer.startCapture() : startCameraSource();
        if (res == 0) {
            isCaptureRunning = true;
            isCaptureStarted = true;
        }

        return res;
    }

    @Override
    public synchronized int stopCapture() {
        isCaptureStarted = false;
        if (usingExternalCamera()) {
            return uvcVideoCapturer.stopCapture();
        } else {
            androidCameraCapturer.stop();
            return 0;
        }
    }

    @Override
    public void destroy() {
        videoFiltersProcessor.stop();
        androidCameraCapturer.stop();
        uvcVideoCapturer.destroy();
        if (usbCameraClient != null) {
            usbCameraClient.unRegister();
            usbCameraClient.destroy();
        }
    }

    @Override
    public boolean isCaptureStarted() {
        return isCaptureStarted;
    }

    @Override
    public CaptureSettings getCaptureSettings() {
        return usingExternalCamera() ? uvcVideoCapturer.getCaptureSettings() : androidCameraCapturer.getCaptureSettings();
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
        if (cameraType == CameraType.AndroidFront) {
            return CameraIndex.Front;
        } else if (cameraType == CameraType.AndroidBack) {
            return CameraIndex.Back;
        } else if (cameraType == CameraType.External) {
            return CameraIndex.External;
        }

        throw new IllegalStateException();
    }

    @Override
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

        if (swapToCameraType == CameraType.External && !isUsbCameraReady()) {
            permissionRequested = false;
            onPositionChanged("back");
            return;
        }
        if (cameraType == CameraType.AndroidFront && swapToCameraType == CameraType.AndroidBack
                || cameraType == CameraType.AndroidBack && swapToCameraType == CameraType.AndroidFront) {
            androidCameraCapturer.setFacing(swapToCameraType);
            if (this.isCaptureStarted) {
                androidCameraCapturer.stop();
                startCameraSource();
            }
        } else if (swapToCameraType == CameraType.External) {
            androidCameraCapturer.stop();
            uvcVideoCapturer.startCapture();
        } else {
            uvcVideoCapturer.stopCapture();
            androidCameraCapturer.stop();
            androidCameraCapturer.setFacing(swapToCameraType);
            startCameraSource();
        }

        cameraType = swapToCameraType;
        checkProcessorState();
    }

    @Override
    public void onFrame(ByteBuffer buffer, int width, int height, int rotation) {
        provideBufferFrame(
                buffer,
                NV21,
                width,
                height,
                rotation,
                getCameraIndex() == AndroidCameraCapturer.CAMERA_FACING_FRONT
        );
    }

    @Override
    public void onFrame(byte[] frame, int width, int height, int rotation) {
        provideByteArrayFrame(
                frame,
                NV21,
                width,
                height,
                rotation,
                getCameraIndex() == AndroidCameraCapturer.CAMERA_FACING_FRONT
        );
    }

    @Override
    public void onFrame(int[] frame, int width, int height, int rotation) {
        provideIntArrayFrame(
                frame,
                ARGB,
                width,
                height,
                0,
                getCameraIndex() == AndroidCameraCapturer.CAMERA_FACING_FRONT
        );
    }

    public void enableBackgroundBlur(boolean enable) {
        videoFiltersProcessor.enableBackgroundBlur = enable;
        checkProcessorState();
    }

    public void enablePixelatedFace(boolean enable) {
        videoFiltersProcessor.enablePixelatedFace = enable;
        checkProcessorState();
    }

    public void setCameraEventsListener(CameraEvents listener) {
        cameraEventsListener = listener;
    }

    public boolean getIsCaptureRunning() {
        return isCaptureRunning;
    }

    public boolean isUsbCameraReady() {
        return device != null && ctrlBlock != null && hasPermission(device);
    }

    public boolean usingExternalCamera() {
        return cameraType == CameraType.External;
    }

    private void checkProcessorState() {
        boolean currentState = videoFiltersProcessor.active();
        if (usingExternalCamera()) {
            uvcVideoCapturer.onFrameProcessorEnabled(currentState);
        } else {
            androidCameraCapturer.onFrameProcessorEnabled(currentState);
        }
    }

    @SuppressLint("MissingPermission")
    private int startCameraSource() {
        if (androidCameraCapturer != null) {
            try {
                androidCameraCapturer.stop();
                androidCameraCapturer.start();
                return 0;
            } catch (Exception e) {
                androidCameraCapturer.release();
                androidCameraCapturer = null;
            }
        }
        return -1;
    }

    private boolean hasPermission(UsbDevice device) {
        try {
            return Boolean.TRUE.equals(usbCameraClient.hasPermission(device));
        } catch (SecurityException ex) {
            // ignore
            log("securityEx: " + ex.getLocalizedMessage());
        }
        return false;
    }

    private void requestPermission(UsbDevice device) {
        log("requestPermission");
        if (permissionRequested) {
            return;
        }
        permissionRequested = true;

        try {
            usbCameraClient.requestPermission(device);
        } catch (SecurityException ex) {
            log("securityEx: " + ex.getLocalizedMessage());
            // ignore
        }
    }

    private void onPositionChanged(String camera) {
        if (cameraEventsListener != null) {
            cameraEventsListener.positionChanged(camera);
        }
    }

    private final IDeviceConnectCallBack deviceConnectListener = new IDeviceConnectCallBack() {
        @Override
        public void onDisConnectDec(@Nullable UsbDevice usbDevice, @Nullable USBMonitor.UsbControlBlock usbControlBlock) {
            log("onDisConnectDec");
        }

        @Override
        public void onDetachDec(@Nullable UsbDevice usbDevice) {
            log("onDetachDec");
            CustomVideoCapturer.this.device = null;
            ctrlBlock = null;

            permissionRequested = false;
            uvcVideoCapturer.closeCamera();

            if (cameraType != fallbackCameraType) {
                onPositionChanged("back");
            }
        }

        @Override
        public void onConnectDev(@Nullable UsbDevice usbDevice, @Nullable USBMonitor.UsbControlBlock usbControlBlock) {
            if (usbDevice == null || usbControlBlock == null) {
                return;
            }
            CustomVideoCapturer.this.device = usbDevice;
            CustomVideoCapturer.this.ctrlBlock = usbControlBlock;
            uvcVideoCapturer.init(usbControlBlock);

            onPositionChanged("external");
        }

        @Override
        public void onCancelDev(@Nullable UsbDevice usbDevice) {

        }

        @Override
        public void onAttachDev(@Nullable UsbDevice usbDevice) {
            if (usbDevice == null) {
                return;
            }
            requestPermission(usbDevice);
        }
    };

    private void log(String message) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message);
        }
    }

    public interface CameraEvents {
        void positionChanged(String position);
    }
}