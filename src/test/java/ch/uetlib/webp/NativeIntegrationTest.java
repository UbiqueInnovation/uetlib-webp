package ch.uetlib.webp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

@SuppressWarnings({"resource", "DataFlowIssue"})
class NativeIntegrationTest {
    private static final String IMAGE_1 = "/images/fronalpstock_featured.jpg";
    private static final String IMAGE_2 = "/images/apollo17_blue_marble.jpg";
    private static final String IMAGE_3 = "/images/yosemite_tunnel_view_diliff.jpg";
    private static final String IMAGE_4 = "/images/chicago_night_2.jpg";
    private static final String IMAGE_5 = "/images/milford_sound_nz.jpg";
    private static final List<String> ALL_IMAGES =
            List.of(IMAGE_1, IMAGE_2, IMAGE_3, IMAGE_4, IMAGE_5);

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    private static void copyCropRgba(
            final byte[] src,
            final int srcWidth,
            final MemorySegment dst,
            final int dstWidth,
            final int dstHeight) {
        long p = 0;
        for (int y = 0; y < dstHeight; y++) {
            final int srcRow = y * srcWidth * 4;
            for (int x = 0; x < dstWidth; x++) {
                final int s = srcRow + x * 4;
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p, src[s]);
                p++;
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p, src[s + 1]);
                p++;
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p, src[s + 2]);
                p++;
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p, src[s + 3]);
                p++;
            }
        }
    }

    private static ImageRgba loadImageRgba(final String resourcePath) {
        try (final var in = NativeIntegrationTest.class.getResourceAsStream(resourcePath)) {
            assumeTrue(in != null, "Missing resource " + resourcePath);
            final BufferedImage image = ImageIO.read(in);
            assumeTrue(image != null, "Failed to decode resource " + resourcePath);

            final int width = image.getWidth();
            final int height = image.getHeight();
            final byte[] rgba = new byte[width * height * 4];
            int p = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    final int argb = image.getRGB(x, y);
                    rgba[p] = (byte) ((argb >> 16) & 0xFF);
                    p++;
                    rgba[p] = (byte) ((argb >> 8) & 0xFF);
                    p++;
                    rgba[p] = (byte) (argb & 0xFF);
                    p++;
                    rgba[p] = (byte) ((argb >>> 24) & 0xFF);
                    p++;
                }
            }
            return new ImageRgba(rgba, width, height);
        } catch (final Exception e) {
            throw new RuntimeException("Failed loading test image " + resourcePath, e);
        }
    }

    private static byte[] readResourceBytes(final String resourcePath) throws Exception {
        try (final var in = NativeIntegrationTest.class.getResourceAsStream(resourcePath)) {
            assumeTrue(in != null, "Missing resource " + resourcePath);
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

    private static MemorySegment bytes(final Arena arena, final byte[] input) {
        final MemorySegment out = arena.allocate(input.length);
        MemorySegment.copy(MemorySegment.ofArray(input), 0, out, 0, input.length);
        return out;
    }

    private static Stream<String> allImages() {
        return ALL_IMAGES.stream();
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> animationPairs() {
        final List<org.junit.jupiter.params.provider.Arguments> out = new ArrayList<>();
        for (int i = 0; i < ALL_IMAGES.size(); i++) {
            final String first = ALL_IMAGES.get(i);
            final String second = ALL_IMAGES.get((i + 1) % ALL_IMAGES.size());
            out.add(org.junit.jupiter.params.provider.Arguments.of(first, second));
        }
        return out.stream();
    }

    @ParameterizedTest
    @MethodSource("allImages")
    void stillRawRgbaProducesWebpContainer(final String imagePath) {
        final ImageRgba input = loadImageRgba(imagePath);
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment rgba = arena.allocate(input.pixels.length);
            MemorySegment.copy(
                    MemorySegment.ofArray(input.pixels), 0, rgba, 0, input.pixels.length);

            final WebpRawImage image =
                    new WebpRawImage(
                            rgba, input.width, input.height, input.width * 4, PixelFormat.RGBA);
            final WebpEncoder encoder = new WebpEncoder();
            try (final EncodedWebp encoded =
                    encoder.encodeStillRaw(image, WebpEncodeOptions.defaults(), null)) {
                assertTrue(encoded.size() > 0);
                final byte[] bytes = encoded.toByteArray();
                assertWebpContainer(bytes);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("animationPairs")
    void animationRawRgbaProducesWebpContainerAndCloseIsIdempotent(
            final String first, final String second) {
        final ImageRgba img1 = loadImageRgba(first);
        final ImageRgba img2 = loadImageRgba(second);
        final int width = Math.min(img1.width, img2.width);
        final int height = Math.min(img1.height, img2.height);

        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment frame1 = arena.allocate(width * height * 4L);
            final MemorySegment frame2 = arena.allocate(width * height * 4L);
            copyCropRgba(img1.pixels, img1.width, frame1, width, height);
            copyCropRgba(img2.pixels, img2.width, frame2, width, height);

            final WebpFrame f1 =
                    new WebpFrame(frame1, width, height, width * 4, PixelFormat.RGBA, 50);
            final WebpFrame f2 =
                    new WebpFrame(frame2, width, height, width * 4, PixelFormat.RGBA, 50);

            final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            final EncodedWebp encoded =
                    encoder.encodeRawFrames(
                            List.of(f1, f2), width, height, 0, WebpEncodeOptions.defaults(), null);

            final byte[] bytes = encoded.toByteArray();
            assertTrue(bytes.length > 0);
            assertWebpContainer(bytes);
            assertDoesNotThrow(encoded::close);
            assertDoesNotThrow(encoded::close);
            assertThrows(IllegalStateException.class, encoded::size);
            assertThrows(IllegalStateException.class, encoded::toByteArray);
        }
    }

    @Test
    void stillMetadataIsAttached() {
        final ImageRgba input = loadImageRgba(IMAGE_1);
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment rgba = arena.allocate(input.pixels.length);
            MemorySegment.copy(
                    MemorySegment.ofArray(input.pixels), 0, rgba, 0, input.pixels.length);

            final byte[] exifBytes = "Exif-meta".getBytes(StandardCharsets.US_ASCII);
            final WebpMetadata metadata =
                    new WebpMetadata(
                            bytes(arena, exifBytes), MemorySegment.NULL, MemorySegment.NULL);

            final WebpEncoder encoder = new WebpEncoder();
            try (final EncodedWebp encoded =
                    encoder.encodeStillRaw(
                            new WebpRawImage(
                                    rgba,
                                    input.width,
                                    input.height,
                                    input.width * 4,
                                    PixelFormat.RGBA),
                            WebpEncodeOptions.defaults(),
                            metadata)) {
                final byte[] bytes = encoded.toByteArray();
                assertWebpContainer(bytes);
                assertTrue(
                        indexOf(bytes, new byte[] {'E', 'X', 'I', 'F'}) >= 0,
                        "EXIF chunk marker not found");
            }
        }
    }

    @Test
    void stillJpegBytesEntryPointProducesWebpContainer() throws Exception {
        final byte[] jpeg = readResourceBytes(IMAGE_1);
        final WebpEncoder encoder = new WebpEncoder();
        try (final EncodedWebp encoded =
                encoder.encodeStillJpeg(jpeg, WebpEncodeOptions.defaults(), null)) {
            final byte[] bytes = encoded.toByteArray();
            assertWebpContainer(bytes);
        }

        assertThrows(
                WebpException.class,
                () ->
                        encoder.encodeStillJpeg(
                                new byte[] {1, 2, 3, 4}, WebpEncodeOptions.defaults(), null));
    }

    @Test
    void stillPngBytesEntryPointProducesWebpContainer() throws Exception {
        final byte[] png = readResourceBytes("/images/lossless_animation/frame_001.png");
        final WebpEncoder encoder = new WebpEncoder();
        try (final EncodedWebp encoded =
                encoder.encodeStillPng(
                        png, WebpEncodeOptions.builder().lossless(true).build(), null)) {
            final byte[] bytes = encoded.toByteArray();
            assertWebpContainer(bytes);
        }

        assertThrows(
                WebpException.class,
                () ->
                        encoder.encodeStillPng(
                                new byte[] {1, 2, 3, 4}, WebpEncodeOptions.defaults(), null));
    }

    @Test
    void nativeJpegAnimationProducesWebpContainer() throws Exception {
        try (final Arena arena = Arena.ofConfined()) {
            final WebpCompressedFrame f1 =
                    new WebpCompressedFrame(bytes(arena, readResourceBytes(IMAGE_1)), 50);
            final WebpCompressedFrame f2 =
                    new WebpCompressedFrame(bytes(arena, readResourceBytes(IMAGE_1)), 50);

            final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            try (final EncodedWebp encoded =
                    encoder.encodeJpegFrames(
                            List.of(f1, f2),
                            0,
                            0,
                            0,
                            WebpEncodeOptions.defaults(),
                            WebpMetadata.EMPTY)) {
                final byte[] bytes = encoded.toByteArray();
                assertTrue(bytes.length > 0);
                assertWebpContainer(bytes);
            }
        }
    }

    @Test
    void nativePngAnimationProducesWebpContainer() throws Exception {
        try (final Arena arena = Arena.ofConfined()) {
            final WebpCompressedFrame f1 =
                    new WebpCompressedFrame(
                            bytes(
                                    arena,
                                    readResourceBytes("/images/lossless_animation/frame_001.png")),
                            50);
            final WebpCompressedFrame f2 =
                    new WebpCompressedFrame(
                            bytes(
                                    arena,
                                    readResourceBytes("/images/lossless_animation/frame_002.png")),
                            50);

            final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            try (final EncodedWebp encoded =
                    encoder.encodePngFrames(
                            List.of(f1, f2),
                            0,
                            0,
                            0,
                            WebpEncodeOptions.builder().lossless(true).build(),
                            WebpMetadata.EMPTY)) {
                final byte[] bytes = encoded.toByteArray();
                assertTrue(bytes.length > 0);
                assertWebpContainer(bytes);
            }
        }
    }

    @Test
    void animationWithCanvasMismatchFailsCleanly() {
        final ImageRgba img1 = loadImageRgba(IMAGE_1);
        try (final Arena arena = Arena.ofConfined()) {
            final int width = Math.min(16, img1.width);
            final int height = Math.min(16, img1.height);
            final MemorySegment frame = arena.allocate(width * height * 4L);
            copyCropRgba(img1.pixels, img1.width, frame, width, height);
            final WebpFrame f1 =
                    new WebpFrame(frame, width, height, width * 4, PixelFormat.RGBA, 50);

            final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            assertThrows(
                    WebpException.class,
                    () ->
                            encoder.encodeRawFrames(
                                    List.of(f1),
                                    width + 1,
                                    height + 1,
                                    0,
                                    WebpEncodeOptions.defaults(),
                                    WebpMetadata.EMPTY));
        }
    }

    @Test
    void stillWithInvalidStrideFailsCleanly() {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment rgba = arena.allocate(16 * 16 * 4L);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new WebpRawImage(rgba, 16, 16, 1, PixelFormat.RGBA));
        }
    }

    @Test
    void nativeJpegAnimationWithInvalidDataFailsCleanly() {
        try (final Arena arena = Arena.ofConfined()) {
            final WebpCompressedFrame frame =
                    new WebpCompressedFrame(bytes(arena, new byte[] {1, 2, 3, 4}), 50);

            final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            assertThrows(
                    WebpException.class,
                    () ->
                            encoder.encodeJpegFrames(
                                    List.of(frame),
                                    0,
                                    0,
                                    0,
                                    WebpEncodeOptions.defaults(),
                                    WebpMetadata.EMPTY));
        }
    }

    @Test
    void nativePngAnimationWithInvalidDataFailsCleanly() {
        try (final Arena arena = Arena.ofConfined()) {
            final WebpCompressedFrame frame =
                    new WebpCompressedFrame(bytes(arena, new byte[] {1, 2, 3, 4}), 50);

            final WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            assertThrows(
                    WebpException.class,
                    () ->
                            encoder.encodePngFrames(
                                    List.of(frame),
                                    0,
                                    0,
                                    0,
                                    WebpEncodeOptions.defaults(),
                                    WebpMetadata.EMPTY));
        }
    }

    private record ImageRgba(byte[] pixels, int width, int height) {}
}
