package com.opentokreactnative.utils;

import android.os.Handler;
import android.os.Looper;

import com.serenegiant.utils.HandlerThreadHandler;

public class BaseUtility {
	private final Handler mUIHandler = new Handler(Looper.getMainLooper());
	private final Thread mUiThread = mUIHandler.getLooper().getThread();
	private Handler mWorkerHandler;
	private long mWorkerThreadID = -1;

	public BaseUtility() {
		if (mWorkerHandler == null) {
			mWorkerHandler = HandlerThreadHandler.createHandler(BaseUtility.class.getName());
			mWorkerThreadID = mWorkerHandler.getLooper().getThread().getId();
		}
	}

	protected synchronized void onDestroy() {
		if (mWorkerHandler != null) {
			try {
				mWorkerHandler.getLooper().quit();
			} catch (final Exception e) {
			}
			mWorkerHandler = null;
		}
	}


	public final void runOnUiThread(final Runnable task, final long duration) {
		if (task == null) return;
		mUIHandler.removeCallbacks(task);
		if ((duration > 0) || Thread.currentThread() != mUiThread) {
			mUIHandler.postDelayed(task, duration);
		} else {
			try {
				task.run();
			} catch (final Exception e) {
			}
		}
	}

	protected final synchronized void queueEvent(final Runnable task, final long delayMillis) {
		if ((task == null) || (mWorkerHandler == null)) return;
		try {
			mWorkerHandler.removeCallbacks(task);
			if (delayMillis > 0) {
				mWorkerHandler.postDelayed(task, delayMillis);
			} else if (mWorkerThreadID == Thread.currentThread().getId()) {
				task.run();
			} else {
				mWorkerHandler.post(task);
			}
		} catch (final Exception e) {
		}
	}
}
