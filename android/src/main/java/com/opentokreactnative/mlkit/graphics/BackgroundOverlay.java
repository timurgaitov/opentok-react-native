package com.opentokreactnative.mlkit.graphics;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import androidx.annotation.ColorInt;

import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.opentokreactnative.mlkit.utils.BitmapEffects;

import java.nio.ByteBuffer;

public class BackgroundOverlay {

    private final ByteBuffer mask;
    private final Bitmap originalImage;
    private final int maskWidth;
    private final int maskHeight;

    public BackgroundOverlay(
            SegmentationMask segmentationMask, Bitmap originalImage) {
        this.originalImage = originalImage;

        mask = segmentationMask.getBuffer();
        maskWidth = segmentationMask.getWidth();
        maskHeight = segmentationMask.getHeight();
    }

    public void drawBlurredBackground(Canvas canvas) {
        Bitmap backgroundShapeBitmap =
                Bitmap.createBitmap(
                        backgroundColorShape(mask), maskWidth, maskHeight, Config.ARGB_8888);

        int scaledWidth = originalImage.getWidth() / 2;
        int scaledHeight = originalImage.getHeight() / 2;

        Bitmap scaledBackground = Bitmap.createScaledBitmap(backgroundShapeBitmap, scaledWidth, scaledHeight, false);
        Bitmap scaledOriginal = Bitmap.createScaledBitmap(originalImage, scaledWidth, scaledHeight, false);

        Bitmap output = Bitmap.createBitmap(scaledWidth, scaledHeight, Config.ARGB_8888);
        Canvas tempCanvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tempCanvas.drawBitmap(scaledBackground, 0, 0, paint);

        // Keeps the source pixels that cover the destination pixels,
        // discards the remaining source and destination pixels.
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        tempCanvas.drawBitmap(scaledOriginal, 0, 0, paint);

        Bitmap blurredBackgroundBitmap = BitmapEffects.blur(output, 5);
        Bitmap result = Bitmap.createScaledBitmap(blurredBackgroundBitmap, originalImage.getWidth(), originalImage.getHeight(), true);

        canvas.drawBitmap(result, 0, 0, null);
    }

    @ColorInt
    private int[] backgroundColorShape(ByteBuffer byteBuffer) {
        @ColorInt int[] colors = new int[maskWidth * maskHeight];
        for (int i = 0; i < maskWidth * maskHeight; i++) {
            // ByteBuffer.getFloat() moves to the next pixel after each call.
            float backgroundLikelihood = byteBuffer.getFloat();
            if (backgroundLikelihood < 0.90) {
                colors[i] = Color.BLACK;
            } else if (backgroundLikelihood < 0.94){
                int alpha = (int) (182.9 * (1 - backgroundLikelihood) - 36.6 + 0.5);
                colors[i] = Color.argb(alpha, 255, 0, 255);
            }
        }
        return colors;
    }


}
