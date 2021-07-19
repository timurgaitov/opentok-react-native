package com.opentokreactnative.utils;

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

    private final CustomVideoCapturer mCustomVideoCapturer;

    public UvcVideoCapturer(CustomVideoCapturer customVideoCapturer) {
        mCustomVideoCapturer = customVideoCapturer;
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
        settings.format = BaseVideoCapturer.NV21;
        settings.expectedDelay = 0;

        return settings;
    }


    public void openCamera(final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
        releaseCamera();

        synchronized (mSync) {
            mUVCCamera = new UVCCamera();
            mUVCCamera.open(ctrlBlock);

            List<Size> supportedSizeList = mUVCCamera.getSupportedSizeList();
            Size previewSize = getPreviewSize(supportedSizeList);
            previewWidth = previewSize.width;
            previewHeight = previewSize.height;
            mUVCCamera.setPreviewSize(previewWidth, previewHeight, UVCCamera.FRAME_FORMAT_MJPEG);

            mSurfaceTexture = new SurfaceTexture(42);

            mUVCCamera.setPreviewTexture(mSurfaceTexture);
            mUVCCamera.setFrameCallback(mIFrameCallback, UVCCamera.PIXEL_FORMAT_YUV420SP);
            mUVCCamera.startPreview();
        }
    }

    public void closeCamera() {
        releaseCamera();
    }

    private synchronized void releaseCamera() {
        synchronized (mSync) {
            if (mUVCCamera != null) {
                try {
                    mUVCCamera.close();
                } catch (Exception e) {
                    // ignore
                }
                try {
                    mUVCCamera.destroy();
                } catch (Exception e) {
                    // ignore
                }
                mUVCCamera = null;
                try {
                    mSurfaceTexture.release();
                } catch (Exception e) {
                    // ignore
                }
                mSurfaceTexture = null;
            }
        }
    }

    private final IFrameCallback mIFrameCallback = new IFrameCallback() {
        @Override
        public void onFrame(final ByteBuffer frame) {
            frame.clear();
            mCustomVideoCapturer.provideBufferFrame(frame, BaseVideoCapturer.NV21, previewWidth, previewHeight, 0, false);
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
