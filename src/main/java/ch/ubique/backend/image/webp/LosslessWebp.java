package ch.ubique.backend.image.webp;

/** @deprecated Use {@link WebpEncodeOptions#builder()}{@code .lossless(true)} instead. */
@Deprecated(forRemoval = true)
public record LosslessWebp() implements WebpQuality {
    public static final LosslessWebp INSTANCE = new LosslessWebp();
}
