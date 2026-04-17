package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Drop-in replacement for the legacy {@code uetlib-core} WebP writer that ran
 * {@code cwebp}/{@code img2webp} subprocesses against temp files.
 *
 * <p>This adapter is provided so callers can migrate without code changes.
 * New code should use {@link WebpEncoder} / {@link WebpAnimationEncoder} directly,
 * which avoid the temp-file dance and return bytes in memory.
 *
 * @deprecated Use {@link WebpEncoder} or {@link WebpAnimationEncoder} directly.
 */
@Deprecated(forRemoval = true)
public class WebpWriter implements AutoCloseable {
    private final Path workingDirectory;
    private final WebpEncodingConfig config;

    public WebpWriter() throws IOException {
        this(WebpEncodingConfig.builder().build());
    }

    public WebpWriter(WebpEncodingConfig config) throws IOException {
        this.config = config;
        this.workingDirectory = Files.createTempDirectory("webp-writer-");
    }

    public Path encodeImages(List<BufferedImage> frames, String xmpMetadata) throws IOException {
        List<Frame> webpFrames = frames.stream().map(Frame.BufferedImageFrame::new).map(Frame.class::cast).toList();
        return encodeFrames(webpFrames, xmpMetadata);
    }

    public Path encodeImages(List<BufferedImage> frames) throws IOException {
        List<Frame> webpFrames = frames.stream().map(Frame.BufferedImageFrame::new).map(Frame.class::cast).toList();
        return encodeFrames(webpFrames);
    }

    public Path encodeFrames(List<Frame> frames, String xmpMetadata) throws IOException {
        try (Arena arena = Arena.ofConfined()) {
            byte[] encoded = NativeWebpCompat.encodeFrames(frames, config, NativeWebpCompat.xmpMetadata(arena, xmpMetadata));
            return NativeWebpCompat.writeWebpToTempFile(workingDirectory, encoded);
        }
    }

    public Path encodeFrames(List<Frame> frames) throws IOException {
        byte[] encoded = NativeWebpCompat.encodeFrames(frames, config, ch.ubique.backend.image.webp.WebpMetadata.EMPTY);
        return NativeWebpCompat.writeWebpToTempFile(workingDirectory, encoded);
    }

    public Path encode(List<Path> framePaths) throws IOException {
        byte[] encoded = NativeWebpCompat.encodePaths(framePaths, config, ch.ubique.backend.image.webp.WebpMetadata.EMPTY);
        return NativeWebpCompat.writeWebpToTempFile(workingDirectory, encoded);
    }

    public Path createMetadataFile(String xmpMetadata) throws IOException {
        Path metadataFile = Files.createTempFile(workingDirectory, null, ".xmp");
        Files.writeString(metadataFile, xmpMetadata.trim().replace("\n", "").replace("\r", ""));
        return metadataFile;
    }

    /**
     * Always throws — attaching metadata to an already-encoded WebP file is not implemented.
     * To attach EXIF/ICCP/XMP, pass the metadata at encode time via
     * {@link WebpEncoder#encodeStillRaw} or {@link WebpAnimationEncoder#encodeRawFrames}
     * with a populated {@link WebpMetadata}.
     *
     * @deprecated Pass {@link WebpMetadata} at encode time instead.
     */
    @Deprecated(forRemoval = true)
    public Path createWebpWithMetadata(Path webp, Path metadata) throws IOException {
        throw new UnsupportedOperationException(
            "createWebpWithMetadata(Path, Path) is not implemented; pass WebpMetadata at encode time instead");
    }

    @Override
    public void close() throws IOException {
        if (Files.exists(workingDirectory)) {
            try (var paths = Files.walk(workingDirectory)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            }
        }
    }
}
