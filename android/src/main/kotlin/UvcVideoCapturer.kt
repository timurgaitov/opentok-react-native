package com.opentokreactnative

import android.util.Log
import com.facebook.react.bridge.ReactApplicationContext
import com.jiangdg.ausbc.MultiCameraClient
import com.jiangdg.ausbc.callback.ICameraStateCallBack
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUVC
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.usb.USBMonitor.UsbControlBlock
import com.opentok.android.BaseVideoCapturer
import com.opentok.android.BaseVideoCapturer.CaptureSettings
import com.opentokreactnative.mlkit.processors.FrameProcessingRunnable
import com.opentokreactnative.mlkit.processors.base.ProcessorFrameListener
import java.nio.ByteBuffer
import java.util.Timer
import java.util.TimerTask

class UvcVideoCapturer(
    val context: ReactApplicationContext,
    val frameListener: ProcessorFrameListener,
    val processingRunnable: FrameProcessingRunnable
) {

    private val TAG = "UvcVideoCapturer"

    val LOW_FPS = 15
    val HIGH_FPS = 30

    val LOW_WIDTH = 640
    val LOW_HEIGHT = 360

    val HIGH_WIDTH = 1280
    val HIGH_HEIGHT = 720

    var previewWidth = HIGH_WIDTH
    var previewHeight = HIGH_HEIGHT

    private var isFrameProcessorActive = false

    private val mSync = Any()

    private var processingThread: Thread? = null
    private var cameraResetTimer: Timer? = null

    private var camera: MultiCameraClient.ICamera? = null

    fun init(controlBlock: UsbControlBlock) {
        synchronized(mSync) {
            if (camera == null) {
                camera = CameraUVC(context, controlBlock.device)
                camera?.setUsbControlBlock(controlBlock)
                camera?.setCameraStateCallBack(cameraStateCallBack)
                camera?.addPreviewDataCallBack(previewDataCallBack)

                processingRunnable.setPreviewSize(LOW_WIDTH, LOW_HEIGHT)
                processingRunnable.setRotation(0)
            }
        }
    }

    fun startCapture(): Int {
        synchronized(mSync) {
            if (camera != null) {
                camera?.openCamera(null, getCameraRequest())
                processingThread = Thread(processingRunnable)
                processingRunnable.setActive(true)
                processingThread?.start()
            }
        }
        return 0
    }

    fun stopCapture(): Int {
        synchronized(mSync) {
            processingRunnable.setActive(false)
            if (processingThread != null) {
                try {
                    // Wait for the thread to complete to ensure that we can't have multiple threads
                    // executing at the same time (i.e., which would happen if we called start too
                    // quickly after stop).
                    processingThread?.join()
                } catch (e: InterruptedException) {
                    log("Frame processing thread interrupted on release.")
                }
                processingThread = null
            }
            camera?.closeCamera()
        }
        return 0
    }

    fun destroy() {
        synchronized(mSync) { releaseCamera() }
    }

    fun getCaptureSettings(): CaptureSettings {
        val settings = CaptureSettings()
        settings.fps = if (isFrameProcessorActive) LOW_FPS else HIGH_FPS
        settings.width = previewWidth
        settings.height = previewHeight
        settings.format =
            if (isFrameProcessorActive) BaseVideoCapturer.ARGB else BaseVideoCapturer.NV21
        settings.expectedDelay = 0
        return settings
    }


    fun closeCamera() {
        synchronized(mSync) {
            camera?.closeCamera()
        }
    }

    @Synchronized
    fun releaseCamera(): Int {
        synchronized(mSync) {
            try {
                stopCapture()
                camera = null
            } catch (e: Exception) {
                // ignore
            }
        }
        return 0
    }

    fun onFrameProcessorEnabled(enabled: Boolean) {
        cameraResetTimer?.cancel()
        if (isFrameProcessorActive != enabled) {
            if (camera != null) {
                cameraResetTimer = Timer()
                cameraResetTimer!!.schedule(object : TimerTask() {
                    override fun run() {
                        isFrameProcessorActive = enabled
                        stopCapture()
                        previewWidth = if (enabled) LOW_WIDTH else HIGH_WIDTH
                        previewHeight = if (enabled) LOW_HEIGHT else HIGH_HEIGHT
                        startCapture()
                    }
                }, 500)
            } else {
                isFrameProcessorActive = enabled
            }
        }
    }

    private fun getCameraRequest(): CameraRequest {
        return CameraRequest.Builder()
            .setPreviewWidth(previewWidth)
            .setPreviewHeight(previewHeight)
            .setRawPreviewData(true)
            .create()
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private val previewDataCallBack: IPreviewDataCallBack = object : IPreviewDataCallBack {
        override fun onPreviewData(
            data: ByteArray?,
            width: Int,
            height: Int,
            format: IPreviewDataCallBack.DataFormat
        ) {
            if (isFrameProcessorActive) {
                if (data != null) {
                    processingRunnable.setNextFrame(ByteBuffer.wrap(data))
                }
            } else {
                frameListener.onFrame(data, width, height, 0)
            }
        }
    }

    private val cameraStateCallBack: ICameraStateCallBack = object : ICameraStateCallBack {
        override fun onCameraState(
            self: MultiCameraClient.ICamera,
            code: ICameraStateCallBack.State,
            msg: String?
        ) {
            log("state: " + code.name + " message: " + msg)
        }
    }

}