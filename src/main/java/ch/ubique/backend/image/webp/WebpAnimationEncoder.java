package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;

/**
 * Encoder for multi-frame (animated) WebP output.
 *
 * <p>Thread-safe: instances are stateless. Each call allocates its own internal {@link Arena}.
 *
 * <h2>Overload map</h2>
 *
 * <table>
 * <tr><th>Source</th><th>Frame type</th><th>Entry point</th></tr>
 * <tr><td>Already-native RGBA frames</td><td>{@link WebpFrame}</td>
 *     <td>{@link #encodeRawFrames(List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
 * <tr><td>JPEG byte frames</td><td>{@link WebpCompressedFrame}</td>
 *     <td>{@link #encodeJpegFrames(List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
 * <tr><td>JPEG {@code byte[]} frames</td><td>{@link WebpCompressedFrameBytes}</td>
 *     <td>{@link #encodeJpegFrameBytes(List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
 * <tr><td>PNG byte frames</td><td>{@link WebpCompressedFrame}</td>
 *     <td>{@link #encodePngFrames(List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
 * <tr><td>PNG {@code byte[]} frames</td><td>{@link WebpCompressedFrameBytes}</td>
 *     <td>{@link #encodePngFrameBytes(List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
 * </table>
 */
public final class WebpAnimationEncoder {

    /* ---------- Raw (RGBA) frames ------------------------------------------------------------ */

    public EncodedWebp encodeRawFrames(
            final List<WebpFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        requireFrames(frames);
        InputValidator.requirePositiveCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        return NativeWebpLibrary.encodeAnimRaw(
                frames,
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /* ---------- Compressed (JPEG / PNG) frames ----------------------------------------------- */

    public EncodedWebp encodeJpegFrames(
            final List<WebpCompressedFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        requireFrames(frames);
        InputValidator.requireOptionalCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        return NativeWebpLibrary.encodeAnimJpegs(
                frames,
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    public EncodedWebp encodePngFrames(
            final List<WebpCompressedFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        requireFrames(frames);
        InputValidator.requireOptionalCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        return NativeWebpLibrary.encodeAnimPngs(
                frames,
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /**
     * Convenience: encode JPEG {@code byte[]} frames. Each frame's JPEG bytes are copied into a
     * shared {@link Arena} for the call; the native layer decodes and encodes as animated WebP in a
     * single C call.
     *
     * @param frames ordered list of {@link WebpCompressedFrameBytes} (JPEG bytes + durationMs)
     */
    public byte[] encodeJpegFrameBytes(
            final List<WebpCompressedFrameBytes> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        return encodeCompressedBytes(
                frames,
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                metadata,
                /* png= */ false);
    }

    /** PNG variant of {@link #encodeJpegFrameBytes}. */
    public byte[] encodePngFrameBytes(
            final List<WebpCompressedFrameBytes> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata) {
        return encodeCompressedBytes(
                frames,
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                metadata,
                /* png= */ true);
    }

    private byte[] encodeCompressedBytes(
            final List<WebpCompressedFrameBytes> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options,
            final @Nullable WebpMetadata metadata,
            final boolean png) {
        requireFrames(frames);
        InputValidator.requireOptionalCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        try (final Arena arena = Arena.ofShared()) {
            final List<WebpCompressedFrame> wrapped = new ArrayList<>(frames.size());
            for (int i = 0; i < frames.size(); i++) {
                final WebpCompressedFrameBytes f = frames.get(i);
                if (f == null || f.data() == null || f.data().length == 0) {
                    throw new IllegalArgumentException("frame[" + i + "] has empty data");
                }
                if (f.durationMs() <= 0) {
                    throw new IllegalArgumentException(
                            "frame[" + i + "] durationMs must be > 0 (was " + f.durationMs() + ")");
                }
                final MemorySegment seg = arena.allocate(f.data().length);
                MemorySegment.copy(MemorySegment.ofArray(f.data()), 0L, seg, 0L, f.data().length);
                wrapped.add(new WebpCompressedFrame(seg, f.durationMs()));
            }
            final EncodedWebp out =
                    png
                            ? encodePngFrames(
                                    wrapped,
                                    canvasWidth,
                                    canvasHeight,
                                    backgroundRgba,
                                    options,
                                    metadata)
                            : encodeJpegFrames(
                                    wrapped,
                                    canvasWidth,
                                    canvasHeight,
                                    backgroundRgba,
                                    options,
                                    metadata);
            try (out) {
                return out.toByteArray();
            }
        }
    }

    /* ---------- Streaming entry points ------------------------------------------------------- */

    /**
     * Open a streaming JPEG-frame encoder. Use this when you have many frames and don't want to
     * hold them all in memory at once — push one at a time, then call {@link
     * WebpAnimationStream#finish}.
     *
     * @see WebpAnimationStream
     */
    public WebpAnimationStream openJpegStream(
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options) {
        InputValidator.requirePositiveCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        return new WebpAnimationStream(
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                WebpAnimationStream.FrameFormat.JPEG);
    }

    /** Streaming PNG-frame variant of {@link #openJpegStream}. */
    public WebpAnimationStream openPngStream(
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options) {
        InputValidator.requirePositiveCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        return new WebpAnimationStream(
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                WebpAnimationStream.FrameFormat.PNG);
    }

    /**
     * Open a streaming WebP-frame encoder. Use this when frames are already stored as still WebP
     * images — the native layer decodes each frame to raw pixels and re-encodes into the animated
     * container. No intermediate JPEG/PNG round-trip occurs.
     *
     * @see WebpAnimationStream
     */
    public WebpAnimationStream openWebpStream(
            final int canvasWidth,
            final int canvasHeight,
            final int backgroundRgba,
            final WebpEncodeOptions options) {
        InputValidator.requirePositiveCanvas(canvasWidth, canvasHeight);
        InputValidator.requireAnimationOptions(options);
        return new WebpAnimationStream(
                canvasWidth,
                canvasHeight,
                backgroundRgba,
                options,
                WebpAnimationStream.FrameFormat.WEBP);
    }

    private static void requireFrames(final List<?> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
    }
}
