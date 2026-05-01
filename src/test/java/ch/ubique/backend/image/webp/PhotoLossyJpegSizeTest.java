package ch.ubique.backend.image.webp;

import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Encodes real-world JPEG photos as lossy WebP and prints the JPEG vs WebP
 * size ratio. Asserts that the WebP output is smaller than the source JPEG —
 * for photographic content at quality 75 the WebP encoder is generally expected
 * to beat JPEG.
 */
class PhotoLossyJpegSizeTest {
    private static final float WEBP_QUALITY = 75f;

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    static Stream<String> photos() {
        return Stream.of(
            "/images/rapeseed_field.jpg",
            "/images/alpine_brook.jpg",
            "/images/alpine_lake.jpg");
    }

    @ParameterizedTest
    @MethodSource("photos")
    void lossyWebpIsSmallerThanSourceJpeg(String resource) throws Exception {
        byte[] jpeg = readResource(resource);

        byte[] webp = new WebpEncoder().encodeStillJpegToBytes(
            jpeg,
            WebpEncodeOptions.builder().quality(WEBP_QUALITY).method(4).build(),
            null);

        assertWebpContainer(webp);

        System.out.printf("[%s] jpeg=%,d bytes  webp(q=%.0f)=%,d bytes  ratio=%.2f%n",
            resource.substring(resource.lastIndexOf('/') + 1),
            jpeg.length, WEBP_QUALITY, webp.length, (double) webp.length / jpeg.length);

        assertTrue(webp.length < jpeg.length,
            () -> "expected lossy WebP (" + webp.length + " B) to be smaller than JPEG ("
                + jpeg.length + " B) for " + resource);
    }

    private static byte[] readResource(String resource) throws Exception {
        try (var in = PhotoLossyJpegSizeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            return in.readAllBytes();
        }
    }

    private static void assertWebpContainer(byte[] bytes) {
        assertTrue(bytes.length >= 12);
        assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'},
            java.util.Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals(new byte[]{'W', 'E', 'B', 'P'},
            java.util.Arrays.copyOfRange(bytes, 8, 12));
    }
}
