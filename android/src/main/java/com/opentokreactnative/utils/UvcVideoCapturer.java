package com.opentokreactnative.utils;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;

import com.opentok.android.BaseVideoCapturer;
import com.serenegiant.usb.IFrameCallback;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;
import java.nio.ByteBuffer;
import java.util.List;

public class UvcVideoCapturer {
    public int previewWidth;
    public int previewHeight;
    private final Object mSync = new Object();
    private UVCCamera mUVCCamera;
    private SurfaceTexture mSurfaceTexture;
    private BaseUtility baseUtility;

    private CustomVideoCapturer mCustomVideoCapturer;

    public UvcVideoCapturer(Context context, CustomVideoCapturer customVideoCapturer) {
        mCustomVideoCapturer = customVideoCapturer;

        baseUtility = new BaseUtility();
    }

    public int startCapture() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.startPreview();
            }
        }
        return 0;
    }

    public int stopCapture() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                mUVCCamera.stopPreview();
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
        settings.format = BaseVideoCapturer.YUV420P;
        settings.expectedDelay = 0;

        return settings;
    }


    public void onConnectCamera(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
        openCamera(device, ctrlBlock);
    }

    public void onDisconnectCamera() {
        releaseCamera();
    }

    private synchronized void releaseCamera() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                try {
                    if (mUVCCamera != null) mUVCCamera.close();
                    if (mUVCCamera != null) mUVCCamera.destroy();
                } catch (final Exception e) {
                } finally {
                    mSurfaceTexture.release();
                }
                mUVCCamera = null;
            }
        }
    }

    private void openCamera(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        releaseCamera();
        baseUtility.queueEvent(new Runnable() {
            @Override
            public void run() {
                final UVCCamera camera = new UVCCamera();
                camera.open(ctrlBlock);
                try {
                    List<Size> supportedSizeList = camera.getSupportedSizeList();
                    Size previewSize = getPreviewSize(supportedSizeList);
                    previewWidth = previewSize.width;
                    previewHeight = previewSize.height;
                    camera.setPreviewSize(previewWidth, previewHeight, UVCCamera.FRAME_FORMAT_MJPEG);
                } catch (final IllegalArgumentException e) {
                    try {
                        camera.setPreviewSize(640, 480, UVCCamera.DEFAULT_PREVIEW_MODE);
                    } catch (final IllegalArgumentException e1) {
                        camera.destroy();
                        return;
                    }
                }
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                }
                mSurfaceTexture = new SurfaceTexture(42);

                camera.setPreviewTexture(mSurfaceTexture);
                camera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
                camera.startPreview();
                synchronized (mSync) {
                    mUVCCamera = camera;
                }
            }
        }, 0);
    }

    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            frame.clear();
            mCustomVideoCapturer.provideBufferFrame(frame, BaseVideoCapturer.YUV420P, previewWidth, previewHeight, 0, false);
        }
    };

    private Size getPreviewSize(List<Size> supportedSizeList) {
        int preferredWidth = 1920;
        int preferredHeight = 1080;
        int maxw = 0;
        int maxh = 0;
        int index = 0;

        for (int i = 0; i < supportedSizeList.size(); ++i) {
            Size size = supportedSizeList.get(i);
            if (size.width >= maxw && size.height >= maxh) {
                if (size.width <= preferredWidth && size.height <= preferredHeight) {
                    maxw = size.width;
                    maxh = size.height;
                    index = i;
                }
            }
        }

        if (maxw == 0 || maxh == 0) {
            // Not found a smaller resolution close to the preferred
            // So choose the lowest resolution possible
            Size size = supportedSizeList.get(0);
            int minw = size.width;
            int minh = size.height;
            for (int i = 1; i < supportedSizeList.size(); ++i) {
                size = supportedSizeList.get(i);
                if (size.width <= minw && size.height <= minh) {
                    minw = size.width;
                    minh = size.height;
                    index = i;
                }
            }
        }

        return supportedSizeList.get(index);
    }
}
