//
//  Copyright (c) 2018 Google Inc.
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import AVFoundation
import CoreVideo
import MLImage

class CameraController : NSObject {
    
    private var _blurBackground: Bool = false
    public var blurBackground: Bool {
        get { return _blurBackground }
        set {
            if (newValue) {
                let options = SelfieSegmenterOptions()
                self.segmenter = Segmenter.segmenter(options: options)
            } else  {
                segmenter = nil
            }
            _blurBackground = newValue
        }
    }
    
    public var pixelateFace: Bool = false
    
    private var isUsingFrontCamera = true
    private var lastFrame: CMSampleBuffer?
    
    private var segmenter: Segmenter? = nil
    
    private var capturer: CustomVideoCapturer
    private var lastFace: Face?
    
    private lazy var captureSession = AVCaptureSession()
    private lazy var sessionQueue = DispatchQueue(label: Constant.sessionQueueLabel)
    
    init(capturer: CustomVideoCapturer) {
        self.capturer = capturer
    }
    
    func setup() {
        setUpCaptureSessionOutput()
        setUpCaptureSessionInput()
    }
    
    func start() {
        startSession()
    }
    
    func stop() {
        stopSession()
    }
    
    
    func switchCamera() {
        lastFace = nil
        isUsingFrontCamera = !isUsingFrontCamera
        setUpCaptureSessionInput()
    }
    
    func swapCamera(to position: AVCaptureDevice.Position) {
        lastFace = nil
        isUsingFrontCamera = AVCaptureDevice.Position.front == position
        setUpCaptureSessionInput()
    }
    
    private func detectFacesOnDevice(in image: VisionImage, original frame: CIImage, width: Int, height: Int) -> CIImage? {
        let options = FaceDetectorOptions()
        options.landmarkMode = .none
        options.contourMode = .none
        options.classificationMode = .none
        options.performanceMode = .fast
        let faceDetector = FaceDetector.faceDetector(options: options)
        var faces: [Face] = []
        do {
            faces = try faceDetector.results(in: image)
        } catch {
            
        }
        
        if faces.isEmpty && lastFace == nil {
            return nil
        } else if !faces.isEmpty {
            lastFace = faces.first!
        }
        
        return UIUtilities.pixelateFace(original: frame, face: lastFace!, width: CGFloat(width), height: CGFloat(height))
    }
    
    private func detectSegmentationMask(in image: VisionImage, original frame: CIImage) -> CIImage? {
        guard let segmenter = self.segmenter else {
            return nil
        }
        var mask: SegmentationMask
        do {
            mask = try segmenter.results(in: image)
        } catch {
            return nil
        }
        
        return UIUtilities.applyBlur(original: frame, mask: mask.buffer)
    }
    
    // MARK: - Private
    
    private func setUpCaptureSessionOutput() {
        weak var weakSelf = self
        sessionQueue.async {
            guard let strongSelf = weakSelf else {
                return
            }
            strongSelf.captureSession.beginConfiguration()
            
            // When performing latency tests to determine ideal capture settings,
            // run the app in 'release' mode to get accurate performance metrics
            strongSelf.captureSession.sessionPreset = AVCaptureSession.Preset.medium
            
            let output = AVCaptureVideoDataOutput()
            output.videoSettings = [
                (kCVPixelBufferPixelFormatTypeKey as String): kCVPixelFormatType_32BGRA
            ]
            output.alwaysDiscardsLateVideoFrames = true
            let outputQueue = DispatchQueue(label: Constant.videoDataOutputQueueLabel)
            output.setSampleBufferDelegate(strongSelf, queue: outputQueue)
            guard strongSelf.captureSession.canAddOutput(output) else {
                return
            }
            strongSelf.captureSession.addOutput(output)
            strongSelf.captureSession.commitConfiguration()
        }
    }
    
    private func setUpCaptureSessionInput() {
        weak var weakSelf = self
        sessionQueue.async {
            guard let strongSelf = weakSelf else {
                return
            }
            let cameraPosition: AVCaptureDevice.Position = strongSelf.isUsingFrontCamera ? .front : .back
            guard let device = strongSelf.captureDevice(forPosition: cameraPosition) else {
                return
            }
            do {
                strongSelf.captureSession.beginConfiguration()
                let currentInputs = strongSelf.captureSession.inputs
                for input in currentInputs {
                    strongSelf.captureSession.removeInput(input)
                }
                
                let input = try AVCaptureDeviceInput(device: device)
                guard strongSelf.captureSession.canAddInput(input) else {
                    return
                }
                strongSelf.captureSession.addInput(input)
                if #available(iOS 13.0, *) {
                    strongSelf.captureSession.connections.first?.automaticallyAdjustsVideoMirroring = false
                }
                strongSelf.captureSession.commitConfiguration()
            } catch {
            }
        }
    }
    
    private func startSession() {
        weak var weakSelf = self
        sessionQueue.async {
            guard let strongSelf = weakSelf else {
                return
            }
            strongSelf.captureSession.startRunning()
        }
    }
    
    private func stopSession() {
        weak var weakSelf = self
        sessionQueue.async {
            guard let strongSelf = weakSelf else {
                return
            }
            strongSelf.captureSession.stopRunning()
        }
    }
    
    private func captureDevice(forPosition position: AVCaptureDevice.Position) -> AVCaptureDevice? {
        if #available(iOS 10.0, *) {
            let discoverySession = AVCaptureDevice.DiscoverySession(
                deviceTypes: [.builtInWideAngleCamera],
                mediaType: .video,
                position: .unspecified
            )
            return discoverySession.devices.first { $0.position == position }
        }
        return nil
    }
    
}

extension CameraController: AVCaptureVideoDataOutputSampleBufferDelegate {
    
    func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        
        let orientation = UIUtilities.imageOrientation(
            fromDevicePosition: isUsingFrontCamera ? .front : .back
        )
        if (!blurBackground && !pixelateFace) {
            capturer.provideFrame(imageBuffer)
            return
        }
        
        let originalImage = CIImage(cvPixelBuffer: imageBuffer)
        
        lastFrame = sampleBuffer
        let visionImage = VisionImage(buffer: sampleBuffer)
        visionImage.orientation = orientation
        
        let imageWidth = CVPixelBufferGetWidth(imageBuffer)
        let imageHeight = CVPixelBufferGetHeight(imageBuffer)
        
        var result = originalImage
        if (pixelateFace) {
            if let face = detectFacesOnDevice(in: visionImage, original: originalImage, width: imageWidth, height: imageHeight) {
                result = face
            }
        }
        
        if (blurBackground) {
            if let background = detectSegmentationMask(in: visionImage, original: result) {
                result = background
            }
        }
        
        if let buffer = UIUtilities.createImageBuffer(from: result, width: imageWidth, height: imageHeight) {
            capturer.provideFrame(buffer)
        }
    }
}


private enum Constant {
    static let videoDataOutputQueueLabel = "com.smartsignals.VideoDataOutputQueue"
    static let sessionQueueLabel = "com.smartsignals.SessionQueue"
}
