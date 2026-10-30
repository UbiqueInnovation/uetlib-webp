package ch.ubique.uetlib.image.webp.awt;

import ch.ubique.uetlib.image.webp.WebpEncodeOptions;
import ch.ubique.uetlib.image.webp.WebpEncoder;
import ch.ubique.uetlib.image.webp.WebpMetadata;
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
        try (final var arena = Arena.ofConfined()) {
            final var raw = WebpBufferedImages.toRawImage(image, arena);
            try (final var out = delegate.encodeStillRaw(raw, options, metadata)) {
                return out.toByteArray();
            }
        }
    }
}
