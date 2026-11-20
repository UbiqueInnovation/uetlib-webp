package ch.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Duration;

import javax.imageio.ImageIO;

/**
 * Native integration tests for {@link WebpAnimationStream} — the push-style streaming encoder.
 * Equivalent to {@code WebpAnimationEncoder#encodeJpegFrameBytes} but with O(one decoded frame)
 * peak memory rather than O(all frames in memory at once).
 */
@SuppressWarnings({"resource", "DataFlowIssue"})
class WebpAnimationStreamNativeTest {

    private static final String JPEG_RESOURCE = "/images/fronalpstock_featured.jpg";
    private static final String PNG_RESOURCE_1 = "/images/lossless_animation/frame_001.png";
    private static final String PNG_RESOURCE_2 = "/images/lossless_animation/frame_002.png";

    private static final Duration FIFTY_MILLIS = Duration.ofMillis(50);

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    @Test
    void streamJpegFramesProducesAnimatedWebp() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        final byte[] webp;
        try (final var stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            webp = stream.finish(WebpMetadata.EMPTY);
        }
        assertWebpContainer(webp);
    }

    @Test
    void streamPngFramesProducesAnimatedWebp() throws Exception {
        final var png1 = readResourceBytes(PNG_RESOURCE_1);
        final var png2 = readResourceBytes(PNG_RESOURCE_2);
        final var dim = readDimensions(png1);
        final var encoder = new WebpAnimationEncoder();
        final byte[] webp;
        try (final var stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            stream.addPng(png1, FIFTY_MILLIS);
            stream.addPng(png2, FIFTY_MILLIS);
            webp = stream.finish();
        }
        assertWebpContainer(webp);
    }

    @Test
    void streamFinishMatchesBatchEncodeForSameInputs() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();

        final byte[] streamed;
        try (final var stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            streamed = stream.finish();
        }

        final var batched =
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
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        try (final var stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addPng(jpeg, FIFTY_MILLIS));
        }
    }

    @Test
    void pngStreamRejectsJpegFrames() throws Exception {
        final var png = readResourceBytes(PNG_RESOURCE_1);
        final var dim = readDimensions(png);
        final var encoder = new WebpAnimationEncoder();
        try (final var stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(png, FIFTY_MILLIS));
        }
    }

    @Test
    @SuppressWarnings("java:S5778")
    void streamRejectsEmptyAndInvalidDuration() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        try (final var stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(IllegalArgumentException.class, () -> stream.addJpeg(null, FIFTY_MILLIS));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stream.addJpeg(new byte[0], FIFTY_MILLIS));
            assertThrows(IllegalArgumentException.class, () -> stream.addJpeg(jpeg, Duration.ZERO));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> stream.addJpeg(jpeg, Duration.ofMillis(-1)));
        }
        try (final var stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            assertThrows(IllegalArgumentException.class, () -> stream.addPng(null, FIFTY_MILLIS));
            assertThrows(
                    IllegalArgumentException.class, () -> stream.addPng(new byte[0], FIFTY_MILLIS));
            assertThrows(IllegalArgumentException.class, () -> stream.addPng(jpeg, Duration.ZERO));
        }
    }

    @Test
    void streamRejectsCorruptFrameBytes() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        // Garbage bytes — the native decoder should fail and surface a WebpException
        // with a non-zero status, exercising the streamAddCompressed error-path branch.
        final var garbage = new byte[256];
        java.util.Arrays.fill(garbage, (byte) 0xAB);
        try (final var stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(garbage, FIFTY_MILLIS));
        }
        try (final var stream =
                encoder.openPngStream(
                        dim[0], dim[1], 0, WebpEncodeOptions.builder().lossless(true).build())) {
            assertThrows(WebpException.class, () -> stream.addPng(garbage, FIFTY_MILLIS));
        }
    }

    @Test
    void streamFinishWithoutFramesFails() {
        // Open and immediately finish — no frames added. The native encoder should
        // refuse to produce a WebP, exercising the streamFinish error-path branch.
        final var encoder = new WebpAnimationEncoder();
        try (final var stream = encoder.openJpegStream(64, 64, 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, stream::finish);
        }
    }

    @Test
    void streamRejectsFrameDimensionMismatch() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var encoder = new WebpAnimationEncoder();
        try (final var stream = encoder.openJpegStream(64, 64, 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(jpeg, FIFTY_MILLIS));
        }
    }

    @Test
    void streamRejectsAddAfterFinish() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        try (final var stream =
                encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            stream.addJpeg(jpeg, FIFTY_MILLIS);
            stream.finish();
            assertThrows(IllegalStateException.class, () -> stream.addJpeg(jpeg, FIFTY_MILLIS));
            assertThrows(IllegalStateException.class, stream::finish);
        }
    }

    @Test
    void streamCloseWithoutFinishIsSafe() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        final var stream = encoder.openJpegStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults());
        assertDoesNotThrow(() -> {
            try (stream) {
                stream.addJpeg(jpeg, FIFTY_MILLIS);
                // Closing without finish() aborts the native handle.
            }
        });
        assertDoesNotThrow(stream::close);
        assertThrows(IllegalStateException.class, () -> stream.addJpeg(jpeg, FIFTY_MILLIS));
        assertThrows(IllegalStateException.class, stream::finish);
    }

    @Test
    void streamWebpFramesProducesAnimatedWebp() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        // Produce a still WebP from the JPEG to use as the frame source.
        final var stillEncoder = new WebpEncoder();
        final byte[] stillWebp;
        try (final var encoded =
                stillEncoder.encodeStillJpeg(
                        jpeg, WebpEncodeOptions.defaults(), WebpMetadata.EMPTY)) {
            stillWebp = encoded.toByteArray();
        }
        final var encoder = new WebpAnimationEncoder();
        final byte[] webp;
        try (final var stream =
                encoder.openWebpStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            stream.addWebp(stillWebp, FIFTY_MILLIS);
            stream.addWebp(stillWebp, FIFTY_MILLIS);
            webp = stream.finish(WebpMetadata.EMPTY);
        }
        assertWebpContainer(webp);
    }

    @Test
    void webpStreamRejectsJpegAndPngFrames() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        try (final var stream =
                encoder.openWebpStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addJpeg(jpeg, FIFTY_MILLIS));
            assertThrows(WebpException.class, () -> stream.addPng(jpeg, FIFTY_MILLIS));
        }
    }

    @Test
    void streamWebpRejectsCorruptFrame() throws Exception {
        final var jpeg = readResourceBytes(JPEG_RESOURCE);
        final var dim = readDimensions(jpeg);
        final var encoder = new WebpAnimationEncoder();
        final var garbage = new byte[256];
        java.util.Arrays.fill(garbage, (byte) 0xAB);
        try (final var stream =
                encoder.openWebpStream(dim[0], dim[1], 0, WebpEncodeOptions.defaults())) {
            assertThrows(WebpException.class, () -> stream.addWebp(garbage, FIFTY_MILLIS));
        }
    }

    @Test
    @SuppressWarnings("java:S5778")
    void streamRejectsInvalidArguments() {
        final var encoder = new WebpAnimationEncoder();
        assertThrows(
                IllegalArgumentException.class,
                () -> encoder.openJpegStream(-1, 64, 0, WebpEncodeOptions.defaults()));
    }

    private static int[] readDimensions(final byte[] bytes) throws Exception {
        final var image = ImageIO.read(new ByteArrayInputStream(bytes));
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
