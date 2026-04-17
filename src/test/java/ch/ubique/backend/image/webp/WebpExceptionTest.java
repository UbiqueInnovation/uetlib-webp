package ch.ubique.backend.image.webp;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebpExceptionTest {
    @Test
    void storesMessage() {
        WebpException exception = new WebpException("boom");
        assertEquals("boom", exception.getMessage());
        assertTrue(exception.nativeStatus().isEmpty());
    }

    @Test
    void storesMessageAndCause() {
        RuntimeException cause = new RuntimeException("root");
        WebpException exception = new WebpException("boom", cause);
        assertEquals("boom", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertTrue(exception.nativeStatus().isEmpty());
    }

    @Test
    void storesNativeStatus() {
        WebpException exception = new WebpException("encode failed", 3);
        assertEquals("encode failed", exception.getMessage());
        assertEquals(OptionalInt.of(3), exception.nativeStatus());
    }

    @Test
    void storesNativeStatusAndCause() {
        RuntimeException cause = new RuntimeException("root");
        WebpException exception = new WebpException("encode failed", 5, cause);
        assertEquals("encode failed", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(OptionalInt.of(5), exception.nativeStatus());
        assertFalse(exception.nativeStatus().isEmpty());
    }
}
