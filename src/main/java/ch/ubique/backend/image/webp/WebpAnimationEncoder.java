package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;
import java.util.List;

public final class WebpAnimationEncoder {
    public EncodedWebp encodeRawFrames(
        List<WebpFrame> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
        return NativeWebpLibrary.encodeAnimRaw(
            frames,
            canvasWidth,
            canvasHeight,
            backgroundRgba,
            options,
            metadata == null ? WebpMetadata.EMPTY : metadata
        );
    }

    public EncodedWebp encodeJpegFrames(
        List<WebpCompressedFrame> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
        return NativeWebpLibrary.encodeAnimJpegs(
            frames,
            canvasWidth,
            canvasHeight,
            backgroundRgba,
            options,
            metadata == null ? WebpMetadata.EMPTY : metadata
        );
    }

    public EncodedWebp encodePngFrames(
        List<WebpCompressedFrame> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
        return NativeWebpLibrary.encodeAnimPngs(
            frames,
            canvasWidth,
            canvasHeight,
            backgroundRgba,
            options,
            metadata == null ? WebpMetadata.EMPTY : metadata
        );
    }
}
