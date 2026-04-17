package ch.ubique.backend.image.webp;

/**
 * Legacy {@code uetlib-core} method enum.
 * New code should pass {@code .method(int)} on {@link WebpEncodeOptions} directly (0..6).
 *
 * @deprecated Use the {@code int} {@code method} field on {@link WebpEncodeOptions}.
 */
@Deprecated(forRemoval = true)
public enum CompressionMethod {
    M0(0),
    M1(1),
    M2(2),
    M3(3),
    M4(4),
    M5(5),
    M6(6);

    private final int method;

    CompressionMethod(int method) {
        this.method = method;
    }

    public int getMethod() {
        return method;
    }
}
