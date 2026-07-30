package ch.ubique.backend.image.webp.awt;

import ch.ubique.backend.image.webp.WebpAnimationEncoder;
import ch.ubique.backend.image.webp.WebpEncodeOptions;
import ch.ubique.backend.image.webp.WebpFrame;
import ch.ubique.backend.image.webp.WebpMetadata;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;

/**
 * AWT-facing animation encoder convenience wrapper.
 *
 * <p>This type lives outside the core package so raw-buffer and compressed-byte consumers can stay
 * on the lower-level API surface without depending on {@link BufferedImage}-centric helpers.
 */
public final class WebpBufferedImageAnimationEncoder {
    private final WebpAnimationEncoder delegate;

    public WebpBufferedImageAnimationEncoder() {
        this(new WebpAnimationEncoder());
    }

    public WebpBufferedImageAnimationEncoder(final WebpAnimationEncoder delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must be non-null");
        }
        this.delegate = delegate;
    }

    public byte[] encodeFrames(
            final List<BufferedImage> images,
            final List<Integer> durationsMs,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        requireFrames(images);
        if (durationsMs == null || durationsMs.size() != images.size()) {
            throw new IllegalArgumentException(
                    "durationsMs must be non-null and match images in size");
        }
        try (final var arena = Arena.ofShared()) {
            final List<WebpFrame> frames = new ArrayList<>(images.size());
            for (var i = 0; i < images.size(); i++) {
                final var img = images.get(i);
                final var dur = durationsMs.get(i);
                if (dur == null || dur <= 0) {
                    throw new IllegalArgumentException(
                            "durationsMs[" + i + "] must be > 0 (was " + dur + ")");
                }
                final var raw = WebpBufferedImages.toRawImage(img, arena);
                frames.add(
                        new WebpFrame(
                                raw.pixels(),
                                raw.width(),
                                raw.height(),
                                raw.stride(),
                                raw.pixelFormat(),
                                dur));
            }
            try (final var out =
                    delegate.encodeRawFrames(
                            frames, canvasWidth, canvasHeight, backgroundRgba, options, metadata)) {
                return out.toByteArray();
            }
        }
    }

    private static void requireFrames(final List<?> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
    }
}
