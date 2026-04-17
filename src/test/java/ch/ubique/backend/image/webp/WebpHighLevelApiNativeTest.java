package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Native integration tests for the heap-friendly encoder overloads added on top of the
 * {@link MemorySegment}-based API. These mirror {@code NativeIntegrationTest} but drive the
 * higher-level entry points ({@link WebpEncoder#encodeStill(BufferedImage, WebpEncodeOptions, WebpMetadata)},
 * {@link WebpEncoder#encodeStillJpegToBytes(byte[], WebpEncodeOptions, WebpMetadata)},
 * {@link WebpEncoder#encodeStillPngToBytes(byte[], WebpEncodeOptions, WebpMetadata)},
 * {@link WebpAnimationEncoder#encodeBufferedImageFrames},
 * {@link WebpAnimationEncoder#encodeJpegFrameBytes},
 * {@link WebpAnimationEncoder#encodePngFrameBytes}).
 */
class WebpHighLevelApiNativeTest {
    private static final String JPEG_RESOURCE = "/images/fronalpstock_featured.jpg";
    private static final String PNG_RESOURCE_1 = "/images/lossless_animation/frame_001.png";
    private static final String PNG_RESOURCE_2 = "/images/lossless_animation/frame_002.png";

    @BeforeAll
    static void configureNativePath() {
        String explicit = System.getProperty("uetlib.webp.native.path");
        if (explicit != null && !explicit.isBlank()) {
            assumeTrue(Files.exists(Path.of(explicit)),
                "Native library path is missing: " + explicit);
            return;
        }
        String libName = System.getProperty("os.name", "").toLowerCase().contains("mac")
            ? "libuetlib_webp_native.dylib"
            : "libuetlib_webp_native.so";
        Path defaultPath = Path.of("native", "build", libName).toAbsolutePath();
        assumeTrue(Files.exists(defaultPath), "Build native library first at " + defaultPath);
        System.setProperty("uetlib.webp.native.path", defaultPath.toString());
    }

    @Test
    void encodeStillBufferedImageReturnsHeapWebp() throws Exception {
        BufferedImage img = loadImage(JPEG_RESOURCE);
        WebpEncoder encoder = new WebpEncoder();

        byte[] bytes = encoder.encodeStill(img, WebpEncodeOptions.defaults(), null);
        assertWebpContainer(bytes);
    }

    @Test
    void encodeStillBufferedImageEmbedsHeapMetadataInOneCall() throws Exception {
        BufferedImage img = loadImage(JPEG_RESOURCE);
        WebpEncoder encoder = new WebpEncoder();

        String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>";
        byte[] exif = "Exif-sample".getBytes(StandardCharsets.US_ASCII);
        WebpMetadata metadata = WebpMetadata.builder()
            .xmp(xmp)
            .exif(exif)
            .build();

        byte[] bytes = encoder.encodeStill(img, WebpEncodeOptions.defaults(), metadata);
        assertWebpContainer(bytes);
        assertTrue(indexOf(bytes, "EXIF".getBytes(StandardCharsets.US_ASCII)) >= 0,
            "EXIF chunk marker not found");
        assertTrue(indexOf(bytes, "XMP ".getBytes(StandardCharsets.US_ASCII)) >= 0,
            "XMP chunk marker not found");
    }

    @Test
    void encodeStillJpegToBytesDecodesAndEncodes() throws Exception {
        byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        WebpEncoder encoder = new WebpEncoder();
        byte[] webp = encoder.encodeStillJpegToBytes(jpeg, WebpEncodeOptions.defaults(), null);
        assertWebpContainer(webp);
    }

    @Test
    void encodeStillPngToBytesDecodesAndEncodes() throws Exception {
        byte[] png = readResourceBytes(PNG_RESOURCE_1);
        WebpEncoder encoder = new WebpEncoder();
        byte[] webp = encoder.encodeStillPngToBytes(png,
            WebpEncodeOptions.builder().lossless(true).build(), null);
        assertWebpContainer(webp);
    }

    @Test
    void encodeBufferedImageFramesProducesAnimatedWebp() throws Exception {
        BufferedImage f1 = loadImage(PNG_RESOURCE_1);
        BufferedImage f2 = loadImage(PNG_RESOURCE_2);
        int w = Math.min(f1.getWidth(), f2.getWidth());
        int h = Math.min(f1.getHeight(), f2.getHeight());

        WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        byte[] bytes = encoder.encodeBufferedImageFrames(
            List.of(cropped(f1, w, h), cropped(f2, w, h)),
            List.of(40, 40),
            w, h, 0,
            WebpEncodeOptions.defaults(),
            null);
        assertWebpContainer(bytes);
    }

    @Test
    void encodeJpegFrameBytesProducesAnimatedWebp() throws Exception {
        byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        byte[] bytes = encoder.encodeJpegFrameBytes(
            List.of(
                new WebpCompressedFrameBytes(jpeg, 50),
                new WebpCompressedFrameBytes(jpeg, 50)),
            0, 0, 0,
            WebpEncodeOptions.defaults(),
            WebpMetadata.EMPTY);
        assertWebpContainer(bytes);
    }

    @Test
    void encodePngFrameBytesProducesAnimatedWebp() throws Exception {
        byte[] png1 = readResourceBytes(PNG_RESOURCE_1);
        byte[] png2 = readResourceBytes(PNG_RESOURCE_2);
        WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        byte[] bytes = encoder.encodePngFrameBytes(
            List.of(
                new WebpCompressedFrameBytes(png1, 50),
                new WebpCompressedFrameBytes(png2, 50)),
            0, 0, 0,
            WebpEncodeOptions.builder().lossless(true).build(),
            WebpMetadata.EMPTY);
        assertWebpContainer(bytes);
    }

    @Test
    void invalidJpegFrameBytesSurfaceAsWebpException() {
        WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(WebpException.class, () -> encoder.encodeJpegFrameBytes(
            List.of(new WebpCompressedFrameBytes(new byte[]{1, 2, 3, 4}, 50)),
            0, 0, 0,
            WebpEncodeOptions.defaults(),
            WebpMetadata.EMPTY));
    }

    private static BufferedImage loadImage(String resource) throws Exception {
        try (var in = WebpHighLevelApiNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            BufferedImage image = ImageIO.read(in);
            assumeTrue(image != null, "Failed to decode resource " + resource);
            return image;
        }
    }

    private static byte[] readResourceBytes(String resource) throws Exception {
        try (var in = WebpHighLevelApiNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            return in.readAllBytes();
        }
    }

    private static BufferedImage cropped(BufferedImage src, int width, int height) {
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
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
