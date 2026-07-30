package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.awt.WebpBufferedImageEncoder;

import org.jspecify.annotations.Nullable;

import java.awt.image.BufferedImage;

/**
 * High-level convenience facade for common still-image conversions.
 *
 * <p>Use this when you want a one-line API for the common cases while keeping the lower-level
 * {@link WebpEncoder} API available for explicit control.
 */
public final class Webp {
    private Webp() {}

    public static Still lossless() {
        return new Still(WebpEncodeOptions.builder().lossless(true).build());
    }

    public static Still lossy(final float quality) {
        return new Still(WebpEncodeOptions.builder().quality(quality).build());
    }

    public static Still withOptions(final WebpEncodeOptions options) {
        return new Still(options);
    }

    public static final class Still {
        private static final WebpEncoder ENCODER = new WebpEncoder();
        private static final WebpBufferedImageEncoder BUFFERED_IMAGE_ENCODER =
                new WebpBufferedImageEncoder(ENCODER);

        private final WebpEncodeOptions options;

        private Still(final WebpEncodeOptions options) {
            if (options == null) {
                throw new IllegalArgumentException("options must be non-null");
            }
            this.options = options;
        }

        private static WebpMetadata defaultMetadata(final @Nullable WebpMetadata metadata) {
            return metadata == null ? WebpMetadata.EMPTY : metadata;
        }

        public byte[] encodePng(final byte[] pngBytes) {
            return encodePng(pngBytes, WebpMetadata.EMPTY);
        }

        public byte[] encodePng(final byte[] pngBytes, final WebpMetadata metadata) {
            return ENCODER.encodeStillPngToBytes(pngBytes, options, defaultMetadata(metadata));
        }

        public byte[] encodeJpeg(final byte[] jpegBytes) {
            return encodeJpeg(jpegBytes, WebpMetadata.EMPTY);
        }

        public byte[] encodeJpeg(final byte[] jpegBytes, final WebpMetadata metadata) {
            return ENCODER.encodeStillJpegToBytes(jpegBytes, options, defaultMetadata(metadata));
        }

        public byte[] encode(final BufferedImage image) {
            return encode(image, WebpMetadata.EMPTY);
        }

        public byte[] encode(final BufferedImage image, final WebpMetadata metadata) {
            return BUFFERED_IMAGE_ENCODER.encode(image, options, defaultMetadata(metadata));
        }

        public WebpEncodeOptions options() {
            return options;
        }
    }
}
