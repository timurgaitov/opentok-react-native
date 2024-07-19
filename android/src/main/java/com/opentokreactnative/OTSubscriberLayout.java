package com.opentokreactnative;

import android.opengl.GLSurfaceView;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.facebook.react.uimanager.ThemedReactContext;
import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.Session;
import com.opentok.android.Subscriber;
import java.util.concurrent.ConcurrentHashMap;
import com.opentok.android.Stream.StreamVideoType;

/**
 * Created by manik on 1/10/18.
 */

public class OTSubscriberLayout extends FrameLayout{

    public OTRN sharedState;

    private String streamId;

    public OTSubscriberLayout(ThemedReactContext reactContext) {

        super(reactContext);
        sharedState = OTRN.getSharedState();
    }

    public void createSubscriberView(String streamId) {

        this.streamId = streamId;

        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        ConcurrentHashMap<String, String> androidOnTopMap = sharedState.getAndroidOnTopMap();
        ConcurrentHashMap<String, String> androidZOrderMap = sharedState.getAndroidZOrderMap();
        Subscriber mSubscriber = mSubscribers.get(streamId);
        FrameLayout mSubscriberViewContainer = new FrameLayout(getContext());
        String pubOrSub = "";
        String zOrder = "";
        if (mSubscriber != null) {
            Session session = mSubscriber.getSession();
            if (session != null) {
                String sessionId = session.getSessionId();
                if (sessionId != null) {
                    if (androidOnTopMap.get(sessionId) != null) {
                        pubOrSub = androidOnTopMap.get(sessionId);
                    }
                    if (androidZOrderMap.get(sessionId) != null) {
                        zOrder = androidZOrderMap.get(sessionId);
                    }
                }
            }
            if (mSubscriber.getView().getParent() != null) {
                ((ViewGroup)mSubscriber.getView().getParent()).removeView(mSubscriber.getView());
            }
            if (mSubscriber.getStream().getStreamVideoType() == StreamVideoType.StreamVideoTypeScreen) {
                mSubscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FIT);
            } else {
                mSubscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, BaseVideoRenderer.STYLE_VIDEO_FILL);
            }
            if (pubOrSub.equals("subscriber") && mSubscriber.getView() instanceof GLSurfaceView) {
                if (zOrder.equals("mediaOverlay")) {
                    ((GLSurfaceView) mSubscriber.getView()).setZOrderMediaOverlay(true);
                } else {
                    ((GLSurfaceView) mSubscriber.getView()).setZOrderOnTop(true);
                }
            }
            ConcurrentHashMap<String, FrameLayout> mSubscriberViewContainers = sharedState.getSubscriberViewContainers();
            mSubscriberViewContainers.put(streamId, mSubscriberViewContainer);
            addView(mSubscriberViewContainer, 0);
            mSubscriberViewContainer.addView(mSubscriber.getView());
            requestLayout();
        }
    }

    public void updateFitLayout(String fitToView) {

        if (this.streamId != null && this.streamId.length() > 0) {
            Subscriber mSubscriber = sharedState.getSubscriber(this.streamId);
            if (mSubscriber != null) {
                String style = null;

                if (mSubscriber.getStream().getStreamVideoType() == StreamVideoType.StreamVideoTypeScreen) {
                    style = BaseVideoRenderer.STYLE_VIDEO_FIT;
                } else if (fitToView.equals("fit")) {
                    style = BaseVideoRenderer.STYLE_VIDEO_FIT;
                } else if (fitToView.equals("fill")) {
                    style = BaseVideoRenderer.STYLE_VIDEO_FILL;
                }

                if (style != null) {
                    mSubscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE, style);
                    requestLayout();
                }
            }
        }
    }

    public void setZOrderMediaOverlay(Boolean flag) {

        if (streamId != null && this.streamId.length() > 0) {
            Subscriber mSubscriber = sharedState.getSubscriber(this.streamId);
            if (mSubscriber != null) {
                View view = mSubscriber.getView();
                if (view instanceof GLSurfaceView) {
                    ((GLSurfaceView) view).setZOrderMediaOverlay(flag);
                    requestLayout();
                }
            }
        }
    }
}
