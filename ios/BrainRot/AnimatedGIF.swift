import ImageIO
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// Plays a GIF from the app bundle. SwiftUI's Image shows only the first frame
/// of a GIF, so this wraps a UIImageView with the frames decoded by ImageIO.
struct AnimatedGIF: UIViewRepresentable {

    let resource: String

    func makeUIView(context: Context) -> UIImageView {
        let view = UIImageView()
        view.contentMode = .scaleAspectFit
        view.image = UIImage.animatedGIF(named: resource)
        view.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        view.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
        return view
    }

    func updateUIView(_ uiView: UIImageView, context: Context) {}
}

extension UIImage {

    /// Decodes every frame and its delay, then hands back one animated UIImage.
    static func animatedGIF(named name: String) -> UIImage? {
        guard let url = Bundle.main.url(forResource: name, withExtension: "gif"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil)
        else { return nil }

        let count = CGImageSourceGetCount(source)
        var frames: [UIImage] = []
        var total: TimeInterval = 0

        for index in 0..<count {
            guard let cgImage = CGImageSourceCreateImageAtIndex(source, index, nil) else { continue }
            frames.append(UIImage(cgImage: cgImage))
            total += frameDelay(source: source, index: index)
        }

        guard !frames.isEmpty else { return nil }
        return UIImage.animatedImage(with: frames, duration: total)
    }

    private static func frameDelay(source: CGImageSource, index: Int) -> TimeInterval {
        let fallback: TimeInterval = 0.07
        guard let properties = CGImageSourceCopyPropertiesAtIndex(source, index, nil)
            as? [CFString: Any],
            let gif = properties[kCGImagePropertyGIFDictionary] as? [CFString: Any]
        else { return fallback }

        let unclamped = gif[kCGImagePropertyGIFUnclampedDelayTime] as? TimeInterval
        let clamped = gif[kCGImagePropertyGIFDelayTime] as? TimeInterval
        let delay = unclamped ?? clamped ?? fallback
        return delay > 0 ? delay : fallback
    }
}
