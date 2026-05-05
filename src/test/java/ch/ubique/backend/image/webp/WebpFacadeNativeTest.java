package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class WebpFacadeNativeTest {
    private static final String JPEG_RESOURCE = "/images/fronalpstock_featured.jpg";
    private static final String PNG_RESOURCE = "/images/lossless_animation/frame_001.png";

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    @Test
    void losslessFacadeEncodesPngBytes() throws Exception {
        byte[] png = readResourceBytes(PNG_RESOURCE);
        byte[] webp = Webp.lossless().encodePng(png);
        assertWebpContainer(webp);
    }

    @Test
    void lossyFacadeEncodesJpegBytes() throws Exception {
        byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        byte[] webp = Webp.lossy(80f).encodeJpeg(jpeg);
        assertWebpContainer(webp);
    }

    @Test
    void facadeEncodesBufferedImage() throws Exception {
        BufferedImage image = loadImage(JPEG_RESOURCE);
        byte[] webp = Webp.lossy(75f).encode(image);
        assertWebpContainer(webp);
    }

    @Test
    void facadePassesMetadata() throws Exception {
        byte[] png = readResourceBytes(PNG_RESOURCE);
        byte[] webp = Webp.lossless().encodePng(
            png,
            WebpMetadata.xmp("<x:xmpmeta xmlns:x=\"adobe:ns:meta/\">demo</x:xmpmeta>")
        );
        assertWebpContainer(webp);
        assertTrue(indexOf(webp, "XMP ".getBytes(StandardCharsets.US_ASCII)) >= 0);
    }

    @Test
    void withOptionsRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> Webp.withOptions(null));
    }

    private static BufferedImage loadImage(String resource) throws Exception {
        try (var in = WebpFacadeNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            BufferedImage image = ImageIO.read(in);
            assumeTrue(image != null, "Failed to decode resource " + resource);
            return image;
        }
    }

    private static byte[] readResourceBytes(String resource) throws Exception {
        try (var in = WebpFacadeNativeTest.class.getResourceAsStream(resource)) {
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

    private static int indexOf(byte[] haystack, byte[] needle) {
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
}
