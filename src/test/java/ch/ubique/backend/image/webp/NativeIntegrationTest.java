package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeIntegrationTest {
    private static final String IMAGE_1 = "/images/fronalpstock_featured.jpg";
    private static final String IMAGE_2 = "/images/apollo17_blue_marble.jpg";
    private static final String IMAGE_3 = "/images/yosemite_tunnel_view_diliff.jpg";
    private static final String IMAGE_4 = "/images/chicago_night_2.jpg";
    private static final String IMAGE_5 = "/images/milford_sound_nz.jpg";
    private static final List<String> ALL_IMAGES = List.of(IMAGE_1, IMAGE_2, IMAGE_3, IMAGE_4, IMAGE_5);

    @BeforeAll
    static void configureNativePath() {
        NativeTestSupport.configureNativePath();
    }

    @ParameterizedTest
    @MethodSource("allImages")
    void stillRawRgbaProducesWebpContainer(String imagePath) {
        ImageRgba input = loadImageRgba(imagePath);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rgba = arena.allocate(input.pixels.length);
            MemorySegment.copy(MemorySegment.ofArray(input.pixels), 0, rgba, 0, input.pixels.length);

            WebpRawImage image = new WebpRawImage(rgba, input.width, input.height, input.width * 4, PixelFormat.RGBA);
            WebpEncoder encoder = new WebpEncoder();
            try (EncodedWebp encoded = encoder.encodeStillRaw(image, WebpEncodeOptions.defaults(), null)) {
                assertTrue(encoded.size() > 0);
                byte[] bytes = encoded.toByteArray();
                assertWebpContainer(bytes);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("animationPairs")
    void animationRawRgbaProducesWebpContainerAndCloseIsIdempotent(String first, String second) {
        ImageRgba img1 = loadImageRgba(first);
        ImageRgba img2 = loadImageRgba(second);
        int width = Math.min(img1.width, img2.width);
        int height = Math.min(img1.height, img2.height);

        try (Arena arena = Arena.ofConfined()) {
            MemorySegment frame1 = arena.allocate(width * height * 4L);
            MemorySegment frame2 = arena.allocate(width * height * 4L);
            copyCropRgba(img1.pixels, img1.width, frame1, width, height);
            copyCropRgba(img2.pixels, img2.width, frame2, width, height);

            WebpFrame f1 = new WebpFrame(frame1, width, height, width * 4, PixelFormat.RGBA, 50);
            WebpFrame f2 = new WebpFrame(frame2, width, height, width * 4, PixelFormat.RGBA, 50);

            WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            EncodedWebp encoded = encoder.encodeRawFrames(
                List.of(f1, f2),
                width,
                height,
                0,
                WebpEncodeOptions.defaults(),
                null);

            byte[] bytes = encoded.toByteArray();
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
        ImageRgba input = loadImageRgba(IMAGE_1);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rgba = arena.allocate(input.pixels.length);
            MemorySegment.copy(MemorySegment.ofArray(input.pixels), 0, rgba, 0, input.pixels.length);

            byte[] exifBytes = "Exif-meta".getBytes(StandardCharsets.US_ASCII);
            WebpMetadata metadata = new WebpMetadata(bytes(arena, exifBytes), MemorySegment.NULL, MemorySegment.NULL);

            WebpEncoder encoder = new WebpEncoder();
            try (EncodedWebp encoded = encoder.encodeStillRaw(
                new WebpRawImage(rgba, input.width, input.height, input.width * 4, PixelFormat.RGBA),
                WebpEncodeOptions.defaults(),
                metadata)) {
                byte[] bytes = encoded.toByteArray();
                assertWebpContainer(bytes);
                assertTrue(indexOf(bytes, new byte[]{'E', 'X', 'I', 'F'}) >= 0, "EXIF chunk marker not found");
            }
        }
    }

    @Test
    void stillJpegBytesEntryPointProducesWebpContainer() throws Exception {
        byte[] jpeg = readResourceBytes(IMAGE_1);
        WebpEncoder encoder = new WebpEncoder();
        try (EncodedWebp encoded = encoder.encodeStillJpeg(jpeg, WebpEncodeOptions.defaults(), null)) {
            byte[] bytes = encoded.toByteArray();
            assertWebpContainer(bytes);
        }

        assertThrows(WebpException.class, () -> encoder.encodeStillJpeg(
            new byte[]{1, 2, 3, 4}, WebpEncodeOptions.defaults(), null));
    }

    @Test
    void stillPngBytesEntryPointProducesWebpContainer() throws Exception {
        byte[] png = readResourceBytes("/images/lossless_animation/frame_001.png");
        WebpEncoder encoder = new WebpEncoder();
        try (EncodedWebp encoded = encoder.encodeStillPng(png, WebpEncodeOptions.builder().lossless(true).build(), null)) {
            byte[] bytes = encoded.toByteArray();
            assertWebpContainer(bytes);
        }

        assertThrows(WebpException.class, () -> encoder.encodeStillPng(
            new byte[]{1, 2, 3, 4}, WebpEncodeOptions.defaults(), null));
    }

    @Test
    void nativeJpegAnimationProducesWebpContainer() throws Exception {
        try (Arena arena = Arena.ofConfined()) {
            WebpCompressedFrame f1 = new WebpCompressedFrame(bytes(arena, readResourceBytes(IMAGE_1)), 50);
            WebpCompressedFrame f2 = new WebpCompressedFrame(bytes(arena, readResourceBytes(IMAGE_1)), 50);

            WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            try (EncodedWebp encoded = encoder.encodeJpegFrames(
                List.of(f1, f2),
                0,
                0,
                0,
                WebpEncodeOptions.defaults(),
                WebpMetadata.EMPTY)) {
                byte[] bytes = encoded.toByteArray();
                assertTrue(bytes.length > 0);
                assertWebpContainer(bytes);
            }
        }
    }

    @Test
    void nativePngAnimationProducesWebpContainer() throws Exception {
        try (Arena arena = Arena.ofConfined()) {
            WebpCompressedFrame f1 = new WebpCompressedFrame(
                bytes(arena, readResourceBytes("/images/lossless_animation/frame_001.png")),
                50);
            WebpCompressedFrame f2 = new WebpCompressedFrame(
                bytes(arena, readResourceBytes("/images/lossless_animation/frame_002.png")),
                50);

            WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            try (EncodedWebp encoded = encoder.encodePngFrames(
                List.of(f1, f2),
                0,
                0,
                0,
                WebpEncodeOptions.builder().lossless(true).build(),
                WebpMetadata.EMPTY)) {
                byte[] bytes = encoded.toByteArray();
                assertTrue(bytes.length > 0);
                assertWebpContainer(bytes);
            }
        }
    }

    @Test
    void animationWithCanvasMismatchFailsCleanly() {
        ImageRgba img1 = loadImageRgba(IMAGE_1);
        try (Arena arena = Arena.ofConfined()) {
            int width = Math.min(16, img1.width);
            int height = Math.min(16, img1.height);
            MemorySegment frame = arena.allocate(width * height * 4L);
            copyCropRgba(img1.pixels, img1.width, frame, width, height);
            WebpFrame f1 = new WebpFrame(frame, width, height, width * 4, PixelFormat.RGBA, 50);

            WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            assertThrows(WebpException.class, () -> encoder.encodeRawFrames(
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
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment rgba = arena.allocate(16 * 16 * 4L);
            assertThrows(IllegalArgumentException.class, () ->
                new WebpRawImage(rgba, 16, 16, 1, PixelFormat.RGBA));
        }
    }

    @Test
    void nativeJpegAnimationWithInvalidDataFailsCleanly() {
        try (Arena arena = Arena.ofConfined()) {
            WebpCompressedFrame frame = new WebpCompressedFrame(bytes(arena, new byte[]{1, 2, 3, 4}), 50);

            WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            assertThrows(WebpException.class, () -> encoder.encodeJpegFrames(
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
        try (Arena arena = Arena.ofConfined()) {
            WebpCompressedFrame frame = new WebpCompressedFrame(bytes(arena, new byte[]{1, 2, 3, 4}), 50);

            WebpAnimationEncoder encoder = new WebpAnimationEncoder();
            assertThrows(WebpException.class, () -> encoder.encodePngFrames(
                List.of(frame),
                0,
                0,
                0,
                WebpEncodeOptions.defaults(),
                WebpMetadata.EMPTY));
        }
    }

    private static void copyCropRgba(byte[] src, int srcWidth, MemorySegment dst, int dstWidth, int dstHeight) {
        long p = 0;
        for (int y = 0; y < dstHeight; y++) {
            int srcRow = y * srcWidth * 4;
            for (int x = 0; x < dstWidth; x++) {
                int s = srcRow + x * 4;
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p++, src[s]);
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p++, src[s + 1]);
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p++, src[s + 2]);
                dst.set(java.lang.foreign.ValueLayout.JAVA_BYTE, p++, src[s + 3]);
            }
        }
    }

    private static ImageRgba loadImageRgba(String resourcePath) {
        try (var in = NativeIntegrationTest.class.getResourceAsStream(resourcePath)) {
            assumeTrue(in != null, "Missing resource " + resourcePath);
            BufferedImage image = ImageIO.read(in);
            assumeTrue(image != null, "Failed to decode resource " + resourcePath);

            int width = image.getWidth();
            int height = image.getHeight();
            byte[] rgba = new byte[width * height * 4];
            int p = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    rgba[p++] = (byte) ((argb >> 16) & 0xFF);
                    rgba[p++] = (byte) ((argb >> 8) & 0xFF);
                    rgba[p++] = (byte) (argb & 0xFF);
                    rgba[p++] = (byte) ((argb >>> 24) & 0xFF);
                }
            }
            return new ImageRgba(rgba, width, height);
        } catch (Exception e) {
            throw new RuntimeException("Failed loading test image " + resourcePath, e);
        }
    }

    private static byte[] readResourceBytes(String resourcePath) throws Exception {
        try (var in = NativeIntegrationTest.class.getResourceAsStream(resourcePath)) {
            assumeTrue(in != null, "Missing resource " + resourcePath);
            return in.readAllBytes();
        }
    }

    private static void assertWebpContainer(byte[] bytes) {
        assertTrue(bytes.length >= 12);
        assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'}, java.util.Arrays.copyOfRange(bytes, 0, 4));
        assertArrayEquals(new byte[]{'W', 'E', 'B', 'P'}, java.util.Arrays.copyOfRange(bytes, 8, 12));
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

    private static MemorySegment bytes(Arena arena, byte[] input) {
        MemorySegment out = arena.allocate(input.length);
        MemorySegment.copy(MemorySegment.ofArray(input), 0, out, 0, input.length);
        return out;
    }

    private static Stream<String> allImages() {
        return ALL_IMAGES.stream();
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> animationPairs() {
        List<org.junit.jupiter.params.provider.Arguments> out = new ArrayList<>();
        for (int i = 0; i < ALL_IMAGES.size(); i++) {
            String first = ALL_IMAGES.get(i);
            String second = ALL_IMAGES.get((i + 1) % ALL_IMAGES.size());
            out.add(org.junit.jupiter.params.provider.Arguments.of(first, second));
        }
        return out.stream();
    }

    private record ImageRgba(byte[] pixels, int width, int height) {
    }
}
