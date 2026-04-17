package ch.ubique.backend.image.webp;

/** @deprecated Use {@link WebpEncodeOptions#builder()}{@code .quality(...)} instead. */
@Deprecated(forRemoval = true)
public record LossyWebp(int quality) implements WebpQuality {
}
