package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Legacy {@code uetlib-core} frame abstraction (BufferedImage or Path).
 * Kept for compatibility with {@link WebpWriter} and {@link WebpUtils}; new code should
 * decode to RGBA itself and use {@link WebpFrame} or {@link WebpCompressedFrame}.
 *
 * @deprecated Use {@link WebpFrame} or {@link WebpCompressedFrame} directly.
 */
@Deprecated(forRemoval = true)
public sealed interface Frame permits Frame.BufferedImageFrame, Frame.PathFrame {
    Duration duration();

    record BufferedImageFrame(BufferedImage image, Duration duration) implements Frame {
        public BufferedImageFrame {
            Objects.requireNonNull(image, "image must not be null");
        }

        public BufferedImageFrame(BufferedImage image) {
            this(image, null);
        }
    }

    record PathFrame(Path path, Duration duration) implements Frame {
        public PathFrame {
            Objects.requireNonNull(path, "path must not be null");
        }

        public PathFrame(Path path) {
            this(path, null);
        }
    }
}
