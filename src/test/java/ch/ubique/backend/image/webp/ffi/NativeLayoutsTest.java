package ch.ubique.backend.image.webp.ffi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLayoutsTest {
    @Test
    void layoutSizesAreNonZeroAndReasonable() {
        assertTrue(NativeLayouts.ENCODE_OPTIONS.byteSize() >= 64);
        assertTrue(NativeLayouts.METADATA.byteSize() >= 48);
        assertTrue(NativeLayouts.RAW_IMAGE.byteSize() >= 32);
        assertTrue(NativeLayouts.ANIM_FRAME.byteSize() >= 40);
        assertTrue(NativeLayouts.ANIM_JOB.byteSize() >= 40);
        assertTrue(NativeLayouts.COMPRESSED_FRAME.byteSize() >= 32);
        assertTrue(NativeLayouts.COMPRESSED_ANIM_JOB.byteSize() >= 40);
        assertTrue(NativeLayouts.RESULT.byteSize() >= 32);
    }

    @Test
    void pointerBackedStructsHaveExpectedAlignment() {
        assertEquals(8, NativeLayouts.METADATA.byteAlignment());
        assertEquals(8, NativeLayouts.RAW_IMAGE.byteAlignment());
        assertEquals(8, NativeLayouts.ANIM_FRAME.byteAlignment());
        assertEquals(8, NativeLayouts.ANIM_JOB.byteAlignment());
        assertEquals(8, NativeLayouts.COMPRESSED_FRAME.byteAlignment());
        assertEquals(8, NativeLayouts.COMPRESSED_ANIM_JOB.byteAlignment());
        assertEquals(8, NativeLayouts.RESULT.byteAlignment());
    }
}
