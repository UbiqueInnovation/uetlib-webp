package ch.ubique.backend.image.webp;

public enum PixelFormat {
    RGBA(1),
    RGB(2),
    BGRA(3),
    BGR(4);

    private final int nativeCode;

    PixelFormat(int nativeCode) {
        this.nativeCode = nativeCode;
    }

    public int nativeCode() {
        return nativeCode;
    }
}
