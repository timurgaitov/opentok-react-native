package com.opentokreactnative.mlkit.processors.base;

import java.nio.ByteBuffer;

public interface ProcessorFrameListener {
    void onFrame(ByteBuffer buffer, int width, int height, int rotation);

    void onFrame(byte[] frame, int width, int height, int rotation);

    void onFrame(int[] frame, int width, int height, int rotation);
}
