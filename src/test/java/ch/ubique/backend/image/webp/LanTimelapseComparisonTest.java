package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Compares native uetlib-webp output vs img2webp CLI (the path uetlib-core shells out to)
 * for the same LAN JPEG frames.
 *
 * PROD (release/2_2, uetlib-core WebpWriter):
 *   ImageWebpLibraryWrapper.perImageArguments() builds:
 *     img2webp -loop 0 -d <ms> -lossy -q <compressionFactor> -m 4 <frame> ...
 *   WebcamTimelapseService only sets .quality(new LossyWebp(100)) — never .compressionFactor().
 *   compressionFactor defaults to 0, so the actual command is -q 0.
 *
 * DEV (main branch, uetlib-webp native):
 *   WebpEncodeOptions.builder().quality(100f).method(4).minimizeSize(true).build()
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

    /**
     * Matches the actual PROD command:
     *   img2webp -loop 0 -d 1000 -lossy -q 0 -m 4 <frames...>
     * (compressionFactor=0, because WebcamTimelapseService never sets it)
     */
    @Test
    void cliImpl_prodActual_compressionFactor0(@TempDir Path tmp) throws Exception {
        assumeTrue(isImg2webpAvailable(), "img2webp not on PATH");
        List<Path> jpegPaths = writeFramesToDisk(loadFrameBytes(), tmp);
        Path out = tmp.resolve("cli_cf0_prod_actual.webp");
        runImg2webp(jpegPaths, /*compressionFactor=*/ 0, out);
        byte[] webp = Files.readAllBytes(out);
        assertWebpContainer(webp);
        System.out.printf("[cli   cf=0   PROD actual ] %,d bytes%n", webp.length);
    }

    /**
     * DEV as currently deployed (main branch):
     *   quality=100, method=4, minimizeSize=true
     */
    @Test
    void nativeImpl_devActual_quality100_minimizeSize(@TempDir Path tmp) throws Exception {
        List<byte[]> frames = loadFrameBytes();
        int[] dim = dimensions(frames.get(0));
        var opts = WebpEncodeOptions.builder().quality(100f).method(4).minimizeSize(true).build();
        byte[] webp = new WebpAnimationEncoder().encodeJpegFrameBytes(
            frames.stream().map(b -> new WebpCompressedFrameBytes(b, DURATION_MS)).toList(),
            dim[0], dim[1], 0, opts, WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        Path out = save(webp, tmp, "native_q100_minSize_dev_actual.webp");
        System.out.printf("[native q=100 minSize=true DEV actual] %,d bytes%n", webp.length);
    }

    /**
     * Apples-to-apples: native at q=0 (same quality as prod CLI) without minimizeSize.
     * Should produce similar output size to the prod CLI path.
     */
    @Test
    void nativeImpl_quality0_noMinimizeSize(@TempDir Path tmp) throws Exception {
        List<byte[]> frames = loadFrameBytes();
        int[] dim = dimensions(frames.get(0));
        var opts = WebpEncodeOptions.builder().quality(0f).method(4).build();
        byte[] webp = new WebpAnimationEncoder().encodeJpegFrameBytes(
            frames.stream().map(b -> new WebpCompressedFrameBytes(b, DURATION_MS)).toList(),
            dim[0], dim[1], 0, opts, WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        Path out = save(webp, tmp, "native_q0.webp");
        System.out.printf("[native q=0                         ] %,d bytes%n", webp.length);
    }

    /**
     * Native at q=75 (a reasonable explicit quality) without minimizeSize.
     */
    @Test
    void nativeImpl_quality75(@TempDir Path tmp) throws Exception {
        List<byte[]> frames = loadFrameBytes();
        int[] dim = dimensions(frames.get(0));
        var opts = WebpEncodeOptions.builder().quality(75f).method(4).build();
        byte[] webp = new WebpAnimationEncoder().encodeJpegFrameBytes(
            frames.stream().map(b -> new WebpCompressedFrameBytes(b, DURATION_MS)).toList(),
            dim[0], dim[1], 0, opts, WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        Path out = save(webp, tmp, "native_q75.webp");
        System.out.printf("[native q=75                        ] %,d bytes%n", webp.length);
    }

    /**
     * Native at q=75 with minimizeSize=true — likely the best replacement for prod:
     * similar visual quality to q=75 but encoder searches for smallest keyframe spacing.
     */
    @Test
    void nativeImpl_quality75_minimizeSize(@TempDir Path tmp) throws Exception {
        List<byte[]> frames = loadFrameBytes();
        int[] dim = dimensions(frames.get(0));
        var opts = WebpEncodeOptions.builder().quality(75f).method(4).minimizeSize(true).build();
        byte[] webp = new WebpAnimationEncoder().encodeJpegFrameBytes(
            frames.stream().map(b -> new WebpCompressedFrameBytes(b, DURATION_MS)).toList(),
            dim[0], dim[1], 0, opts, WebpMetadata.EMPTY);
        assertWebpContainer(webp);
        Path out = save(webp, tmp, "native_q75_minSize.webp");
        System.out.printf("[native q=75  minSize=true          ] %,d bytes%n", webp.length);
    }

    // ---- img2webp CLI helper — mirrors ImageWebpLibraryWrapper exactly ----

    private static void runImg2webp(List<Path> frames, int compressionFactor, Path output)
            throws Exception {
        // img2webp -loop 0 -o <out> [ -d <ms> -lossy -q <cf> -m 4 <frame> ... ]
        // Note: ImageWebpLibraryWrapper puts -loop/-o BEFORE the per-frame args (via concat order)
        List<String> cmd = new ArrayList<>(List.of(
            "img2webp", "-loop", "0", "-o", output.toString()));
        for (Path frame : frames) {
            cmd.addAll(List.of(
                "-d", String.valueOf(DURATION_MS),
                "-lossy",
                "-q", String.valueOf(compressionFactor),
                "-m", "4",
                frame.toString()));
        }
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(proc.getInputStream().readAllBytes());
        int exit = proc.waitFor();
        assertTrue(exit == 0, "img2webp failed (exit " + exit + "): " + out);
    }

    // ---- helpers ----

    private static List<byte[]> loadFrameBytes() throws Exception {
        List<byte[]> frames = new ArrayList<>();
        for (String res : LAN_RESOURCES) {
            try (var in = LanTimelapseComparisonTest.class.getResourceAsStream(res)) {
                assumeTrue(in != null, "Missing test resource: " + res);
                frames.add(in.readAllBytes());
            }
        }
        return frames;
    }

    private static List<Path> writeFramesToDisk(List<byte[]> frames, Path dir) throws Exception {
        List<Path> paths = new ArrayList<>();
        for (int i = 0; i < frames.size(); i++) {
            Path p = dir.resolve("frame_%03d.jpg".formatted(i));
            Files.write(p, frames.get(i));
            paths.add(p);
        }
        return paths;
    }

    private static int[] dimensions(byte[] jpegBytes) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(jpegBytes));
        return new int[]{img.getWidth(), img.getHeight()};
    }

    private static Path save(byte[] data, Path dir, String name) throws Exception {
        String override = System.getProperty("webp.output.dir");
        Path dest = (override != null ? Path.of(override) : dir).resolve(name);
        Files.write(dest, data);
        return dest;
    }

    private static boolean isImg2webpAvailable() {
        try {
            return new ProcessBuilder("img2webp", "-version")
                .redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static void assertWebpContainer(byte[] bytes) {
        assertTrue(bytes.length >= 12, "WebP too small");
        assertArrayEquals(new byte[]{'R', 'I', 'F', 'F'},
            java.util.Arrays.copyOfRange(bytes, 0, 4), "Missing RIFF header");
        assertArrayEquals(new byte[]{'W', 'E', 'B', 'P'},
            java.util.Arrays.copyOfRange(bytes, 8, 12), "Missing WEBP marker");
    }
}
