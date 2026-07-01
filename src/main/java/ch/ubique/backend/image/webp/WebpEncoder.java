package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * Encoder for single-image (still) WebP output.
 *
 * <p>Thread-safe: instances are stateless; share one across threads. Each call opens its own
 * internal {@link Arena} for the native downcall.
 *
 * <h2>Overload map</h2>
 *
 * <table>
 * <tr><th>Source</th><th>Lowest-allocation API</th><th>Convenience API (returns {@code byte[]})</th></tr>
 * <tr><td>Already-native RGBA</td><td>{@link #encodeStillRaw(WebpRawImage, WebpEncodeOptions, WebpMetadata)}</td><td>—</td></tr>
 * <tr><td>JPEG bytes</td><td>{@link #encodeStillJpeg(MemorySegment, WebpEncodeOptions, WebpMetadata)}</td><td>{@link #encodeStillJpegToBytes(byte[], WebpEncodeOptions, WebpMetadata)}</td></tr>
 * <tr><td>PNG bytes</td><td>{@link #encodeStillPng(MemorySegment, WebpEncodeOptions, WebpMetadata)}</td><td>{@link #encodeStillPngToBytes(byte[], WebpEncodeOptions, WebpMetadata)}</td></tr>
 * </table>
 */
public final class WebpEncoder {

    private static MemorySegment copyToArena(final Arena arena, final byte[] source) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("input bytes must be non-null and non-empty");
        }
        final MemorySegment target = arena.allocate(source.length);
        MemorySegment.copy(MemorySegment.ofArray(source), 0, target, 0, source.length);
        return target;
    }

    /**
     * Encode already-native RGBA pixels. The caller owns {@code image.pixels()} and its backing
     * {@link Arena}; the native layer reads from it during the call only.
     */
    public EncodedWebp encodeStillRaw(
            final WebpRawImage image,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        if (image == null) {
            throw new IllegalArgumentException("image must be non-null");
        }
        InputValidator.requireStillOptions(options);
        return NativeWebpLibrary.encodeStillRaw(image, options, metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /**
     * Decode JPEG bytes natively (libjpeg-turbo) and encode as a still WebP in one C call.
     * Avoids round-tripping through Java {@code ImageIO} / {@code BufferedImage}.
     *
     * <p>The supplied {@link MemorySegment} must be readable for the duration of the call;
     * its contents are decoded inside the native layer.
     */
    public EncodedWebp encodeStillJpeg(
            final MemorySegment jpegBytes,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        InputValidator.requireStillOptions(options);
        return NativeWebpLibrary.encodeStillJpeg(jpegBytes, options, metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /**
     * Convenience overload for callers holding a {@code byte[]}; copies the bytes into a
     * confined {@link Arena} for the duration of the encode.
     */
    public EncodedWebp encodeStillJpeg(
            final byte[] jpegBytes, final WebpEncodeOptions options, final @Nullable WebpMetadata metadata) {
        try (final Arena arena = Arena.ofConfined()) {
            return encodeStillJpeg(copyToArena(arena, jpegBytes), options, metadata);
        }
    }

    /**
     * Convenience: decode JPEG bytes and return the WebP bytes as a heap {@code byte[]}.
     * The native buffer is freed before this method returns.
     */
    public byte[] encodeStillJpegToBytes(
            final byte[] jpegBytes, final WebpEncodeOptions options, final @Nullable WebpMetadata metadata) {
        try (final EncodedWebp out = encodeStillJpeg(jpegBytes, options, metadata)) {
            return out.toByteArray();
        }
    }

    /** Decode PNG bytes natively (libpng) and encode as a still WebP in one C call. */
    public EncodedWebp encodeStillPng(
            final MemorySegment pngBytes,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        InputValidator.requireStillOptions(options);
        return NativeWebpLibrary.encodeStillPng(pngBytes, options, metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /** Convenience overload for callers holding a {@code byte[]}. */
    public EncodedWebp encodeStillPng(byte[] pngBytes, WebpEncodeOptions options, WebpMetadata metadata) {
        try (Arena arena = Arena.ofConfined()) {
            return encodeStillPng(copyToArena(arena, pngBytes), options, metadata);
        }
    }

    /**
     * Convenience: decode PNG bytes and return the WebP bytes as a heap {@code byte[]}.
     * The native buffer is freed before this method returns.
     */
    public byte[] encodeStillPngToBytes(byte[] pngBytes, WebpEncodeOptions options, WebpMetadata metadata) {
        try (EncodedWebp out = encodeStillPng(pngBytes, options, metadata)) {
            return out.toByteArray();
        }
    }
}
