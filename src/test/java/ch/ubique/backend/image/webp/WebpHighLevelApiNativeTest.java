package ch.ubique.backend.image.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import ch.ubique.backend.image.webp.awt.WebpBufferedImageAnimationEncoder;
import ch.ubique.backend.image.webp.awt.WebpBufferedImageEncoder;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Native integration tests for the heap-friendly encoder overloads added on top of the {@link
 * MemorySegment}-based API. These mirror {@code NativeIntegrationTest} but drive the higher-level
 * entry points ({@link WebpBufferedImageEncoder#encode(BufferedImage, WebpEncodeOptions,
 * WebpMetadata)}, {@link WebpEncoder#encodeStillJpegToBytes(byte[], WebpEncodeOptions,
 * WebpMetadata)}, {@link WebpEncoder#encodeStillPngToBytes(byte[], WebpEncodeOptions,
 * WebpMetadata)}, {@link WebpBufferedImageAnimationEncoder#encodeFrames}, {@link
 * WebpAnimationEncoder#encodeJpegFrameBytes}, {@link WebpAnimationEncoder#encodePngFrameBytes}).
 */
@SuppressWarnings("DataFlowIssue")
class WebpHighLevelApiNativeTest {
    private static final String JPEG_RESOURCE = "/images/fronalpstock_featured.jpg";
    private static final String PNG_RESOURCE_1 = "/images/lossless_animation/frame_001.png";
    private static final String PNG_RESOURCE_2 = "/images/lossless_animation/frame_002.png";

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    private static BufferedImage loadImage(final String resource) throws Exception {
        try (final var in = WebpHighLevelApiNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            final BufferedImage image = ImageIO.read(in);
            assumeTrue(image != null, "Failed to decode resource " + resource);
            return image;
        }
    }

    private static byte[] readResourceBytes(final String resource) throws Exception {
        try (final var in = WebpHighLevelApiNativeTest.class.getResourceAsStream(resource)) {
            assumeTrue(in != null, "Missing resource " + resource);
            return in.readAllBytes();
        }
    }

    private static BufferedImage cropped(
            final BufferedImage src, final int width, final int height) {
        final BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
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
    void encodeStillBufferedImageReturnsHeapWebp() throws Exception {
        final BufferedImage img = loadImage(JPEG_RESOURCE);
        final WebpBufferedImageEncoder encoder = new WebpBufferedImageEncoder();

        final byte[] bytes = encoder.encode(img, WebpEncodeOptions.defaults(), null);
        assertWebpContainer(bytes);
    }

    @Test
    void encodeStillBufferedImageEmbedsHeapMetadataInOneCall() throws Exception {
        final BufferedImage img = loadImage(JPEG_RESOURCE);
        final WebpBufferedImageEncoder encoder = new WebpBufferedImageEncoder();

        final String xmp = "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"/>";
        final byte[] exif = "Exif-sample".getBytes(StandardCharsets.US_ASCII);
        final WebpMetadata metadata = WebpMetadata.builder().xmp(xmp).exif(exif).build();

        final byte[] bytes = encoder.encode(img, WebpEncodeOptions.defaults(), metadata);
        assertWebpContainer(bytes);
        assertTrue(
                indexOf(bytes, "EXIF".getBytes(StandardCharsets.US_ASCII)) >= 0,
                "EXIF chunk marker not found");
        assertTrue(
                indexOf(bytes, "XMP ".getBytes(StandardCharsets.US_ASCII)) >= 0,
                "XMP chunk marker not found");
    }

    @Test
    void encodeStillJpegToBytesDecodesAndEncodes() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final WebpEncoder encoder = new WebpEncoder();
        final byte[] webp =
                encoder.encodeStillJpegToBytes(jpeg, WebpEncodeOptions.defaults(), null);
        assertWebpContainer(webp);
    }

    @Test
    void encodeStillPngToBytesDecodesAndEncodes() throws Exception {
        final byte[] png = readResourceBytes(PNG_RESOURCE_1);
        final WebpEncoder encoder = new WebpEncoder();
        final byte[] webp =
                encoder.encodeStillPngToBytes(
                        png, WebpEncodeOptions.builder().lossless(true).build(), null);
        assertWebpContainer(webp);
    }

    @Test
    void encodeBufferedImageFramesProducesAnimatedWebp() throws Exception {
        final BufferedImage f1 = loadImage(PNG_RESOURCE_1);
        final BufferedImage f2 = loadImage(PNG_RESOURCE_2);
        final int w = Math.min(f1.getWidth(), f2.getWidth());
        final int h = Math.min(f1.getHeight(), f2.getHeight());

        final WebpBufferedImageAnimationEncoder encoder = new WebpBufferedImageAnimationEncoder();
        final byte[] bytes =
                encoder.encodeFrames(
                        List.of(cropped(f1, w, h), cropped(f2, w, h)),
                        List.of(40, 40),
                        w,
                        h,
                        0,
                        WebpEncodeOptions.defaults(),
                        null);
        assertWebpContainer(bytes);
    }

    @Test
    void encodeJpegFrameBytesProducesAnimatedWebp() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final byte[] bytes =
                encoder.encodeJpegFrameBytes(
                        List.of(
                                new WebpCompressedFrameBytes(jpeg, 50),
                                new WebpCompressedFrameBytes(jpeg, 50)),
                        0,
                        0,
                        0,
                        WebpEncodeOptions.defaults(),
                        WebpMetadata.EMPTY);
        assertWebpContainer(bytes);
    }

    @Test
    void encodePngFrameBytesProducesAnimatedWebp() throws Exception {
        final byte[] png1 = readResourceBytes(PNG_RESOURCE_1);
        final byte[] png2 = readResourceBytes(PNG_RESOURCE_2);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final byte[] bytes =
                encoder.encodePngFrameBytes(
                        List.of(
                                new WebpCompressedFrameBytes(png1, 50),
                                new WebpCompressedFrameBytes(png2, 50)),
                        0,
                        0,
                        0,
                        WebpEncodeOptions.builder().lossless(true).build(),
                        WebpMetadata.EMPTY);
        assertWebpContainer(bytes);
    }

    @Test
    void invalidJpegFrameBytesSurfaceAsWebpException() {
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(
                WebpException.class,
                () ->
                        encoder.encodeJpegFrameBytes(
                                List.of(new WebpCompressedFrameBytes(new byte[] {1, 2, 3, 4}, 50)),
                                0,
                                0,
                                0,
                                WebpEncodeOptions.defaults(),
                                WebpMetadata.EMPTY));
    }
}
