package ch.ubique.backend.image.webp;

import java.util.OptionalInt;

/**
 * Exception thrown by the WebP encoder.
 *
 * <p>When the failure originated from a native call, {@link #nativeStatus()} carries the
 * {@code uetlib_webp_status} returned by the native layer (or empty if the failure happened
 * before/after the native invocation). The original {@link Throwable} is preserved as the cause.
 */
public final class WebpException extends RuntimeException {
    private static final int NO_STATUS = Integer.MIN_VALUE;

    private final int status;

    public WebpException(String message) {
        super(message);
        this.status = NO_STATUS;
    }

    public WebpException(String message, Throwable cause) {
        super(message, cause);
        this.status = NO_STATUS;
    }

    public WebpException(String message, int nativeStatus, Throwable cause) {
        super(message, cause);
        this.status = nativeStatus;
    }

    public WebpException(String message, int nativeStatus) {
        super(message);
        this.status = nativeStatus;
    }

    public OptionalInt nativeStatus() {
        return status == NO_STATUS ? OptionalInt.empty() : OptionalInt.of(status);
    }
}
