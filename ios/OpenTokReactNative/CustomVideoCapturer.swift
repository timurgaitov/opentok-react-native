
import Foundation
import OpenTok

class CustomVideoCapturer: NSObject, OTVideoCapture {

    fileprivate var captureStarted = false
    
    var videoCaptureConsumer: OTVideoCaptureConsumer?
    var videoContentHint: OTVideoContentHint = OTVideoContentHint.none
    
    private var cameraController: CameraController?
    private var frame: OTVideoFrame?
    
    override init() {
        super.init()
        cameraController = CameraController(capturer: self)
        cameraController?.setup()
    }
    
    func initCapture() {

    }
    
    func releaseCapture() {
        
    }
    
    func start() -> Int32 {
        captureStarted = true
        cameraController?.start()
        return 0
    }
    
    func stop() -> Int32 {
        captureStarted = false
        cameraController?.stop()
        return 0
    }
    
    func isCaptureStarted() -> Bool {
        return captureStarted
    }
    
    func captureSettings(_ videoFormat: OTVideoFormat) -> Int32 {
        return 0
    }
    
    func enableBackgroundBlur(enabled: Bool) {
        cameraController?.blurBackground = enabled
    }
    
    func enablePixelatedFace(enabled: Bool) {
        cameraController?.pixelateFace = enabled
    }
    
    func swapCamera(to position: AVCaptureDevice.Position) {
        cameraController?.swapCamera(to: position)
    }
    
    func provideFrame(_ buffer: CVPixelBuffer) {
        if frame == nil {
            let format = OTVideoFormat()
            format.pixelFormat = .ARGB
            format.imageWidth = UInt32(CVPixelBufferGetWidth(buffer))
            format.imageHeight = UInt32(CVPixelBufferGetHeight(buffer))
            format.bytesPerRow = [CVPixelBufferGetBytesPerRow(buffer)]
            
            frame = OTVideoFrame(format: format)
        }

        guard let frame = frame else {
          return
        }

        CVPixelBufferLockBaseAddress(buffer, .readOnly)
        if let frameData = CVPixelBufferGetBaseAddress(buffer) {
          frame.orientation = .left
          frame.clearPlanes()
          frame.planes?.addPointer(frameData)

          videoCaptureConsumer?.consumeFrame(frame)
        }
        CVPixelBufferUnlockBaseAddress(buffer, .readOnly)
    }
}
