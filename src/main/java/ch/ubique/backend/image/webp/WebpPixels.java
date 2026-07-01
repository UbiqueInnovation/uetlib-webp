package ch.ubique.backend.image.webp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/** Core raw-pixel buffer helpers that do not depend on AWT. */
public final class WebpPixels {
    private WebpPixels() {}

    /** Allocate a zeroed RGBA segment of {@code width * height * 4} bytes. */
    public static MemorySegment allocateRgba(final Arena arena, final int width, final int height) {
        if (arena == null) {
            throw new IllegalArgumentException("arena must be non-null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0");
        }
        final long size =
                InputValidator.multiplyExact(
                        width,
                        InputValidator.multiplyExact(height, 4L, "height * 4"),
                        "width * height * 4");
        final MemorySegment segment = arena.allocate(size, 1L);
        segment.fill((byte) 0);
        return segment;
    }
}
