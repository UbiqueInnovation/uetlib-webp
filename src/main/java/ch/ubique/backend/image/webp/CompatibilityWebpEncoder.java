package ch.ubique.backend.image.webp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Compatibility adapter that takes raw {@code byte[]} RGBA buffers (legacy {@code uetlib-core} shape)
 * and forwards to {@link WebpEncoder}/{@link WebpAnimationEncoder}. Used by JMH benchmarks that
 * compare against the old API.
 *
 * @deprecated Use {@link WebpEncoder} or {@link WebpAnimationEncoder} directly.
 */
@Deprecated(forRemoval = true)
public final class CompatibilityWebpEncoder {
    private final WebpEncoder stillEncoder = new WebpEncoder();
    private final WebpAnimationEncoder animationEncoder = new WebpAnimationEncoder();

    public byte[] encodeStillRgba(byte[] rgba, int width, int height, WebpEncodeOptions options) {
        int stride = width * 4;
        try (Arena arena = Arena.ofConfined();
             EncodedWebp encoded = stillEncoder.encodeStillRaw(
                 new WebpRawImage(copyToArena(arena, rgba), width, height, stride, PixelFormat.RGBA),
                 options,
                 WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    public byte[] encodeAnimationRgba(List<byte[]> rgbaFrames, int width, int height, int durationMs, WebpEncodeOptions options) {
        int stride = width * 4;
        try (Arena arena = Arena.ofConfined()) {
            List<WebpFrame> frames = new ArrayList<>(rgbaFrames.size());
            for (byte[] frame : rgbaFrames) {
                frames.add(new WebpFrame(copyToArena(arena, frame), width, height, stride, PixelFormat.RGBA, durationMs));
            }
            try (EncodedWebp encoded = animationEncoder.encodeRawFrames(frames, width, height, 0, options, WebpMetadata.EMPTY)) {
                return encoded.toByteArray();
            }
        }
    }

    private static MemorySegment copyToArena(Arena arena, byte[] source) {
        MemorySegment target = arena.allocate(source.length);
        MemorySegment.copy(MemorySegment.ofArray(source), 0, target, 0, source.length);
        return target;
    }
}
