package ch.ubique.backend.image.webp;

public final class WebpEncodeOptions {
    public static final int ABI_VERSION = 1;

    private final boolean lossless;
    private final float quality;
    private final int method;
    private final boolean exact;
    private final int nearLossless;
    private final int targetSize;
    private final float targetPsnr;
    private final int threadLevel;
    private final int alphaQuality;
    private final int loopCount;
    private final int kmin;
    private final int kmax;
    private final boolean allowMixed;
    private final boolean minimizeSize;

    private WebpEncodeOptions(Builder b) {
        this.lossless = b.lossless;
        this.quality = b.quality;
        this.method = b.method;
        this.exact = b.exact;
        this.nearLossless = b.nearLossless;
        this.targetSize = b.targetSize;
        this.targetPsnr = b.targetPsnr;
        this.threadLevel = b.threadLevel;
        this.alphaQuality = b.alphaQuality;
        this.loopCount = b.loopCount;
        this.kmin = b.kmin;
        this.kmax = b.kmax;
        this.allowMixed = b.allowMixed;
        this.minimizeSize = b.minimizeSize;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static WebpEncodeOptions defaults() {
        return builder().build();
    }

    public boolean lossless() { return lossless; }
    public float quality() { return quality; }
    public int method() { return method; }
    public boolean exact() { return exact; }
    public int nearLossless() { return nearLossless; }
    public int targetSize() { return targetSize; }
    public float targetPsnr() { return targetPsnr; }
    public int threadLevel() { return threadLevel; }
    public int alphaQuality() { return alphaQuality; }
    public int loopCount() { return loopCount; }
    public int kmin() { return kmin; }
    public int kmax() { return kmax; }
    public boolean allowMixed() { return allowMixed; }
    public boolean minimizeSize() { return minimizeSize; }

    public static final class Builder {
        private boolean lossless;
        private float quality = 75f;
        private int method = 4;
        private boolean exact;
        private int nearLossless = 100;
        private int targetSize;
        private float targetPsnr;
        private int threadLevel = 1;
        private int alphaQuality = 100;
        private int loopCount = 0;
        private int kmin;
        private int kmax;
        private boolean allowMixed;
        private boolean minimizeSize;

        public Builder lossless(boolean value) { this.lossless = value; return this; }
        public Builder quality(float value) { this.quality = value; return this; }
        public Builder method(int value) { this.method = value; return this; }
        public Builder exact(boolean value) { this.exact = value; return this; }
        public Builder nearLossless(int value) { this.nearLossless = value; return this; }
        public Builder targetSize(int value) { this.targetSize = value; return this; }
        public Builder targetPsnr(float value) { this.targetPsnr = value; return this; }
        public Builder threadLevel(int value) { this.threadLevel = value; return this; }
        public Builder alphaQuality(int value) { this.alphaQuality = value; return this; }
        public Builder loopCount(int value) { this.loopCount = value; return this; }
        public Builder kmin(int value) { this.kmin = value; return this; }
        public Builder kmax(int value) { this.kmax = value; return this; }
        public Builder allowMixed(boolean value) { this.allowMixed = value; return this; }
        public Builder minimizeSize(boolean value) { this.minimizeSize = value; return this; }

        public WebpEncodeOptions build() {
            return new WebpEncodeOptions(this);
        }
    }
}
