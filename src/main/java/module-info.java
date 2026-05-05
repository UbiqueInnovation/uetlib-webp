/**
 * In-process WebP encoder backed by libwebp via the Java Foreign Function &amp; Memory API.
 */
module ch.ubique.backend.image.webp {
    requires static java.desktop;

    exports ch.ubique.backend.image.webp;
    exports ch.ubique.backend.image.webp.awt;
}
