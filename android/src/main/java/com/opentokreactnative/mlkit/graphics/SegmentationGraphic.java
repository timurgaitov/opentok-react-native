/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.opentokreactnative.mlkit.graphics;

import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import androidx.annotation.ColorInt;

import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.opentokreactnative.mlkit.utils.BitmapEffects;

import java.nio.ByteBuffer;

/** Draw the mask from SegmentationResult in preview. */
public class SegmentationGraphic extends GraphicOverlay.Graphic {

  private final ByteBuffer mask;
  private final Bitmap originalImage;
  private final int maskWidth;
  private final int maskHeight;

  public SegmentationGraphic(
      GraphicOverlay overlay,
      SegmentationMask segmentationMask, Bitmap originalImage) {
    super(overlay);
    this.originalImage = originalImage;

    mask = segmentationMask.getBuffer();
    maskWidth = segmentationMask.getWidth();
    maskHeight = segmentationMask.getHeight();

  }

  /** Draws the segmented background on the supplied canvas. */
  @Override
  public void draw(Canvas canvas) {
    Bitmap backgroundShapeBitmap =
        Bitmap.createBitmap(
            maskColorsFromByteBuffer(mask), maskWidth, maskHeight, Config.ARGB_8888);

    Bitmap backgroundBitmap = getBackgroundBitmap(originalImage, backgroundShapeBitmap);
    Bitmap blurredBackgroundBitmap = BitmapEffects.blur(backgroundBitmap, 20);

    canvas.drawBitmap(blurredBackgroundBitmap, getTransformationMatrix(), null);

    // Reset byteBuffer pointer to beginning, so that the mask can be redrawn if screen is refreshed
    mask.rewind();
  }

  public static Bitmap getBackgroundBitmap(Bitmap original, Bitmap backgroundShape) {
    Bitmap output = Bitmap.createBitmap(original.getWidth(),
            original.getHeight(), Bitmap.Config.ARGB_8888);

    Canvas canvas = new Canvas(output);

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    canvas.drawBitmap(backgroundShape, 0, 0, paint);

    // Keeps the source pixels that cover the destination pixels,
    // discards the remaining source and destination pixels.
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

    canvas.drawBitmap(original, 0, 0, paint);

    return output;
  }

  @ColorInt
  private int[] maskColorsFromByteBuffer(ByteBuffer byteBuffer) {
    @ColorInt int[] colors = new int[maskWidth * maskHeight];
    for (int i = 0; i < maskWidth * maskHeight; i++) {
      // ByteBuffer.getFloat() moves to the next pixel after each call.
      if (byteBuffer.getFloat() < 0.9) {
        colors[i] = Color.BLACK;
      }
    }
    return colors;
  }


}
