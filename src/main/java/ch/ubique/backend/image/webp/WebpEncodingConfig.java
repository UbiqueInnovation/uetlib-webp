package ch.ubique.backend.image.webp;

import java.time.Duration;

/**
 * Legacy config record from {@code uetlib-core}. Kept as a compatibility shim.
 * New code should use {@link WebpEncodeOptions}.
 *
 * @deprecated Use {@link WebpEncodeOptions}.
 */
@Deprecated(forRemoval = true)
public record WebpEncodingConfig(
    Duration duration,
    WebpQuality quality,
    CompressionMethod method,
    int compressionFactor
) {
    public WebpEncodingConfig withDuration(Duration duration) {
        return new WebpEncodingConfig(duration, quality, method, compressionFactor);
    }

    public static WebpEncodingConfigBuilder builder() {
        return new WebpEncodingConfigBuilder(
            Duration.ofSeconds(1),
            LosslessWebp.INSTANCE,
            CompressionMethod.M4,
            75);
    }

    public static final class WebpEncodingConfigBuilder {
        private Duration duration;
        private WebpQuality quality;
        private CompressionMethod method;
        private int compressionFactor;

        private WebpEncodingConfigBuilder(
            Duration duration,
            WebpQuality quality,
            CompressionMethod method,
            int compressionFactor
        ) {
            this.duration = duration;
            this.quality = quality;
            this.method = method;
            this.compressionFactor = compressionFactor;
        }

        public WebpEncodingConfigBuilder duration(Duration duration) {
            this.duration = duration;
            return this;
        }

        public WebpEncodingConfigBuilder quality(WebpQuality quality) {
            this.quality = quality;
            return this;
        }

        public WebpEncodingConfigBuilder method(CompressionMethod method) {
            this.method = method;
            return this;
        }

        public WebpEncodingConfigBuilder compressionFactor(int compressionFactor) {
            this.compressionFactor = compressionFactor;
            return this;
        }

        public WebpEncodingConfig build() {
            return new WebpEncodingConfig(duration, quality, method, compressionFactor);
        }
    }
}
