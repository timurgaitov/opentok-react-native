package com.opentokreactnative.utils;

public final class CameraIndex {
    public static final int Back = 0;
    public static final int Front = 1;
    public static final int External = 2;

    public static final int defaultCamera = Back;

    public static int from(String position) {
        switch (position) {
            case CameraPosition.External:
                return CameraIndex.External;
            case CameraPosition.Front:
                return CameraIndex.Front;
            case CameraPosition.Back:
            default:
                return CameraIndex.Back;
        }
    }
}
