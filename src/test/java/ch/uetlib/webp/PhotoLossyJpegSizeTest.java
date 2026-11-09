package ch.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Encodes real-world JPEG photos as lossy WebP and prints the JPEG vs WebP size ratio. Asserts that
 * the WebP output is smaller than the source JPEG — for photographic content at quality 75 the WebP
 * encoder is generally expected to beat JPEG.
 */
class PhotoLossyJpegSizeTest {
    private static final float WEBP_QUALITY = 75.0f;

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    static Stream<String> photos() {
        return Stream.of(
                "/images/fronalpstock_featured.jpg",
                "/images/yosemite_tunnel_view_diliff.jpg",
                "/images/milford_sound_nz.jpg");
    }

    private static byte[] readResource(final String resource) throws Exception {
        try (final var in = PhotoLossyJpegSizeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            return in.readAllBytes();
        }
    }

    @ParameterizedTest
    @MethodSource("photos")
    void lossyWebpIsSmallerThanSourceJpeg(final String resource) throws Exception {
        final byte[] jpeg = readResource(resource);

        final byte[] webp =
                new WebpEncoder()
                        .encodeStillJpegToBytes(
                                jpeg,
                                WebpEncodeOptions.builder().quality(WEBP_QUALITY).method(4).build(),
                                null);

        assertWebpContainer(webp);

        System.out.printf(
                "[%s] jpeg=%,d bytes  webp(q=%.0f)=%,d bytes  ratio=%.2f%n",
                resource.substring(resource.lastIndexOf('/') + 1),
                jpeg.length,
                WEBP_QUALITY,
                webp.length,
                (double) webp.length / jpeg.length);

        assertTrue(
                webp.length < jpeg.length,
                () ->
                        "expected lossy WebP ("
                                + webp.length
                                + " B) to be smaller than JPEG ("
                                + jpeg.length
                                + " B) for "
                                + resource);
    }

    private static void assertWebpContainer(final byte[] bytes) {
        assertTrue(bytes.length >= 12);
        assertArrayEquals(
                new byte[] {'R', 'I', 'F', 'F'}, java.util.Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals(
                new byte[] {'W', 'E', 'B', 'P'}, java.util.Arrays.copyOfRange(bytes, 8, 12));
    }
}
