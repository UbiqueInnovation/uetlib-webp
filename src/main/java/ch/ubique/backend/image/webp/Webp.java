package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.awt.WebpBufferedImageEncoder;
import java.awt.image.BufferedImage;

/**
 * High-level convenience facade for common still-image conversions.
 *
 * <p>Use this when you want a one-line API for the common cases while keeping the lower-level
 * {@link WebpEncoder} API available for explicit control.
 */
public final class Webp {
    private Webp() {
    }

    public static Still lossless() {
        return new Still(WebpEncodeOptions.builder().lossless(true).build());
    }

    public static Still lossy(float quality) {
        return new Still(WebpEncodeOptions.builder().quality(quality).build());
    }

    public static Still withOptions(WebpEncodeOptions options) {
        return new Still(options);
    }

    public static final class Still {
        private static final WebpEncoder ENCODER = new WebpEncoder();
        private static final WebpBufferedImageEncoder BUFFERED_IMAGE_ENCODER = new WebpBufferedImageEncoder(ENCODER);

        private final WebpEncodeOptions options;

        private Still(WebpEncodeOptions options) {
            if (options == null) {
                throw new IllegalArgumentException("options must be non-null");
            }
            this.options = options;
        }

        public byte[] encodePng(byte[] pngBytes) {
            return encodePng(pngBytes, WebpMetadata.EMPTY);
        }

        public byte[] encodePng(byte[] pngBytes, WebpMetadata metadata) {
            return ENCODER.encodeStillPngToBytes(pngBytes, options, defaultMetadata(metadata));
        }

        public byte[] encodeJpeg(byte[] jpegBytes) {
            return encodeJpeg(jpegBytes, WebpMetadata.EMPTY);
        }

        public byte[] encodeJpeg(byte[] jpegBytes, WebpMetadata metadata) {
            return ENCODER.encodeStillJpegToBytes(jpegBytes, options, defaultMetadata(metadata));
        }

        public byte[] encode(BufferedImage image) {
            return encode(image, WebpMetadata.EMPTY);
        }

        public byte[] encode(BufferedImage image, WebpMetadata metadata) {
            return BUFFERED_IMAGE_ENCODER.encode(image, options, defaultMetadata(metadata));
        }

        public WebpEncodeOptions options() {
            return options;
        }

        private static WebpMetadata defaultMetadata(WebpMetadata metadata) {
            return metadata == null ? WebpMetadata.EMPTY : metadata;
        }
    }
}
