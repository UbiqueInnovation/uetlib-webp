package ch.ubique.backend.image.webp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PixelFormatTest {
    @Test
    void nativeCodesAreStable() {
        assertEquals(1, PixelFormat.RGBA.nativeCode());
        assertEquals(2, PixelFormat.RGB.nativeCode());
        assertEquals(3, PixelFormat.BGRA.nativeCode());
        assertEquals(4, PixelFormat.BGR.nativeCode());
    }
}
