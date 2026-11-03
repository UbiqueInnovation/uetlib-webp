package ch.ubique.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;

class WebpFacadeNativeTest {
    private static final String JPEG_RESOURCE = "/images/fronalpstock_featured.jpg";
    private static final String PNG_RESOURCE = "/images/lossless_animation/frame_001.png";

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    private static BufferedImage loadImage(final String resource) throws Exception {
        try (final var in = WebpFacadeNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            final BufferedImage image = ImageIO.read(in);
            assumeTrue(image != null, "Failed to decode resource " + resource);
            return image;
        }
    }

    private static byte[] readResourceBytes(final String resource) throws Exception {
        try (final var in = WebpFacadeNativeTest.class.getResourceAsStream(resource)) {
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
    void losslessFacadeEncodesPngBytes() throws Exception {
        final byte[] png = readResourceBytes(PNG_RESOURCE);
        final byte[] webp = Webp.lossless().encodePng(png);
        assertWebpContainer(webp);
    }

    @Test
    void lossyFacadeEncodesJpegBytes() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final byte[] webp = Webp.lossy(80.0f).encodeJpeg(jpeg);
        assertWebpContainer(webp);
    }

    @Test
    void facadeEncodesBufferedImage() throws Exception {
        final BufferedImage image = loadImage(JPEG_RESOURCE);
        final byte[] webp = Webp.lossy(75.0f).encode(image);
        assertWebpContainer(webp);
    }

    @Test
    void facadePassesMetadata() throws Exception {
        final byte[] png = readResourceBytes(PNG_RESOURCE);
        final byte[] webp =
                Webp.lossless()
                        .encodePng(
                                png,
                                WebpMetadata.xmp(
                                        "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">demo</x:xmpmeta>"));
        assertWebpContainer(webp);
        assertTrue(indexOf(webp, "XMP ".getBytes(StandardCharsets.US_ASCII)) >= 0);
    }

    @Test
    void withOptionsRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Webp.withOptions(null));
    }
}
