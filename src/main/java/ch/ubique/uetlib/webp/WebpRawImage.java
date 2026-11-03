package ch.ubique.uetlib.webp;

import java.lang.foreign.MemorySegment;

public record WebpRawImage(
        MemorySegment pixels, int width, int height, int stride, PixelFormat pixelFormat) {
    public WebpRawImage {
        InputValidator.requireRawImage(pixels, width, height, stride, pixelFormat);
    }
}
