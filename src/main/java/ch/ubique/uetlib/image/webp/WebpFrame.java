package ch.ubique.uetlib.image.webp;

import java.lang.foreign.MemorySegment;

public record WebpFrame(
        MemorySegment pixels,
        int width,
        int height,
        int stride,
        PixelFormat pixelFormat,
        int durationMs) {
    public WebpFrame {
        InputValidator.requireRawImage(pixels, width, height, stride, pixelFormat);
        InputValidator.requirePositiveDuration(durationMs, "durationMs");
    }
}
