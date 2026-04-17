package ch.ubique.backend.image.webp;

import ch.ubique.backend.image.webp.Frame.BufferedImageFrame;
import ch.ubique.backend.image.webp.Frame.PathFrame;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityWebpWriterTest {
    @Test
    void perFrameDurationsAreWrittenToWebp() throws IOException {
        try (WebpWriter writer = new WebpWriter()) {
            List<Frame> frames = List.of(
                new BufferedImageFrame(createTestImage(Color.RED), Duration.ofMillis(100)),
                new BufferedImageFrame(createTestImage(Color.GREEN), Duration.ofMillis(300)),
                new BufferedImageFrame(createTestImage(Color.BLUE), Duration.ofMillis(750)));

            Path webp = writer.encodeFrames(frames);
            assertEquals(List.of(100, 300, 750), readFrameDurations(webp));
        }
    }

    @Test
    void pathFrameIsSupported() throws IOException {
        Path tmpDir = Files.createTempDirectory("native-webp-path-frame-");
        try {
            Path png1 = writeTempPng(tmpDir, "frame1.png", Color.RED);
            Path png2 = writeTempPng(tmpDir, "frame2.png", Color.BLUE);
            try (WebpWriter writer = new WebpWriter()) {
                Path webp = writer.encodeFrames(List.of(
                    new PathFrame(png1, Duration.ofMillis(200)),
                    new PathFrame(png2, Duration.ofMillis(800))));
                assertWebpContainer(Files.readAllBytes(webp));
                assertEquals(List.of(200, 800), readFrameDurations(webp));
            }
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Test
    void framesWithoutDurationUseConfigDefault() throws IOException {
        WebpEncodingConfig config = WebpEncodingConfig.builder().duration(Duration.ofMillis(250)).build();
        try (WebpWriter writer = new WebpWriter(config)) {
            Path webp = writer.encodeFrames(List.of(
                new BufferedImageFrame(createTestImage(Color.RED)),
                new BufferedImageFrame(createTestImage(Color.BLUE))));
            assertEquals(List.of(250, 250), readFrameDurations(webp));
        }
    }

    @Test
    void encodeImagesConvenienceMethodsAreSupported() throws IOException {
        try (WebpWriter writer = new WebpWriter(WebpEncodingConfig.builder().quality(new LossyWebp(80)).build())) {
            Path webp = writer.encodeImages(List.of(createTestImage(Color.RED), createTestImage(Color.GREEN)));
            assertWebpContainer(Files.readAllBytes(webp));

            Path webpWithXmp = writer.encodeImages(
                List.of(createTestImage(Color.BLUE), createTestImage(Color.YELLOW)),
                "<x:xmpmeta>\ncompat</x:xmpmeta>");
            byte[] bytes = Files.readAllBytes(webpWithXmp);
            assertWebpContainer(bytes);
            assertTrue(indexOf(bytes, new byte[]{'X', 'M', 'P', ' '}) >= 0);
        }
    }

    @Test
    void encodePathListConvenienceMethodIsSupported() throws IOException {
        Path tmpDir = Files.createTempDirectory("native-webp-path-list-");
        try {
            Path png1 = writeTempPng(tmpDir, "frame1.png", Color.RED);
            Path png2 = writeTempPng(tmpDir, "frame2.png", Color.GREEN);
            try (WebpWriter writer = new WebpWriter()) {
                Path webp = writer.encode(List.of(png1, png2));
                assertWebpContainer(Files.readAllBytes(webp));
            }
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Test
    void bmpPathListFallsBackToImageIoDecode() throws IOException {
        Path tmpDir = Files.createTempDirectory("native-webp-bmp-path-list-");
        try {
            Path bmp1 = tmpDir.resolve("frame1.bmp");
            Path bmp2 = tmpDir.resolve("frame2.bmp");
            Files.write(bmp1, imageBytes(createTestImage(Color.RED), "bmp"));
            Files.write(bmp2, imageBytes(createTestImage(Color.GREEN), "bmp"));

            WebpEncodingConfig config = WebpEncodingConfig.builder().duration(Duration.ofMillis(456)).build();
            try (WebpWriter writer = new WebpWriter(config)) {
                Path webp = writer.encode(List.of(bmp1, bmp2));
                assertWebpContainer(Files.readAllBytes(webp));
                assertEquals(List.of(456, 456), readFrameDurations(webp));
            }
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Test
    void jpegPathListUsesNativeCompressedFastPath() throws IOException {
        Path tmpDir = Files.createTempDirectory("native-webp-jpeg-path-list-");
        try {
            Path jpg1 = tmpDir.resolve("frame1.jpg");
            Path jpg2 = tmpDir.resolve("frame2.jpg");
            Files.write(jpg1, readResourceBytes("/images/LAN_20260415_000000_original.jpg"));
            Files.write(jpg2, readResourceBytes("/images/LAN_20260415_001000_original.jpg"));

            WebpEncodingConfig config = WebpEncodingConfig.builder()
                .duration(Duration.ofMillis(123))
                .quality(new LossyWebp(75))
                .build();
            try (WebpWriter writer = new WebpWriter(config)) {
                Path webp = writer.encode(List.of(jpg1, jpg2));
                assertWebpContainer(Files.readAllBytes(webp));
                assertEquals(List.of(123, 123), readFrameDurations(webp));
            }
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Test
    void jpegPathFramesKeepPerFrameDurationsOnNativeCompressedFastPath() throws IOException {
        Path tmpDir = Files.createTempDirectory("native-webp-jpeg-path-frame-");
        try {
            Path jpg1 = tmpDir.resolve("frame1.jpg");
            Path jpg2 = tmpDir.resolve("frame2.jpg");
            Files.write(jpg1, readResourceBytes("/images/LAN_20260415_000000_original.jpg"));
            Files.write(jpg2, readResourceBytes("/images/LAN_20260415_001000_original.jpg"));

            try (WebpWriter writer = new WebpWriter(WebpEncodingConfig.builder().quality(new LossyWebp(75)).build())) {
                Path webp = writer.encodeFrames(List.of(
                    new PathFrame(jpg1, Duration.ofMillis(111)),
                    new PathFrame(jpg2, Duration.ofMillis(222))));
                assertWebpContainer(Files.readAllBytes(webp));
                assertEquals(List.of(111, 222), readFrameDurations(webp));
            }
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Test
    void partialImageAndPathFrameConvenienceConstructorsKeepDefaults() {
        Path path = Path.of("frame.png");
        PathFrame frame = new PathFrame(path);
        assertEquals(path, frame.path());
        assertEquals(null, frame.duration());

        WebpEncodingConfig config = WebpEncodingConfig.builder().build();
        PartialImage image = new PartialImage(path.toFile(), config);
        assertEquals(path.toFile(), image.file());
        assertEquals(config, image.config());
        assertFalse(image.ownedFile());
    }

    @Test
    void webpUtilsEncodeAnimationFromBufferedImages() {
        byte[] webp = WebpUtils.animationFromBufferedImages(
            List.of(createTestImage(Color.RED), createTestImage(Color.GREEN)),
            WebpEncodingConfig.builder().quality(new LossyWebp(75)).build());
        assertWebpContainer(webp);
    }

    @Test
    void webpUtilsEncodeStillAndAnimationFromExistingPngs() throws IOException {
        byte[] still = WebpUtils.fromBufferedImage(createTestImage(Color.MAGENTA));
        assertWebpContainer(still);

        Path tmpDir = Files.createTempDirectory("native-webp-utils-pngs-");
        try {
            Path png1 = writeTempPng(tmpDir, "frame1.png", Color.RED);
            Path png2 = writeTempPng(tmpDir, "frame2.png", Color.BLUE);
            byte[] webp = WebpUtils.animationFromExistingPngs(
                List.of(png1, png2),
                WebpEncodingConfig.builder().duration(Duration.ofMillis(150)).build());
            assertWebpContainer(webp);
            Path out = Files.createTempFile("native-webp-utils-pngs-", ".webp");
            try {
                Files.write(out, webp);
                assertEquals(List.of(150, 150), readFrameDurations(out));
            } finally {
                Files.deleteIfExists(out);
            }
        } finally {
            deleteRecursively(tmpDir);
        }
    }

    @Test
    void webpUtilsEncodeAnimationWithMetadata() {
        byte[] webp = WebpUtils.animationFromBufferedImages(
            List.of(createTestImage(Color.RED), createTestImage(Color.GREEN)),
            WebpEncodingConfig.builder().build(),
            "<x:xmpmeta>\ncompat-utils</x:xmpmeta>");
        assertWebpContainer(webp);
        assertTrue(indexOf(webp, new byte[]{'X', 'M', 'P', ' '}) >= 0);

        try (Arena arena = Arena.ofConfined()) {
            assertEquals(ch.ubique.backend.image.webp.WebpMetadata.EMPTY, NativeWebpCompat.xmpMetadata(arena, null));
        }
    }

    @Test
    void webpUtilsEncodeAnimationFromPngBytes() throws IOException {
        byte[] png1 = pngBytes(createTestImage(Color.RED));
        byte[] png2 = pngBytes(createTestImage(Color.GREEN));
        byte[] webp = WebpUtils.animationFromImagesAsByteArrays(
            List.of(png1, png2),
            WebpEncodingConfig.builder().duration(Duration.ofMillis(234)).build());
        assertWebpContainer(webp);
        Path out = Files.createTempFile("native-webp-png-bytes-", ".webp");
        try {
            Files.write(out, webp);
            assertEquals(List.of(234, 234), readFrameDurations(out));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void webpUtilsEncodeAnimationFromBmpBytesFallsBackToImageIo() throws IOException {
        byte[] bmp1 = imageBytes(createTestImage(Color.RED), "bmp");
        byte[] bmp2 = imageBytes(createTestImage(Color.GREEN), "bmp");
        byte[] webp = WebpUtils.animationFromImagesAsByteArrays(
            List.of(bmp1, bmp2),
            WebpEncodingConfig.builder().duration(Duration.ofMillis(345)).build());

        assertWebpContainer(webp);
        Path out = Files.createTempFile("native-webp-bmp-bytes-", ".webp");
        try {
            Files.write(out, webp);
            assertEquals(List.of(345, 345), readFrameDurations(out));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void webpUtilsEncodeAnimationFromJpegBytesUsesNativeCompressedFastPath() throws IOException {
        byte[] jpeg1 = readResourceBytes("/images/LAN_20260415_000000_original.jpg");
        byte[] jpeg2 = readResourceBytes("/images/LAN_20260415_001000_original.jpg");
        byte[] webp = WebpUtils.animationFromImagesAsByteArrays(
            List.of(jpeg1, jpeg2),
            WebpEncodingConfig.builder()
                .duration(Duration.ofMillis(321))
                .quality(new LossyWebp(75))
                .build());

        assertWebpContainer(webp);
        Path out = Files.createTempFile("native-webp-jpeg-bytes-", ".webp");
        try {
            Files.write(out, webp);
            assertEquals(List.of(321, 321), readFrameDurations(out));
        } finally {
            Files.deleteIfExists(out);
        }
    }

    @Test
    void invalidCompatibilityInputsFailClearly() throws IOException {
        assertFalse(NativeWebpCompat.allJpegBytes(null));
        assertFalse(NativeWebpCompat.allJpegBytes(List.of()));
        assertFalse(NativeWebpCompat.allJpegBytes(List.of(new byte[]{1, 2})));
        assertFalse(NativeWebpCompat.allPngBytes(null));
        assertFalse(NativeWebpCompat.allPngBytes(List.of()));
        assertFalse(NativeWebpCompat.allPngBytes(List.of(new byte[]{(byte) 0x89, 'P', 'N'})));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodeFrames(
            List.of(),
            WebpEncodingConfig.builder().build(),
            ch.ubique.backend.image.webp.WebpMetadata.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodePaths(
            List.of(),
            WebpEncodingConfig.builder().build(),
            WebpMetadata.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodeJpegBytes(
            List.of(),
            WebpEncodingConfig.builder().build(),
            WebpMetadata.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodeJpegBytes(
            List.of(new byte[]{1, 2, 3}),
            WebpEncodingConfig.builder().build(),
            WebpMetadata.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodePngBytes(
            List.of(),
            WebpEncodingConfig.builder().build(),
            WebpMetadata.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodePngBytes(
            List.of(new byte[]{1, 2, 3}),
            WebpEncodingConfig.builder().build(),
            WebpMetadata.EMPTY));

        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodeFrames(
            List.of(
                new BufferedImageFrame(createTestImage(Color.RED), Duration.ZERO),
                new BufferedImageFrame(createTestImage(Color.BLUE), Duration.ofMillis(100))),
            WebpEncodingConfig.builder().build(),
            ch.ubique.backend.image.webp.WebpMetadata.EMPTY));

        BufferedImage larger = new BufferedImage(11, 10, BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class, () -> NativeWebpCompat.encodeFrames(
            List.of(
                new BufferedImageFrame(createTestImage(Color.RED), Duration.ofMillis(100)),
                new BufferedImageFrame(larger, Duration.ofMillis(100))),
            WebpEncodingConfig.builder().build(),
            ch.ubique.backend.image.webp.WebpMetadata.EMPTY));

        Path invalidImage = Files.createTempFile("native-webp-invalid-", ".png");
        try {
            Files.writeString(invalidImage, "not an image");
            assertThrows(IOException.class, () -> NativeWebpCompat.rgbaFromPath(invalidImage));
        } finally {
            Files.deleteIfExists(invalidImage);
        }

        Path missingImage = Files.createTempFile("native-webp-missing-", ".jpg");
        Files.deleteIfExists(missingImage);
        assertThrows(IOException.class, () -> NativeWebpCompat.encodePaths(
            List.of(missingImage),
            WebpEncodingConfig.builder().build(),
            WebpMetadata.EMPTY));

        assertThrows(UncheckedIOException.class, () -> WebpUtils.animationFromImagesAsByteArrays(
            List.of(new byte[]{1, 2, 3}),
            WebpEncodingConfig.builder().build()));
    }

    @Test
    void createWebpWithMetadataExistingBytesIsExplicitlyUnsupported() throws IOException {
        try (WebpWriter writer = new WebpWriter()) {
            Path metadata = writer.createMetadataFile("<x:xmpmeta>test</x:xmpmeta>");
            Path webp = writer.encodeFrames(List.of(
                new BufferedImageFrame(createTestImage(Color.RED)),
                new BufferedImageFrame(createTestImage(Color.BLUE))));
            assertThrows(UnsupportedOperationException.class, () -> writer.createWebpWithMetadata(webp, metadata));
        }
        assertThrows(UnsupportedOperationException.class, () -> WebpUtils.addMetadata(new byte[]{1}, "xmp"));
        assertThrows(UnsupportedOperationException.class, () -> WebpUtils.addMetadata(Path.of("x.webp"), "xmp"));
    }

    private static List<Integer> readFrameDurations(Path webpFile) throws IOException {
        Process process = new ProcessBuilder("webpmux", "-info", webpFile.toString())
            .redirectErrorStream(true)
            .start();
        String output = new String(process.getInputStream().readAllBytes());

        List<Integer> durations = new ArrayList<>();
        Pattern frameLinePattern = Pattern.compile(
            "^\\s*\\d+:\\s+\\d+\\s+\\d+\\s+\\S+\\s+\\d+\\s+\\d+\\s+(\\d+)");
        for (String line : output.lines().toList()) {
            var matcher = frameLinePattern.matcher(line);
            if (matcher.find()) {
                durations.add(Integer.parseInt(matcher.group(1)));
            }
        }
        return durations;
    }

    private static BufferedImage createTestImage(Color color) {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 10, 10);
        graphics.dispose();
        return image;
    }

    private static Path writeTempPng(Path dir, String name, Color color) throws IOException {
        Path path = dir.resolve(name);
        ImageIO.write(createTestImage(color), "png", path.toFile());
        return path;
    }

    private static byte[] pngBytes(BufferedImage image) throws IOException {
        return imageBytes(image, "png");
    }

    private static byte[] imageBytes(BufferedImage image, String format) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] readResourceBytes(String resource) throws IOException {
        try (var in = CompatibilityWebpWriterTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing test resource: " + resource);
            }
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

    private static void deleteRecursively(Path dir) throws IOException {
        if (Files.exists(dir)) {
            try (var paths = Files.walk(dir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
