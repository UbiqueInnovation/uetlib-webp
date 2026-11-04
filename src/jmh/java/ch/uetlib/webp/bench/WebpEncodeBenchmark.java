package ch.uetlib.webp.bench;

import ch.uetlib.webp.PixelFormat;
import ch.uetlib.webp.WebpAnimationEncoder;
import ch.uetlib.webp.WebpCompressedFrame;
import ch.uetlib.webp.WebpEncodeOptions;
import ch.uetlib.webp.WebpEncoder;
import ch.uetlib.webp.WebpFrame;
import ch.uetlib.webp.WebpMetadata;
import ch.uetlib.webp.WebpRawImage;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
public class WebpEncodeBenchmark {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int FRAME_COUNT = 24;
    private static final int FRAME_DURATION_MS = 42;
    private static final String[] WEBCAM_IMAGES = {
        "/images/LAN_20260415_000000_original.jpg",
        "/images/LAN_20260415_001000_original.jpg",
        "/images/LAN_20260415_002000_original.jpg",
        "/images/LAN_20260415_003000_original.jpg",
        "/images/LAN_20260415_004000_original.jpg",
        "/images/LAN_20260415_005000_original.jpg"
    };
    private static final String[] LOSSLESS_ANIMATION_PNGS = {
        "/images/lossless_animation/frame_001.png",
        "/images/lossless_animation/frame_002.png",
        "/images/lossless_animation/frame_003.png",
        "/images/lossless_animation/frame_004.png",
        "/images/lossless_animation/frame_005.png",
        "/images/lossless_animation/frame_006.png",
        "/images/lossless_animation/frame_007.png",
        "/images/lossless_animation/frame_008.png",
        "/images/lossless_animation/frame_009.png",
        "/images/lossless_animation/frame_010.png",
        "/images/lossless_animation/frame_011.png",
        "/images/lossless_animation/frame_012.png"
    };

    private final WebpEncoder directStillEncoder = new WebpEncoder();
    private final WebpAnimationEncoder directAnimationEncoder = new WebpAnimationEncoder();
    private byte[] still;
    private byte[] stillPpm;
    private List<byte[]> animation;
    private List<byte[]> animationPpm;
    private List<byte[]> webcamAnimation;
    private List<byte[]> webcamAnimationPpm;
    private List<byte[]> webcamAnimationJpeg;
    private List<Path> webcamAnimationJpegPaths;
    private int webcamWidth;
    private int webcamHeight;
    private List<byte[]> losslessAnimation;
    private List<byte[]> losslessAnimationPng;
    private List<Path> losslessAnimationPngPaths;
    private int losslessWidth;
    private int losslessHeight;
    private Path benchmarkTempDir;
    private Arena directArena;
    private WebpRawImage directStill;
    private List<WebpFrame> directAnimation;
    private List<WebpFrame> directWebcamAnimation;
    private List<WebpCompressedFrame> directWebcamJpegAnimation;
    private List<WebpFrame> directLosslessAnimation;
    private WebpEncodeOptions options;
    private WebpEncodeOptions threadedOptions;
    private WebpEncodeOptions losslessOptions;
    private WebpEncodeOptions threadedLosslessOptions;
    private WebpEncodeOptions losslessKeyframeOptions;
    private LegacyCoreWebpAdapter legacyCore;
    private String stillCliPath;
    private String animationCliPath;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        still = syntheticRgba(WIDTH, HEIGHT, 0);
        stillPpm = ppmFromRgba(still, WIDTH, HEIGHT);
        animation = new ArrayList<>();
        animationPpm = new ArrayList<>();
        for (var i = 0; i < FRAME_COUNT; i++) {
            final var frame = syntheticRgba(WIDTH, HEIGHT, i);
            animation.add(frame);
            animationPpm.add(ppmFromRgba(frame, WIDTH, HEIGHT));
        }
        options = WebpEncodeOptions.builder().quality(75.0f).method(4).threadLevel(0).build();
        threadedOptions =
                WebpEncodeOptions.builder().quality(75.0f).method(4).threadLevel(1).build();
        losslessOptions =
                WebpEncodeOptions.builder()
                        .lossless(true)
                        .quality(75.0f)
                        .method(4)
                        .threadLevel(0)
                        .build();
        threadedLosslessOptions =
                WebpEncodeOptions.builder()
                        .lossless(true)
                        .quality(75.0f)
                        .method(4)
                        .threadLevel(1)
                        .build();
        losslessKeyframeOptions =
                WebpEncodeOptions.builder()
                        .lossless(true)
                        .quality(75.0f)
                        .method(4)
                        .threadLevel(0)
                        .kmin(3)
                        .kmax(5)
                        .build();
        stillCliPath = System.getenv().getOrDefault("WEBP_CLI", "cwebp");
        animationCliPath = System.getenv().getOrDefault("WEBP_ANIM_CLI", "img2webp");
        benchmarkTempDir = Files.createTempDirectory("webp-bench-inputs-");
        loadWebcamFrames();
        loadLosslessAnimationFrames();

        directArena = Arena.ofShared();
        final var stillPixels = copyToArena(directArena, still);
        directStill = new WebpRawImage(stillPixels, WIDTH, HEIGHT, WIDTH * 4, PixelFormat.RGBA);

        directAnimation = new ArrayList<>(animation.size());
        for (final var frame : animation) {
            directAnimation.add(
                    new WebpFrame(
                            copyToArena(directArena, frame),
                            WIDTH,
                            HEIGHT,
                            WIDTH * 4,
                            PixelFormat.RGBA,
                            FRAME_DURATION_MS));
        }

        directWebcamAnimation = new ArrayList<>(webcamAnimation.size());
        for (final var frame : webcamAnimation) {
            directWebcamAnimation.add(
                    new WebpFrame(
                            copyToArena(directArena, frame),
                            webcamWidth,
                            webcamHeight,
                            webcamWidth * 4,
                            PixelFormat.RGBA,
                            FRAME_DURATION_MS));
        }

        directWebcamJpegAnimation = new ArrayList<>(webcamAnimationJpeg.size());
        for (final var frame : webcamAnimationJpeg) {
            directWebcamJpegAnimation.add(
                    new WebpCompressedFrame(copyToArena(directArena, frame), FRAME_DURATION_MS));
        }

        directLosslessAnimation = new ArrayList<>(losslessAnimation.size());
        for (final var frame : losslessAnimation) {
            directLosslessAnimation.add(
                    new WebpFrame(
                            copyToArena(directArena, frame),
                            losslessWidth,
                            losslessHeight,
                            losslessWidth * 4,
                            PixelFormat.RGBA,
                            1000));
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        directArena.close();
        if (legacyCore != null) {
            legacyCore.close();
        }
        deleteRecursively(benchmarkTempDir);
    }

    @Benchmark
    public byte[] nativeStillDirectRgba() {
        try (final var encoded =
                directStillEncoder.encodeStillRaw(directStill, options, WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] nativeStillDirectRgbaThreaded() {
        try (final var encoded =
                directStillEncoder.encodeStillRaw(
                        directStill, threadedOptions, WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] nativeAnimDirectRgba() {
        try (final var encoded =
                directAnimationEncoder.encodeRawFrames(
                        directAnimation, WIDTH, HEIGHT, 0, options, WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] nativeAnimDirectRgbaThreaded() {
        try (final var encoded =
                directAnimationEncoder.encodeRawFrames(
                        directAnimation, WIDTH, HEIGHT, 0, threadedOptions, WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] webcamNativeAnimDirectRgba() {
        try (final var encoded =
                directAnimationEncoder.encodeRawFrames(
                        directWebcamAnimation,
                        webcamWidth,
                        webcamHeight,
                        0,
                        options,
                        WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] webcamNativeAnimJpegTurbo() {
        try (final var encoded =
                directAnimationEncoder.encodeJpegFrames(
                        directWebcamJpegAnimation,
                        webcamWidth,
                        webcamHeight,
                        0,
                        options,
                        WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] webcamNativeAnimJpegTurboThreaded() {
        try (final var encoded =
                directAnimationEncoder.encodeJpegFrames(
                        directWebcamJpegAnimation,
                        webcamWidth,
                        webcamHeight,
                        0,
                        threadedOptions,
                        WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] cliStillTempFile() throws IOException, InterruptedException {
        final var in = Files.createTempFile("webp-bench", ".ppm");
        final var out = Files.createTempFile("webp-bench", ".webp");
        try {
            Files.write(in, stillPpm);
            final var process =
                    new ProcessBuilder(
                                    stillCliPath,
                                    "-quiet",
                                    "-q",
                                    "75",
                                    in.toString(),
                                    "-o",
                                    out.toString())
                            .start();
            final var code = process.waitFor();
            if (code != 0) {
                final var stderr =
                        new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new IllegalStateException("cwebp failed with code " + code + ": " + stderr);
            }
            return Files.readAllBytes(out);
        } finally {
            Files.deleteIfExists(in);
            Files.deleteIfExists(out);
        }
    }

    @Benchmark
    public byte[] cliAnimTempFiles() throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(animationPpm, ".ppm", "img2webp");
    }

    @Benchmark
    public byte[] webcamCliAnimPpmTempFiles() throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(webcamAnimationPpm, ".ppm", "img2webp webcam PPM");
    }

    @Benchmark
    public byte[] webcamCliAnimJpegTempFiles() throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(webcamAnimationJpeg, ".jpg", "img2webp webcam JPEG");
    }

    @Benchmark
    public byte[] webcamStreamCliAnimJpegTempFiles() throws IOException, InterruptedException {
        final List<Path> inputs = new ArrayList<>(WEBCAM_IMAGES.length);
        final var out = Files.createTempFile("webp-bench-stream-anim", ".webp");
        try {
            for (var i = 0; i < WEBCAM_IMAGES.length; i++) {
                final var frame =
                        Files.createTempFile("webp-bench-stream-frame-" + i + "-", ".jpg");
                try (final var in = openResource(WEBCAM_IMAGES[i])) {
                    Files.copy(in, frame, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                inputs.add(frame);
            }
            return encodeAnimationFilesWithImg2Webp(inputs, out, "img2webp webcam stream JPEG");
        } finally {
            for (final var input : inputs) {
                Files.deleteIfExists(input);
            }
            Files.deleteIfExists(out);
        }
    }

    @Benchmark
    public byte[] webcamStreamNativeImageIoDirectRgba() throws IOException {
        final List<WebpFrame> frames = new ArrayList<>(WEBCAM_IMAGES.length);
        try (final var arena = Arena.ofConfined()) {
            var width = -1;
            var height = -1;
            for (final var imagePath : WEBCAM_IMAGES) {
                final var rgba = decodeImageRgba(readResource(imagePath), imagePath);
                if (width < 0) {
                    width = rgba.width;
                    height = rgba.height;
                } else if (rgba.width != width || rgba.height != height) {
                    throw new IllegalStateException("Webcam frame dimensions differ: " + imagePath);
                }
                frames.add(
                        new WebpFrame(
                                copyToArena(arena, rgba.pixels),
                                width,
                                height,
                                width * 4,
                                PixelFormat.RGBA,
                                FRAME_DURATION_MS));
            }

            try (final var encoded =
                    directAnimationEncoder.encodeRawFrames(
                            frames, width, height, 0, options, WebpMetadata.EMPTY)) {
                return encoded.toByteArray();
            }
        }
    }

    @Benchmark
    public byte[] webcamStreamNativeJpegTurbo() throws IOException {
        final List<WebpCompressedFrame> frames = new ArrayList<>(WEBCAM_IMAGES.length);
        try (final var arena = Arena.ofConfined()) {
            for (final var imagePath : WEBCAM_IMAGES) {
                frames.add(
                        new WebpCompressedFrame(
                                copyToArena(arena, readResource(imagePath)), FRAME_DURATION_MS));
            }

            try (final var encoded =
                    directAnimationEncoder.encodeJpegFrames(
                            frames, 0, 0, 0, options, WebpMetadata.EMPTY)) {
                return encoded.toByteArray();
            }
        }
    }

    @Benchmark
    public byte[] oldCoreWebpWriterWebcamJpegPaths() {
        return legacyCore().writerEncodePaths(webcamAnimationJpegPaths, true);
    }

    @Benchmark
    public byte[] oldCoreWebpUtilsLosslessPngBytes() {
        return legacyCore().animationFromImagesAsByteArrays(losslessAnimationPng, false);
    }

    @Benchmark
    public byte[] losslessPngNativeAnimDirectRgba() {
        try (final var encoded =
                directAnimationEncoder.encodeRawFrames(
                        directLosslessAnimation,
                        losslessWidth,
                        losslessHeight,
                        0xFFFFFFFF,
                        losslessOptions,
                        WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] losslessPngNativeAnimDirectRgbaThreaded() {
        try (final var encoded =
                directAnimationEncoder.encodeRawFrames(
                        directLosslessAnimation,
                        losslessWidth,
                        losslessHeight,
                        0xFFFFFFFF,
                        threadedLosslessOptions,
                        WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] losslessPngCliAnimTempFiles() throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(
                losslessAnimationPng, ".png", "img2webp lossless PNG", true, 1000);
    }

    @Benchmark
    public byte[] losslessPngNativeAnimDirectRgbaKeyframes() {
        try (final var encoded =
                directAnimationEncoder.encodeRawFrames(
                        directLosslessAnimation,
                        losslessWidth,
                        losslessHeight,
                        0xFFFFFFFF,
                        losslessKeyframeOptions,
                        WebpMetadata.EMPTY)) {
            return encoded.toByteArray();
        }
    }

    @Benchmark
    public byte[] losslessPngCliAnimTempFilesKeyframes() throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(
                losslessAnimationPng, ".png", "img2webp lossless PNG keyframes", true, 1000, 3, 5);
    }

    private byte[] encodeAnimationWithImg2Webp(
            final List<byte[]> frames, final String inputSuffix, final String label)
            throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(frames, inputSuffix, label, false);
    }

    private byte[] encodeAnimationWithImg2Webp(
            final List<byte[]> frames,
            final String inputSuffix,
            final String label,
            final boolean lossless)
            throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(frames, inputSuffix, label, lossless, FRAME_DURATION_MS);
    }

    private byte[] encodeAnimationWithImg2Webp(
            final List<byte[]> frames,
            final String inputSuffix,
            final String label,
            final boolean lossless,
            final int durationMs)
            throws IOException, InterruptedException {
        return encodeAnimationWithImg2Webp(
                frames, inputSuffix, label, lossless, durationMs, null, null);
    }

    private byte[] encodeAnimationWithImg2Webp(
            final List<byte[]> frames,
            final String inputSuffix,
            final String label,
            final boolean lossless,
            final int durationMs,
            final Integer kmin,
            final Integer kmax)
            throws IOException, InterruptedException {
        final List<Path> inputs = new ArrayList<>(frames.size());
        final var out = Files.createTempFile("webp-bench-anim", ".webp");
        try {
            for (var i = 0; i < frames.size(); i++) {
                final var frame = Files.createTempFile("webp-bench-frame-" + i + "-", inputSuffix);
                Files.write(frame, frames.get(i));
                inputs.add(frame);
            }

            return encodeAnimationFilesWithImg2Webp(
                    inputs, out, label, lossless, durationMs, kmin, kmax);
        } finally {
            for (final var input : inputs) {
                Files.deleteIfExists(input);
            }
            Files.deleteIfExists(out);
        }
    }

    private byte[] encodeAnimationFilesWithImg2Webp(
            final List<Path> inputs, final Path out, final String label)
            throws IOException, InterruptedException {
        return encodeAnimationFilesWithImg2Webp(inputs, out, label, false, FRAME_DURATION_MS);
    }

    private byte[] encodeAnimationFilesWithImg2Webp(
            final List<Path> inputs, final Path out, final String label, final boolean lossless)
            throws IOException, InterruptedException {
        return encodeAnimationFilesWithImg2Webp(inputs, out, label, lossless, FRAME_DURATION_MS);
    }

    private byte[] encodeAnimationFilesWithImg2Webp(
            final List<Path> inputs,
            final Path out,
            final String label,
            final boolean lossless,
            final int durationMs)
            throws IOException, InterruptedException {
        return encodeAnimationFilesWithImg2Webp(
                inputs, out, label, lossless, durationMs, null, null);
    }

    private byte[] encodeAnimationFilesWithImg2Webp(
            final List<Path> inputs,
            final Path out,
            final String label,
            final boolean lossless,
            final int durationMs,
            final Integer kmin,
            final Integer kmax)
            throws IOException, InterruptedException {
        final var command =
                buildImg2WebpCommand(
                        animationCliPath, inputs, out, lossless, durationMs, kmin, kmax);
        final var process = new ProcessBuilder(command).start();
        final var code = process.waitFor();
        if (code != 0) {
            final var stderr =
                    new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new IllegalStateException(label + " failed with code " + code + ": " + stderr);
        }
        return Files.readAllBytes(out);
    }

    private void loadWebcamFrames() throws IOException {
        webcamAnimation = new ArrayList<>(WEBCAM_IMAGES.length);
        webcamAnimationPpm = new ArrayList<>(WEBCAM_IMAGES.length);
        webcamAnimationJpeg = new ArrayList<>(WEBCAM_IMAGES.length);
        webcamAnimationJpegPaths = new ArrayList<>(WEBCAM_IMAGES.length);

        for (var i = 0; i < WEBCAM_IMAGES.length; i++) {
            final var imagePath = WEBCAM_IMAGES[i];
            final var jpeg = readResource(imagePath);
            final var rgba = decodeImageRgba(jpeg, imagePath);
            if (webcamAnimation.isEmpty()) {
                webcamWidth = rgba.width;
                webcamHeight = rgba.height;
            } else if (rgba.width != webcamWidth || rgba.height != webcamHeight) {
                throw new IllegalStateException("Webcam frame dimensions differ: " + imagePath);
            }
            webcamAnimation.add(rgba.pixels);
            webcamAnimationPpm.add(ppmFromRgba(rgba.pixels, rgba.width, rgba.height));
            webcamAnimationJpeg.add(jpeg);
            final var path = benchmarkTempDir.resolve("webcam-frame-" + i + ".jpg");
            Files.write(path, jpeg);
            webcamAnimationJpegPaths.add(path);
        }
    }

    private void loadLosslessAnimationFrames() throws IOException {
        losslessAnimation = new ArrayList<>(LOSSLESS_ANIMATION_PNGS.length);
        losslessAnimationPng = new ArrayList<>(LOSSLESS_ANIMATION_PNGS.length);
        losslessAnimationPngPaths = new ArrayList<>(LOSSLESS_ANIMATION_PNGS.length);
        for (var i = 0; i < LOSSLESS_ANIMATION_PNGS.length; i++) {
            final var imagePath = LOSSLESS_ANIMATION_PNGS[i];
            final var png = readResource(imagePath);
            final var rgba = decodeImageRgba(png, imagePath);
            if (losslessAnimation.isEmpty()) {
                losslessWidth = rgba.width;
                losslessHeight = rgba.height;
            } else if (rgba.width != losslessWidth || rgba.height != losslessHeight) {
                throw new IllegalStateException("Lossless frame dimensions differ: " + imagePath);
            }
            losslessAnimation.add(rgba.pixels);
            losslessAnimationPng.add(png);
            final var path = benchmarkTempDir.resolve("lossless-frame-" + i + ".png");
            Files.write(path, png);
            losslessAnimationPngPaths.add(path);
        }
    }

    static List<String> buildImg2WebpCommand(
            final String animationCliPath,
            final List<Path> inputs,
            final Path out,
            final boolean lossless,
            final int durationMs,
            final Integer kmin,
            final Integer kmax) {
        final List<String> command = new ArrayList<>();
        command.add(animationCliPath);
        command.add("-loop");
        command.add("0");
        command.add("-o");
        command.add(out.toString());
        if (kmin != null && kmax != null) {
            command.add("-kmin");
            command.add(Integer.toString(kmin));
            command.add("-kmax");
            command.add(Integer.toString(kmax));
        }
        for (final var input : inputs) {
            command.add("-d");
            command.add(Integer.toString(durationMs));
            command.add(lossless ? "-lossless" : "-lossy");
            command.add("-q");
            command.add("75");
            command.add("-m");
            command.add("4");
            command.add(input.toString());
        }
        return command;
    }

    private static byte[] syntheticRgba(final int width, final int height, final int frameIndex) {
        final var out = new byte[width * height * 4];
        var p = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                out[p] = (byte) ((x + frameIndex * 3) & 0xFF);
                p++;
                out[p] = (byte) ((y + frameIndex * 7) & 0xFF);
                p++;
                out[p] = (byte) ((x + y + frameIndex * 11) & 0xFF);
                p++;
                out[p] = (byte) 0xFF;
                p++;
            }
        }
        return out;
    }

    private static byte[] readResource(final String resourcePath) throws IOException {
        try (final var in = openResource(resourcePath)) {
            return in.readAllBytes();
        }
    }

    private static InputStream openResource(final String resourcePath) throws IOException {
        final var in = WebpEncodeBenchmark.class.getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IOException("Missing resource: " + resourcePath);
        }
        return in;
    }

    private static ImageRgba decodeImageRgba(final byte[] encoded, final String label)
            throws IOException {
        final var image = ImageIO.read(new ByteArrayInputStream(encoded));
        if (image == null) {
            throw new IOException("Failed to decode image resource: " + label);
        }

        final var width = image.getWidth();
        final var height = image.getHeight();
        final var rgba = new byte[width * height * 4];
        var p = 0;
        for (var y = 0; y < height; y++) {
            for (var x = 0; x < width; x++) {
                final var argb = image.getRGB(x, y);
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
    }

    private static byte[] ppmFromRgba(final byte[] rgba, final int width, final int height) {
        final var header =
                ("P6\n" + width + " " + height + "\n255\n").getBytes(StandardCharsets.US_ASCII);
        final var out = new byte[header.length + width * height * 3];
        System.arraycopy(header, 0, out, 0, header.length);
        var src = 0;
        var dst = header.length;
        for (var i = 0; i < width * height; i++) {
            out[dst] = rgba[src];
            dst++;
            src++;
            out[dst] = rgba[src];
            dst++;
            src++;
            out[dst] = rgba[src];
            dst++;
            src++;
            src++;
        }
        return out;
    }

    private static MemorySegment copyToArena(final Arena arena, final byte[] input) {
        final var out = arena.allocate(input.length);
        MemorySegment.copy(MemorySegment.ofArray(input), 0, out, 0, input.length);
        return out;
    }

    private LegacyCoreWebpAdapter legacyCore() {
        if (legacyCore == null) {
            legacyCore = new LegacyCoreWebpAdapter();
        }
        return legacyCore;
    }

    private static void deleteRecursively(final Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (final var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .forEach(
                            path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (final IOException e) {
                                    throw new IllegalStateException(
                                            "Failed to delete benchmark temp path: " + path, e);
                                }
                            });
        } catch (final IOException e) {
            throw new IllegalStateException("Failed to delete benchmark temp dir: " + dir, e);
        }
    }

    private record ImageRgba(byte[] pixels, int width, int height) {}
}
