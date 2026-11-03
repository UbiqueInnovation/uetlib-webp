package ch.ubique.uetlib.webp;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * Compares native uetlib-webp output vs img2webp CLI (the path uetlib-core shells out to) for the
 * same LAN JPEG frames.
 *
 * <p>PROD (release/2_2, uetlib-core WebpWriter): ImageWebpLibraryWrapper.perImageArguments()
 * builds: img2webp -loop 0 -d <ms> -lossy -q <compressionFactor> -m 4 <frame> ...
 * WebcamTimelapseService only sets .quality(new LossyWebp(100)) — never .compressionFactor().
 * compressionFactor defaults to 0, so the actual command is -q 0.
 *
 * <p>DEV (main branch, uetlib-webp native):
 * WebpEncodeOptions.builder().quality(100f).method(4).minimizeSize(true).build()
 */
class LanTimelapseComparisonTest {
    private static final String[] LAN_RESOURCES = {
        "/images/LAN_20260415_000000_original.jpg",
        "/images/LAN_20260415_001000_original.jpg",
        "/images/LAN_20260415_002000_original.jpg",
        "/images/LAN_20260415_003000_original.jpg",
        "/images/LAN_20260415_004000_original.jpg",
        "/images/LAN_20260415_005000_original.jpg",
    };
    private static final int DURATION_MS = 1000;

    @BeforeAll
    static void setup() {
        NativeTestSupport.configureNativePath();
    }

    private static void runImg2webp(
            final List<Path> frames, final int compressionFactor, final Path output)
            throws Exception {
        // img2webp -loop 0 -o <out> [ -d <ms> -lossy -q <cf> -m 4 <frame> ... ]
        // Note: ImageWebpLibraryWrapper puts -loop/-o BEFORE the per-frame args (via concat order)
        final List<String> cmd =
                new ArrayList<>(List.of("img2webp", "-loop", "0", "-o", output.toString()));
        for (final Path frame : frames) {
            cmd.addAll(
                    List.of(
                            "-d",
                            String.valueOf(DURATION_MS),
                            "-lossy",
                            "-q",
                            String.valueOf(compressionFactor),
                            "-m",
                            "4",
                            frame.toString()));
        }
        final Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        final String out = new String(proc.getInputStream().readAllBytes());
        final int exit = proc.waitFor();
        assertEquals(0, exit, "img2webp failed (exit " + exit + "): " + out);
    }

    private static List<byte[]> loadFrameBytes() throws Exception {
        final List<byte[]> frames = new ArrayList<>();
        for (final String res : LAN_RESOURCES) {
            try (final var in = LanTimelapseComparisonTest.class.getResourceAsStream(res)) {
                assumeTrue(in != null, "Missing test resource: " + res);
                frames.add(in.readAllBytes());
            }
        }
        return frames;
    }

    private static List<Path> writeFramesToDisk(final List<byte[]> frames, final Path dir)
            throws Exception {
        final List<Path> paths = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            final Path p = dir.resolve("frame_%03d.jpg".formatted(i));
            Files.write(p, frames.get(i));
            paths.add(p);
        }
        return paths;
    }

    /**
     * Matches the actual PROD command: img2webp -loop 0 -d 1000 -lossy -q 0 -m 4 <frames...>
     * (compressionFactor=0, because WebcamTimelapseService never sets it)
     */
    @Test
    void cliImpl_prodActual_compressionFactor0(@TempDir final Path tmp) throws Exception {
        assumeTrue(isImg2webpAvailable(), "img2webp not on PATH");
        final List<Path> jpegPaths = writeFramesToDisk(loadFrameBytes(), tmp);
        final Path out = tmp.resolve("cli_cf0_prod_actual.webp");
        runImg2webp(jpegPaths, /* compressionFactor= */ 0, out);
        final byte[] webp = Files.readAllBytes(out);
        assertWebpContainer(webp);
        System.out.printf("[cli   cf=0   PROD actual ] %,d bytes%n", webp.length);
    }

    /** DEV as currently deployed (main branch): quality=100, method=4, minimizeSize=true */
    @Test
    void nativeImpl_devActual_quality100_minimizeSize(@TempDir final Path tmp) throws Exception {
        final List<byte[]> frames = loadFrameBytes();
        final int[] dim = dimensions(frames.get(0));
        final var opts =
                WebpEncodeOptions.builder().quality(100.0f).method(4).minimizeSize(true).build();
        final byte[] webp =
                new WebpAnimationEncoder()
                        .encodeJpegFrameBytes(
                                frames.stream()
                                        .map(b -> new WebpCompressedFrameBytes(b, DURATION_MS))
                                        .toList(),
                                dim[0],
                                dim[1],
                                0,
                                opts,
                                WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        final Path out = save(webp, tmp, "native_q100_minSize_dev_actual.webp");
        System.out.printf("[native q=100 minSize=true DEV actual] %,d bytes%n", webp.length);
    }

    // ---- img2webp CLI helper — mirrors ImageWebpLibraryWrapper exactly ----

    /**
     * Apples-to-apples: native at q=0 (same quality as prod CLI) without minimizeSize. Should
     * produce similar output size to the prod CLI path.
     */
    @Test
    void nativeImpl_quality0_noMinimizeSize(@TempDir final Path tmp) throws Exception {
        final List<byte[]> frames = loadFrameBytes();
        final int[] dim = dimensions(frames.get(0));
        final var opts = WebpEncodeOptions.builder().quality(0.0f).method(4).build();
        final byte[] webp =
                new WebpAnimationEncoder()
                        .encodeJpegFrameBytes(
                                frames.stream()
                                        .map(b -> new WebpCompressedFrameBytes(b, DURATION_MS))
                                        .toList(),
                                dim[0],
                                dim[1],
                                0,
                                opts,
                                WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        final Path out = save(webp, tmp, "native_q0.webp");
        System.out.printf("[native q=0                         ] %,d bytes%n", webp.length);
    }

    // ---- helpers ----

    /** Native at q=75 (a reasonable explicit quality) without minimizeSize. */
    @Test
    void nativeImpl_quality75(@TempDir final Path tmp) throws Exception {
        final List<byte[]> frames = loadFrameBytes();
        final int[] dim = dimensions(frames.get(0));
        final var opts = WebpEncodeOptions.builder().quality(75.0f).method(4).build();
        final byte[] webp =
                new WebpAnimationEncoder()
                        .encodeJpegFrameBytes(
                                frames.stream()
                                        .map(b -> new WebpCompressedFrameBytes(b, DURATION_MS))
                                        .toList(),
                                dim[0],
                                dim[1],
                                0,
                                opts,
                                WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        final Path out = save(webp, tmp, "native_q75.webp");
        System.out.printf("[native q=75                        ] %,d bytes%n", webp.length);
    }

    /**
     * Native at q=75 with minimizeSize=true — likely the best replacement for prod: similar visual
     * quality to q=75 but encoder searches for smallest keyframe spacing.
     */
    @Test
    void nativeImpl_quality75_minimizeSize(@TempDir final Path tmp) throws Exception {
        final List<byte[]> frames = loadFrameBytes();
        final int[] dim = dimensions(frames.get(0));
        final var opts =
                WebpEncodeOptions.builder().quality(75.0f).method(4).minimizeSize(true).build();
        final byte[] webp =
                new WebpAnimationEncoder()
                        .encodeJpegFrameBytes(
                                frames.stream()
                                        .map(b -> new WebpCompressedFrameBytes(b, DURATION_MS))
                                        .toList(),
                                dim[0],
                                dim[1],
                                0,
                                opts,
                                WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        final Path out = save(webp, tmp, "native_q75_minSize.webp");
        System.out.printf("[native q=75  minSize=true          ] %,d bytes%n", webp.length);
    }

    private static int[] dimensions(final byte[] jpegBytes) throws Exception {
        final BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
        return new int[] {img.getWidth(), img.getHeight()};
    }

    private static Path save(final byte[] data, final Path dir, final String name)
            throws Exception {
        final String override = System.getProperty("webp.output.dir");
        final Path dest = (override != null ? Path.of(override) : dir).resolve(name);
        Files.write(dest, data);
        return dest;
    }

    private static boolean isImg2webpAvailable() {
        try {
            return new ProcessBuilder("img2webp", "-version")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    == 0;
        } catch (final Exception e) {
            return false;
        }
    }

    private static void assertWebpContainer(final byte[] bytes) {
        assertTrue(bytes.length >= 12, "WebP too small");
        assertArrayEquals(
                new byte[] {'R', 'I', 'F', 'F'},
                java.util.Arrays.copyOfRange(bytes, 0, 4),
                "Missing RIFF header");
        assertArrayEquals(
                new byte[] {'W', 'E', 'B', 'P'},
                java.util.Arrays.copyOfRange(bytes, 8, 12),
                "Missing WEBP marker");
    }
}
