package ch.ubique.uetlib.image.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Native integration tests for {@link WebpAnimationStream} — the push-style streaming encoder.
 * Equivalent to {@code WebpAnimationEncoder#encodeJpegFrameBytes} but with O(one decoded frame)
 * peak memory rather than O(all frames in memory at once).
 */
@SuppressWarnings("resource")
class WebpAnimationStreamNativeTest {
    private static final String JPEG_RESOURCE = "/images/fronalpstock_featured.jpg";
    private static final String PNG_RESOURCE_1 = "/images/lossless_animation/frame_001.png";
    private static final String PNG_RESOURCE_2 = "/images/lossless_animation/frame_002.png";

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    @Test
    void streamJpegFramesProducesAnimatedWebp() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final byte[] webp;
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addJpeg(jpeg, 50);
            stream.addJpeg(jpeg, 50);
            stream.addJpeg(jpeg, 50);
            webp = stream.finish(WebpMetadata.EMPTY);
        }
        assertWebpContainer(webp);
    }

    @Test
    void streamPngFramesProducesAnimatedWebp() throws Exception {
        final byte[] png1 = readResourceBytes(PNG_RESOURCE_1);
        final byte[] png2 = readResourceBytes(PNG_RESOURCE_2);
        final int[] dim = readDimensions(png1);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final byte[] webp;
        try (final WebpAnimationStream stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            stream.addPng(png1, 50);
            stream.addPng(png2, 50);
            webp = stream.finish();
        }
        assertWebpContainer(webp);
    }

    @Test
    void streamFinishMatchesBatchEncodeForSameInputs() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();

        final byte[] streamed;
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addJpeg(jpeg, 50);
            stream.addJpeg(jpeg, 50);
            streamed = stream.finish();
        }

        final byte[] batched =
                encoder.encodeJpegFrameBytes(
                        java.util.List.of(
                                new WebpCompressedFrameBytes(jpeg, 50),
                                new WebpCompressedFrameBytes(jpeg, 50)),
                        dim[0],
                        dim[1],
                        0,
                        WebpEncodeOptions.defaults(),
                        WebpMetadata.EMPTY);

        // Both paths run libwebp with identical config and identical input, so the
        // assembled bytes must be byte-identical.
        assertArrayEquals(
                batched, streamed, "streamed output should be byte-identical to batched output");
    }

    @Test
    void streamRejectsMixedFrameTypes() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addPng(jpeg, 50));
        }
    }

    @Test
    void pngStreamRejectsJpegFrames() throws Exception {
        final byte[] png = readResourceBytes(PNG_RESOURCE_1);
        final int[] dim = readDimensions(png);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        try (final WebpAnimationStream stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(png, 50));
        }
    }

    @Test
    void streamRejectsEmptyAndInvalidDuration() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(IllegalArgumentException.class, () -> stream.addJpeg(null, 50));
            assertThrows(IllegalArgumentException.class, () -> stream.addJpeg(new byte[0], 50));
            assertThrows(IllegalArgumentException.class, () -> stream.addJpeg(jpeg, 0));
            assertThrows(IllegalArgumentException.class, () -> stream.addJpeg(jpeg, -1));
        }
        try (final WebpAnimationStream stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            assertThrows(IllegalArgumentException.class, () -> stream.addPng(null, 50));
            assertThrows(IllegalArgumentException.class, () -> stream.addPng(new byte[0], 50));
            assertThrows(IllegalArgumentException.class, () -> stream.addPng(jpeg, 0));
        }
    }

    @Test
    void streamRejectsCorruptFrameBytes() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        // Garbage bytes — the native decoder should fail and surface a WebpException
        // with a non-zero status, exercising the streamAddCompressed error-path branch.
        final byte[] garbage = new byte[256];
        java.util.Arrays.fill(garbage, (byte) 0xAB);
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(garbage, 50));
        }
        try (final WebpAnimationStream stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            assertThrows(WebpException.class, () -> stream.addPng(garbage, 50));
        }
    }

    @Test
    void streamFinishWithoutFramesFails() throws Exception {
        // Open and immediately finish — no frames added. The native encoder should
        // refuse to produce a WebP, exercising the streamFinish error-path branch.
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final WebpAnimationStream stream =
                encoder.openJpegStream(64, 64, 0, WebpEncodeOptions.defaults());
        try {
            assertThrows(WebpException.class, stream::finish);
        } finally {
            stream.close();
        }
    }

    @Test
    void streamRejectsFrameDimensionMismatch() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(64, 64, 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(jpeg, 50));
        }
    }

    @Test
    void streamRejectsAddAfterFinish() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults());
        try {
            stream.addJpeg(jpeg, 50);
            stream.addJpeg(jpeg, 50);
            stream.finish();
            assertThrows(IllegalStateException.class, () -> stream.addJpeg(jpeg, 50));
            assertThrows(IllegalStateException.class, stream::finish);
        } finally {
            stream.close(); // idempotent
        }
    }

    @Test
    void streamCloseWithoutFinishIsSafe() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        try (final WebpAnimationStream stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addJpeg(jpeg, 50);
            // intentionally drop without finish() — close() should abort the native handle
        }
    }

    @Test
    void streamWebpFramesProducesAnimatedWebp() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        // Produce a still WebP from the JPEG to use as the frame source.
        final WebpEncoder stillEncoder = new WebpEncoder();
        final byte[] stillWebp;
        try (final EncodedWebp encoded =
                stillEncoder.encodeStillJpeg(
                        jpeg, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY)) {
            stillWebp = encoded.toByteArray();
        }
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final byte[] webp;
        try (final WebpAnimationStream stream =
                encoder.openWebpStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addWebp(stillWebp, 50);
            stream.addWebp(stillWebp, 50);
            webp = stream.finish(WebpMetadata.EMPTY);
        }
        assertWebpContainer(webp);
    }

    @Test
    void webpStreamRejectsJpegAndPngFrames() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        try (final WebpAnimationStream stream =
                encoder.openWebpStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(jpeg, 50));
            assertThrows(WebpException.class, () -> stream.addPng(jpeg, 50));
        }
    }

    @Test
    void streamWebpRejectsCorruptFrame() throws Exception {
        final byte[] jpeg = readResourceBytes(JPEG_RESOURCE);
        final int[] dim = readDimensions(jpeg);
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        final byte[] garbage = new byte[256];
        java.util.Arrays.fill(garbage, (byte) 0xAB);
        try (final WebpAnimationStream stream =
                encoder.openWebpStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addWebp(garbage, 50));
        }
    }

    @Test
    void streamRejectsInvalidArguments() {
        final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
        assertThrows(
                IllegalArgumentException.class,
                () -> encoder.openJpegStream(-1, 64, 0, WebpEncodeOptions.defaults()));
    }

    private static int[] readDimensions(final byte[] bytes) throws Exception {
        final BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        return new int[] {image.getWidth(), image.getHeight()};
    }

    private static byte[] readResourceBytes(final String resource) throws Exception {
        try (final var in = WebpAnimationStreamNativeTest.class.getResourceAsStream(resource)) {
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
}
