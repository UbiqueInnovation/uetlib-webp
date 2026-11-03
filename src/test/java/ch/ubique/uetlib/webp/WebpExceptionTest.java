package ch.ubique.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

class WebpExceptionTest {
    @Test
    void storesMessage() {
        final WebpException exception = new WebpException("boom");
        assertEquals("boom", exception.getMessage());
        assertTrue(exception.nativeStatus().isEmpty());
    }

    @Test
    void storesMessageAndCause() {
        final RuntimeException cause = new RuntimeException("root");
        final WebpException exception = new WebpException("boom", cause);
        assertEquals("boom", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertTrue(exception.nativeStatus().isEmpty());
    }

    @Test
    void storesNativeStatus() {
        final WebpException exception = new WebpException("encode failed", 3);
        assertEquals("encode failed", exception.getMessage());
        assertEquals(OptionalInt.of(3), exception.nativeStatus());
    }

    @Test
    void storesNativeStatusAndCause() {
        final RuntimeException cause = new RuntimeException("root");
        final WebpException exception = new WebpException("encode failed", 5, cause);
        assertEquals("encode failed", exception.getMessage());
        assertSame(cause, exception.getCause());
        assertEquals(OptionalInt.of(5), exception.nativeStatus());
        assertFalse(exception.nativeStatus().isEmpty());
    }
}
