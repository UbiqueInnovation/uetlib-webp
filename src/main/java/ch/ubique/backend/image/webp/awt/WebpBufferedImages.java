package ch.ubique.backend.image.webp.awt;

import ch.ubique.backend.image.webp.PixelFormat;
import ch.ubique.backend.image.webp.WebpRawImage;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Utility helpers for going from {@link BufferedImage} to native WebP inputs.
 *
 * <p>The conversion bulk-fetches ARGB ints via {@link
 * BufferedImage#getRGB(int,int,int,int,int[],int,int)}, which routes through the image's {@code
 * ColorModel} exactly once. Per-pixel {@code getRGB(x,y)} is roughly an order of magnitude slower
 * and is avoided here. Works for any {@link BufferedImage} type (including {@code TYPE_CUSTOM} and
 * {@code TYPE_BYTE_GRAY}) because the JDK normalises through {@code ColorModel}.
 */
public final class WebpBufferedImages {
    private WebpBufferedImages() {}

    /**
     * Convert a {@link BufferedImage} into a {@link WebpRawImage} whose pixel buffer is allocated
     * inside {@code arena}. The returned image is tightly packed RGBA (stride = {@code width * 4}).
     */
    public static WebpRawImage toRawImage(final BufferedImage source, final Arena arena) {
        if (source == null) {
            throw new IllegalArgumentException("source must be non-null");
        }
        if (arena == null) {
            throw new IllegalArgumentException("arena must be non-null");
        }
        final var width = source.getWidth();
        final var height = source.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "source has non-positive dimensions: " + width + "x" + height);
        }
        final var pixelBytes = Math.multiplyExact(width, Math.multiplyExact(height, 4L));
        final var rgba = arena.allocate(pixelBytes, 1L);

        final var argb = source.getRGB(0, 0, width, height, null, 0, width);
        final var packed = new byte[argb.length * 4];
        for (int i = 0, p = 0; i < argb.length; i++) {
            final var v = argb[i];
            packed[p] = (byte) (v >> 16);
            p++;
            packed[p] = (byte) (v >> 8);
            p++;
            packed[p] = (byte) v;
            p++;
            packed[p] = (byte) (v >>> 24);
            p++;
        }
        MemorySegment.copy(MemorySegment.ofArray(packed), 0L, rgba, 0L, packed.length);
        return new WebpRawImage(rgba, width, height, width * 4, PixelFormat.RGBA);
    }

    /** Expose the raw RGBA bytes without going through a {@link MemorySegment}. */
    public static byte[] toRgbaBytes(final BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("source must be non-null");
        }
        final var width = source.getWidth();
        final var height = source.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "source has non-positive dimensions: " + width + "x" + height);
        }
        final var argb = source.getRGB(0, 0, width, height, null, 0, width);
        final var rgba = new byte[argb.length * 4];
        for (int i = 0, p = 0; i < argb.length; i++) {
            final var v = argb[i];
            rgba[p] = (byte) (v >> 16);
            p++;
            rgba[p] = (byte) (v >> 8);
            p++;
            rgba[p] = (byte) v;
            p++;
            rgba[p] = (byte) (v >>> 24);
            p++;
        }
        return rgba;
    }
}
