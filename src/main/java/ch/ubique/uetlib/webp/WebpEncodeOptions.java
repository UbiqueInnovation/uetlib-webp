package ch.ubique.uetlib.webp;

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

    /**
     * {@code 0} means "let libwebp pick" (matches img2webp / WebPAnimEncoderOptionsInit: kmin=9).
     */
    private final int kmin;

    /**
     * {@code 0} means "let libwebp pick" (matches img2webp / WebPAnimEncoderOptionsInit: kmax=17).
     */
    private final int kmax;

    private final boolean allowMixed;
    private final boolean minimizeSize;

    private WebpEncodeOptions(final Builder b) {
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

    private static void requireBooleanInt(final String label, final int value) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(label + " must be 0 or 1");
        }
    }

    private static void requireNonNegative(final String label, final int value) {
        if (value < 0) {
            throw new IllegalArgumentException(label + " must be >= 0");
        }
    }

    private static void requireRange(
            final String label, final int value, final int min, final int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(label + " must be in [" + min + ", " + max + "]");
        }
    }

    private static void requireRange(
            final String label, final float value, final float min, final float max) {
        if (!Float.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(
                    label + " must be finite and in [" + min + ", " + max + "]");
        }
    }

    private static void requireFiniteNonNegative(final String label, final float value) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(label + " must be finite and >= 0");
        }
    }

    public boolean lossless() {
        return lossless;
    }

    public float quality() {
        return quality;
    }

    public int method() {
        return method;
    }

    public boolean exact() {
        return exact;
    }

    public int nearLossless() {
        return nearLossless;
    }

    public int targetSize() {
        return targetSize;
    }

    public float targetPsnr() {
        return targetPsnr;
    }

    public int threadLevel() {
        return threadLevel;
    }

    public int alphaQuality() {
        return alphaQuality;
    }

    public int loopCount() {
        return loopCount;
    }

    public int kmin() {
        return kmin;
    }

    public int kmax() {
        return kmax;
    }

    public boolean allowMixed() {
        return allowMixed;
    }

    public boolean minimizeSize() {
        return minimizeSize;
    }

    void validateStill() {
        validateCommon();
        if (loopCount != 0 || kmin != 0 || kmax != 0 || allowMixed || minimizeSize) {
            throw new IllegalArgumentException(
                    "animation-only options must remain at defaults for still encodes");
        }
    }

    void validateAnimation() {
        validateCommon();
        if ((kmin == 0) != (kmax == 0)) {
            throw new IllegalArgumentException(
                    "kmin and kmax must either both be 0 or both be > 0");
        }
        if (kmin > 0 && kmin >= kmax) {
            throw new IllegalArgumentException("kmin must be < kmax when keyframe bounds are set");
        }
    }

    private void validateCommon() {
        requireRange("quality", quality, 0.0f, 100.0f);
        requireRange("method", method, 0, 6);
        requireRange("nearLossless", nearLossless, 0, 100);
        requireNonNegative("targetSize", targetSize);
        requireFiniteNonNegative("targetPsnr", targetPsnr);
        requireBooleanInt("threadLevel", threadLevel);
        requireRange("alphaQuality", alphaQuality, 0, 100);
        requireNonNegative("loopCount", loopCount);
        requireNonNegative("kmin", kmin);
        requireNonNegative("kmax", kmax);
        if (targetSize > 0 && targetPsnr > 0.0f) {
            throw new IllegalArgumentException("targetSize and targetPsnr are mutually exclusive");
        }
    }

    public static final class Builder {
        private boolean lossless;
        private float quality = 75.0f;
        private int method = 4;
        private boolean exact;
        private int nearLossless = 100;
        private int targetSize;
        private float targetPsnr;
        private int threadLevel = 1;
        private int alphaQuality = 100;
        private int loopCount;
        private int kmin;
        private int kmax;
        private boolean allowMixed;
        private boolean minimizeSize;

        public Builder lossless(final boolean value) {
            this.lossless = value;
            return this;
        }

        public Builder quality(final float value) {
            this.quality = value;
            return this;
        }

        public Builder method(final int value) {
            this.method = value;
            return this;
        }

        public Builder exact(final boolean value) {
            this.exact = value;
            return this;
        }

        public Builder nearLossless(final int value) {
            this.nearLossless = value;
            return this;
        }

        public Builder targetSize(final int value) {
            this.targetSize = value;
            return this;
        }

        public Builder targetPsnr(final float value) {
            this.targetPsnr = value;
            return this;
        }

        public Builder threadLevel(final int value) {
            this.threadLevel = value;
            return this;
        }

        public Builder alphaQuality(final int value) {
            this.alphaQuality = value;
            return this;
        }

        public Builder loopCount(final int value) {
            this.loopCount = value;
            return this;
        }

        public Builder kmin(final int value) {
            this.kmin = value;
            return this;
        }

        public Builder kmax(final int value) {
            this.kmax = value;
            return this;
        }

        public Builder allowMixed(final boolean value) {
            this.allowMixed = value;
            return this;
        }

        public Builder minimizeSize(final boolean value) {
            this.minimizeSize = value;
            return this;
        }

        public WebpEncodeOptions build() {
            final var options = new WebpEncodeOptions(this);
            options.validateAnimation();
            return options;
        }
    }
}
