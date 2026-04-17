package ch.ubique.backend.image.webp;

import java.io.File;

/**
 * Legacy {@code uetlib-core} record. Not used by the in-memory encoders.
 *
 * @deprecated Use {@link EncodedWebp} or write encoded bytes yourself.
 */
@Deprecated(forRemoval = true)
public record PartialImage(File file, WebpEncodingConfig config, boolean ownedFile) {
    public PartialImage(File file, WebpEncodingConfig config) {
        this(file, config, false);
    }
}
