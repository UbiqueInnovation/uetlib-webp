package ch.ubique.uetlib.image.webp;

/**
 * Heap-friendly animation frame descriptor for JPEG or PNG byte frames.
 *
 * <p>This is the {@code byte[]} counterpart of {@link WebpCompressedFrame} (which wraps an
 * already-native {@link java.lang.foreign.MemorySegment}). Use this type with {@link
 * WebpAnimationEncoder#encodeJpegFrameBytes} / {@link WebpAnimationEncoder#encodePngFrameBytes} to
 * hand raw JPEG/PNG bytes straight to the animation encoder — it handles the one-time copy into a
 * shared native arena internally.
 *
 * @param data raw JPEG or PNG bytes; must be non-null and non-empty
 * @param durationMs per-frame display duration in milliseconds; must be &gt; 0
 */
public record WebpCompressedFrameBytes(byte[] data, int durationMs) {
    public WebpCompressedFrameBytes {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data must be non-null and non-empty");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
    }
}
