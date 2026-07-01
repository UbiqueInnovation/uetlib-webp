package ch.ubique.backend.image.webp;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;

final class InputValidator {
    private InputValidator() {
    }

    static void requireOptions(WebpEncodeOptions options) {
        if (options == null) {
            throw new IllegalArgumentException("options must be non-null");
        }
    }

    static void requireStillOptions(WebpEncodeOptions options) {
        requireOptions(options);
        options.validateStill();
    }

    static void requireAnimationOptions(WebpEncodeOptions options) {
        requireOptions(options);
        options.validateAnimation();
    }

    static void requireRawImage(
            final MemorySegment pixels,
            final int width,
            final int height,
            final int stride,
            final @Nullable PixelFormat pixelFormat) {
        requireReadableSegment(pixels, "pixels");
        if (pixelFormat == null) {
            throw new IllegalArgumentException("pixelFormat must be non-null");
        }
        if (width <= 0 || height <= 0 || stride <= 0) {
            throw new IllegalArgumentException("width/height/stride must be > 0");
        }

        long minStride = multiplyExact(width, pixelFormat.bytesPerPixel(), "width * bytesPerPixel");
        if (stride < minStride) {
            throw new IllegalArgumentException(
                "stride must be >= width * bytesPerPixel (" + minStride + "), was " + stride);
        }

        long requiredBytes = requiredImageBytes(height, stride);
        if (pixels.byteSize() < requiredBytes) {
            throw new IllegalArgumentException(
                "pixel buffer is too small: requires " + requiredBytes + " bytes but has " + pixels.byteSize());
        }
    }

    static void requireReadableSegment(final @Nullable MemorySegment segment, final String label) {
        if (segment == null || segment.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException(label + " must be non-null/non-NULL");
        }
        if (segment.byteSize() <= 0) {
            throw new IllegalArgumentException(label + " must be non-empty");
        }
    }

    static void requirePositiveDuration(int durationMs, String label) {
        if (durationMs <= 0) {
            throw new IllegalArgumentException(label + " must be > 0");
        }
    }

    static void requirePositiveCanvas(int canvasWidth, int canvasHeight) {
        if (canvasWidth <= 0 || canvasHeight <= 0) {
            throw new IllegalArgumentException("canvasWidth/canvasHeight must be > 0");
        }
    }

    static void requireOptionalCanvas(int canvasWidth, int canvasHeight) {
        if (canvasWidth < 0 || canvasHeight < 0) {
            throw new IllegalArgumentException("canvasWidth/canvasHeight must be >= 0");
        }
        if ((canvasWidth == 0) != (canvasHeight == 0)) {
            throw new IllegalArgumentException("canvasWidth and canvasHeight must both be 0 or both be > 0");
        }
    }

    static long multiplyExact(long left, long right, String label) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("overflow while computing " + label, e);
        }
    }

    private static long requiredImageBytes(int height, int stride) {
        return multiplyExact(height, stride, "height * stride");
    }
}
