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
import CoreImage.CIFilterBuiltins
import MLImage
import CoreImage

public class UIUtilities {
    
    // MARK: - Public
    
    public static func applyBlur(original originalImage: CIImage,
                                 mask: CVPixelBuffer) -> CIImage? {
        
        var maskImage = CIImage(cvPixelBuffer: mask)
        
        // Scale the mask image to fit the bounds of the video frame.
        let scaleX = originalImage.extent.width / maskImage.extent.width
        let scaleY = originalImage.extent.height / maskImage.extent.height
        maskImage = maskImage.transformed(by: .init(scaleX: scaleX, y: scaleY))
        
        let blurFilter = CIFilter(name: "CIGaussianBlur")!
        blurFilter.setValue(originalImage, forKey: kCIInputImageKey)
        let blurredBackground = blurFilter.outputImage
        
        let blendFilter = CIFilter(name: "CIBlendWithMask")!
        blendFilter.setValue(originalImage, forKey: kCIInputImageKey)
        blendFilter.setValue(blurredBackground, forKey: kCIInputBackgroundImageKey)
        blendFilter.setValue(maskImage, forKey: kCIInputMaskImageKey)
        
        return blendFilter.outputImage
    }
    
    public static func pixelateFace(original originalImage: CIImage,
                                    faces: [Face], width: CGFloat, height: CGFloat) -> CIImage? {
        var maskImage: CIImage?
        for face in faces {
            let radius = face.frame.size.height / 1.6
            let filterParams = [
                "inputRadius0": radius,
                "inputRadius1": radius + 1.0,
                "inputColor0": CIColor(red:0.0, green:1.0, blue:0.0, alpha:1.0),
                "inputColor1": CIColor(red:0.0, green:0.0, blue:0.0, alpha:0.0),
                kCIInputCenterKey: CIVector(x: face.frame.midX, y: height - face.frame.midY),
            ] as [String : Any]
            
            let circle = CIFilter(name: "CIRadialGradient", parameters: filterParams)!.outputImage
            
            if maskImage == nil {
                maskImage = circle
            } else {
                let filter = CIFilter(name: "CISourceOverCompositing")!
                filter.setValue(circle, forKey: kCIInputImageKey)
                filter.setValue(maskImage, forKey: kCIInputBackgroundImageKey)
                maskImage = filter.outputImage
            }
        }
     
        guard let maskImage = maskImage else {
            return nil
        }
        
        let pixellateFilter = CIFilter(name: "CIPixellate")!
        pixellateFilter.setValue(originalImage, forKey: kCIInputImageKey)
        pixellateFilter.setValue(12, forKey: kCIInputScaleKey)
        let pixellateImage = pixellateFilter.outputImage
        
        let blendFilter = CIFilter(name: "CIBlendWithMask")!
        blendFilter.setValue(pixellateImage, forKey: kCIInputImageKey)
        blendFilter.setValue(originalImage, forKey: kCIInputBackgroundImageKey)
        blendFilter.setValue(maskImage, forKey: kCIInputMaskImageKey)
        
        return blendFilter.outputImage
    }
    
    public static func imageOrientation(
        fromDevicePosition devicePosition: AVCaptureDevice.Position = .back
    ) -> UIImage.Orientation {
        var deviceOrientation = UIDevice.current.orientation
        if deviceOrientation == .faceDown || deviceOrientation == .faceUp
            || deviceOrientation
            == .unknown
        {
            deviceOrientation = currentUIOrientation()
        }
        switch deviceOrientation {
        case .portrait:
            return devicePosition == .front ? .leftMirrored : .right
        case .landscapeLeft:
            return devicePosition == .front ? .downMirrored : .up
        case .portraitUpsideDown:
            return devicePosition == .front ? .rightMirrored : .left
        case .landscapeRight:
            return devicePosition == .front ? .upMirrored : .down
        case .faceDown, .faceUp, .unknown:
            return .up
        @unknown default:
            fatalError()
        }
    }
    
    public static func createImageBuffer(from image: CIImage, width: Int, height: Int) -> CVImageBuffer? {
        var buffer: CVPixelBuffer? = nil
        CVPixelBufferCreate(
            kCFAllocatorDefault, width, height, kCVPixelFormatType_32BGRA, nil,
            &buffer)
        guard let imageBuffer = buffer else { return nil }
        
        let flags = CVPixelBufferLockFlags(rawValue: 0)
        CVPixelBufferLockBaseAddress(imageBuffer, flags)

        let ciContext = CIContext()
        ciContext.render(image, to: imageBuffer)
        CVPixelBufferUnlockBaseAddress(imageBuffer, flags)
        return imageBuffer
    }
    
    // MARK: - Private
    
    private static func currentUIOrientation() -> UIDeviceOrientation {
        let deviceOrientation = { () -> UIDeviceOrientation in
            switch UIApplication.shared.statusBarOrientation {
            case .landscapeLeft:
                return .landscapeRight
            case .landscapeRight:
                return .landscapeLeft
            case .portraitUpsideDown:
                return .portraitUpsideDown
            case .portrait, .unknown:
                return .portrait
            @unknown default:
                fatalError()
            }
        }
        guard Thread.isMainThread else {
            var currentOrientation: UIDeviceOrientation = .portrait
            DispatchQueue.main.sync {
                currentOrientation = deviceOrientation()
            }
            return currentOrientation
        }
        return deviceOrientation()
    }
}
