package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Push-style animation encoder. Each {@code addJpeg} / {@code addPng} call decodes one frame
 * natively and feeds it to the underlying {@code WebPAnimEncoder}; the per-frame work buffer
 * is freed before the call returns. {@link #finish} flushes and returns the assembled WebP.
 *
 * <p>Peak memory while encoding is {@code O(one decoded frame + accumulated WebP output)},
 * independent of total frame count — making this the right choice for long timelapses where
 * holding all frame bytes in memory at once would be expensive.
 *
 * <h2>Lifecycle</h2>
 * <pre>{@code
 * try (var stream = encoder.openJpegStream(w, h, 0, opts)) {
 *     for (var jpeg : sourceJpegs) {
 *         stream.addJpeg(jpeg, 200);
 *     }
 *     byte[] webp = stream.finish(WebpMetadata.xmp(xmp));
 * }
 * }</pre>
 *
 * <p>If {@link #close} runs without {@link #finish} having been called, the native handle
 * is aborted (resources freed, no output produced). A {@link Cleaner} is registered as a
 * safety net if the caller drops the reference without closing.
 *
 * <p>Not thread-safe: a single stream must be driven by one thread.
 */
public final class WebpAnimationStream implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    private final State state;
    private final Cleaner.Cleanable cleanable;
    private final boolean jpeg;

    /** Use {@link WebpAnimationEncoder#openJpegStream} / {@code openPngStream} to construct. */
    WebpAnimationStream(int canvasWidth, int canvasHeight, int bgcolorRgba,
                        WebpEncodeOptions options, boolean jpeg) {
        long handle = NativeWebpLibrary.streamOpen(canvasWidth, canvasHeight, bgcolorRgba, options);
        this.state = new State(handle);
        this.cleanable = CLEANER.register(this, state);
        this.jpeg = jpeg;
    }

    /**
     * Decode a JPEG frame and append it to the animation. Throws if this stream was opened
     * for PNG, if the frame is empty, or if its dimensions don't match the canvas.
     */
    public void addJpeg(byte[] jpegBytes, int durationMs) {
        if (!jpeg) {
            throw new WebpException("addJpeg called on PNG stream");
        }
        addCompressed(jpegBytes, durationMs, /*isJpeg=*/ true);
    }

    public void addPng(byte[] pngBytes, int durationMs) {
        if (jpeg) {
            throw new WebpException("addPng called on JPEG stream");
        }
        addCompressed(pngBytes, durationMs, /*isJpeg=*/ false);
    }

    private void addCompressed(byte[] data, int durationMs, boolean isJpeg) {
        long h = state.handle.get();
        if (h == 0L) {
            throw new IllegalStateException("WebpAnimationStream is closed");
        }
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("frame bytes must be non-null and non-empty");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment seg = arena.allocate(data.length);
            MemorySegment.copy(MemorySegment.ofArray(data), 0L, seg, 0L, data.length);
            if (isJpeg) {
                NativeWebpLibrary.streamAddJpeg(h, seg, durationMs);
            } else {
                NativeWebpLibrary.streamAddPng(h, seg, durationMs);
            }
        }
    }

    /**
     * Flush the trailing frame, assemble the WebP, mux metadata if any, and return the
     * encoded bytes. The native handle is destroyed before this method returns; subsequent
     * {@link #addJpeg} / {@link #addPng} / {@link #finish} calls will throw.
     */
    public byte[] finish(WebpMetadata metadata) {
        long h = state.handle.getAndSet(0L);
        if (h == 0L) {
            throw new IllegalStateException("WebpAnimationStream is already closed");
        }
        // streamFinish always destroys the handle (success or failure).
        try (EncodedWebp out = NativeWebpLibrary.streamFinish(h, metadata)) {
            return out.toByteArray();
        }
    }

    public byte[] finish() {
        return finish(WebpMetadata.EMPTY);
    }

    /**
     * Aborts the stream if {@link #finish} hasn't been called. Idempotent and safe to call
     * after {@code finish}.
     */
    @Override
    public void close() {
        cleanable.clean();
    }

    /** Cleanup state — must NOT hold a reference back to {@link WebpAnimationStream}. */
    private static final class State implements Runnable {
        final AtomicLong handle;

        State(long handleAddress) {
            this.handle = new AtomicLong(handleAddress);
        }

        @Override
        public void run() {
            long h = handle.getAndSet(0L);
            if (h != 0L) {
                NativeWebpLibrary.streamAbort(h);
            }
        }
    }
}
