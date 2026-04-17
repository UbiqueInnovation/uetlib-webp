package ch.ubique.backend.image.webp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WebpCompressedFrameBytesTest {

    @Test
    void acceptsValidInputs() {
        byte[] data = {1, 2, 3};
        WebpCompressedFrameBytes frame = new WebpCompressedFrameBytes(data, 40);
        assertArrayEquals(data, frame.data());
        assertEquals(40, frame.durationMs());
    }

    @Test
    void rejectsNullData() {
        assertThrows(IllegalArgumentException.class,
            () -> new WebpCompressedFrameBytes(null, 10));
    }

    @Test
    void rejectsEmptyData() {
        assertThrows(IllegalArgumentException.class,
            () -> new WebpCompressedFrameBytes(new byte[0], 10));
    }

    @Test
    void rejectsNonPositiveDuration() {
        byte[] data = {1};
        assertThrows(IllegalArgumentException.class,
            () -> new WebpCompressedFrameBytes(data, 0));
        assertThrows(IllegalArgumentException.class,
            () -> new WebpCompressedFrameBytes(data, -5));
    }
}
