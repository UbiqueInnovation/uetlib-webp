package ch.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.uetlib.webp.awt.WebpBufferedImageAnimationEncoder;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"DataFlowIssue", "resource"})
class WebpInputValidationTest {
    @Test
    void durationMillisRequiresPositiveIntRange() {
        assertEquals(
                1, InputValidator.requirePositiveDurationMillis(Duration.ofMillis(1), "duration"));
        assertEquals(
                Integer.MAX_VALUE,
                InputValidator.requirePositiveDurationMillis(
                        Duration.ofMillis(Integer.MAX_VALUE), "duration"));
        for (final var duration :
                List.of(
                        Duration.ZERO,
                        Duration.ofNanos(999_999),
                        Duration.ofMillis(-1),
                        Duration.ofMillis((long) Integer.MAX_VALUE + 1))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> InputValidator.requirePositiveDurationMillis(duration, "duration"));
        }
    }

    @Test
    void rawImageRequiresPositiveGeometryAndNonNullPixels() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment pixels = arena.allocate(16);
            assertDoesNotThrow(() -> new WebpRawImage(pixels, 1, 1, 4, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(null, 1, 1, 4, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(MemorySegment.NULL, 1, 1, 4, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(pixels, 0, 1, 4, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(pixels, 1, 0, 4, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(pixels, 1, 1, 0, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(pixels, 2, 1, 3, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(arena.allocate(3), 1, 1, 4, PixelFormat.RGBA));
            assertThrows(
                    IllegalArgumentException.class, () -> new WebpRawImage(pixels, 1, 1, 4, null));
        }
    }

    @Test
    void frameRequiresPositiveGeometryDurationAndNonNullPixels() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment pixels = arena.allocate(16);
            assertDoesNotThrow(() -> new WebpFrame(pixels, 1, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(null, 1, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(MemorySegment.NULL, 1, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(pixels, 0, 1, 4, PixelFormat.RGBA, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(pixels, 1, 0, 4, PixelFormat.RGBA, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(pixels, 1, 1, 0, PixelFormat.RGBA, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(pixels, 1, 1, 4, PixelFormat.RGBA, 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpFrame(arena.allocate(3), 1, 1, 4, PixelFormat.RGBA, 33));
        }
    }

    @Test
    void compressedFrameRequiresDataAndPositiveDuration() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment data = arena.allocate(16);
            assertDoesNotThrow(() -> new WebpCompressedFrame(data, 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpCompressedFrame(null, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpCompressedFrame(MemorySegment.NULL, 33));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpCompressedFrame(arena.allocate(0), 33));
            assertThrows(IllegalArgumentException.class, () -> new WebpCompressedFrame(data, 0));
        }
    }

    @Test
    void animationEncoderRejectsEmptyFrames() {
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeRawFrames(
                                List.of(),
                                1,
                                1,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeJpegFrames(
                                List.of(),
                                0,
                                0,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodePngFrames(
                                List.of(),
                                0,
                                0,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
    }

    @Test
    void byteFrameAnimationRejectsEmptyOrMismatchedInputs() {
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeJpegFrameBytes(
                                List.of(),
                                0,
                                0,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodePngFrameBytes(
                                List.of(),
                                0,
                                0,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
    }

    @Test
    void bufferedImageFramesValidatesDurationsList() {
        final WebpBufferedImageAnimationEncoder encoder = new WebpBufferedImageAnimationEncoder();
        final java.awt.image.BufferedImage img =
                new java.awt.image.BufferedImage(2, 2, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeFrames(
                                List.of(),
                                List.of(1),
                                2,
                                2,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeFrames(
                                List.of(img),
                                null,
                                2,
                                2,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeFrames(
                                List.of(img, img),
                                List.of(10),
                                2,
                                2,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeFrames(
                                List.of(img),
                                List.of(0),
                                2,
                                2,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        encoder.encodeFrames(
                                List.of(img),
                                Collections.singletonList(null),
                                2,
                                2,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
    }

    @Test
    void optionsRejectInvalidRangesAndContradictions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().quality(-1.0f).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().method(7).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().threadLevel(2).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().alphaQuality(101).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().targetSize(1).targetPsnr(1.0f).build());
        assertThrows(
                IllegalArgumentException.class, () -> WebpEncodeOptions.builder().kmin(5).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().kmin(5).kmax(5).build());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WebpEncoder()
                                .encodeStillRaw(
                                        new WebpRawImage(
                                                MemorySegment.ofArray(new byte[4]),
                                                1,
                                                1,
                                                4,
                                                PixelFormat.RGBA),
                                        WebpEncodeOptions.builder().allowMixed(true).build(),
                                        WebpMetadata.EMPTY));
    }
}
