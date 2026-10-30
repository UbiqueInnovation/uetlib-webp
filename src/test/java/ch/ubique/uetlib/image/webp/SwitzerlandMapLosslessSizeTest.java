package ch.ubique.uetlib.image.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Encodes the {@code switzerland_map.png} test resource as lossless WebP and compares the resulting
 * size against the original PNG. Large flat-color regions (red background, navy land mass) are a
 * best-case for WebP's lossless compressor, so the WebP output is expected to be substantially
 * smaller than the PNG.
 */
@SuppressWarnings("DataFlowIssue")
class SwitzerlandMapLosslessSizeTest {
    private static final String PNG_RESOURCE = "/images/switzerland_map.png";

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    private static byte[] readResource(final String resource) throws Exception {
        try (final var in = SwitzerlandMapLosslessSizeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            return in.readAllBytes();
        }
    }

    private static void assertWebpContainer(final byte[] bytes) {
        assertTrue(bytes.length >= 12);
        assertArrayEquals(
                new byte[] {'R', 'I', 'F', 'F'}, java.util.Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals(
                new byte[] {'W', 'E', 'B', 'P'}, java.util.Arrays.copyOfRange(bytes, 8, 12));
    }

    private static int indexOf(final byte[] haystack, final byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    @Test
    void losslessWebpIsSmallerThanSourcePng() throws Exception {
        final byte[] png = readResource(PNG_RESOURCE);

        final byte[] webp =
                new WebpEncoder()
                        .encodeStillPngToBytes(
                                png,
                                WebpEncodeOptions.builder().lossless(true).method(6).build(),
                                null);

        assertWebpContainer(webp);
        // VP8L is libwebp's lossless bitstream chunk; its presence proves the
        // encoder did not silently fall back to lossy VP8.
        assertTrue(
                indexOf(webp, "VP8L".getBytes(StandardCharsets.US_ASCII)) >= 0,
                "expected VP8L (lossless) chunk in encoded WebP");

        System.out.printf(
                "[switzerland_map] png=%,d bytes  webp(lossless)=%,d bytes  ratio=%.2f%n",
                png.length, webp.length, (double) webp.length / png.length);

        assertTrue(
                webp.length < png.length,
                () ->
                        "expected lossless WebP ("
                                + webp.length
                                + " B) to be smaller than PNG ("
                                + png.length
                                + " B)");
    }
}
