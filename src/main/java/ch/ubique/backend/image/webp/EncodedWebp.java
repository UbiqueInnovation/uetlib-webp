package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.ffi.NativeWebpLibrary;

import org.jspecify.annotations.Nullable;

import java.lang.foreign.MemorySegment;
import java.lang.ref.Cleaner;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns one malloc'd, native-encoded WebP byte buffer. Closeable; double-close is safe.
 *
 * <p>Backed by a {@link Cleaner} as a safety net: if a caller forgets {@link #close()} (e.g. drops
 * the reference without try-with-resources), the underlying buffer is still freed when the {@code
 * EncodedWebp} becomes unreachable. The cleanup state intentionally does not hold a strong
 * reference back to the {@code EncodedWebp} itself, so the GC is free to collect promptly. Callers
 * should still close explicitly to release memory at a deterministic point.
 */
public final class EncodedWebp implements AutoCloseable {
    private static final Cleaner CLEANER = Cleaner.create();

    private final MemorySegment nativeData;
    private final long size;
    private final State state;
    private final Cleaner.Cleanable cleanable;

    public EncodedWebp(final @Nullable MemorySegment nativeData, final long size) {
        if (nativeData == null || nativeData.equals(MemorySegment.NULL)) {
            throw new IllegalArgumentException("nativeData must be non-null/non-NULL");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("size must be > 0");
        }
        this.nativeData = nativeData;
        this.size = size;
        this.state = new State(nativeData.address());
        this.cleanable = CLEANER.register(this, state);
    }

    public MemorySegment bytes() {
        ensureOpen();
        return nativeData.reinterpret(size);
    }

    public long size() {
        ensureOpen();
        return size;
    }

    public byte[] toByteArray() {
        ensureOpen();
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "EncodedWebp exceeds maximum heap byte[] size: " + size);
        }
        final byte[] out = new byte[(int) size];
        MemorySegment.copy(bytes(), 0, MemorySegment.ofArray(out), 0, size);
        return out;
    }

    @Override
    public void close() {
        cleanable.clean();
    }

    private void ensureOpen() {
        if (state.closed.get()) {
            throw new IllegalStateException("EncodedWebp is already closed");
        }
    }

    /** Cleanup state — must NOT hold a reference back to {@link EncodedWebp}. */
    private static final class State implements Runnable {
        final AtomicBoolean closed = new AtomicBoolean(false);
        private final long dataAddress;

        State(final long dataAddress) {
            this.dataAddress = dataAddress;
        }

        @Override
        public void run() {
            if (closed.compareAndSet(false, true)) {
                NativeWebpLibrary.freeBuffer(dataAddress);
            }
        }
    }
}
