# uetlib-webp

Production-oriented in-process WebP engine for Java.

## Quick start

Lossless PNG bytes to WebP in one line:

```java
byte[] webp = Webp.lossless().encodePng(pngBytes);
```

Lossy JPEG bytes:

```java
byte[] webp = Webp.lossy(80f).encodeJpeg(jpegBytes);
```

`BufferedImage`:

```java
byte[] webp = Webp.lossy(75f).encode(image);
```

## What is implemented now

- In-memory still WebP encode from raw pixel buffers (RGBA/RGB/BGRA/BGR)
- Direct still WebP encode from JPEG bytes (libjpeg-turbo) and PNG bytes (libpng) — no Java decode hop
- In-memory animated WebP encode from raw frames in one native call
- Native compressed JPEG / PNG animation paths
- Optional AWT/`BufferedImage` helpers exposed under `ch.ubique.uetlib.image.webp.awt`
- High-level `Webp` facade for one-line still-image conversions
- Lossy + lossless options exposed
- Metadata attachment (EXIF/ICCP/XMP)
- Java integration via the FFM API (Java 25, JPMS module `ch.ubique.uetlib.image.webp`)
- Cleaner-backed safety net so a forgotten `EncodedWebp.close()` still frees native memory
- No subprocesses and no temp files in the hot path

For end-to-end usage examples see [`docs/USAGE.md`](docs/USAGE.md).

## Repository layout

- `src/main/java/ch/ubique/uetlib/image/webp`: Java API
- `src/main/java/ch/ubique/uetlib/image/webp/awt`: optional AWT/`BufferedImage` adapters
- `src/main/java/ch/ubique/uetlib/image/webp/ffi`: FFM bridge
- `native/include/uetlib_webp.h`: native ABI
- `native/src/uetlib_webp.c`: native implementation
- `native/CMakeLists.txt`: native build
- `src/jmh/java/ch/ubique/uetlib/image/webp/bench`: benchmarks
- `docs/`: architecture/plan/bench/build docs

## Build native library

### Linux x86_64 / macOS arm64

The native library links against `libwebp`, `libwebpmux`, `libturbojpeg`
(libjpeg-turbo), and `libpng`, resolved through `pkg-config`.

Build dependencies on Debian/Ubuntu:

```bash
apt-get install -y \
  build-essential cmake pkg-config \
  libwebp-dev libturbojpeg0-dev libpng-dev
```

`libwebp-dev` ships the `libwebp` and `libwebpmux` pkg-config files. On Ubuntu
24.04+ the libjpeg-turbo dev package is named `libturbojpeg-dev`.

On macOS (Homebrew):

```bash
brew install cmake pkg-config webp jpeg-turbo libpng
```

Build:

```bash
./scripts/build-native.sh
```

### Runtime dependencies

Only the shared libraries are needed at runtime. On Debian/Ubuntu:

```bash
apt-get install -y libwebp7 libwebpmux3 libturbojpeg libpng16-16
```

Version-suffixed package names vary by distro release (e.g. `libwebp7` →
`libwebp8`, `libpng16-16` → `libpng16-16t64` on Ubuntu 24.04). The published JAR
embeds `libuetlib_webp_native.so` for `linux-x86_64` / `linux-aarch64` /
`macos-aarch64`; the four libraries above must still be installed on the host.

## Build Java module

```bash
mvn -DskipTests compile
```

## Runtime loading

The native library is resolved in this order:

1. `-Duetlib.webp.native.path=/abs/path/to/libuetlib_webp_native.{so,dylib}` — explicit override.
2. `native/build/libuetlib_webp_native.{so,dylib}` — local CMake build, used in-tree.
3. Classpath resource `/native/<os>-<arch>/libuetlib_webp_native.{so,dylib}` — the packaged
   JAR layout. The supported classifiers are `linux-x86_64`, `linux-aarch64`,
   `macos-aarch64`. The resource is streamed into a temp file and loaded.
4. `java.library.path` (`System.loadLibrary`) — fallback.

CI builds the native library on each `(os, arch)` matrix entry and stages it under
`src/main/resources/native/<classifier>/` before packaging, so the published JAR is
self-contained for the architectures listed above.

## Fast path example

```java
import ch.ubique.uetlib.image.webp.*;
import java.lang.foreign.*;

try (Arena arena = Arena.ofConfined()) {
    MemorySegment rgba = arena.allocate(width * height * 4L);
    // ... fill rgba ...

    WebpRawImage image = new WebpRawImage(rgba, width, height, width * 4, PixelFormat.RGBA);
    WebpEncodeOptions opts = WebpEncodeOptions.builder()
        .quality(80f)
        .method(5)
        .build();

    WebpEncoder encoder = new WebpEncoder();
    try (EncodedWebp out = encoder.encodeStillRaw(image, opts, WebpMetadata.EMPTY)) {
        byte[] webp = out.toByteArray();
    }
}
```

Direct JPEG/PNG bytes (no Java decode):

```java
WebpEncoder encoder = new WebpEncoder();
try (EncodedWebp out = encoder.encodeStillJpeg(jpegBytes, opts, WebpMetadata.EMPTY)) {
    byte[] webp = out.toByteArray();
}
```

High-level facade:

```java
import ch.ubique.uetlib.image.webp.Webp;

byte[] lossless = Webp.lossless().encodePng(pngBytes);
byte[] lossy = Webp.lossy(80f).encodeJpeg(jpegBytes);
```

`BufferedImage` convenience lives in the AWT package:

```java
import ch.ubique.uetlib.image.webp.awt.WebpBufferedImageEncoder;

byte[] webp = new WebpBufferedImageEncoder().encode(image, opts, WebpMetadata.EMPTY);
```

Run on Java 25 with `--enable-native-access=ch.ubique.uetlib.image.webp`
(or `ALL-UNNAMED` for unnamed-module / classpath consumers).

## Benchmarks

Run JMH:

```bash
./scripts/build-native.sh
./scripts/run-benchmarks.sh
```

List available benchmarks:

```bash
./scripts/run-benchmarks.sh -l
```

Run a short smoke benchmark:

```bash
./scripts/run-benchmarks.sh ch.ubique.uetlib.image.webp.bench.WebpEncodeBenchmark.nativeStillRawRgba -wi 0 -i 1 -f 1 -r 1s -w 1s
```

Compare this library against the old `uetlib-core` WebP implementation:

```bash
mvn -f ~/work/git/uetlib-core/pom.xml package
LEGACY_CP="$(./scripts/legacy-core-classpath.sh)" \
  ./scripts/run-benchmarks.sh '.*(oldCore|newLibrary).*' -wi 2 -i 5 -f 3
```

The old implementation is loaded through an isolated child-first classloader because both libraries intentionally expose the same `ch.ubique.uetlib.image.webp` package names.
You can also provide the legacy classpath manually through `LEGACY_CP`, `UETLIB_CORE_CLASSPATH`, or `-Duetlib.webp.legacy.classpath=...`.

Pass extra JVM flags through `JMH_JAVA_OPTS` when needed:

```bash
JMH_JAVA_OPTS="-Djmh.ignoreLock=true" ./scripts/run-benchmarks.sh -l
```

The benchmark suite includes explicit CLI + temp-file baselines for still (`cwebp`) and animated (`img2webp`) output.
Default native-vs-CLI comparison benchmarks use CLI-equivalent encoder settings: quality `75`, method `4`, loop `0`, no explicit keyframe flags, and no WebP thread-level flag.
Benchmarks suffixed with `Threaded` enable native `threadLevel=1` and are fast-path tuning benchmarks, not direct CLI-equivalent comparisons.
The suite also includes direct/off-heap native benchmarks (`nativeStillDirectRgba`, `nativeAnimDirectRgba`) that reuse setup-owned `MemorySegment` frame buffers.
Webcam sequence benchmarks are prefixed with `webcam` and use the six `LAN_...` JPEG frames in `src/test/resources/images`.
`oldCore...` benchmarks load the legacy `uetlib-core` WebP API (CLI-backed) via reflection and compare it against the native FFM-based encoders here.
`webcamNativeAnimJpegTurbo` and `webcamStreamNativeJpegTurbo` use native libjpeg-turbo decode plus native WebP animation encode.
`webcamStreamCliAnimJpegTempFiles` models InputStream-to-temp-JPEG files followed by `img2webp`.
`webcamStreamNativeImageIoDirectRgba` models InputStream-to-memory, Java `ImageIO` JPEG decode, then direct native WebP animation encode.
Lossless PNG animation benchmarks are prefixed with `losslessPng` and use rendered frames extracted from `/Users/martinalig/Downloads/animation.webp`.
Benchmarks suffixed with `Keyframes` explicitly force `kmin=3/kmax=5`; the default compatibility benchmarks intentionally omit those flags to match the old `uetlib-core` behavior.
The CLI baseline writes a temporary PPM input file because `cwebp` accepts PNM files, not RGBA byte dumps.
Set `WEBP_CLI` or `WEBP_ANIM_CLI` if `cwebp` or `img2webp` is not on `PATH`.
If JMH reports `Unable to find the resource: /META-INF/BenchmarkList`, run `mvn -q clean test-compile` so the JMH annotation processor regenerates the benchmark index.

## Test corpus licensing

Freely licensed web images used by integration tests are stored in `src/test/resources/images`.
Attribution and license details are documented in `src/test/resources/images/ATTRIBUTION.md`.

## Current tradeoffs

- Native output is copied once into a stable malloc-owned buffer before returning to Java.
- Compressed JPEG decode+encode path is implemented through libjpeg-turbo for animation inputs.
- Compressed PNG decode+encode path is implemented through libpng for animation inputs.
- Other compatibility formats still fall back to Java `ImageIO`.

## Changelog

See [CHANGELOG.md](CHANGELOG.md)

## License

Except where otherwise noted, original uetlib code is licensed under the [Apache License 2.0](LICENSE). Third-party files retain their original license terms; see [NOTICE](NOTICE) and any third-party notices in this repository.
