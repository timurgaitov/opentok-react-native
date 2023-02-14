package com.opentokreactnative.utils;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDevice;

import com.facebook.react.bridge.ReactApplicationContext;
import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.Publisher;
import com.opentokreactnative.mlkit.camera.AndroidCameraCapturer;
import com.opentokreactnative.mlkit.processors.FrameProcessingRunnable;
import com.opentokreactnative.mlkit.processors.VideoFiltersProcessor;
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener;
import com.serenegiant.usb.USBMonitor;

import java.nio.ByteBuffer;

public class CustomVideoCapturer extends BaseVideoCapturer implements BaseVideoCapturer.CaptureSwitch, ProcessorFrameListener {
    private final static CameraType fallbackCameraType = CameraType.AndroidBack;
    private final static int fallbackCameraIndex = CameraIndex.Back;

    private CameraType cameraType = fallbackCameraType;
    private boolean isCaptureStarted = false;
    private boolean isCaptureRunning = false;
    private boolean isCapturePaused = false;

    private final USBMonitor usbMonitor;
    private final UvcVideoCapturer uvcVideoCapturer;

    private final VideoFiltersProcessor videoFiltersProcessor;
    private AndroidCameraCapturer androidCameraCapturer;

    private UsbDevice device;
    private USBMonitor.UsbControlBlock ctrlBlock;

    private CameraEvents cameraEventsListener;
    private boolean permissionRequested = false;

    public CustomVideoCapturer(ReactApplicationContext context, Publisher.CameraCaptureResolution resolution, Publisher.CameraCaptureFrameRate fps) {
        usbMonitor = new USBMonitor(context, deviceConnectListener);

        videoFiltersProcessor = new VideoFiltersProcessor(context, this);

        FrameProcessingRunnable androidRunnable = new FrameProcessingRunnable(videoFiltersProcessor);
        androidCameraCapturer = new AndroidCameraCapturer(context.getCurrentActivity(), androidRunnable);
        androidCameraCapturer.setFacing(AndroidCameraCapturer.CAMERA_FACING_BACK);
        androidCameraCapturer.setFrameListener(this);

        FrameProcessingRunnable usbRunnable = new FrameProcessingRunnable(videoFiltersProcessor);
        uvcVideoCapturer = new UvcVideoCapturer(context, this, usbRunnable);
    }

    public synchronized void init() {
        usbMonitor.register();
    }

    @Override
    public synchronized int startCapture() {
        if (isCaptureStarted) {
            return -1;
        }

        int res = cameraType == CameraType.External ? uvcVideoCapturer.startCapture() : startCameraSource();
        if (res == 0) {
            isCaptureRunning = true;
            isCaptureStarted = true;
        }

        return res;
    }

    @Override
    public synchronized int stopCapture() {
        isCaptureStarted = false;
        if (usbMonitor != null) {
            usbMonitor.unregister();
            usbMonitor.destroy();
        }
        if (cameraType == CameraType.External) {
            return uvcVideoCapturer.releaseCamera();
        } else {
            uvcVideoCapturer.releaseCamera();
            androidCameraCapturer.stop();
            return 0;
        }
    }

    @Override
    public void destroy() {
        videoFiltersProcessor.stop();
        androidCameraCapturer.stop();
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
        return cameraType == CameraType.External ? uvcVideoCapturer.getCaptureSettings() : androidCameraCapturer.getCaptureSettings();
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
            final boolean doesNotMatter = false;
            uvcVideoCapturer.openCamera(ctrlBlock, doesNotMatter);
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

    private void checkProcessorState() {
        boolean currentState = videoFiltersProcessor.active();
        androidCameraCapturer.onFrameProcessorEnabled(currentState);
        uvcVideoCapturer.onFrameProcessorEnabled(currentState, ctrlBlock);
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

    private void onPositionChanged(String camera) {
        if (cameraEventsListener != null) {
            cameraEventsListener.positionChanged(camera);
        }
    }

    private final USBMonitor.OnDeviceConnectListener deviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {
            requestPermission();
        }

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            CustomVideoCapturer.this.device = device;
            CustomVideoCapturer.this.ctrlBlock = ctrlBlock;

            onPositionChanged("external");
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        }

        @Override
        public void onDettach(final UsbDevice device) {
            CustomVideoCapturer.this.device = null;
            ctrlBlock = null;

            permissionRequested = false;
            uvcVideoCapturer.closeCamera();

            if (cameraType != fallbackCameraType) {
                onPositionChanged("back");
            }
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };

    public interface CameraEvents {
        void positionChanged(String position);
    }
}