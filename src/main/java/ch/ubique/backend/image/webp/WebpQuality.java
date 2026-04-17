package ch.ubique.backend.image.webp;

/**
 * Legacy {@code uetlib-core} quality discriminator.
 * New code should set {@code lossless} and {@code quality} on {@link WebpEncodeOptions} directly.
 *
 * @deprecated Use {@link WebpEncodeOptions}.
 */
@Deprecated(forRemoval = true)
public sealed interface WebpQuality permits LosslessWebp, LossyWebp {
}
