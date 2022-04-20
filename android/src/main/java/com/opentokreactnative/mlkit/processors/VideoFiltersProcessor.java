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
import com.opentokreactnative.mlkit.graphics.VideoOverlay;
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener;
import com.opentokreactnative.mlkit.processors.base.VisionProcessorBase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VideoFiltersProcessor extends VisionProcessorBase<List<Task<?>>> {

    private FaceDetector faceDetector;
    private Segmenter segmenter;
    private List<Face> cachedFaces = new ArrayList<>();
    public boolean enableBackgroundBlur = false;
    public boolean enablePixelatedFace = false;
    private final ProcessorFrameListener frameListener;
    private final VideoOverlay videoOverlay;

    public VideoFiltersProcessor(Context context, ProcessorFrameListener frameListener) {
        super(context);
        setupFaceDetector();
        setupSegmenter();
        this.frameListener = frameListener;
        videoOverlay = new VideoOverlay();
    }

    public void clearCache() {
        cachedFaces = new ArrayList<>();
    }

    private void setupFaceDetector() {
        FaceDetectorOptions faceDetectorOptions = new FaceDetectorOptions.Builder().build();
        faceDetector = FaceDetection.getClient(faceDetectorOptions);
    }

    private void setupSegmenter() {
        SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                .build();
        segmenter = Segmentation.getClient(options);
    }

    @Override
    public boolean active() {
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
            @Nullable Bitmap originalCameraImage
    ) {
        SegmentationMask mask = null;
        Face face = null;
        for (Task<?> task : results) {
            Object taskResult = task.getResult();
            if (taskResult instanceof SegmentationMask && enableBackgroundBlur) {
                mask = ((SegmentationMask) taskResult);
            } else if (taskResult instanceof List<?> && enablePixelatedFace) {
                face = getFace((List<?>) taskResult);
            }
        }

        videoOverlay.setBitmap(originalCameraImage);
        videoOverlay.setFace(face);
        videoOverlay.setSegmentationMask(mask);
        int[] frame = videoOverlay.getFrame();
        if (frame != null && frameListener != null) {
            frameListener.onFrame(frame, originalCameraImage.getWidth(), originalCameraImage.getHeight(), 0);
        }
    }

    private Face getFace(List<?> taskResult) {
        List<?> resultList = (List<?>) taskResult;
        if (!resultList.isEmpty() && resultList.get(0) instanceof Face) {
            List<Face> faces = (List<Face>) resultList;
            cachedFaces = faces;
            return faces.get(0);
        } else if (!cachedFaces.isEmpty()) {
            // if we lose tracking of the face, we use previous positions.
            return cachedFaces.get(0);
        }

        return null;
    }

    @Override
    protected void onFailure(@NonNull Exception e) {

    }
}
