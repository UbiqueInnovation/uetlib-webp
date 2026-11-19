package ch.uetlib.webp;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

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
// equals and hashCode are overridden, so the array component is not compared by reference.
@SuppressWarnings("ArrayRecordComponent")
public record WebpCompressedFrameBytes(byte[] data, int durationMs) {
    public WebpCompressedFrameBytes {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data must be non-null and non-empty");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
    }

    @Override
    public boolean equals(final @Nullable Object o) {
        return o instanceof final WebpCompressedFrameBytes that
                && durationMs == that.durationMs
                && Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(data) + durationMs;
    }

    @Override
    public String toString() {
        return "WebpCompressedFrameBytes[data.length="
                + data.length
                + ", durationMs="
                + durationMs
                + "]";
    }
}
