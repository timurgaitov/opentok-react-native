package com.opentokreactnative;

import android.widget.FrameLayout;

import com.facebook.react.bridge.Callback;
import com.opentok.android.Connection;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;

import java.util.concurrent.ConcurrentHashMap;
/**
 * Created by manik on 1/10/18.
 */

public class OTRN {

    public static OTRN sharedState;

    private ConcurrentHashMap<String, Stream> subscriberStreams = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Subscriber> subscribers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Publisher> publishers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> androidOnTopMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, String> androidZOrderMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FrameLayout> subscriberViewContainers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, FrameLayout> publisherViewContainers = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Callback> publisherDestroyedCallbacks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Callback> sessionConnectCallbacks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Callback> sessionDisconnectCallbacks = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Connection> connections = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Stream> publisherDestroyedStreams = new ConcurrentHashMap<>();

    public static synchronized OTRN getSharedState() {

        if (sharedState == null) {
            sharedState = new OTRN();
        }
        return sharedState;
    }

    public ConcurrentHashMap<String, String> getAndroidOnTopMap() {

        return this.androidOnTopMap;
    }

    public ConcurrentHashMap<String, String> getAndroidZOrderMap() {

        return this.androidZOrderMap;
    }

    public ConcurrentHashMap<String, Stream> getSubscriberStreams() {

        return this.subscriberStreams;
    }

    public ConcurrentHashMap<String, Subscriber> getSubscribers() {

        return this.subscribers;
    }

    public Subscriber getSubscriber(String streamId) {

        return this.subscribers.get(streamId);
    }

    public ConcurrentHashMap<String, FrameLayout> getSubscriberViewContainers() {

        return this.subscriberViewContainers;
    }

    public ConcurrentHashMap<String, Publisher> getPublishers() {

        return this.publishers;
    }

    public Publisher getPublisher(String publisherId) {

        return this.publishers.get(publisherId);
    }

    public ConcurrentHashMap<String, FrameLayout> getPublisherViewContainers() {

        return this.publisherViewContainers;
    }

    public ConcurrentHashMap<String, Callback> getPublisherDestroyedCallbacks() {

        return this.publisherDestroyedCallbacks;
    }

    public ConcurrentHashMap<String, Callback> getSessionConnectCallbacks() {

        return this.sessionConnectCallbacks;
    }

    public ConcurrentHashMap<String, Callback> getSessionDisconnectCallbacks() {

        return this.sessionDisconnectCallbacks;
    }

    public ConcurrentHashMap<String, Connection> getConnections() {

        return this.connections;
    }

    public ConcurrentHashMap<String, Session> getSessions() {

        return this.sessions;
    }

    public ConcurrentHashMap<String, Stream> getPublisherDestroyedStreams() {
        return this.publisherDestroyedStreams;
    }

    private OTRN() {}
}
