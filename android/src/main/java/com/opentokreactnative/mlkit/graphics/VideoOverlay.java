package com.opentokreactnative.mlkit.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.segmentation.SegmentationMask;

import java.util.List;

public class VideoOverlay {
    private Canvas canvas;
    private Bitmap outputBitmap;
    private int width = 0;
    private int height = 0;
    private int[] frame;

    private Bitmap currentBitmap;
    private FaceOverlay faceOverlay = null;
    private BackgroundOverlay backgroundOverlay = null;

    public VideoOverlay() {
    }

    public void setBitmap(Bitmap bitmap) {
        currentBitmap = bitmap;
    }

    public void setFaces(List<Face> faces) {
        if (faces != null) {
            faceOverlay = new FaceOverlay(faces, currentBitmap);
        } else {
            faceOverlay = null;
        }
    }

    public void setSegmentationMask(SegmentationMask mask) {
        if (mask != null) {
            backgroundOverlay = new BackgroundOverlay(mask, currentBitmap);
        } else {
            backgroundOverlay = null;
        }
    }

    public int[] getFrame() {
        applyOverlays();
        if (outputBitmap != null) {
            outputBitmap.getPixels(frame, 0, width, 0, 0, width, height);
            return frame;
        }

        return null;
    }

    private void applyOverlays() {
        updateBitmapSizeIfNeeded();

        if (width <= 0 || height <= 0) {
            outputBitmap = null;
            return;
        }

        canvas.drawBitmap(currentBitmap, 0, 0, null);

        if (faceOverlay != null) {
            faceOverlay.drawPixelatedFaceBitmap(canvas);
        }

        if (backgroundOverlay != null) {
            backgroundOverlay.drawBlurredBackground(canvas);
        }
    }

    private void updateBitmapSizeIfNeeded() {
        if (outputBitmap == null || currentBitmap.getWidth() != width || currentBitmap.getHeight() != height) {
            width = currentBitmap.getWidth();
            height = currentBitmap.getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            if (outputBitmap != null) {
                outputBitmap.recycle();
                outputBitmap = null;
            }

            outputBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            canvas = new Canvas(outputBitmap);

            frame = new int[width * height];
        }
    }

}