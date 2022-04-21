package com.opentokreactnative.mlkit.graphics;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

import com.google.mlkit.vision.face.Face;
import com.opentokreactnative.mlkit.utils.BitmapEffects;


public class FaceOverlay {
    private volatile Face face;
    private final Bitmap originalBitmap;

    public FaceOverlay(Face face, Bitmap original) {
        this.face = face;
        this.originalBitmap = original;
    }

    public void drawPixelatedFaceBitmap(Canvas canvas) {
        Bitmap output = Bitmap.createBitmap(originalBitmap.getWidth(),
                originalBitmap.getHeight(), Bitmap.Config.ARGB_8888);

        Canvas tempCanvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.FILL);

        drawHeadPlaceholderCircle(face, tempCanvas, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        drawPixelatedFace(originalBitmap, tempCanvas, paint, face);

        canvas.drawBitmap(output, 0, 0, null);
    }

    private void drawHeadPlaceholderCircle(Face face, Canvas canvas, Paint paint) {
        Rect boundingBox = face.getBoundingBox();
        int x = boundingBox.centerX();
        int y = boundingBox.centerY();
        float radius = boundingBox.height() / 1.4f;

        canvas.drawCircle(x, y, radius, paint);
    }

    private void drawPixelatedFace(Bitmap original, Canvas canvas, Paint paint, Face face) {
        Rect boundingBox = face.getBoundingBox();
        int halfWidth = boundingBox.width() / 2;
        int halfHeight = boundingBox.height() / 2;

        int x = boundingBox.centerX();
        int y = boundingBox.centerY();

        int headOffset = 25;
        int left = x - halfWidth - headOffset;
        int top = y - halfHeight - headOffset;
        int right = x + halfWidth + headOffset;
        int bottom = y + halfHeight + headOffset;

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

}
