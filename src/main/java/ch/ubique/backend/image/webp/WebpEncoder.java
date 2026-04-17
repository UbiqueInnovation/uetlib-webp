package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

public final class WebpEncoder {
    public EncodedWebp encodeStillRaw(WebpRawImage image, WebpEncodeOptions options, WebpMetadata metadata) {
        return NativeWebpLibrary.encodeStillRaw(image, options, metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /**
     * Decode JPEG bytes natively (libjpeg-turbo) and encode as a still WebP in one C call.
     * Avoids round-tripping through Java {@code ImageIO} / {@link java.awt.image.BufferedImage}.
     *
     * <p>The supplied {@link MemorySegment} must be readable for the duration of the call;
     * its contents are decoded inside the native layer.
     */
    public EncodedWebp encodeStillJpeg(MemorySegment jpegBytes, WebpEncodeOptions options, WebpMetadata metadata) {
        return NativeWebpLibrary.encodeStillJpeg(jpegBytes, options, metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /**
     * Convenience overload for callers holding a {@code byte[]}; copies the bytes into a
     * confined {@link Arena} for the duration of the encode.
     */
    public EncodedWebp encodeStillJpeg(byte[] jpegBytes, WebpEncodeOptions options, WebpMetadata metadata) {
        try (Arena arena = Arena.ofConfined()) {
            return encodeStillJpeg(copyToArena(arena, jpegBytes), options, metadata);
        }
    }

    /**
     * Decode PNG bytes natively (libpng) and encode as a still WebP in one C call.
     */
    public EncodedWebp encodeStillPng(MemorySegment pngBytes, WebpEncodeOptions options, WebpMetadata metadata) {
        return NativeWebpLibrary.encodeStillPng(pngBytes, options, metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /** Convenience overload for callers holding a {@code byte[]}. */
    public EncodedWebp encodeStillPng(byte[] pngBytes, WebpEncodeOptions options, WebpMetadata metadata) {
        try (Arena arena = Arena.ofConfined()) {
            return encodeStillPng(copyToArena(arena, pngBytes), options, metadata);
        }
    }

    private static MemorySegment copyToArena(Arena arena, byte[] source) {
        if (source == null || source.length == 0) {
            throw new IllegalArgumentException("input bytes must be non-null and non-empty");
        }
        MemorySegment target = arena.allocate(source.length);
        MemorySegment.copy(MemorySegment.ofArray(source), 0, target, 0, source.length);
        return target;
    }
}
