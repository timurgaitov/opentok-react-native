package com.opentokreactnative.mlkit.utils;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.android.renderscript.BuildConfig;
import com.google.android.renderscript.Toolkit;

public class BitmapEffects {

    public static Bitmap blur(Bitmap source, int radius) {
        return Toolkit.INSTANCE.blur(source, radius);
    }

    public static Bitmap pixelate(Bitmap source, int blockSize, int initX, int initY, int finalX, int finalY) {
        Bitmap bitmap = source.copy(source.getConfig(), true);
        boolean printLogs = BuildConfig.DEBUG;

        int red;
        int blue;
        int green;

        int pixel;
        int count;

        // Looping through the bitmap
        for (int x = initX; x < finalX; x += blockSize) {
            for (int y = initY; y < finalY; y += blockSize) {

                red = 0;
                blue = 0;
                green = 0;
                count = 0;

                int maxBlockX = x + blockSize;
                if (maxBlockX >= finalX) {
                    maxBlockX = finalX;
                }

                int maxBlockY = y + blockSize;
                if (maxBlockY >= finalY){
                    maxBlockY = finalY;
                }

                // Scanning colors in blocks of $blockSize
                for (int blockX = x; blockX < maxBlockX; blockX++) {
                    for (int blockY = y; blockY < maxBlockY; blockY++) {
                        try {
                            pixel = source.getPixel(x, y);
                            red += Color.red(pixel);
                            blue += Color.blue(pixel);
                            green += Color.green(pixel);
                            count++;
                        } catch (Exception e) {
                            if (printLogs) {
                                Log.e("face", "----- Error while extracting colors from bitmap -----");
                                Log.e("face", "initX: " + initX + " initY: " + initY + " finalX: " + finalX + " finalY: " + finalY);
                                Log.e("face", "blockX: " + blockX + " blockY: " + blockY);
                                Log.e("face", "maxBlockX: " + maxBlockX + " maxBlockY: " + maxBlockY);
                                Log.e("face", "w: " + bitmap.getWidth() + " h: " + bitmap.getHeight());
                            }
                        }
                    }
                }

                // Getting average color
                red /= count;
                blue /= count;
                green /= count;

                // Setting the pixels with average color
                for (int blockX = x; blockX < maxBlockX; blockX++) {
                    for (int blockY = y; blockY < maxBlockY; blockY++) {
                        try {
                            bitmap.setPixel(blockX, blockY, Color.argb(255, red, green, blue));
                        } catch (Exception e) {
                            if (printLogs) {
                                Log.e("face", "----- Error while filling pixelated bitmap -----");
                                Log.e("face", "initX: " + initX + " initY: " + initY + " finalX: " + finalX + " finalY: " + finalY);
                                Log.e("face", "blockX: " + blockX + " blockY: " + blockY);
                                Log.e("face", "maxBlockX: " + maxBlockX + " maxBlockY: " + maxBlockY);
                                Log.e("face", "w: " + bitmap.getWidth() + " h: " + bitmap.getHeight());
                            }
                        }
                    }
                }
            }
        }

        return bitmap;
    }

    /**
     * Pixelates the whole bitmap. It can be slow.
     *
     * @param source    bitmap to pixelate
     * @param blockSize amount of pixels inside each block
     * @return pixelated bitmap
     */
    public static Bitmap pixelate(Bitmap source, int blockSize) {
        return pixelate(source, blockSize, 0, 0, source.getWidth(), source.getHeight());
    }
}
