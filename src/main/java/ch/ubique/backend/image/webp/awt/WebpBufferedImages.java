package ch.ubique.backend.image.webp.awt;

import ch.ubique.backend.image.webp.PixelFormat;
import ch.ubique.backend.image.webp.WebpRawImage;
import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Utility helpers for going from {@link BufferedImage} to native WebP inputs.
 *
 * <p>The conversion bulk-fetches ARGB ints via {@link BufferedImage#getRGB(int,int,int,int,int[],int,int)},
 * which routes through the image's {@code ColorModel} exactly once. Per-pixel {@code getRGB(x,y)}
 * is roughly an order of magnitude slower and is avoided here. Works for any
 * {@link BufferedImage} type (including {@code TYPE_CUSTOM} and {@code TYPE_BYTE_GRAY}) because
 * the JDK normalises through {@code ColorModel}.
 */
public final class WebpBufferedImages {
    private WebpBufferedImages() {
    }

    /**
     * Convert a {@link BufferedImage} into a {@link WebpRawImage} whose pixel buffer is allocated
     * inside {@code arena}. The returned image is tightly packed RGBA (stride = {@code width * 4}).
     */
    public static WebpRawImage toRawImage(BufferedImage source, Arena arena) {
        if (source == null) {
            throw new IllegalArgumentException("source must be non-null");
        }
        if (arena == null) {
            throw new IllegalArgumentException("arena must be non-null");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("source has non-positive dimensions: " + width + "x" + height);
        }
        long pixelBytes = Math.multiplyExact((long) width, Math.multiplyExact((long) height, 4L));
        MemorySegment rgba = arena.allocate(pixelBytes, 1L);

        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        byte[] packed = new byte[argb.length * 4];
        for (int i = 0, p = 0; i < argb.length; i++) {
            int v = argb[i];
            packed[p++] = (byte) (v >> 16);
            packed[p++] = (byte) (v >> 8);
            packed[p++] = (byte) v;
            packed[p++] = (byte) (v >>> 24);
        }
        MemorySegment.copy(MemorySegment.ofArray(packed), 0L, rgba, 0L, packed.length);
        return new WebpRawImage(rgba, width, height, width * 4, PixelFormat.RGBA);
    }

    /**
     * Expose the raw RGBA bytes without going through a {@link MemorySegment}.
     */
    public static byte[] toRgbaBytes(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("source must be non-null");
        }
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("source has non-positive dimensions: " + width + "x" + height);
        }
        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgba = new byte[argb.length * 4];
        for (int i = 0, p = 0; i < argb.length; i++) {
            int v = argb[i];
            rgba[p++] = (byte) (v >> 16);
            rgba[p++] = (byte) (v >> 8);
            rgba[p++] = (byte) v;
            rgba[p++] = (byte) (v >>> 24);
        }
        return rgba;
    }
}
