package ch.ubique.backend.image.webp;

import java.lang.foreign.MemorySegment;

public record WebpCompressedFrame(MemorySegment data, int durationMs) {
    public WebpCompressedFrame {
        InputValidator.requireReadableSegment(data, "data");
        InputValidator.requirePositiveDuration(durationMs, "durationMs");
    }
}
