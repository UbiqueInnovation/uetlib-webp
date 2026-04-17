package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.imageio.ImageIO;

final class NativeWebpCompat {
    private NativeWebpCompat() {
    }

    static WebpEncodeOptions toOptions(WebpEncodingConfig config) {
        boolean lossless = config.quality() instanceof LosslessWebp;
        float quality = config.quality() instanceof LossyWebp lossy
            ? lossy.quality()
            : config.compressionFactor();

        return WebpEncodeOptions.builder()
            .lossless(lossless)
            .quality(quality)
            .method(config.method().getMethod())
            .threadLevel(0)
            .build();
    }

    static byte[] encodeStill(BufferedImage image, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        ImageRgba rgba = rgbaFromBufferedImage(image);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pixels = copyToArena(arena, rgba.pixels());
            WebpRawImage raw = new WebpRawImage(pixels, rgba.width(), rgba.height(), rgba.width() * 4, PixelFormat.RGBA);
            try (EncodedWebp encoded = new WebpEncoder().encodeStillRaw(raw, toOptions(config), metadata)) {
                return encoded.toByteArray();
            }
        }
    }

    static byte[] encodeFrames(Collection<Frame> frames, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames must be non-empty");
        }
        if (allPathFrames(frames) && allJpegPathFrames(frames)) {
            return encodeJpegPathFrames(frames, config, metadata);
        }
        if (allPathFrames(frames) && allPngPathFrames(frames)) {
            return encodePngPathFrames(frames, config, metadata);
        }

        try (Arena arena = Arena.ofConfined()) {
            List<WebpFrame> nativeFrames = new ArrayList<>(frames.size());
            int width = -1;
            int height = -1;
            for (Frame frame : frames) {
                WebpEncodingConfig frameConfig = frame.duration() != null
                    ? config.withDuration(frame.duration())
                    : config;
                ImageRgba rgba = switch (frame) {
                    case Frame.BufferedImageFrame bif -> rgbaFromBufferedImage(bif.image());
                    case Frame.PathFrame pf -> rgbaFromPath(pf.path());
                };
                if (width < 0) {
                    width = rgba.width();
                    height = rgba.height();
                } else if (rgba.width() != width || rgba.height() != height) {
                    throw new IllegalArgumentException("all frames must have identical dimensions");
                }
                nativeFrames.add(new WebpFrame(
                    copyToArena(arena, rgba.pixels()),
                    width,
                    height,
                    width * 4,
                    PixelFormat.RGBA,
                    durationMs(frameConfig.duration())));
            }

            try (EncodedWebp encoded = new WebpAnimationEncoder().encodeRawFrames(
                nativeFrames,
                width,
                height,
                0xFFFFFFFF,
                toOptions(config),
                metadata)) {
                return encoded.toByteArray();
            }
        }
    }

    static byte[] encodePaths(Collection<Path> paths, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("paths must be non-empty");
        }
        if (allJpegPaths(paths)) {
            return encodeJpegPaths(paths, config, metadata);
        }
        if (allPngPaths(paths)) {
            return encodePngPaths(paths, config, metadata);
        }
        List<Frame> frames = paths.stream().map(Frame.PathFrame::new).map(Frame.class::cast).toList();
        return encodeFrames(frames, config, metadata);
    }

    static byte[] encodeJpegBytes(Collection<byte[]> jpegs, WebpEncodingConfig config, WebpMetadata metadata) {
        if (jpegs == null || jpegs.isEmpty()) {
            throw new IllegalArgumentException("jpegs must be non-empty");
        }
        try (Arena arena = Arena.ofConfined()) {
            List<WebpCompressedFrame> nativeFrames = new ArrayList<>(jpegs.size());
            int durationMs = durationMs(config.duration());
            for (byte[] jpeg : jpegs) {
                if (!isJpeg(jpeg)) {
                    throw new IllegalArgumentException("all compressed frames must be JPEG images");
                }
                nativeFrames.add(new WebpCompressedFrame(copyToArena(arena, jpeg), durationMs));
            }
            try (EncodedWebp encoded = new WebpAnimationEncoder().encodeJpegFrames(
                nativeFrames,
                0,
                0,
                0xFFFFFFFF,
                toOptions(config),
                metadata)) {
                return encoded.toByteArray();
            }
        }
    }

    static byte[] encodePngBytes(Collection<byte[]> pngs, WebpEncodingConfig config, WebpMetadata metadata) {
        if (pngs == null || pngs.isEmpty()) {
            throw new IllegalArgumentException("pngs must be non-empty");
        }
        try (Arena arena = Arena.ofConfined()) {
            List<WebpCompressedFrame> nativeFrames = new ArrayList<>(pngs.size());
            int durationMs = durationMs(config.duration());
            for (byte[] png : pngs) {
                if (!isPng(png)) {
                    throw new IllegalArgumentException("all compressed frames must be PNG images");
                }
                nativeFrames.add(new WebpCompressedFrame(copyToArena(arena, png), durationMs));
            }
            try (EncodedWebp encoded = new WebpAnimationEncoder().encodePngFrames(
                nativeFrames,
                0,
                0,
                0xFFFFFFFF,
                toOptions(config),
                metadata)) {
                return encoded.toByteArray();
            }
        }
    }

    static WebpMetadata xmpMetadata(Arena arena, String xmp) {
        if (xmp == null) {
            return WebpMetadata.EMPTY;
        }
        byte[] cleaned = xmp.trim().replace("\n", "").replace("\r", "").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MemorySegment xmpSegment = copyToArena(arena, cleaned);
        return new WebpMetadata(MemorySegment.NULL, MemorySegment.NULL, xmpSegment);
    }

    static ImageRgba rgbaFromPath(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("failed to decode image: " + path);
        }
        return rgbaFromBufferedImage(image);
    }

    static boolean allJpegBytes(Collection<byte[]> images) {
        return images != null && !images.isEmpty() && images.stream().allMatch(NativeWebpCompat::isJpeg);
    }

    static boolean allPngBytes(Collection<byte[]> images) {
        return images != null && !images.isEmpty() && images.stream().allMatch(NativeWebpCompat::isPng);
    }

    static ImageRgba rgbaFromBufferedImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        // Bulk fetch ARGB int[] in one call instead of per-pixel getRGB(x,y),
        // which goes through ColorModel and is roughly an order of magnitude slower.
        int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
        byte[] rgba = new byte[width * height * 4];
        for (int i = 0, p = 0; i < argb.length; i++) {
            int v = argb[i];
            rgba[p++] = (byte) (v >> 16);
            rgba[p++] = (byte) (v >> 8);
            rgba[p++] = (byte) v;
            rgba[p++] = (byte) (v >>> 24);
        }
        return new ImageRgba(rgba, width, height);
    }

    static Path writeWebpToTempFile(Path workingDirectory, byte[] data) throws IOException {
        Path out = Files.createTempFile(workingDirectory, null, ".webp");
        Files.write(out, data);
        return out;
    }

    static MemorySegment copyToArena(Arena arena, byte[] input) {
        MemorySegment out = arena.allocate(input.length);
        MemorySegment.copy(MemorySegment.ofArray(input), 0, out, 0, input.length);
        return out;
    }

    private static byte[] encodeJpegPaths(Collection<Path> paths, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        List<byte[]> jpegs = new ArrayList<>(paths.size());
        for (Path path : paths) {
            jpegs.add(Files.readAllBytes(path));
        }
        return encodeJpegBytes(jpegs, config, metadata);
    }

    private static byte[] encodePngPaths(Collection<Path> paths, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        List<byte[]> pngs = new ArrayList<>(paths.size());
        for (Path path : paths) {
            pngs.add(Files.readAllBytes(path));
        }
        return encodePngBytes(pngs, config, metadata);
    }

    private static byte[] encodeJpegPathFrames(Collection<Frame> frames, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            List<WebpCompressedFrame> nativeFrames = new ArrayList<>(frames.size());
            for (Frame frame : frames) {
                Frame.PathFrame pathFrame = (Frame.PathFrame) frame;
                byte[] jpeg = Files.readAllBytes(pathFrame.path());
                Duration duration = pathFrame.duration() != null ? pathFrame.duration() : config.duration();
                nativeFrames.add(new WebpCompressedFrame(copyToArena(arena, jpeg), durationMs(duration)));
            }
            try (EncodedWebp encoded = new WebpAnimationEncoder().encodeJpegFrames(
                nativeFrames,
                0,
                0,
                0xFFFFFFFF,
                toOptions(config),
                metadata)) {
                return encoded.toByteArray();
            }
        }
    }

    private static byte[] encodePngPathFrames(Collection<Frame> frames, WebpEncodingConfig config, WebpMetadata metadata)
        throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            List<WebpCompressedFrame> nativeFrames = new ArrayList<>(frames.size());
            for (Frame frame : frames) {
                Frame.PathFrame pathFrame = (Frame.PathFrame) frame;
                byte[] png = Files.readAllBytes(pathFrame.path());
                Duration duration = pathFrame.duration() != null ? pathFrame.duration() : config.duration();
                nativeFrames.add(new WebpCompressedFrame(copyToArena(arena, png), durationMs(duration)));
            }
            try (EncodedWebp encoded = new WebpAnimationEncoder().encodePngFrames(
                nativeFrames,
                0,
                0,
                0xFFFFFFFF,
                toOptions(config),
                metadata)) {
                return encoded.toByteArray();
            }
        }
    }

    private static boolean allPathFrames(Collection<Frame> frames) {
        return frames.stream().allMatch(Frame.PathFrame.class::isInstance);
    }

    private static boolean allJpegPathFrames(Collection<Frame> frames) {
        return frames.stream()
            .map(Frame.PathFrame.class::cast)
            .map(Frame.PathFrame::path)
            .allMatch(NativeWebpCompat::isJpegPath);
    }

    private static boolean allPngPathFrames(Collection<Frame> frames) {
        return frames.stream()
            .map(Frame.PathFrame.class::cast)
            .map(Frame.PathFrame::path)
            .allMatch(NativeWebpCompat::isPngPath);
    }

    private static boolean allJpegPaths(Collection<Path> paths) {
        return paths.stream().allMatch(path -> path != null && isJpegPath(path));
    }

    private static boolean allPngPaths(Collection<Path> paths) {
        return paths.stream().allMatch(path -> path != null && isPngPath(path));
    }

    private static boolean isJpegPath(Path path) {
        try (var in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(3);
            return isJpeg(header);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isPngPath(Path path) {
        try (var in = Files.newInputStream(path)) {
            byte[] header = in.readNBytes(8);
            return isPng(header);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes != null
            && bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xFF
            && (bytes[1] & 0xFF) == 0xD8
            && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] bytes) {
        return bytes != null
            && bytes.length >= 8
            && (bytes[0] & 0xFF) == 0x89
            && bytes[1] == 'P'
            && bytes[2] == 'N'
            && bytes[3] == 'G'
            && (bytes[4] & 0xFF) == 0x0D
            && (bytes[5] & 0xFF) == 0x0A
            && (bytes[6] & 0xFF) == 0x1A
            && (bytes[7] & 0xFF) == 0x0A;
    }

    private static int durationMs(Duration duration) {
        long millis = duration.toMillis();
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("duration must be between 1 and Integer.MAX_VALUE ms");
        }
        return (int) millis;
    }

    record ImageRgba(byte[] pixels, int width, int height) {
    }
}
