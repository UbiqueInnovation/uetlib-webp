package ch.ubique.backend.image.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Regression test for CMYK / YCCK JPEGs (4-component, Adobe APP14).
 *
 * <p>The TurboJPEG decode path ({@code tjDecompress2} -> {@code TJPF_RGB}) cannot perform the
 * CMYK->RGB conversion and fails with a decode error, so such JPEGs could not be encoded to WebP.
 */
class CmykJpegNativeTest {
    private static final String CMYK_JPEG_RESOURCE = "/images/cmyk_quadrants.jpg";

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    @Test
    void encodesCmykJpeg() throws Exception {
        byte[] jpeg = readResourceBytes(CMYK_JPEG_RESOURCE);
        byte[] webp = Webp.lossy(80f).encodeJpeg(jpeg);
        assertWebpContainer(webp);
    }

    private static byte[] readResourceBytes(String resource) throws Exception {
        try (var in = CmykJpegNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            return in.readAllBytes();
        }
    }

    private static void assertWebpContainer(byte[] bytes) {
        assertTrue(bytes.length >= 12);
        assertArrayEquals(
                new byte[] {'R', 'I', 'F', 'F'}, java.util.Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals(
                new byte[] {'W', 'E', 'B', 'P'}, java.util.Arrays.copyOfRange(bytes, 8, 12));
    }
}
