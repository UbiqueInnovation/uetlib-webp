package ch.ubique.backend.image.webp;

import java.lang.foreign.MemorySegment;

public record WebpCompressedFrame(MemorySegment data, int durationMs) {
    public WebpCompressedFrame {
        if (data == null || data.equals(MemorySegment.NULL) || data.byteSize() <= 0) {
            throw new IllegalArgumentException("data must be non-null/non-NULL and non-empty");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
    }
}
