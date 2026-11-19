package ch.uetlib.webp;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.jspecify.annotations.Nullable;

/**
 * Heap-friendly animation frame descriptor for JPEG or PNG byte frames.
 *
 * <p>This is the {@code byte[]} counterpart of {@link WebpCompressedFrame} (which wraps an
 * already-native {@link java.lang.foreign.MemorySegment}). Use this type with {@link
 * WebpAnimationEncoder#encodeJpegFrameBytes} / {@link WebpAnimationEncoder#encodePngFrameBytes} to
 * hand raw JPEG/PNG bytes straight to the animation encoder — it handles the one-time copy into a
 * shared native arena internally.
 *
 * @param data raw JPEG or PNG bytes; must be non-null and non-empty
 * @param durationMs per-frame display duration in milliseconds; must be &gt; 0
 */
// equals and hashCode are overridden, so the array component is not compared by reference.
@SuppressWarnings("ArrayRecordComponent")
public record WebpCompressedFrameBytes(byte[] data, int durationMs) {
    public WebpCompressedFrameBytes {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("data must be non-null and non-empty");
        }
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
    }

    @Override
    public boolean equals(final @Nullable Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof final WebpCompressedFrameBytes that)) {
            return false;
        }

        return new EqualsBuilder()
                .append(data, that.data)
                .append(durationMs, that.durationMs)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(data).append(durationMs).toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("data.length", data.length)
                .append("durationMs", durationMs)
                .toString();
    }
}
