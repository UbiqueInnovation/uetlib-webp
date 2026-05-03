package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.awt.WebpBufferedImageAnimationEncoder;
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
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(pixels, 2, 1, 3, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(arena.allocate(3), 1, 1, 4, PixelFormat.RGBA));
            assertThrows(IllegalArgumentException.class, () -> new WebpRawImage(pixels, 1, 1, 4, null));
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
            assertThrows(IllegalArgumentException.class, () -> new WebpFrame(arena.allocate(3), 1, 1, 4, PixelFormat.RGBA, 33));
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

    @Test
    void byteFrameAnimationRejectsEmptyOrMismatchedInputs() {
        WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeJpegFrameBytes(java.util.List.of(), 0, 0, 0, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodePngFrameBytes(java.util.List.of(), 0, 0, 0, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
    }

    @Test
    void bufferedImageFramesValidatesDurationsList() {
        WebpBufferedImageAnimationEncoder encoder = new WebpBufferedImageAnimationEncoder();
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeFrames(java.util.List.of(), java.util.List.of(1), 2, 2, 0,
                WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeFrames(java.util.List.of(img), null, 2, 2, 0,
                WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeFrames(java.util.List.of(img, img), java.util.List.of(10), 2, 2, 0,
                WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeFrames(java.util.List.of(img), java.util.Arrays.asList(0), 2, 2, 0,
                WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
        assertThrows(IllegalArgumentException.class,
            () -> encoder.encodeFrames(java.util.List.of(img), java.util.Arrays.asList((Integer) null), 2, 2, 0,
                WebpEncodeOptions.defaults(), WebpMetadata.EMPTY));
    }

    @Test
    void optionsRejectInvalidRangesAndContradictions() {
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().quality(-1f).build());
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().method(7).build());
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().threadLevel(2).build());
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().alphaQuality(101).build());
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().targetSize(1).targetPsnr(1f).build());
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().kmin(5).build());
        assertThrows(IllegalArgumentException.class, () -> WebpEncodeOptions.builder().kmin(5).kmax(5).build());
        assertThrows(IllegalArgumentException.class, () -> new WebpEncoder().encodeStillRaw(
            new WebpRawImage(MemorySegment.ofArray(new byte[4]), 1, 1, 4, PixelFormat.RGBA),
            WebpEncodeOptions.builder().allowMixed(true).build(),
            WebpMetadata.EMPTY));
    }
}
