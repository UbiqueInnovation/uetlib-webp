package ch.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebpEncodeOptionsTest {
    @Test
    void defaultsAreSane() {
        final WebpEncodeOptions defaults = WebpEncodeOptions.defaults();
        assertFalse(defaults.lossless());
        assertEquals(75.0f, defaults.quality());
        assertEquals(4, defaults.method());
        assertFalse(defaults.exact());
        assertEquals(100, defaults.nearLossless());
        assertEquals(0, defaults.targetSize());
        assertEquals(0.0f, defaults.targetPsnr());
        assertEquals(1, defaults.threadLevel());
        assertEquals(100, defaults.alphaQuality());
        assertEquals(0, defaults.loopCount());
        assertEquals(0, defaults.kmin());
        assertEquals(0, defaults.kmax());
        assertFalse(defaults.allowMixed());
        assertFalse(defaults.minimizeSize());
    }

    @Test
    void builderOverridesValues() {
        final WebpEncodeOptions options =
                WebpEncodeOptions.builder()
                        .lossless(true)
                        .quality(92.0f)
                        .method(6)
                        .exact(true)
                        .nearLossless(80)
                        .targetSize(2048)
                        .threadLevel(0)
                        .alphaQuality(91)
                        .loopCount(2)
                        .kmin(5)
                        .kmax(17)
                        .allowMixed(true)
                        .minimizeSize(true)
                        .build();

        assertTrue(options.lossless());
        assertEquals(92.0f, options.quality());
        assertEquals(6, options.method());
        assertTrue(options.exact());
        assertEquals(80, options.nearLossless());
        assertEquals(2048, options.targetSize());
        assertEquals(0.0f, options.targetPsnr());
        assertEquals(0, options.threadLevel());
        assertEquals(91, options.alphaQuality());
        assertEquals(2, options.loopCount());
        assertEquals(5, options.kmin());
        assertEquals(17, options.kmax());
        assertTrue(options.allowMixed());
        assertTrue(options.minimizeSize());
    }

    @Test
    void builderRejectsInvalidRanges() {
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().quality(Float.NaN).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().targetPsnr(-1.0f).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> WebpEncodeOptions.builder().loopCount(-1).build());
    }
}
