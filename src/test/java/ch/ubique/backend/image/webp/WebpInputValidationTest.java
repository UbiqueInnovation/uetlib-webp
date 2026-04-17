package ch.ubique.backend.image.webp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebpInputValidationTest {
    @Test
    void rawImageRequiresPositiveGeometryAndNonNullPixels() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixels = arena.allocate(16);
            assertDoesNotThrow(() -> new WebpRawImage(pixels, 1, 1, 4, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(null, 1, 1, 4, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(MemorySegment.NULL, 1, 1, 4, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(pixels, 0, 1, 4, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(pixels, 1, 0, 4, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(pixels, 1, 1, 0, PixelFormat.RGBA));
        }
    }

    @Test
    void frameRequiresPositiveGeometryDurationAndNonNullPixels() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixels = arena.allocate(16);
            assertDoesNotThrow(() -> new WebpFrame(pixels, 1, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(null, 1, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(MemorySegment.NULL, 1, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(pixels, 0, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(pixels, 1, 0, 4, PixelFormat.RGBA, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(pixels, 1, 1, 0, PixelFormat.RGBA, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(pixels, 1, 1, 4, PixelFormat.RGBA, 0));
        }
    }

    @Test
    void compressedFrameRequiresDataAndPositiveDuration() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment data = arena.allocate(16);
            assertDoesNotThrow(() -> new WebpCompressedFrame(data, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpCompressedFrame(null, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpCompressedFrame(MemorySegment.NULL, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpCompressedFrame(arena.allocate(0), 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpCompressedFrame(data, 0));
        }
    }

    @Test
    void animationEncoderRejectsEmptyFrames() {
        WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeRawFrames(java.util.List.of(), 1, 1, 0, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeJpegFrames(java.util.List.of(), 0, 0, 0, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodePngFrames(java.util.List.of(), 0, 0, 0, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
    }
}
