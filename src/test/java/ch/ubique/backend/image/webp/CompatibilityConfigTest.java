package ch.ubique.backend.image.webp;

import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompatibilityConfigTest {
    @Test
    void withDurationReturnsCopyWithNewDuration() {
        WebpEncodingConfig original = WebpEncodingConfig.builder()
            .duration(Duration.ofSeconds(1))
            .quality(LosslessWebp.INSTANCE)
            .method(CompressionMethod.M4)
            .compressionFactor(75)
            .build();

        Duration newDuration = Duration.ofMillis(500);
        WebpEncodingConfig modified = original.withDuration(newDuration);

        assertEquals(newDuration, modified.duration());
        assertEquals(original.quality(), modified.quality());
        assertEquals(original.method(), modified.method());
        assertEquals(original.compressionFactor(), modified.compressionFactor());
    }

    @Test
    void compatibilityOptionsUseCliEquivalentSingleThreadedSettingsForLossless() {
        WebpEncodingConfig config = WebpEncodingConfig.builder()
            .duration(Duration.ofMillis(42))
            .quality(LosslessWebp.INSTANCE)
            .method(CompressionMethod.M6)
            .compressionFactor(88)
            .build();

        WebpEncodeOptions options = NativeWebpCompat.toOptions(config);

        assertEquals(true, options.lossless());
        assertEquals(88f, options.quality());
        assertEquals(6, options.method());
        assertEquals(0, options.threadLevel());
        assertEquals(0, options.kmin());
        assertEquals(0, options.kmax());
        assertEquals(0, options.loopCount());
    }

    @Test
    void compatibilityOptionsUseLossyQualityAndStillStaySingleThreaded() {
        WebpEncodingConfig config = WebpEncodingConfig.builder()
            .quality(new LossyWebp(63))
            .method(CompressionMethod.M2)
            .compressionFactor(88)
            .build();

        WebpEncodeOptions options = NativeWebpCompat.toOptions(config);

        assertEquals(false, options.lossless());
        assertEquals(63f, options.quality());
        assertEquals(2, options.method());
        assertEquals(0, options.threadLevel());
        assertEquals(0, options.kmin());
        assertEquals(0, options.kmax());
    }
}
