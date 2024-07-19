package com.opentokreactnative.mlkit.processors;

import android.annotation.SuppressLint;
import android.hardware.Camera;
import android.util.Log;

import com.opentokreactnative.mlkit.camera.FrameMetadata;

import java.nio.ByteBuffer;
import java.util.IdentityHashMap;

/**
 * This runnable controls access to the underlying receiver, calling it to process frames when
 * available from the camera. This is designed to run detection on frames as fast as possible
 * (i.e., without unnecessary context switching or waiting on the next frame).
 *
 * <p>While detection is running on a frame, new frames may be received from the camera. As these
 * frames come in, the most recent frame is held onto as pending. As soon as detection and its
 * associated processing is done for the previous frame, detection on the mostly recently received
 * frame will immediately start on the same thread.
 *
 *  The [setNextFrame(ByteBuffer)] method was add for cameras that only use one buffer.
 */
public class FrameProcessingRunnable implements Runnable {

    private final Object processorLock = new Object();

    // This lock guards all of the member variables below.
    private final Object lock = new Object();
    private boolean active = true;
    private Camera camera;

    private final String TAG = "FrameProcessingRunnable";

    // These pending variables hold the state associated with the new frame awaiting processing.
    private ByteBuffer pendingFrameData;
    private final VideoFiltersProcessor filtersProcessor;

    private int previewWidth = 0;
    private int previewHeight = 0;
    private int rotation = 0;

    public FrameProcessingRunnable(VideoFiltersProcessor processor) {
        filtersProcessor = processor;
    }

    /** Marks the runnable as active/not active. Signals any blocked threads to continue. */
    public void setActive(boolean active) {
        synchronized (lock) {
            this.active = active;
            lock.notifyAll();
        }
    }

    public void setPreviewSize(int width, int height) {
        this.previewWidth = width;
        this.previewHeight = height;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    /**
     * Sets the frame data received from the camera. This adds the previous unused frame buffer (if
     * present) back to the camera, and keeps a pending reference to the frame data for future use.
     */
    @SuppressWarnings("ByteBufferBackingArray")
    public void setNextFrame(byte[] data, Camera camera, IdentityHashMap<byte[], ByteBuffer> bytesToByteBuffer ) {
        synchronized (lock) {
            this.camera = camera;
            if (pendingFrameData != null) {
                camera.addCallbackBuffer(pendingFrameData.array());
                pendingFrameData = null;
            }

            if (!bytesToByteBuffer.containsKey(data)) {
                Log.d(
                        TAG,
                        "Skipping frame. Could not find ByteBuffer associated with the image "
                                + "data from the camera.");
                return;
            }

            pendingFrameData = bytesToByteBuffer.get(data);

            // Notify the processor thread if it is waiting on the next frame (see below).
            lock.notifyAll();
        }
    }

    public void setNextFrame(ByteBuffer data) {
        synchronized (lock) {

            pendingFrameData = cloneBuffer(data);

            lock.notifyAll();
        }
    }

    private ByteBuffer cloneBuffer(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind(); //copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    /**
     * As long as the processing thread is active, this executes detection on frames continuously.
     * The next pending frame is either immediately available or hasn't been received yet. Once it
     * is available, we transfer the frame info to local variables and run detection on that frame.
     * It immediately loops back for the next frame without pausing.
     *
     * <p>If detection takes longer than the time in between new frames from the camera, this will
     * mean that this loop will run without ever waiting on a frame, avoiding any context switching
     * or frame acquisition time latency.
     *
     * <p>If you find that this is using more CPU than you'd like, you should probably decrease the
     * FPS setting above to allow for some idle time in between frames.
     */
    @SuppressLint("InlinedApi")
    @SuppressWarnings({"GuardedBy", "ByteBufferBackingArray"})
    @Override
    public void run() {
        ByteBuffer data;

        while (true) {
            synchronized (lock) {
                while (active && (pendingFrameData == null)) {
                    try {
                        // Wait for the next frame to be received from the camera, since we
                        // don't have it yet.
                        lock.wait();
                    } catch (InterruptedException e) {
                        Log.d(TAG, "Frame processing loop terminated.", e);
                        return;
                    }
                }

                if (!active) {
                    // Exit the loop once this camera source is stopped or released.  We check
                    // this here, immediately after the wait() above, to handle the case where
                    // setActive(false) had been called, triggering the termination of this
                    // loop.
                    return;
                }

                // Hold onto the frame data locally, so that we can use this for detection
                // below.  We need to clear pendingFrameData to ensure that this buffer isn't
                // recycled back to the camera before we are done using that data.
                data = pendingFrameData;
                pendingFrameData = null;
            }

            // The code below needs to run outside of synchronization, because this will allow
            // the camera to add pending frame(s) while we are running detection on the current
            // frame.

            try {
                synchronized (processorLock) {
                    if (filtersProcessor.active()) {
                        filtersProcessor.processByteBuffer(
                                data,
                                new FrameMetadata.Builder()
                                        .setWidth(previewWidth)
                                        .setHeight(previewHeight)
                                        .setRotation(rotation)
                                        .build());
                    }
                }
            } catch (Exception t) {
                Log.e(TAG, "Exception thrown from receiver.", t);
            } finally {
                if (camera != null) {
                    camera.addCallbackBuffer(data.array());
                }
            }
        }
    }
}