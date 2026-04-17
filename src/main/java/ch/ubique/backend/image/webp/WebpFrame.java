package ch.ubique.backend.image.webp;

import java.lang.foreign.MemorySegment;

public record WebpFrame(MemorySegment pixels, int width, int height, int stride, PixelFormat pixelFormat, int durationMs) {
    public WebpFrame {
        if (pixels == null || pixels.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("pixels must be non-null/non-NULL");
        }
        if (width <= 0 || height <= 0 || stride <= 0 || durationMs <= 0) {
            throw new IllegalArgumentException("width/height/stride/durationMs must be > 0");
        }
    }
}
