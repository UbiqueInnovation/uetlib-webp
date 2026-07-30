package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Push-style animation encoder. Each {@code addJpeg} / {@code addPng} call decodes one frame
 * natively and feeds it to the underlying {@code WebPAnimEncoder}; the per-frame work buffer is
 * freed before the call returns. {@link #finish} flushes and returns the assembled WebP.
 *
 * <p>Peak memory while encoding is {@code O(one decoded frame + accumulated WebP output)},
 * independent of total frame count — making this the right choice for long timelapses where holding
 * all frame bytes in memory at once would be expensive.
 *
 * <h2>Lifecycle</h2>
 *
 * <pre>{@code
 * try (var stream = encoder.openJpegStream(w, h, 0, opts)) {
 *     for (var jpeg : sourceJpegs) {
 *         stream.addJpeg(jpeg, 200);
 *     }
 *     byte[] webp = stream.finish(WebpMetadata.xmp(xmp));
 * }
 * }</pre>
 *
 * <p>If {@link #close} runs without {@link #finish} having been called, the native handle is
 * aborted (resources freed, no output produced). A {@link Cleaner} is registered as a safety net if
 * the caller drops the reference without closing.
 *
 * <p>Not thread-safe: a single stream must be driven by one thread.
 */
public final class WebpAnimationStream implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    enum FrameFormat {
        JPEG,
        PNG,
        WEBP
    }

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private final FrameFormat format;

    /**
     * Use {@link WebpAnimationEncoder#openJpegStream} / {@code openPngStream} / {@code
     * openWebpStream} to construct.
     */
    WebpAnimationStream(
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options,
            final FrameFormat format) {
        final var handle =
                NativeWebpLibrary.streamOpen(canvasWidth, canvasHeight, bgcolorRgba, options);
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, state);
        this.format = format;
    }

    /**
     * Decode a JPEG frame and append it to the animation. Throws if this stream was not opened for
     * JPEG, if the frame is empty, or if its dimensions don't match the canvas.
     */
    public void addJpeg(final byte[] jpegBytes, final int durationMs) {
        if (format != FrameFormat.JPEG) {
            throw new WebpException("addJpeg called on " + format + " stream");
        }
        addCompressed(jpegBytes, durationMs, FrameFormat.JPEG);
    }

    public void addPng(final byte[] pngBytes, final int durationMs) {
        if (format != FrameFormat.PNG) {
            throw new WebpException("addPng called on " + format + " stream");
        }
        addCompressed(pngBytes, durationMs, FrameFormat.PNG);
    }

    public void addWebp(final byte[] webpBytes, final int durationMs) {
        if (format != FrameFormat.WEBP) {
            throw new WebpException("addWebp called on " + format + " stream");
        }
        addCompressed(webpBytes, durationMs, FrameFormat.WEBP);
    }

    private void addCompressed(final byte[] data, final int durationMs, final FrameFormat fmt) {
        final var h = state.handle.get();
        if (h == 0L) {
            throw new IllegalStateException("WebpAnimationStream is closed");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("frame bytes must be non-null and non-empty");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
        try (final var arena = Arena.ofConfined()) {
            final var seg = arena.allocate(data.length);
            MemorySegment.copy(MemorySegment.ofArray(data), 0L, seg, 0L, data.length);
            switch (fmt) {
                case JPEG -> NativeWebpLibrary.streamAddJpeg(h, seg, durationMs);
                case PNG -> NativeWebpLibrary.streamAddPng(h, seg, durationMs);
                case WEBP -> NativeWebpLibrary.streamAddWebp(h, seg, durationMs);
            }
        }
    }

    /**
     * Flush the trailing frame, assemble the WebP, mux metadata if any, and return the encoded
     * bytes. The native handle is destroyed before this method returns; subsequent {@link #addJpeg}
     * / {@link #addPng} / {@link #finish} calls will throw.
     */
    public byte[] finish(final WebpMetadata metadata) {
        final var h = state.handle.getAndSet(0L);
        if (h == 0L) {
            throw new IllegalStateException("WebpAnimationStream is already closed");
        }
        // streamFinish always destroys the handle (success or failure).
        try (final var out = NativeWebpLibrary.streamFinish(h, metadata)) {
            return out.toByteArray();
        }
    }

    public byte[] finish() {
        return finish(WebpMetadata.EMPTY);
    }

    /**
     * Aborts the stream if {@link #finish} hasn't been called. Idempotent and safe to call after
     * {@code finish}.
     */
    @Override
    public void close() {
        cleanable.clean();
    }

    /** Cleanup state — must NOT hold a reference back to {@link WebpAnimationStream}. */
    private record State(AtomicLong handle) implements Runnable {
        private State(final long handle) {
            this(new AtomicLong(handle));
        }

        @Override
        public void run() {
            final var h = handle.getAndSet(0L);
            if (h != 0L) {
                NativeWebpLibrary.streamAbort(h);
            }
        }
    }
}
