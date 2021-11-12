package com.opentokreactnative.utils;

public final class CameraPosition {
    public static final String Back = "back";
    public static final String Front = "front";
    public static final String External = "external";

    public static final String defaultCamera = Back;

    public static String fromIndex(int index) {
        switch (index) {
            case CameraIndex.External:
                return CameraPosition.External;
            case CameraIndex.Front:
                return CameraPosition.Front;
            case CameraIndex.Back:
            default:
                return CameraPosition.Back;
        }
    }
}
