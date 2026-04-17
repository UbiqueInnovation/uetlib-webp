package ch.ubique.backend.image.webp;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebpMetadataTest {
    @Test
    void nullInputsNormalizeToNullSegments() {
        WebpMetadata metadata = new WebpMetadata(null, null, null);
        assertEquals(MemorySegment.NULL, metadata.exif());
        assertEquals(MemorySegment.NULL, metadata.iccp());
        assertEquals(MemorySegment.NULL, metadata.xmp());
    }

    @Test
    void explicitSegmentsArePreserved() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment exif = bytes(arena, new byte[]{1, 2});
            MemorySegment iccp = bytes(arena, new byte[]{3, 4, 5});
            MemorySegment xmp = bytes(arena, new byte[]{6});
            WebpMetadata metadata = new WebpMetadata(exif, iccp, xmp);
            assertEquals(exif, metadata.exif());
            assertEquals(iccp, metadata.iccp());
            assertEquals(xmp, metadata.xmp());
        }
    }

    private static MemorySegment bytes(Arena arena, byte[] input) {
        MemorySegment out = arena.allocate(input.length);
        MemorySegment.copy(MemorySegment.ofArray(input), 0, out, 0, input.length);
        return out;
    }
}
