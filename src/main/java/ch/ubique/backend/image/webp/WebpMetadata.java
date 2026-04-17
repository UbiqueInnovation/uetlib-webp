package ch.ubique.backend.image.webp;

import java.lang.foreign.MemorySegment;

public record WebpMetadata(MemorySegment exif, MemorySegment iccp, MemorySegment xmp) {
    public static final WebpMetadata EMPTY = new WebpMetadata(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);

    public WebpMetadata {
        exif = exif == null ? MemorySegment.NULL : exif;
        iccp = iccp == null ? MemorySegment.NULL : iccp;
        xmp = xmp == null ? MemorySegment.NULL : xmp;
    }
}
