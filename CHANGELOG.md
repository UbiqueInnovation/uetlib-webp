# Changelog

All notable changes to this project will be documented in this file.

## Unreleased

- Hand-written FFM bridge in jextract style (VarHandle-based struct accessors).
- Native loader resolves shared library from JAR resources under
  `/native/<classifier>/lib<name>.<ext>` for `linux-x86_64`, `linux-aarch64`,
  `macos-aarch64`, falling back to `java.library.path`.
- `WebpException` carries optional native status and cause.
- `EncodedWebp` registers a `Cleaner` safety net so a forgotten `close()` still
  frees the malloc'd buffer; staging arenas are released eagerly.
- Direct still WebP encode from JPEG (libjpeg-turbo) and PNG (libpng) bytes.
- `BufferedImage`→RGBA conversion uses bulk `getRGB(...)`.
- Animation Java/C encode paths de-duplicated behind shared helpers.
- `module-info.java` published; `-sources.jar` + `-javadoc.jar` attached.
- GitHub Actions matrix builds native libraries for the three supported
  classifiers (`linux-x86_64`, `linux-aarch64`, `macos-aarch64`) using the
  standard Ubique `action-systems-mvn-prepare` / `-install` /
  `-package-deploy` / `-github-release` actions; tag deploys fan-in into a
  single multi-classifier JAR pushed to Artifactory.
- Compatibility surface (`WebpWriter`, `WebpUtils`, `WebpEncodingConfig`,
  `Frame`, `PartialImage`, `CompressionMethod`, `LosslessWebp`, `LossyWebp`,
  `WebpQuality`, `CompatibilityWebpEncoder`) marked
  `@Deprecated(forRemoval = true)`.
- Interlaced PNG decode bug fixed (`png_set_interlace_handling`).
- Native still encode uses `WebPMemoryWriter` so the encoded output is owned by
  a single malloc'd buffer that is handed off to Java intact.
- New `docs/USAGE.md` end-to-end usage guide; `README.md` and
  `docs/BUILD_MATRIX.md` updated to reflect the Java 25 / JPMS module / 3-arch
  packaging story.
- `uetlib_webp_status_string` removed from the native ABI; replaced by status
  codes carried on `WebpException`.
