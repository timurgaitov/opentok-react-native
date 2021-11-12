package com.opentokreactnative;

/**
 * Created by manik on 1/29/18.
 */

import android.hardware.usb.UsbDevice;
import android.util.Log;
import android.widget.FrameLayout;
import android.view.View;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;

import com.opentok.android.BaseVideoCapturer;
import com.opentok.android.Session;
import com.opentok.android.Connection;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Stream;
import com.opentok.android.OpentokError;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;
import com.opentokreactnative.utils.CameraIndex;
import com.opentokreactnative.utils.CameraPosition;
import com.opentokreactnative.utils.CustomVideoCapturer;
import com.opentok.android.VideoUtils;
import com.opentok.android.AudioDeviceManager;
import com.opentokreactnative.utils.EventUtils;
import com.opentokreactnative.utils.ScreenshotVideoRenderer;
import com.opentokreactnative.utils.UsbMonitorWrapper;
import com.opentokreactnative.utils.Utils;
import com.serenegiant.usb.USBMonitor;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class OTSessionManager extends ReactContextBaseJavaModule
        implements Session.SessionListener,
        PublisherKit.PublisherListener,
        PublisherKit.AudioLevelListener,
        SubscriberKit.SubscriberListener,
        Session.SignalListener,
        Session.ConnectionListener,
        Session.ReconnectionListener,
        Session.ArchiveListener,
        Session.StreamPropertiesListener,
        SubscriberKit.AudioLevelListener,
        SubscriberKit.AudioStatsListener,
        SubscriberKit.VideoStatsListener,
        SubscriberKit.VideoListener,
        SubscriberKit.StreamListener{

    private ConcurrentHashMap<String, Integer> connectionStatusMap = new ConcurrentHashMap<>();
    private ArrayList<String> jsEvents = new ArrayList<String>();
    private ArrayList<String> componentEvents = new ArrayList<String>();
    private UsbMonitorWrapper usbMonitor;
    private static final String TAG = "OTRN";
    private final String sessionPreface = "session:";
    private final String publisherPreface = "publisher:";
    private final String subscriberPreface = "subscriber:";
    private Boolean logLevel = true;
    public OTRN sharedState;
    private Integer currentCameraIndex = null;
    private Integer requestedCameraIndex = null;

    public OTSessionManager(ReactApplicationContext reactContext) {
        super(reactContext);
        sharedState = OTRN.getSharedState();
    }

    @ReactMethod
    public void initSession(String apiKey, String sessionId, ReadableMap sessionOptions) {
        if (usbMonitor != null) {
            usbMonitor.destroy();
        }

        usbMonitor = new UsbMonitorWrapper(this.getReactApplicationContext());
        usbMonitor.registerExternalListener(deviceConnectListener);
        final boolean useTextureViews = sessionOptions.getBoolean("useTextureViews");
        final boolean isCamera2Capable = sessionOptions.getBoolean("isCamera2Capable");
        final boolean connectionEventsSuppressed = sessionOptions.getBoolean("connectionEventsSuppressed");
        final boolean ipWhitelist = sessionOptions.getBoolean("ipWhitelist");
        final boolean enableStereoOutput = sessionOptions.getBoolean("enableStereoOutput");
        if (enableStereoOutput) {
            OTCustomAudioDriver otCustomAudioDriver = new OTCustomAudioDriver(this.getReactApplicationContext());
            AudioDeviceManager.setAudioDevice(otCustomAudioDriver);
        }
        // Note: IceConfig is an additional property not supported at the moment.
        // final ReadableMap iceConfig = sessionOptions.getMap("iceConfig");
        // final List<Session.Builder.IceServer> iceConfigServerList = (List<Session.Builder.IceServer>) iceConfig.getArray("customServers");
        // final Session.Builder.IncludeServers iceConfigServerConfig; // = iceConfig.getString("includeServers");
        final String proxyUrl = sessionOptions.getString("proxyUrl");
        String androidOnTop = sessionOptions.getString("androidOnTop");
        String androidZOrder = sessionOptions.getString("androidZOrder");
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        ConcurrentHashMap<String, String> mAndroidOnTopMap = sharedState.getAndroidOnTopMap();
        ConcurrentHashMap<String, String> mAndroidZOrderMap = sharedState.getAndroidZOrderMap();


        Session mSession = new Session.Builder(this.getReactApplicationContext(), apiKey, sessionId)
                .sessionOptions(new Session.SessionOptions() {
                    @Override
                    public boolean useTextureViews() {
                        return useTextureViews;
                    }

                    @Override
                    public boolean isCamera2Capable() {
                        return isCamera2Capable;
                    }
                })
                .connectionEventsSuppressed(connectionEventsSuppressed)
                // Note: setCustomIceServers is an additional property not supported at the moment.
                // .setCustomIceServers(serverList, config)
                .setIpWhitelist(ipWhitelist)
                .setProxyUrl(proxyUrl)
                .build();
        mSession.setSessionListener(this);
        mSession.setSignalListener(this);
        mSession.setConnectionListener(this);
        mSession.setReconnectionListener(this);
        mSession.setArchiveListener(this);
        mSession.setStreamPropertiesListener(this);
        mSessions.put(sessionId, mSession);
        mAndroidOnTopMap.put(sessionId, androidOnTop);
        mAndroidZOrderMap.put(sessionId, androidZOrder);
    }

    @ReactMethod
    public void connect(String sessionId, String token, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        ConcurrentHashMap<String, Callback> mSessionConnectCallbacks = sharedState.getSessionConnectCallbacks();
        mSessionConnectCallbacks.put(sessionId, callback);
        Session mSession = mSessions.get(sessionId);
        if (mSession != null) {
            mSession.connect(token);
        } else {
            WritableMap errorInfo = EventUtils.createError("Error connecting to session. Could not find native session instance");
            callback.invoke(errorInfo);
        }
    }

    @ReactMethod
    public void initPublisher(String publisherId, ReadableMap properties, Callback callback) {
        printLogs("Init Publisher");
        String name = properties.getString("name");
        Boolean videoTrack = properties.getBoolean("videoTrack");
        Boolean audioTrack = properties.getBoolean("audioTrack");
        String cameraPosition = properties.getString("cameraPosition");
        Boolean audioFallbackEnabled = properties.getBoolean("audioFallbackEnabled");
        int audioBitrate = properties.getInt("audioBitrate");
        String frameRate = "FPS_" + properties.getInt("frameRate");
        String resolution = properties.getString("resolution");
        Boolean publishAudio = properties.getBoolean("publishAudio");
        Boolean publishVideo = properties.getBoolean("publishVideo");
        String videoSource = properties.getString("videoSource");
        Publisher mPublisher = null;
        if (videoSource.equals("screen")) {
            View view = getCurrentActivity().getWindow().getDecorView().getRootView();
            OTScreenCapturer capturer = new OTScreenCapturer(view);
            mPublisher = new Publisher.Builder(this.getReactApplicationContext())
                    .audioTrack(audioTrack)
                    .videoTrack(videoTrack)
                    .name(name)
                    .audioBitrate(audioBitrate)
                    .resolution(Publisher.CameraCaptureResolution.valueOf(resolution))
                    .frameRate(Publisher.CameraCaptureFrameRate.valueOf(frameRate))
                    .capturer(capturer)
                    .build();
            mPublisher.setPublisherVideoType(PublisherKit.PublisherKitVideoType.PublisherKitVideoTypeScreen);
        } else {

            ScreenshotVideoRenderer screenshotVideoRenderer = new ScreenshotVideoRenderer(this.getReactApplicationContext());
            CustomVideoCapturer capturer = null;
            printLogs("init publish camera: " + cameraPosition);
            if (cameraPosition.equals(CameraPosition.External) && usbMonitor.isDeviceAvailable()) {
                printLogs("using custom capturer");
                capturer = new CustomVideoCapturer(this.getReactApplicationContext(), usbMonitor.getControlBlock());
                capturer.setCameraEventsListener(position -> sendCameraPositionChanged(position));
            }
            mPublisher = new Publisher.Builder(this.getReactApplicationContext())
                    .audioTrack(audioTrack)
                    .videoTrack(videoTrack)
                    .name(name)
                    .audioBitrate(audioBitrate)
                    .resolution(Publisher.CameraCaptureResolution.valueOf(resolution))
                    .frameRate(Publisher.CameraCaptureFrameRate.valueOf(frameRate))
                    .capturer(capturer)
                    .renderer(screenshotVideoRenderer)
                    .build();
        }
        mPublisher.setPublisherListener(this);
        mPublisher.setAudioLevelListener(this);
        mPublisher.setAudioFallbackEnabled(audioFallbackEnabled);
        mPublisher.setPublishVideo(publishVideo);
        mPublisher.setPublishAudio(publishAudio);
        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        mPublishers.put(publisherId, mPublisher);
        if (!videoSource.equals("screen")) {
            this.cycleToCameraType(mPublisher, cameraPosition);
        }
        callback.invoke();
    }

    private void cycleToCameraType(Publisher publisher, String cameraPosition) {
        String publisherId = Utils.getPublisherId(publisher);

        printLogs("cycleToCameraType: " + cameraPosition);
        if (cameraPosition == null) {
            return;
        }

        int nextCameraIndex = CameraIndex.from(cameraPosition);
        if (nextCameraIndex == CameraIndex.External && !usbMonitor.isDeviceAvailable()) {
            nextCameraIndex = CameraIndex.defaultCamera;
            sendOnCameraPositionChangedEvent(publisherId, nextCameraIndex);
            return;
        }
        if (currentCameraIndex != null && nextCameraIndex == currentCameraIndex) {
            printLogs("next camera is same as current: " + currentCameraIndex);
            return;
        }

//        if (currentCameraIndex != null && currentCameraIndex == nextCameraIndex
//                && (currentCameraIndex == CameraIndex.Back || currentCameraIndex == CameraIndex.Front)) {
//            nextCameraIndex = currentCameraIndex == CameraIndex.Back ? CameraIndex.Front : CameraIndex.Back;
//        }

        String current = currentCameraIndex != null ? CameraPosition.fromIndex(currentCameraIndex) : "null";
        printLogs("current camera is: " + current);
        printLogs("next camera is: " + CameraPosition.fromIndex(nextCameraIndex));
        // TODO: it's switching when entering encounter room and usb camera is connected
        if (!isSwitchingAndroidCameras(publisher, nextCameraIndex)) {
            switchCapturer(publisher);
            return;
        }

        if (nextCameraIndex != CameraIndex.External) {
            BaseVideoCapturer.CaptureSwitch capturer = (BaseVideoCapturer.CaptureSwitch) publisher.getCapturer();
            capturer.swapCamera(nextCameraIndex);
        }

        currentCameraIndex = nextCameraIndex;
    }

    private void sendOnCameraPositionChangedEvent(String publisherId, Integer nextCameraIndex) {
        String event = publisherId + ":" + publisherPreface +  "onCameraChanged";
        printLogs(event);
        if (nextCameraIndex != null) {
          sendEventWithString(this.getReactApplicationContext(), event, CameraPosition.fromIndex(nextCameraIndex));
        }
    }

    private Publisher switchCapturer(Publisher currentPublisher) {
        String publisherId = Utils.getPublisherId(currentPublisher);

       sendOnCameraPositionChangedEvent(publisherId, CameraIndex.External);

        String event = publisherId + ":" + publisherPreface +  "Recreate";
        sendEventMap(this.getReactApplicationContext(), event, null);

        printLogs("switching capturer...");
        return  null;
    }

    private boolean isSwitchingAndroidCameras(Publisher publisher, int nextCameraIndex) {
        boolean isCapturerCustom = publisher.getCapturer() instanceof CustomVideoCapturer;
        return !isCapturerCustom && (nextCameraIndex == CameraIndex.Back || nextCameraIndex == CameraIndex.Front);
    }

    private void sendCameraPositionChanged(String position) {
        Object[] publishers = sharedState.getPublishers().values().toArray();
        if (publishers.length > 0) {
            Publisher publisher = (Publisher) publishers[0];
            if (publisher != null) {
                String id = Utils.getPublisherId(publisher);
                String event = id + ":" + publisherPreface + "cameraPositionChanged";
                sendEventWithString(getReactApplicationContext(), event, position);
            }
        }
    }

    @ReactMethod
    public void publish(String sessionId, String publisherId, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);
        if (mSession != null) {
            ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
            Publisher mPublisher = mPublishers.get(publisherId);
            if (mPublisher != null) {
                mSession.publish(mPublisher);
                callback.invoke();
            } else {
                WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native publisher instance.");
                callback.invoke(errorInfo);
            }
        } else {
            WritableMap errorInfo = EventUtils.createError("Error publishing. Could not find native session instance.");
            callback.invoke(errorInfo);
        }
    }

    @ReactMethod
    public void subscribeToStream(String streamId, String sessionId, ReadableMap properties, Callback callback) {
        printLogs("subscribe to stream: " + streamId);

        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Stream stream = mSubscriberStreams.get(streamId);
        Session mSession = mSessions.get(sessionId);
        Subscriber mSubscriber = new Subscriber.Builder(getReactApplicationContext(), stream).build();
        mSubscriber.setSubscriberListener(this);
        mSubscriber.setAudioLevelListener(this);
        mSubscriber.setAudioStatsListener(this);
        mSubscriber.setVideoStatsListener(this);
        mSubscriber.setVideoListener(this);
        mSubscriber.setStreamListener(this);
        mSubscriber.setSubscribeToAudio(properties.getBoolean("subscribeToAudio"));
        mSubscriber.setSubscribeToVideo(properties.getBoolean("subscribeToVideo"));
        if (properties.hasKey("preferredFrameRate")) {
            mSubscriber.setPreferredFrameRate((float) properties.getDouble("preferredFrameRate"));
        }
        if (properties.hasKey("preferredResolution")
                && properties.getMap("preferredResolution").hasKey("width")
                && properties.getMap("preferredResolution").hasKey("height")) {
            ReadableMap preferredResolution = properties.getMap("preferredResolution");
            VideoUtils.Size resolution = new VideoUtils.Size(
                    preferredResolution.getInt("width"),
                    preferredResolution.getInt("height"));
            mSubscriber.setPreferredResolution(resolution);
        }
        mSubscribers.put(streamId, mSubscriber);
        if (mSession != null) {
            mSession.subscribe(mSubscriber);
            callback.invoke(null, streamId);
        } else {
            WritableMap errorInfo = EventUtils.createError("Error subscribing. The native session instance could not be found.");
            callback.invoke(errorInfo);
        }
    }

    @ReactMethod
    public void removeSubscriber(final String streamId, final Callback callback) {

        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                String mStreamId = streamId;
                Callback mCallback = callback;
                ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
                ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
                ConcurrentHashMap<String, FrameLayout> mSubscriberViewContainers = sharedState.getSubscriberViewContainers();
                Subscriber mSubscriber = mSubscribers.get(mStreamId);
                FrameLayout mSubscriberViewContainer = mSubscriberViewContainers.get(mStreamId);
                if (mSubscriberViewContainer != null) {
                    mSubscriberViewContainer.removeAllViews();
                }
                mSubscriberViewContainers.remove(mStreamId);
                mSubscribers.remove(mStreamId);
                mSubscriberStreams.remove(mStreamId);
                mCallback.invoke();

            }
        });
    }

    @ReactMethod
    public void disconnectSession(String sessionId, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        ConcurrentHashMap<String, Callback> mSessionDisconnectCallbacks = sharedState.getSessionDisconnectCallbacks();
        Session mSession = mSessions.get(sessionId);
        mSessionDisconnectCallbacks.put(sessionId, callback);
        if (mSession != null) {
            mSession.disconnect();
        }
    }

    @ReactMethod
    public void publishAudio(String publisherId, Boolean publishAudio) {

        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);
        if (mPublisher != null) {
            mPublisher.setPublishAudio(publishAudio);
        }
    }

    @ReactMethod
    public void publishVideo(String publisherId, Boolean publishVideo) {

        ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
        Publisher mPublisher = mPublishers.get(publisherId);
        if (mPublisher != null) {
            mPublisher.setPublishVideo(publishVideo);
        }
    }

    @ReactMethod
    public void subscribeToAudio(String streamId, Boolean subscribeToAudio) {

        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Subscriber mSubscriber = mSubscribers.get(streamId);
        if (mSubscriber != null) {
            mSubscriber.setSubscribeToAudio(subscribeToAudio);
        }
    }

    @ReactMethod
    public void subscribeToVideo(String streamId, Boolean subscribeToVideo) {

        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Subscriber mSubscriber = mSubscribers.get(streamId);
        if (mSubscriber != null) {
            mSubscriber.setSubscribeToVideo(subscribeToVideo);
        }
    }

    @ReactMethod
    public void setPreferredResolution(String streamId, ReadableMap resolution) {

        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Subscriber mSubscriber = mSubscribers.get(streamId);
        if (mSubscriber != null ) {
            if (resolution.hasKey("width")
                    && resolution.hasKey("height")) {
                VideoUtils.Size preferredResolution = new VideoUtils.Size(
                        resolution.getInt("width"),
                        resolution.getInt("height"));
                mSubscriber.setPreferredResolution(preferredResolution);
            } else {
                mSubscriber.setPreferredResolution(SubscriberKit.NO_PREFERRED_RESOLUTION);
            }
        }
    }

    @ReactMethod
    public void setPreferredFrameRate(String streamId, Float frameRate) {

        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Subscriber mSubscriber = mSubscribers.get(streamId);
        if (mSubscriber != null) {
            mSubscriber.setPreferredFrameRate(frameRate);
        }
    }

    @ReactMethod
    public void changeCameraPosition(String publisherId, String cameraPosition) {

        Publisher mPublisher = sharedState.getPublisher(publisherId);
        if (mPublisher != null) {
            this.cycleToCameraType(mPublisher, cameraPosition);
        } else {
            printLogs("publisher is null: " + publisherId);
        }
    }

    @ReactMethod
    public void setNativeEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            jsEvents.add(events.getString(i));
        }
    }

    @ReactMethod
    public void removeNativeEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            jsEvents.remove(events.getString(i));
        }
    }

    @ReactMethod
    public void setJSComponentEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            componentEvents.add(events.getString(i));
        }
    }

    @ReactMethod
    public void removeJSComponentEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            componentEvents.remove(events.getString(i));
        }
    }

    @ReactMethod
    public void sendSignal(String sessionId, ReadableMap signal, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);
        ConcurrentHashMap<String, Connection> mConnections = sharedState.getConnections();
        String connectionId = signal.getString("to");
        Connection mConnection = null;
        if (connectionId != null) {
            mConnection = mConnections.get(connectionId);
        }
        if (mConnection != null && mSession != null) {
            mSession.sendSignal(signal.getString("type"), signal.getString("data"), mConnection);
            callback.invoke();
        } else if (mSession != null) {
            mSession.sendSignal(signal.getString("type"), signal.getString("data"));
            callback.invoke();
        } else {
            WritableMap errorInfo = EventUtils.createError("There was an error sending the signal. The native session instance could not be found.");
            callback.invoke(errorInfo);
        }

    }

    @ReactMethod
    public void destroyPublisher(final String publisherId, final Callback callback) {
        printLogs("destroyPublisher: " + publisherId);
        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                ConcurrentHashMap<String, Callback> mPublisherDestroyedCallbacks = sharedState.getPublisherDestroyedCallbacks();
                ConcurrentHashMap<String, Publisher> mPublishers = sharedState.getPublishers();
                ConcurrentHashMap<String, FrameLayout> mPublisherViewContainers = sharedState.getPublisherViewContainers();
                ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
                FrameLayout mPublisherViewContainer = mPublisherViewContainers.get(publisherId);
                Publisher mPublisher = mPublishers.get(publisherId);
                Session mSession = null;
                mPublisherDestroyedCallbacks.put(publisherId, callback);
                if (mPublisher != null && mPublisher.getSession() != null && mPublisher.getSession().getSessionId() != null) {
                    mSession = mSessions.get(mPublisher.getSession().getSessionId());
                }

                if (mPublisherViewContainer != null) {
                    mPublisherViewContainer.removeAllViews();
                }
                mPublisherViewContainers.remove(publisherId);
                if (mSession != null && mPublisher != null) {
                    mSession.unpublish(mPublisher);
                }
                if (mPublisher != null) {
                    try {
                        printLogs("Stopping capture: " + publisherId);
                        mPublisher.getCapturer().stopCapture();
                    } catch (Exception ex) {
                        // ignore
                    }
                }
                mPublishers.remove(publisherId);
            }
        });
    }

    @ReactMethod
    public void publisherTakeSnapshot(String publisherId, Promise promise) {
        Publisher publisher = sharedState.getPublisher(publisherId);
        if (publisher != null && publisher.getRenderer() instanceof ScreenshotVideoRenderer) {
            ((ScreenshotVideoRenderer) publisher.getRenderer()).saveScreenshot(promise);
        } else {
            promise.reject("Unable to snapshot", "Publisher doesn't have a snapshot renderer");
        }
    }

    @ReactMethod
    public void getSessionInfo(String sessionId, Callback callback) {
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        Session mSession = mSessions.get(sessionId);
        WritableMap sessionInfo = null;
        if (mSession != null){
            sessionInfo = EventUtils.prepareJSSessionMap(mSession);
            sessionInfo.putString("sessionId", mSession.getSessionId());
            sessionInfo.putInt("connectionStatus", getConnectionStatus(mSession.getSessionId()));
        }
        callback.invoke(sessionInfo);
    }

    @ReactMethod
    public void enableLogs(Boolean logLevel) {
        setLogLevel(logLevel);
    }

    @ReactMethod
    public void recreatePublisher(String publisherId, Callback callback) {
        FrameLayout publisherViewContainer = sharedState.getPublisherViewContainers().get(publisherId);
        Publisher publisher = sharedState.getPublishers().get(publisherId);

        Session session = null;
        if (publisher != null && publisher.getSession() != null && publisher.getSession().getSessionId() != null) {
            session = sharedState.getSessions().get(publisher.getSession().getSessionId());
        }
        if (publisherViewContainer != null) {
            printLogs("removing publisher views..." + publisherId);
            publisherViewContainer.removeAllViews();
        }
        sharedState.getPublisherViewContainers().remove(publisherId);
        if (session != null && publisher != null) {
            printLogs("unpublishing publisher views..." + publisherId);
            session.unpublish(publisher);
        }
        if (publisher != null) {
            printLogs("destroying publisher..." + publisherId);
            publisher.getCapturer().stopCapture();
            publisher.getCapturer().destroy();
        }

        if (callback != null) {
            callback.invoke();
        }
    }

    private void setLogLevel(Boolean logLevel) {
        this.logLevel = logLevel;
    }

    private void sendEventMap(ReactContext reactContext, String eventName, @Nullable WritableMap eventData) {

        if (Utils.contains(jsEvents, eventName) || Utils.contains(componentEvents, eventName)) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, eventData);
        }
    }

    private void sendEventWithString(ReactContext reactContext, String eventName, String eventString) {

        if (Utils.contains(jsEvents, eventName) || Utils.contains(componentEvents, eventName)) {
            reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, eventString);
        }
    }

    private Integer getConnectionStatus(String sessionId) {
        Integer connectionStatus = 0;
        if (this.connectionStatusMap.get(sessionId) != null) {
            connectionStatus = this.connectionStatusMap.get(sessionId);
        }
        return connectionStatus;
    }

    private void setConnectionStatus(String sessionId, Integer connectionStatus) {
        this.connectionStatusMap.put(sessionId, connectionStatus);
    }


    private void printLogs(String message) {
        if (this.logLevel) {
            Log.i(TAG, message);
        }
    }

    @Override
    public String getName() {

        return this.getClass().getSimpleName();
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        if (Utils.didConnectionFail(opentokError)) {
            setConnectionStatus(session.getSessionId(), 6);
        }
        WritableMap errorInfo = EventUtils.prepareJSErrorMap(opentokError);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onError", errorInfo);
        printLogs("Session Error: "+ opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());
    }

    @Override
    public void onDisconnected(Session session) {
        if (usbMonitor != null) {
            usbMonitor.destroy();
        }
        ConcurrentHashMap<String, Session> mSessions = sharedState.getSessions();
        ConcurrentHashMap<String, Callback> mSessionDisconnectCallbacks = sharedState.getSessionDisconnectCallbacks();
        ConcurrentHashMap<String, Callback> mSessionConnectCallbacks = sharedState.getSessionDisconnectCallbacks();
        setConnectionStatus(session.getSessionId(), 0);
        WritableMap sessionInfo = EventUtils.prepareJSSessionMap(session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onDisconnected", sessionInfo);
        Callback disconnectCallback = mSessionDisconnectCallbacks.get(session.getSessionId());
        if (disconnectCallback != null) {
            disconnectCallback.invoke();
        }
        mSessions.remove(session.getSessionId());
        mSessionConnectCallbacks.remove(session.getSessionId());
        mSessionDisconnectCallbacks.remove(session.getSessionId());
        printLogs("onDisconnected: Disconnected from session: " + session.getSessionId());
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        String streamId = stream.getStreamId();
        printLogs("onStreamReceived: New Stream Received " + streamId + " in session: " + session.getSessionId());
        ConcurrentHashMap<String, Stream> destroyedStreams = sharedState.getPublisherDestroyedStreams();
        Stream destroyedStream = destroyedStreams.get(streamId);
        if (destroyedStream != null) {
            printLogs("Stream already destroyed: " + streamId);
            destroyedStreams.remove(streamId);
            return;
        }
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        mSubscriberStreams.put(stream.getStreamId(), stream);
        WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamReceived", streamInfo);

    }

    @Override
    public void onConnected(Session session) {

        setConnectionStatus(session.getSessionId(), 1);
        ConcurrentHashMap<String, Callback> mSessionConnectCallbacks = sharedState.getSessionConnectCallbacks();
        Callback mCallback = mSessionConnectCallbacks.get(session.getSessionId());
        if (mCallback != null) {
            mCallback.invoke();
        }
        WritableMap sessionInfo = EventUtils.prepareJSSessionMap(session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onConnected", sessionInfo);
        printLogs("onConnected: Connected to session: "+session.getSessionId());
    }

    @Override
    public void onReconnected(Session session) {

        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onReconnected", null);
        printLogs("Reconnected");
    }

    @Override
    public void onReconnecting(Session session) {

        setConnectionStatus(session.getSessionId(), 3);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onReconnecting", null);
        printLogs("Reconnecting");
    }

    @Override
    public void onArchiveStarted(Session session, String id, String name) {

        WritableMap archiveInfo = Arguments.createMap();
        archiveInfo.putString("archiveId", id);
        archiveInfo.putString("name", name);
        archiveInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onArchiveStarted", archiveInfo);
        printLogs("Archive Started: " + id);
    }

    @Override
    public void onArchiveStopped(Session session, String id) {

        WritableMap archiveInfo = Arguments.createMap();
        archiveInfo.putString("archiveId", id);
        archiveInfo.putString("name", "");
        archiveInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onArchiveStopped", archiveInfo);
        printLogs("Archive Stopped: " + id);
    }
    @Override
    public void onConnectionCreated(Session session, Connection connection) {

        ConcurrentHashMap<String, Connection> mConnections = sharedState.getConnections();
        mConnections.put(connection.getConnectionId(), connection);
        WritableMap connectionInfo = EventUtils.prepareJSConnectionMap(connection);
        connectionInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onConnectionCreated", connectionInfo);
        printLogs("onConnectionCreated: Connection Created: "+connection.getConnectionId());
    }

    @Override
    public void onConnectionDestroyed(Session session, Connection connection) {

        ConcurrentHashMap<String, Connection> mConnections = sharedState.getConnections();
        mConnections.remove(connection.getConnectionId());
        WritableMap connectionInfo = EventUtils.prepareJSConnectionMap(connection);
        connectionInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onConnectionDestroyed", connectionInfo);
        printLogs("onConnectionDestroyed: Connection Destroyed: "+connection.getConnectionId());
    }
    @Override
    public void onStreamDropped(Session session, Stream stream) {

        WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamDropped", streamInfo);
        printLogs("onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        String publisherId = Utils.getPublisherId(publisherKit);
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        mSubscriberStreams.put(stream.getStreamId(), stream);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface + "onStreamCreated";
            WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, publisherKit.getSession());
            sendEventMap(this.getReactApplicationContext(), event, streamInfo);
        }
        printLogs("onStreamCreated: Publisher Stream Created. Own stream "+stream.getStreamId());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        String publisherId = Utils.getPublisherId(publisherKit);
        String event = publisherId + ":" + publisherPreface + "onStreamDestroyed";
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        String streamId = stream.getStreamId();
        sharedState.getPublisherDestroyedStreams().put(streamId, stream);
        mSubscriberStreams.remove(streamId);
        if (publisherId.length() > 0) {
            printLogs("onStreamDestroyed publisherId: " + publisherId);
            WritableMap streamInfo = EventUtils.prepareJSStreamMap(stream, publisherKit.getSession());
            sendEventMap(this.getReactApplicationContext(), event, streamInfo);
        }
        Callback mCallback = sharedState.getPublisherDestroyedCallbacks().get(publisherId);
        if (mCallback != null) {
            mCallback.invoke();
        }
        //sharedState.getPublishers().remove(publisherId);
        printLogs("onStreamDestroyed: Publisher Stream Destroyed. Own stream "+stream.getStreamId());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

        String publisherId = Utils.getPublisherId(publisherKit);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface +  "onError";
            WritableMap errorInfo = EventUtils.prepareJSErrorMap(opentokError);
            sendEventMap(this.getReactApplicationContext(), event, errorInfo);
        }
        printLogs("onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());
    }

    @Override
    public void onAudioLevelUpdated(PublisherKit publisher, float audioLevel) {

        String publisherId = Utils.getPublisherId(publisher);
        if (publisherId.length() > 0) {
            String event = publisherId + ":" + publisherPreface + "onAudioLevelUpdated";
            sendEventWithString(this.getReactApplicationContext(), event, String.valueOf(audioLevel));
        }
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onConnected", subscriberInfo);
        }
        printLogs("onConnected: Subscriber connected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onDisconnected", subscriberInfo);
        }
        printLogs("onDisconnected: Subscriber disconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onReconnected(SubscriberKit subscriberKit) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onReconnected", subscriberInfo);
        }
        printLogs("onReconnected: Subscriber reconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

        String streamId = Utils.getStreamIdBySubscriber(subscriberKit);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriberKit.getSession()));
            }
            subscriberInfo.putMap("error", EventUtils.prepareJSErrorMap(opentokError));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onError", subscriberInfo);
        }
        printLogs("onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

    }

    @Override
    public void onSignalReceived(Session session, String type, String data, Connection connection) {

        WritableMap signalInfo = Arguments.createMap();
        signalInfo.putString("type", type);
        signalInfo.putString("data", data);
        if(connection != null) {
            signalInfo.putString("connectionId", connection.getConnectionId());
        }
        signalInfo.putString("sessionId", session.getSessionId());
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onSignalReceived", signalInfo);
        printLogs("onSignalReceived: Data: " + data + " Type: " + type);
    }

    @Override
    public void onAudioStats(SubscriberKit subscriber, SubscriberKit.SubscriberAudioStats stats) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putMap("audioStats", EventUtils.prepareAudioNetworkStats(stats));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onAudioStats", subscriberInfo);
        }
    }

    @Override
    public void onVideoStats(SubscriberKit subscriber, SubscriberKit.SubscriberVideoStats stats) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putMap("videoStats", EventUtils.prepareVideoNetworkStats(stats));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoStats", subscriberInfo);
        }
    }

    @Override
    public void onAudioLevelUpdated(SubscriberKit subscriber, float audioLevel) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putString("audioLevel", String.valueOf(audioLevel));
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onAudioLevelUpdated", subscriberInfo);
        }
    }

    @Override
    public void onVideoDisabled(SubscriberKit subscriber, String reason) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putString("reason", reason);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDisabled", subscriberInfo);
        }
        printLogs("onVideoDisabled " + reason);
    }

    @Override
    public void onVideoEnabled(SubscriberKit subscriber, String reason) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            subscriberInfo.putString("reason", reason);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoEnabled", subscriberInfo);
        }
        printLogs("onVideoEnabled " + reason);
    }

    @Override
    public void onVideoDisableWarning(SubscriberKit subscriber) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDisableWarning", subscriberInfo);
        }
        printLogs("onVideoDisableWarning");
    }

    @Override
    public void onVideoDisableWarningLifted(SubscriberKit subscriber) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDisableWarningLifted", subscriberInfo);
        }
        printLogs("onVideoDisableWarningLifted");
    }

    @Override
    public void onVideoDataReceived(SubscriberKit subscriber) {

        String streamId = Utils.getStreamIdBySubscriber(subscriber);
        if (streamId.length() > 0) {
            ConcurrentHashMap<String, Stream> streams = sharedState.getSubscriberStreams();
            Stream mStream = streams.get(streamId);
            WritableMap subscriberInfo = Arguments.createMap();
            if (mStream != null) {
                subscriberInfo.putMap("stream", EventUtils.prepareJSStreamMap(mStream, subscriber.getSession()));
            }
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoDataReceived", subscriberInfo);
        }
    }

    @Override
    public void onStreamHasAudioChanged(Session session, Stream stream, boolean Audio) {

        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("hasAudio", !Audio, Audio, stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamHasAudioChanged");
    }
    @Override
    public void onStreamHasVideoChanged(Session session, Stream stream, boolean Video) {

        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("hasVideo", !Video, Video, stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamHasVideoChanged: " + stream.getStreamId());
    }

    @Override
    public void onStreamVideoDimensionsChanged(Session session, Stream stream, int width, int height) {
        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        Stream mStream = mSubscriberStreams.get(stream.getStreamId());
        WritableMap oldVideoDimensions = Arguments.createMap();
        if ( mStream != null ){
            oldVideoDimensions.putInt("height", mStream.getVideoHeight());
            oldVideoDimensions.putInt("width", mStream.getVideoWidth());
        }
        WritableMap newVideoDimensions = Arguments.createMap();
        newVideoDimensions.putInt("height", height);
        newVideoDimensions.putInt("width", width);
        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("videoDimensions", oldVideoDimensions, newVideoDimensions, stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamVideoDimensionsChanged");

    }

    @Override
    public void onStreamVideoTypeChanged(Session session, Stream stream, Stream.StreamVideoType videoType) {

        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        String oldVideoType = stream.getStreamVideoType().toString();
        WritableMap eventData = EventUtils.prepareStreamPropertyChangedEventData("videoType", oldVideoType, videoType.toString(), stream, session);
        sendEventMap(this.getReactApplicationContext(), session.getSessionId() + ":" + sessionPreface + "onStreamPropertyChanged", eventData);
        printLogs("onStreamVideoTypeChanged");
    }

    private final USBMonitor.OnDeviceConnectListener deviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(final UsbDevice device) {}

        @Override
        public void onConnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock, final boolean createNew) {
            printLogs("Device onConnect");
            if (currentCameraIndex != null && currentCameraIndex != CameraIndex.External) {
                // TODO: getting first publisher is not clean
                Publisher publisher = (Publisher) sharedState.getPublishers().values().toArray()[0];
                cycleToCameraType(publisher, CameraPosition.External);
            }
        }

        @Override
        public void onDisconnect(final UsbDevice device, final USBMonitor.UsbControlBlock ctrlBlock) {
        }

        @Override
        public void onDettach(final UsbDevice device) {
            printLogs("Device onDettach");
            Object[] publishers = sharedState.getPublishers().values().toArray();
            if (publishers.length > 0) {
                Publisher publisher = (Publisher) publishers[0];
                cycleToCameraType(publisher, CameraPosition.defaultCamera);
            }
        }

        @Override
        public void onCancel(final UsbDevice device) {
        }
    };
}
