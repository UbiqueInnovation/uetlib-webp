package ch.ubique.uetlib.webp.awt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.ubique.uetlib.webp.PixelFormat;
import ch.ubique.uetlib.webp.WebpPixels;
import ch.ubique.uetlib.webp.WebpRawImage;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

@SuppressWarnings("DataFlowIssue")
class WebpBufferedImagesTest {

    private static byte[] toHeap(final MemorySegment segment) {
        final byte[] out = new byte[(int) segment.byteSize()];
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, 0, out, 0, out.length);
        return out;
    }

    @Test
    void toRawImagePacksArgbIntoRgbaSegment() {
        final BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(10, 20, 30, 40).getRGB());
        image.setRGB(1, 0, new Color(200, 150, 100, 255).getRGB());

        try (final Arena arena = Arena.ofConfined()) {
            final WebpRawImage raw = WebpBufferedImages.toRawImage(image, arena);
            assertEquals(2, raw.width());
            assertEquals(1, raw.height());
            assertEquals(8, raw.stride());
            assertEquals(PixelFormat.RGBA, raw.pixelFormat());
            final byte[] bytes = toHeap(raw.pixels());
            assertArrayEquals(
                    new byte[] {10, 20, 30, 40, (byte) 200, (byte) 150, 100, (byte) 255}, bytes);
        }
    }

    @Test
    void toRawImagePreservesOpaqueAlpha() {
        final BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, new Color(5, 6, 7).getRGB());
        try (final Arena arena = Arena.ofConfined()) {
            final WebpRawImage raw = WebpBufferedImages.toRawImage(image, arena);
            final byte[] bytes = toHeap(raw.pixels());
            assertArrayEquals(new byte[] {5, 6, 7, (byte) 0xFF}, bytes);
        }
    }

    @Test
    void toRawImageRejectsNullArguments() {
        try (final Arena arena = Arena.ofConfined()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WebpBufferedImages.toRawImage(null, arena));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            WebpBufferedImages.toRawImage(
                                    new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), null));
        }
    }

    @Test
    void toRgbaBytesMatchesToRawImageLayout() {
        final BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, new Color(1, 2, 3, 4).getRGB());
        image.setRGB(1, 0, new Color(5, 6, 7, 8).getRGB());
        image.setRGB(0, 1, new Color(9, 10, 11, 12).getRGB());
        image.setRGB(1, 1, new Color(13, 14, 15, 16).getRGB());

        final byte[] bytes = WebpBufferedImages.toRgbaBytes(image);
        assertArrayEquals(
                new byte[] {
                    1, 2, 3, 4,
                    5, 6, 7, 8,
                    9, 10, 11, 12,
                    13, 14, 15, 16
                },
                bytes);
    }

    @Test
    void toRgbaBytesRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> WebpBufferedImages.toRgbaBytes(null));
    }

    @Test
    void allocateRgbaReturnsZeroedSegmentOfExpectedSize() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment seg = WebpPixels.allocateRgba(arena, 3, 4);
            assertNotNull(seg);
            assertEquals(3L * 4L * 4L, seg.byteSize());
            for (long i = 0; i < seg.byteSize(); i++) {
                assertEquals(0, seg.get(ValueLayout.JAVA_BYTE, i));
            }
        }
    }

    @Test
    void allocateRgbaRejectsBadInputs() {
        try (final Arena arena = Arena.ofConfined()) {
            assertThrows(IllegalArgumentException.class, () -> WebpPixels.allocateRgba(null, 1, 1));
            assertThrows(
                    IllegalArgumentException.class, () -> WebpPixels.allocateRgba(arena, 0, 1));
            assertThrows(
                    IllegalArgumentException.class, () -> WebpPixels.allocateRgba(arena, 1, -1));
        }
    }

    @Test
    void toRawImageRejectsZeroSizedImages() {
        // BufferedImage itself rejects zero dims, so we need a mock-ish subclass. Simpler: use 1x1
        // then assert that toRawImage rejects 0x0 via a custom subclass.
        final BufferedImage zero =
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB) {
                    @Override
                    public int getWidth() {
                        return 0;
                    }

                    @Override
                    public int getHeight() {
                        return 0;
                    }
                };
        try (final Arena arena = Arena.ofConfined()) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> WebpBufferedImages.toRawImage(zero, arena));
        }
    }
}
