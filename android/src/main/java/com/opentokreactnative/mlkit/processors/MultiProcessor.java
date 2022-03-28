package com.opentokreactnative.mlkit.processors;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;
import com.opentokreactnative.mlkit.processors.base.VisionProcessorBase;
import com.opentokreactnative.mlkit.graphics.SegmentationGraphic;
import com.opentokreactnative.mlkit.graphics.FaceGraphic;
import com.opentokreactnative.mlkit.graphics.GraphicOverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MultiProcessor extends VisionProcessorBase<List<Task<?>>> {

    private FaceDetector faceDetector;
    private Segmenter segmenter;
    private List<Face> cachedFaces = new ArrayList<>();
    public boolean enableBackgroundBlur = false;
    public boolean enablePixelatedFace = false;


    public MultiProcessor(Context context) {
        super(context);
        setupFaceDetector();
        setupSegmenter();
    }

    private void setupFaceDetector() {
        FaceDetectorOptions faceDetectorOptions = new FaceDetectorOptions.Builder()
                .build();
        faceDetector = FaceDetection.getClient(faceDetectorOptions);
    }

    private void setupSegmenter() {
        SelfieSegmenterOptions.Builder optionsBuilder = new SelfieSegmenterOptions.Builder();
        optionsBuilder.setDetectorMode(SelfieSegmenterOptions.STREAM_MODE);

        SelfieSegmenterOptions options = optionsBuilder
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .build();
        segmenter = Segmentation.getClient(options);
    }

    @Override
    public boolean enabled() {
        return enablePixelatedFace || enableBackgroundBlur;
    }

    @Override
    protected Task<List<Task<?>>> detectInImage(InputImage image) {
        if (enableBackgroundBlur && enablePixelatedFace) {
            return Tasks.whenAllComplete(faceDetector.process(image), segmenter.process(image));
        } else if (enableBackgroundBlur) {
            return Tasks.whenAllComplete(segmenter.process(image));
        } else if (enablePixelatedFace) {
            return Tasks.whenAllComplete(faceDetector.process(image));
        }

        return Tasks.forResult(Collections.emptyList());
    }

    @Override
    protected void onSuccess(
            @NonNull List<Task<?>> results,
            @NonNull GraphicOverlay graphicOverlay,
            @Nullable Bitmap originalCameraImage
    ) {
        for (Task<?> task : results) {
            Object taskResult = task.getResult();
            if (taskResult instanceof SegmentationMask && enableBackgroundBlur) {
                graphicOverlay.add(new SegmentationGraphic(
                                graphicOverlay,
                                (SegmentationMask) taskResult,
                                originalCameraImage
                        )
                );
            } else if (taskResult instanceof List<?> && enablePixelatedFace) {
                processFaces((List<?>) taskResult, graphicOverlay, originalCameraImage);
            }
        }
    }

    private void processFaces(List<?> taskResult, GraphicOverlay graphicOverlay, Bitmap original) {
        List<?> resultList = (List<?>) taskResult;
        if (!resultList.isEmpty() && resultList.get(0) instanceof Face) {
            List<Face> faces = (List<Face>) resultList;
            cachedFaces = faces;
            drawFaces(faces, graphicOverlay, original);
        } else if (!cachedFaces.isEmpty()) {
            // if we lose tracking of the face, we use previous positions.
            drawFaces(cachedFaces, graphicOverlay, original);
        }
    }

    private void drawFaces(List<Face> faces, GraphicOverlay graphicOverlay, Bitmap original) {
        for (Face face : faces) {
            graphicOverlay.add(new FaceGraphic(graphicOverlay, face, original));
        }
    }

    @Override
    protected void onFailure(@NonNull Exception e) {

    }
}
