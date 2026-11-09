# Changelog

## 1.0.1

- Replace personal photo fixtures with the existing attributed test images.
- Update third-party notices and test-data attribution.
- Update shared Maven build and publish workflows to v0.2.4.

## 1.0.0

- Move Java packages and class references from `ch.ubique.uetlib` to `ch.uetlib`.

- Rename standalone WebP Java packages to `ch.uetlib.webp`.

### Breaking changes

- Move Java packages from `ch.ubique.backend` to `ch.uetlib`, including class-name resources. The combined OpenRewrite
  migration also renames known Spring configuration keys to `ch.uetlib.*`.
- Remove the inherited Ubique Artifactory repository and deployment destination; private consumers must configure their
  own registry.
- Add Apache-2.0 licensing for original code, preserve third-party licenses, and add public project metadata and
  contribution/security guidance.

- Move Maven coordinates from `ch.ubique:uetlib-*` to `ch.uetlib:uetlib-*`.
- Align uetlib parent, BOM and library references with the migration releases.

### Migration

After the migration releases and `uetlib-rewrite` are published, run this from the consuming project, then review the
diff and run its tests:

```sh
mvn -U org.openrewrite.maven:rewrite-maven-plugin:6.46.1:run \
  -Drewrite.recipeArtifactCoordinates=ch.uetlib:uetlib-rewrite:RELEASE \
  -Drewrite.activeRecipes=ch.uetlib.MigrateToPublicUetlib
```

The recipe updates Maven coordinates/versions, Java references and known class-name resources. Review application-owned
packages under the old namespace, Kotlin, reflection patterns, native-access module flags and persisted class names
separately. Use `:dryRun` instead of `:run` to preview a patch.

## 0.1.2

- latest uetlib parent
- version updates

## 0.1.1

- updated actions to not run tests twice for sonar
- update uetlib version

## 0.1.0

- Extend `uetlib-parent` to support jspecify nullability annotations.

## 0.0.10

- CMYK/YCCK JPEG decode support. The TurboJPEG API cannot convert 4-component
  CMYK/YCCK, so these are now decoded through the base libjpeg API (Adobe APP14
  inversion handled) and converted to RGB. Applied to all three JPEG entry
  points: still encode, animation encode, and the streaming encoder.
- Native build now also links `libjpeg` (from libjpeg-turbo) alongside
  `libturbojpeg`; CI installs `libjpeg-dev` (build) and `libjpeg-turbo8`
  (runtime). `README.md` native-dependency section updated accordingly.
- Renovate configuration (`renovate.json5`) added.

## 0.0.9

- `Webp` facade: one-line still-image conversions (`Webp.lossy(q)`,
  `Webp.lossless()`, `Webp.withOptions(...)`) over the lower-level
  `WebpEncoder`. `README.md` / `docs/USAGE.md` updated.

## 0.0.8

- `InputValidator` and expanded `WebpEncodeOptions` validation to reject invalid
  arguments at the API boundary.
- AWT helpers split into a dedicated `awt` package: `WebpBufferedImages`,
  `WebpBufferedImageEncoder`, `WebpBufferedImageAnimationEncoder`.
- `WebpPixels` helper added; `WebpEncoder` / `WebpAnimationEncoder` slimmed by
  delegating to shared helpers.

## 0.0.7

- Removed the deprecated compatibility surface (`WebpWriter`, `WebpUtils`,
  `WebpEncodingConfig`, `Frame`, `PartialImage`, `LosslessWebp`, `LossyWebp`,
  `WebpQuality`, `NativeWebpCompat`) and its tests.
- Added size-comparison tests (`PhotoLossyJpegSizeTest`,
  `SwitzerlandMapLosslessSizeTest`) and their image fixtures.

## 0.0.6

- Build animations directly from pre-encoded WebP frames (native path plus
  `WebpAnimationEncoder` / `WebpAnimationStream` support).

## 0.0.5

- Decode error-handling fix in the native FFI bindings.
- Added `LanTimelapseComparisonTest`.

## 0.0.4

- Encoding-config fix in the native still-encode path; corresponding
  `WebpEncodeOptions` adjustment.

## 0.0.3

- Streaming animation encoder: incremental frame add over a new native ABI (`WebpAnimationStream`, native streaming
  functions, `NativeWebpLibrary` bindings), with native integration test support (`NativeTestSupport`).

## 0.0.2

- Metadata support (`WebpMetadata`: XMP / EXIF / ICC) wired through the encoder.
- `WebpImages` convenience helpers and `WebpCompressedFrameBytes`.
- Expanded animation API and high-level / input-validation / metadata tests.

## 0.0.1

- Initial implementation.
- Native WebP encode library (`native/`, libwebp + libwebpmux + libturbojpeg +
  libpng) built via CMake.
- Hand-written FFM bridge in jextract style (VarHandle-based struct accessors).
- Native loader resolves the shared library from JAR resources under
  `/native/<classifier>/lib<name>.<ext>` for `linux-x86_64`, `linux-aarch64`,
  `macos-aarch64`, falling back to `java.library.path`.
- Direct still WebP encode from JPEG bytes (libjpeg-turbo) and PNG bytes (libpng) without a Java decode hop. Native
  still encode uses `WebPMemoryWriter` so the output is a single malloc'd buffer handed to Java intact. Interlaced PNG
  decode handled via `png_set_interlace_handling`.
- `BufferedImage`→RGBA conversion via bulk `getRGB(...)`.
- `EncodedWebp` registers a `Cleaner` safety net so a forgotten `close()` still
  frees the native buffer; staging arenas are released eagerly.
- `WebpException` carries optional native status code and cause.
- `module-info.java` published; `-sources.jar` and `-javadoc.jar` attached.
- Legacy compatibility surface marked `@Deprecated(forRemoval = true)` (removed
  in 0.0.7).
- GitHub Actions matrix builds native libraries for the three supported
  classifiers; tag deploys fan-in into a single multi-classifier JAR pushed to
  Artifactory.

[Unreleased]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.10...v0.1.0
[0.0.10]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.9...v0.0.10
[0.0.9]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.8...v0.0.9
[0.0.8]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.7...v0.0.8
[0.0.7]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.6...v0.0.7
[0.0.6]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.5...v0.0.6
[0.0.5]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.4...v0.0.5
[0.0.4]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.3...v0.0.4
[0.0.3]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/UbiqueInnovation/uetlib-webp/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/UbiqueInnovation/uetlib-webp/releases/tag/v0.0.1
