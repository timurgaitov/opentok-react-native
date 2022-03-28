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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceContour;
import com.opentokreactnative.mlkit.utils.BitmapEffects;

/**
 * Graphic instance for rendering face position, contour, and landmarks within the associated
 * graphic overlay view.
 */
public class FaceGraphic extends GraphicOverlay.Graphic {
    private volatile Face face;
    private final Bitmap originalBitmap;

    public FaceGraphic(GraphicOverlay overlay, Face face, Bitmap original) {
        super(overlay);
        this.face = face;
        this.originalBitmap = original;
    }

    /**
     * Draws the face annotations for position on the supplied canvas.
     */
    @Override
    public void draw(Canvas canvas) {
        if (this.face == null) {
            canvas.drawBitmap(BitmapEffects.blur(originalBitmap, 20), getTransformationMatrix(), null);
            return;
        }

        canvas.drawBitmap(getPixelatedFaceBitmap(originalBitmap, this.face), getTransformationMatrix(), null);
    }

    public Bitmap getPixelatedFaceBitmap(Bitmap original, Face face) {
        Bitmap output = Bitmap.createBitmap(original.getWidth(),
                original.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        drawHeadBoxPlaceholder(canvas, paint, face);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        drawPixelatedFace(original, canvas, paint, face);

        return output;
    }

    private void drawHeadBoxPlaceholder(Canvas canvas, Paint paint, Face face) {
        int x = face.getBoundingBox().centerX();
        int y = face.getBoundingBox().centerY();

        int left = x - (face.getBoundingBox().width() / 2);
        int top = y - (face.getBoundingBox().height() / 2) - 50;
        int right = x + (face.getBoundingBox().width() / 2) + 50;
        int bottom = y + (face.getBoundingBox().height() / 2);

        if (left < 0) {
            left = 0;
        }
        if (top < 0) {
            top = 0;
        }
        if (right > originalBitmap.getWidth()) {
            right = originalBitmap.getWidth();
        }
        if (bottom > originalBitmap.getHeight()) {
            bottom = originalBitmap.getHeight();
        }
        canvas.drawRect(left, top, right, bottom, paint);
    }

    private void drawPixelatedFace(Bitmap original, Canvas canvas, Paint paint, Face face) {
        int x = face.getBoundingBox().centerX();
        int y = face.getBoundingBox().centerY();

        int left = x - (face.getBoundingBox().width() / 2);
        int top = y - (face.getBoundingBox().height() / 2) - 20;
        int right = x + (face.getBoundingBox().width() / 2);
        int bottom = y + (face.getBoundingBox().height() / 2);

        if (left < 0) {
            left = 0;
        }
        if (top < 0) {
            top = 0;
        }
        if (right > original.getWidth()) {
            right = original.getWidth();
        }
        if (bottom > original.getHeight()) {
            bottom = original.getHeight();
        }

        Bitmap facePix = BitmapEffects.pixelate(original, 15, left, top, right, bottom);
        canvas.drawBitmap(facePix, 0, 0, paint);
    }

    private void drawFaceContour(Canvas canvas, Paint paint, Face face) {
        Path path = new Path();
        boolean first = true;
        FaceContour contour = face.getContour(FaceContour.FACE);
        for (PointF point : contour.getPoints()) {
            if (first) {
                path.moveTo(point.x, point.y);
                first = false;
            } else {
                path.lineTo(point.x, point.y);
            }
        }

        canvas.drawPath(path, paint);
    }

}
