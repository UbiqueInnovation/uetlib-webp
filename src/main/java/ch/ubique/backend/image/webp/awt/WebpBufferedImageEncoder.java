package ch.ubique.backend.image.webp.awt;

import ch.ubique.backend.image.webp.EncodedWebp;
import ch.ubique.backend.image.webp.WebpEncodeOptions;
import ch.ubique.backend.image.webp.WebpEncoder;
import ch.ubique.backend.image.webp.WebpMetadata;
import ch.ubique.backend.image.webp.WebpRawImage;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;

/**
 * AWT-facing still-image encoder convenience wrapper.
 */
public final class WebpBufferedImageEncoder {
    private final WebpEncoder delegate;

    public WebpBufferedImageEncoder() {
        this(new WebpEncoder());
    }

    public WebpBufferedImageEncoder(WebpEncoder delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must be non-null");
        }
        this.delegate = delegate;
    }

    public byte[] encode(BufferedImage image, WebpEncodeOptions options, WebpMetadata metadata) {
        try (Arena arena = Arena.ofConfined()) {
            WebpRawImage raw = WebpBufferedImages.toRawImage(image, arena);
            try (EncodedWebp out = delegate.encodeStillRaw(raw, options, metadata)) {
                return out.toByteArray();
            }
        }
    }
}
