package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;
import java.awt.image.BufferedImage;
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
 * <table>
 * <tr><th>Source</th><th>Frame type</th><th>Entry point</th></tr>
 * <tr><td>Already-native RGBA frames</td><td>{@link WebpFrame}</td>
 *     <td>{@link #encodeRawFrames(List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
 * <tr><td>{@link BufferedImage} frames</td><td>—</td>
 *     <td>{@link #encodeBufferedImageFrames(List, List, int, int, int, WebpEncodeOptions, WebpMetadata)}</td></tr>
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
        List<WebpFrame> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        requireFrames(frames);
        return NativeWebpLibrary.encodeAnimRaw(
            frames,
            canvasWidth,
            canvasHeight,
            backgroundRgba,
            options,
            metadata == null ? WebpMetadata.EMPTY : metadata
        );
    }

    /**
     * Convenience: encode a list of {@link BufferedImage} frames. Each image is converted to
     * RGBA inside a shared {@link Arena} for the duration of the call. Returns the WebP bytes
     * as a heap {@code byte[]} (the native buffer is freed before return).
     *
     * @param images one {@link BufferedImage} per frame — must be non-empty
     * @param durationsMs parallel list of per-frame durations in milliseconds; must match
     *                    {@code images} in size and contain only positive values
     * @param canvasWidth WebP canvas width
     * @param canvasHeight WebP canvas height
     * @param backgroundRgba ARGB background for transparent frames (pass {@code 0} for black)
     */
    public byte[] encodeBufferedImageFrames(
        List<BufferedImage> images,
        List<Integer> durationsMs,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        requireFrames(images);
        if (durationsMs == null || durationsMs.size() != images.size()) {
            throw new IllegalArgumentException(
                "durationsMs must be non-null and match images in size");
        }
        try (Arena arena = Arena.ofShared()) {
            List<WebpFrame> frames = new ArrayList<>(images.size());
            for (int i = 0; i < images.size(); i++) {
                BufferedImage img = images.get(i);
                Integer dur = durationsMs.get(i);
                if (dur == null || dur <= 0) {
                    throw new IllegalArgumentException(
                        "durationsMs[" + i + "] must be > 0 (was " + dur + ")");
                }
                WebpRawImage raw = WebpImages.toRawImage(img, arena);
                frames.add(new WebpFrame(
                    raw.pixels(), raw.width(), raw.height(), raw.stride(),
                    raw.pixelFormat(), dur));
            }
            try (EncodedWebp out = encodeRawFrames(
                    frames, canvasWidth, canvasHeight, backgroundRgba, options, metadata)) {
                return out.toByteArray();
            }
        }
    }

    /* ---------- Compressed (JPEG / PNG) frames ----------------------------------------------- */

    public EncodedWebp encodeJpegFrames(
        List<WebpCompressedFrame> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        requireFrames(frames);
        return NativeWebpLibrary.encodeAnimJpegs(
            frames, canvasWidth, canvasHeight, backgroundRgba, options,
            metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    public EncodedWebp encodePngFrames(
        List<WebpCompressedFrame> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        requireFrames(frames);
        return NativeWebpLibrary.encodeAnimPngs(
            frames, canvasWidth, canvasHeight, backgroundRgba, options,
            metadata == null ? WebpMetadata.EMPTY : metadata);
    }

    /**
     * Convenience: encode JPEG {@code byte[]} frames. Each frame's JPEG bytes are copied into
     * a shared {@link Arena} for the call; the native layer decodes and encodes as animated
     * WebP in a single C call.
     *
     * @param frames ordered list of {@link WebpCompressedFrameBytes} (JPEG bytes + durationMs)
     */
    public byte[] encodeJpegFrameBytes(
        List<WebpCompressedFrameBytes> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        return encodeCompressedBytes(
            frames, canvasWidth, canvasHeight, backgroundRgba, options, metadata, /*png=*/ false);
    }

    /** PNG variant of {@link #encodeJpegFrameBytes}. */
    public byte[] encodePngFrameBytes(
        List<WebpCompressedFrameBytes> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata
    ) {
        return encodeCompressedBytes(
            frames, canvasWidth, canvasHeight, backgroundRgba, options, metadata, /*png=*/ true);
    }

    private byte[] encodeCompressedBytes(
        List<WebpCompressedFrameBytes> frames,
        int canvasWidth,
        int canvasHeight,
        int backgroundRgba,
        WebpEncodeOptions options,
        WebpMetadata metadata,
        boolean png
    ) {
        requireFrames(frames);
        try (Arena arena = Arena.ofShared()) {
            List<WebpCompressedFrame> wrapped = new ArrayList<>(frames.size());
            for (int i = 0; i < frames.size(); i++) {
                WebpCompressedFrameBytes f = frames.get(i);
                if (f == null || f.data() == null || f.data().length == 0) {
                    throw new IllegalArgumentException("frame[" + i + "] has empty data");
                }
                if (f.durationMs() <= 0) {
                    throw new IllegalArgumentException(
                        "frame[" + i + "] durationMs must be > 0 (was " + f.durationMs() + ")");
                }
                MemorySegment seg = arena.allocate(f.data().length);
                MemorySegment.copy(MemorySegment.ofArray(f.data()), 0L, seg, 0L, f.data().length);
                wrapped.add(new WebpCompressedFrame(seg, f.durationMs()));
            }
            EncodedWebp out = png
                ? encodePngFrames(wrapped, canvasWidth, canvasHeight, backgroundRgba, options, metadata)
                : encodeJpegFrames(wrapped, canvasWidth, canvasHeight, backgroundRgba, options, metadata);
            try (out) {
                return out.toByteArray();
            }
        }
    }

    /* ---------- Streaming entry points ------------------------------------------------------- */

    /**
     * Open a streaming JPEG-frame encoder. Use this when you have many frames and don't want
     * to hold them all in memory at once — push one at a time, then call
     * {@link WebpAnimationStream#finish}.
     *
     * @see WebpAnimationStream
     */
    public WebpAnimationStream openJpegStream(
        int canvasWidth, int canvasHeight, int backgroundRgba, WebpEncodeOptions options) {
        return new WebpAnimationStream(canvasWidth, canvasHeight, backgroundRgba, options, /*jpeg=*/ true);
    }

    /** Streaming PNG-frame variant of {@link #openJpegStream}. */
    public WebpAnimationStream openPngStream(
        int canvasWidth, int canvasHeight, int backgroundRgba, WebpEncodeOptions options) {
        return new WebpAnimationStream(canvasWidth, canvasHeight, backgroundRgba, options, /*jpeg=*/ false);
    }

    private static void requireFrames(List<?> frames) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
    }
}
