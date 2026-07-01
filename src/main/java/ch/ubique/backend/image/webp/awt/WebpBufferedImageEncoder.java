package ch.ubique.backend.image.webp.awt;

import ch.ubique.backend.image.webp.EncodedWebp;
import ch.ubique.backend.image.webp.WebpEncodeOptions;
import ch.ubique.backend.image.webp.WebpEncoder;
import ch.ubique.backend.image.webp.WebpMetadata;
import ch.ubique.backend.image.webp.WebpRawImage;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;

/** AWT-facing still-image encoder convenience wrapper. */
public final class WebpBufferedImageEncoder {
    private final WebpEncoder delegate;

    public WebpBufferedImageEncoder() {
        this(new WebpEncoder());
    }

    public WebpBufferedImageEncoder(final WebpEncoder delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must be non-null");
        }
        this.delegate = delegate;
    }

    public byte[] encode(
            final BufferedImage image,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        try (final Arena arena = Arena.ofConfined()) {
            final WebpRawImage raw = WebpBufferedImages.toRawImage(image, arena);
            try (final EncodedWebp out = delegate.encodeStillRaw(raw, options, metadata)) {
                return out.toByteArray();
            }
        }
    }
}
