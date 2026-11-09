package ch.uetlib.webp;

public enum PixelFormat {
    RGBA(1),
    RGB(2),
    BGRA(3),
    BGR(4);

    private final int nativeCode;
    private final int bytesPerPixel;

    PixelFormat(final int nativeCode) {
        this.nativeCode = nativeCode;
        this.bytesPerPixel = nativeCode == 1 || nativeCode == 3 ? 4 : 3;
    }

    public int nativeCode() {
        return nativeCode;
    }

    public int bytesPerPixel() {
        return bytesPerPixel;
    }
}
