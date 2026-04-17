package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * Utility helpers for going from {@link BufferedImage} to native WebP inputs.
 *
 * <p>The conversion bulk-fetches ARGB ints via {@link BufferedImage#getRGB(int,int,int,int,int[],int,int)},
 * which routes through the image's {@code ColorModel} exactly once. Per-pixel {@code getRGB(x,y)}
 * is roughly an order of magnitude slower and is avoided here. Works for any
 * {@link BufferedImage} type (including {@code TYPE_CUSTOM} and {@code TYPE_BYTE_GRAY}) because
 * the JDK normalises through {@code ColorModel}.
 */
public final class WebpImages {

    private WebpImages() {}

    /**
     * Convert a {@link BufferedImage} into a {@link WebpRawImage} whose pixel buffer is allocated
     * inside {@code arena}. The returned image is tightly packed RGBA (stride = {@code width * 4}).
     *
     * <p>Alpha is preserved — opaque source images come back with {@code 0xFF} in the alpha channel.
     *
     * @param source image to convert; must be non-null with positive dimensions
     * @param arena arena owning the RGBA pixel buffer; caller is responsible for closing it
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
        long pixelBytes = (long) width * (long) height * 4L;
        MemorySegment rgba = arena.allocate(pixelBytes, 1L);

        int[] argb = source.getRGB(0, 0, width, height, null, 0, width);
        // Pack ARGB int[] → RGBA bytes. Using a byte[] staging buffer + one bulk copy into the
        // segment is measurably faster than per-byte JAVA_BYTE.set in a hot loop.
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
     * Expose the raw RGBA bytes without going through a {@link MemorySegment}. Useful for
     * callers that want to hand the result to something else (e.g. a different image pipeline).
     * Prefer {@link #toRawImage(BufferedImage, Arena)} when feeding back into this library.
     */
    public static byte[] toRgbaBytes(BufferedImage source) {
        if (source == null) {
            throw new IllegalArgumentException("source must be non-null");
        }
        int width = source.getWidth();
        int height = source.getHeight();
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

    /**
     * Allocate a native RGBA segment of {@code width * height * 4} bytes in {@code arena}
     * (initialised to zero). The caller is expected to populate it before using it as a
     * {@link WebpRawImage} pixel buffer. Mostly useful for tests and tiling scenarios.
     */
    public static MemorySegment allocateRgba(Arena arena, int width, int height) {
        if (arena == null) {
            throw new IllegalArgumentException("arena must be non-null");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be > 0");
        }
        MemorySegment seg = arena.allocate((long) width * (long) height * 4L, 1L);
        // fresh arena-allocated segments are already zero, but pin the assumption.
        seg.fill((byte) 0);
        return seg;
    }

    /** Convenience so callers don't need to import {@link ValueLayout} just to write a byte. */
    @SuppressWarnings("unused")
    static void writeByte(MemorySegment segment, long offset, int value) {
        segment.set(ValueLayout.JAVA_BYTE, offset, (byte) value);
    }
}
