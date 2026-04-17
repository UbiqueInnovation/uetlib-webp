package ch.ubique.backend.image.webp;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.nio.file.Path;
import java.util.Collection;
import javax.imageio.ImageIO;

/**
 * Static helpers preserved for compatibility with the legacy {@code uetlib-core}
 * {@code WebpUtils} surface. New code should use {@link WebpEncoder} or
 * {@link WebpAnimationEncoder} directly.
 *
 * @deprecated Use {@link WebpEncoder} / {@link WebpAnimationEncoder} directly.
 */
@Deprecated(forRemoval = true)
public final class WebpUtils {
    private WebpUtils() {
    }

    public static byte[] fromBufferedImage(BufferedImage bufferedImage) {
        try {
            return NativeWebpCompat.encodeStill(bufferedImage, WebpEncodingConfig.builder().build(), WebpMetadata.EMPTY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Always throws — attaching metadata to an already-encoded WebP is not implemented.
     * Pass {@link WebpMetadata} at encode time instead.
     *
     * @deprecated Pass {@link WebpMetadata} at encode time.
     */
    @Deprecated(forRemoval = true)
    public static byte[] addMetadata(Path inputWebp, String xmp) {
        throw new UnsupportedOperationException(
            "addMetadata(Path, String) is not implemented; pass WebpMetadata at encode time instead");
    }

    /**
     * Always throws — attaching metadata to an already-encoded WebP is not implemented.
     * Pass {@link WebpMetadata} at encode time instead.
     *
     * @deprecated Pass {@link WebpMetadata} at encode time.
     */
    @Deprecated(forRemoval = true)
    public static byte[] addMetadata(byte[] inputWebp, String xmp) {
        throw new UnsupportedOperationException(
            "addMetadata(byte[], String) is not implemented; pass WebpMetadata at encode time instead");
    }

    public static byte[] animationFromBufferedImages(
        Collection<BufferedImage> bufferedImages,
        WebpEncodingConfig config
    ) {
        try {
            var frames = bufferedImages.stream()
                .map(Frame.BufferedImageFrame::new)
                .map(Frame.class::cast)
                .toList();
            return NativeWebpCompat.encodeFrames(frames, config, WebpMetadata.EMPTY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] animationFromExistingPngs(Collection<Path> pathsToPngs, WebpEncodingConfig config) {
        try {
            return NativeWebpCompat.encodePaths(pathsToPngs, config, WebpMetadata.EMPTY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] animationFromImagesAsByteArrays(Collection<byte[]> pngImages, WebpEncodingConfig config) {
        try {
            if (NativeWebpCompat.allJpegBytes(pngImages)) {
                return NativeWebpCompat.encodeJpegBytes(pngImages, config, WebpMetadata.EMPTY);
            }
            if (NativeWebpCompat.allPngBytes(pngImages)) {
                return NativeWebpCompat.encodePngBytes(pngImages, config, WebpMetadata.EMPTY);
            }
            var frames = pngImages.stream()
                .map(WebpUtils::decodeImage)
                .map(Frame.BufferedImageFrame::new)
                .map(Frame.class::cast)
                .toList();
            return NativeWebpCompat.encodeFrames(frames, config, WebpMetadata.EMPTY);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static byte[] animationFromBufferedImages(
        Collection<BufferedImage> bufferedImages,
        WebpEncodingConfig config,
        String xmp
    ) {
        try (Arena arena = Arena.ofConfined()) {
            var frames = bufferedImages.stream()
                .map(Frame.BufferedImageFrame::new)
                .map(Frame.class::cast)
                .toList();
            return NativeWebpCompat.encodeFrames(frames, config, NativeWebpCompat.xmpMetadata(arena, xmp));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static BufferedImage decodeImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new IOException("failed to decode image bytes");
            }
            return image;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
