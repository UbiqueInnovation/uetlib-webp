package ch.ubique.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("DataFlowIssue")
class WebpMetadataTest {
    private static MemorySegment bytes(final Arena arena, final byte[] input) {
        final MemorySegment out = arena.allocate(input.length);
        MemorySegment.copy(MemorySegment.ofArray(input), 0, out, 0, input.length);
        return out;
    }

    private static byte[] toBytes(final MemorySegment segment) {
        final byte[] out = new byte[(int) segment.byteSize()];
        for (int i = 0; i < out.length; i++) {
            out[i] = segment.get(ValueLayout.JAVA_BYTE, i);
        }
        return out;
    }

    @Test
    void nullInputsNormalizeToNullSegments() {
        final WebpMetadata metadata = new WebpMetadata(null, null, null);
        assertEquals(MemorySegment.NULL, metadata.exif());
        assertEquals(MemorySegment.NULL, metadata.iccp());
        assertEquals(MemorySegment.NULL, metadata.xmp());
    }

    @Test
    void explicitSegmentsArePreserved() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment exif = bytes(arena, new byte[] {1, 2});
            final MemorySegment iccp = bytes(arena, new byte[] {3, 4, 5});
            final MemorySegment xmp = bytes(arena, new byte[] {6});
            final WebpMetadata metadata = new WebpMetadata(exif, iccp, xmp);
            assertEquals(exif, metadata.exif());
            assertEquals(iccp, metadata.iccp());
            assertEquals(xmp, metadata.xmp());
        }
    }

    @Test
    void emptyConstantReportsEmpty() {
        assertTrue(WebpMetadata.EMPTY.isEmpty());
    }

    @Test
    void xmpFromStringEncodesUtf8() {
        final String xml = "<?xpacket id=\"a\"?>äöü";
        final WebpMetadata metadata = WebpMetadata.xmp(xml);
        final byte[] expected = xml.getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(expected, toBytes(metadata.xmp()));
        assertEquals(MemorySegment.NULL, metadata.exif());
        assertEquals(MemorySegment.NULL, metadata.iccp());
        assertFalse(metadata.isEmpty());
    }

    @Test
    void xmpFromNullOrEmptyStringReturnsSharedEmpty() {
        assertSame(WebpMetadata.EMPTY, WebpMetadata.xmp((String) null));
        assertSame(WebpMetadata.EMPTY, WebpMetadata.xmp(""));
    }

    @Test
    void xmpFromBytesWrapsHeapSegment() {
        final byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        final WebpMetadata metadata = WebpMetadata.xmp(bytes);
        assertNotNull(metadata.xmp());
        assertArrayEquals(bytes, toBytes(metadata.xmp()));
        // heap-backed segment, not native
        assertFalse(metadata.xmp().isNative());
        assertFalse(metadata.isEmpty());
    }

    @Test
    void exifAndIccpFactoriesWrapHeapBytes() {
        final byte[] exif = {1, 2, 3};
        final byte[] iccp = {4, 5};
        final WebpMetadata metaExif = WebpMetadata.exif(exif);
        final WebpMetadata metaIccp = WebpMetadata.iccp(iccp);
        assertArrayEquals(exif, toBytes(metaExif.exif()));
        assertEquals(MemorySegment.NULL, metaExif.iccp());
        assertEquals(MemorySegment.NULL, metaExif.xmp());
        assertArrayEquals(iccp, toBytes(metaIccp.iccp()));
        assertFalse(metaExif.isEmpty());
        assertFalse(metaIccp.isEmpty());
    }

    @Test
    void nullOrEmptyBytesFactoriesProduceEmptyMetadata() {
        assertTrue(WebpMetadata.exif(null).isEmpty());
        assertTrue(WebpMetadata.exif(new byte[0]).isEmpty());
        assertTrue(WebpMetadata.iccp(null).isEmpty());
        assertTrue(WebpMetadata.iccp(new byte[0]).isEmpty());
        assertTrue(WebpMetadata.xmp((byte[]) null).isEmpty());
        assertTrue(WebpMetadata.xmp(new byte[0]).isEmpty());
    }

    @Test
    void builderMixesHeapAndNativeInputs() {
        try (final Arena arena = Arena.ofConfined()) {
            final byte[] exif = {10, 11, 12};
            final MemorySegment iccpSeg = bytes(arena, new byte[] {20, 21});
            final WebpMetadata metadata =
                    WebpMetadata.builder().exif(exif).iccp(iccpSeg).xmp("x").build();
            assertArrayEquals(exif, toBytes(metadata.exif()));
            assertEquals(iccpSeg, metadata.iccp());
            assertArrayEquals("x".getBytes(StandardCharsets.UTF_8), toBytes(metadata.xmp()));
            assertFalse(metadata.isEmpty());
        }
    }

    @Test
    void builderNullInputsAreNormalised() {
        final WebpMetadata metadata =
                WebpMetadata.builder()
                        .exif((byte[]) null)
                        .iccp((byte[]) null)
                        .xmp((String) null)
                        .build();
        assertTrue(metadata.isEmpty());

        final WebpMetadata metadataSeg =
                WebpMetadata.builder()
                        .exif((MemorySegment) null)
                        .iccp((MemorySegment) null)
                        .xmp((MemorySegment) null)
                        .build();
        assertTrue(metadataSeg.isEmpty());
    }

    @Test
    void builderXmpByteOverloadOnNullOrEmptyYieldsEmptyChunk() {
        final WebpMetadata metadata = WebpMetadata.builder().xmp(new byte[0]).build();
        assertTrue(metadata.isEmpty());
    }
}
