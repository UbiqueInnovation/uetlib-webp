package ch.ubique.uetlib.image.webp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * WebP-container metadata chunks to embed alongside the encoded pixel payload.
 *
 * <p>All three fields are optional and default to {@link MemorySegment#NULL}, which instructs the
 * native layer to skip the corresponding chunk entirely. When at least one chunk is non-empty the
 * native encoder routes the output through {@code WebPMux} in the same C call as the encode, so no
 * second Java round-trip is required.
 *
 * <h2>Heap-friendly construction</h2>
 *
 * Most callers hold their metadata as plain heap {@code byte[]} or {@link String} (e.g. XMP XML).
 * Use the static factories below — they copy into an internal heap-backed segment so the caller
 * never has to open an {@link Arena} just to describe metadata. The encoder copies those segments
 * into its own confined arena for the duration of the native call, so once the encode returns the
 * {@code WebpMetadata} instance holds no native-owned memory and does not require {@code close()}.
 *
 * <pre>{@code
 * WebpMetadata meta = WebpMetadata.xmp(xmpXml);            // XMP only
 * WebpMetadata meta = WebpMetadata.exif(exifBytes);        // EXIF only
 * WebpMetadata meta = WebpMetadata.builder()               // mix & match
 *         .xmp(xmpXml)
 *         .iccp(iccpBytes)
 *         .build();
 * }</pre>
 */
public record WebpMetadata(MemorySegment exif, MemorySegment iccp, MemorySegment xmp) {
    /** Shared instance representing "no metadata chunks". */
    public static final WebpMetadata EMPTY =
            new WebpMetadata(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);

    public WebpMetadata(
            @Nullable final MemorySegment exif,
            @Nullable final MemorySegment iccp,
            @Nullable final MemorySegment xmp) {
        this.exif = exif == null ? MemorySegment.NULL : exif;
        this.iccp = iccp == null ? MemorySegment.NULL : iccp;
        this.xmp = xmp == null ? MemorySegment.NULL : xmp;
    }

    /** EXIF-only metadata from a heap {@code byte[]}. */
    public static WebpMetadata exif(final byte[] exifBytes) {
        return builder().exif(exifBytes).build();
    }

    /** ICC profile (ICCP) from a heap {@code byte[]}. */
    public static WebpMetadata iccp(final byte[] iccpBytes) {
        return builder().iccp(iccpBytes).build();
    }

    /** XMP chunk from a heap {@code byte[]}. */
    public static WebpMetadata xmp(final byte[] xmpBytes) {
        return builder().xmp(xmpBytes).build();
    }

    /**
     * XMP chunk from a {@link String}; encoded as UTF-8. Blank/empty input returns {@link #EMPTY}.
     */
    public static WebpMetadata xmp(final @Nullable String xmpXml) {
        if (xmpXml == null || xmpXml.isEmpty()) {
            return EMPTY;
        }
        return xmp(xmpXml.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean isEmpty(final @Nullable MemorySegment segment) {
        return segment == null || segment.equals(MemorySegment.NULL) || segment.byteSize() == 0L;
    }

    /** New mutable builder over an initially {@link #EMPTY} metadata object. */
    public static Builder builder() {
        return new Builder();
    }

    /** True if every chunk is empty; the native encoder skips muxing in that case. */
    public boolean isEmpty() {
        return isEmpty(exif) && isEmpty(iccp) && isEmpty(xmp);
    }

    /** Builder for mix-and-match EXIF/ICCP/XMP metadata from heap inputs. */
    public static final class Builder {
        private MemorySegment exif = MemorySegment.NULL;
        private MemorySegment iccp = MemorySegment.NULL;
        private MemorySegment xmp = MemorySegment.NULL;

        private Builder() {}

        private static MemorySegment wrap(final byte @Nullable [] bytes) {
            if (bytes == null || bytes.length == 0) {
                return MemorySegment.NULL;
            }
            // Heap-backed segment — the encoder copies into its own confined arena before the
            // native call, so the caller does not have to manage an Arena just for metadata.
            return MemorySegment.ofArray(bytes);
        }

        public Builder exif(final byte[] bytes) {
            this.exif = wrap(bytes);
            return this;
        }

        public Builder iccp(final byte[] bytes) {
            this.iccp = wrap(bytes);
            return this;
        }

        public Builder xmp(final byte[] bytes) {
            this.xmp = wrap(bytes);
            return this;
        }

        public Builder xmp(final @Nullable String xmpXml) {
            this.xmp =
                    (xmpXml == null || xmpXml.isEmpty())
                            ? MemorySegment.NULL
                            : wrap(xmpXml.getBytes(StandardCharsets.UTF_8));
            return this;
        }

        /** Provide an already-allocated EXIF segment (e.g. from a user-managed {@link Arena}). */
        public Builder exif(final MemorySegment segment) {
            this.exif = nullSafe(segment);
            return this;
        }

        public Builder iccp(final MemorySegment segment) {
            this.iccp = nullSafe(segment);
            return this;
        }

        public Builder xmp(final MemorySegment segment) {
            this.xmp = nullSafe(segment);
            return this;
        }

        public WebpMetadata build() {
            return new WebpMetadata(exif, iccp, xmp);
        }

        private static MemorySegment nullSafe(final @Nullable MemorySegment segment) {
            return segment == null ? MemorySegment.NULL : segment;
        }
    }
}
