# Build matrix

| OS      | Arch    | Classifier      | Status      | Notes                                |
|---------|---------|-----------------|-------------|--------------------------------------|
| Linux   | x86_64  | `linux-x86_64`  | First-class | Primary production target            |
| Linux   | aarch64 | `linux-aarch64` | First-class | Built on `ubuntu-24.04-arm` runner   |
| macOS   | aarch64 | `macos-aarch64` | First-class | Apple Silicon dev + CI               |
| macOS   | x86_64  | `macos-x86_64`  | Supported   | Built on `macos-13` runner           |
| Windows | any     | —               | Unsupported | `NativeLibraryLoader` rejects it     |

CI builds the native library on every matrix entry and stages it under
`src/main/resources/native/<classifier>/` so the published JAR is multi-arch
in one go. See [`.github/workflows/build.yml`](../.github/workflows/build.yml)
and
[`.github/workflows/build_on_tag_and_deploy_artifactory.yml`](../.github/workflows/build_on_tag_and_deploy_artifactory.yml).

## Toolchain requirements

- Java 25 (final FFM API + JPMS module `ch.ubique.backend.image.webp`)
- Maven 3.9+
- CMake 3.20+
- C compiler (gcc / clang)
- `libwebp` + `libwebpmux` development packages
- `libjpeg-turbo` / `libturbojpeg` development package
- `libpng` development package

### Per-platform install hints

- Debian/Ubuntu:
  `apt-get install cmake pkg-config libwebp-dev libturbojpeg0-dev libpng-dev`
- macOS (Homebrew):
  `brew install cmake pkg-config webp jpeg-turbo libpng`

## Native outputs

| OS    | Library file                          |
|-------|---------------------------------------|
| Linux | `libuetlib_webp_native.so`            |
| macOS | `libuetlib_webp_native.dylib`         |

## Runtime resolution

`NativeLibraryLoader` resolves the shared library in this order:

1. `-Duetlib.webp.native.path=/abs/path/to/lib...` — explicit override.
2. `native/build/libuetlib_webp_native.{so,dylib}` — local CMake build.
3. Classpath resource `/native/<classifier>/libuetlib_webp_native.{so,dylib}` —
   the packaged JAR layout listed above.
4. `System.loadLibrary("uetlib_webp_native")` — `java.library.path` fallback.
