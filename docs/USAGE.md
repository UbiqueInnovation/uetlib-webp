# Using `uetlib-webp`

This document walks through the supported entry points end to end. All encoders
return an [`EncodedWebp`](../src/main/java/ch/ubique/backend/image/webp/EncodedWebp.java),
which owns one malloc-backed native buffer.

## 1. Prerequisites

- Java 25 (the library targets `--release 25` and ships as JPMS module
  `ch.ubique.backend.image.webp`).
- The native shared library `uetlib_webp_native`. The loader resolves it in this
  order:
  1. `-Duetlib.webp.native.path=/abs/path/to/libuetlib_webp_native.{so,dylib}`
  2. `native/build/libuetlib_webp_native.{so,dylib}` (in-tree CMake build).
  3. Classpath resource `/native/<classifier>/libuetlib_webp_native.{so,dylib}`
     with `<classifier>` in `{linux-x86_64, linux-aarch64, macos-aarch64}` —
     produced by CI and packaged inside the JAR.
  4. `System.loadLibrary("uetlib_webp_native")` via `java.library.path`.

Run Java consumers with native access enabled:

```
--enable-native-access=ch.ubique.backend.image.webp
```

For classpath (unnamed module) consumers use `ALL-UNNAMED` instead.

## 2. Core API overview

| Type                        | Purpose                                           |
|-----------------------------|---------------------------------------------------|
| `WebpEncoder`               | Single-image encode (raw / JPEG bytes / PNG bytes) |
| `WebpAnimationEncoder`      | Multi-frame encode (raw / JPEG / PNG frames)      |
| `WebpRawImage`              | Descriptor for one raw-pixel image                |
| `WebpFrame`                 | Raw-pixel animation frame with `durationMs`       |
| `WebpCompressedFrame`       | JPEG/PNG-bytes animation frame with `durationMs`  |
| `WebpEncodeOptions`         | Builder for WebP encoder options                  |
| `WebpMetadata`              | EXIF / ICCP / XMP byte payloads; `WebpMetadata.EMPTY` for "none" |
| `EncodedWebp`               | `AutoCloseable` native byte buffer holder         |
| `WebpException`             | `RuntimeException` with optional native status    |

`PixelFormat` values: `RGBA`, `RGB`, `BGRA`, `BGR`. Stride is in bytes and must
match the row layout of the supplied `MemorySegment`.

## 3. Build encoder options

```java
WebpEncodeOptions opts = WebpEncodeOptions.builder()
    .quality(80f)       // 0..100, lossy
    .method(5)          // 0 (fast) .. 6 (best)
    .lossless(false)
    .threadLevel(1)     // off=0, on=1
    .alphaQuality(100)
    .loopCount(0)       // 0 = infinite (animation only)
    // .exact(true), .nearLossless(60), .targetSize(...), .targetPsnr(...),
    // .kmin(3), .kmax(5), .allowMixed(true), .minimizeSize(true)
    .build();
```

Defaults match CLI parity with `cwebp`/`img2webp`: `quality=75, method=4,
threadLevel=1, alphaQuality=100, loopCount=0`.

## 4. Still encode — raw pixels

```java
import ch.ubique.backend.image.webp.*;
import java.lang.foreign.*;

try (Arena arena = Arena.ofConfined()) {
    int w = 640, h = 480;
    MemorySegment rgba = arena.allocate((long) w * h * 4);
    // ... fill rgba with RGBA bytes ...

    WebpRawImage image = new WebpRawImage(rgba, w, h, w * 4, PixelFormat.RGBA);
    WebpEncoder encoder = new WebpEncoder();
    try (EncodedWebp out = encoder.encodeStillRaw(image, opts, WebpMetadata.EMPTY)) {
        byte[] webpBytes = out.toByteArray();
        // or out.bytes() for a zero-copy MemorySegment view
    }
}
```

## 5. Still encode — JPEG / PNG bytes (no Java decode)

The native layer decodes via libjpeg-turbo / libpng and encodes as WebP in one
C call, skipping `ImageIO` / `BufferedImage` entirely.

```java
WebpEncoder encoder = new WebpEncoder();

try (EncodedWebp out = encoder.encodeStillJpeg(jpegBytes, opts, WebpMetadata.EMPTY)) {
    Files.write(Path.of("photo.webp"), out.toByteArray());
}

try (EncodedWebp out = encoder.encodeStillPng(pngBytes, opts, WebpMetadata.EMPTY)) {
    Files.write(Path.of("photo.webp"), out.toByteArray());
}
```

Both methods have `byte[]` and `MemorySegment` overloads. The `byte[]` overloads
copy into a confined arena for the duration of the call; use the
`MemorySegment` overloads when you already hold native memory (e.g. from
`MemorySegment.ofBuffer`) to avoid the copy.

## 6. Animation encode — raw frames

```java
List<WebpFrame> frames = new ArrayList<>();
try (Arena arena = Arena.ofShared()) {
    for (Image img : sourceFrames) {
        MemorySegment rgba = arena.allocate((long) img.w * img.h * 4);
        // ... fill rgba ...
        frames.add(new WebpFrame(rgba, img.w, img.h, img.w * 4,
                                  PixelFormat.RGBA, /*durationMs*/ 40));
    }

    WebpAnimationEncoder enc = new WebpAnimationEncoder();
    try (EncodedWebp out = enc.encodeRawFrames(
            frames,
            /*canvasWidth*/ 640, /*canvasHeight*/ 480,
            /*backgroundRgba*/ 0x00000000,
            opts, WebpMetadata.EMPTY)) {
        Files.write(Path.of("anim.webp"), out.toByteArray());
    }
}
```

The shared arena keeps every frame buffer alive for the duration of the native
call; closing the arena after `encodeRawFrames` returns is safe because
`EncodedWebp` owns its own malloc'd output buffer.

## 7. Animation encode — JPEG / PNG byte frames

Use these when your source is already compressed (common for webcam / MJPEG
streams or PNG sprite sequences). The native layer decodes each frame with
libjpeg-turbo or libpng and drives `WebPAnimEncoder` directly.

```java
List<WebpCompressedFrame> frames = List.of(
    new WebpCompressedFrame(jpegSegment1, 40),
    new WebpCompressedFrame(jpegSegment2, 40),
    new WebpCompressedFrame(jpegSegment3, 40)
);

try (EncodedWebp out = new WebpAnimationEncoder().encodeJpegFrames(
        frames, canvasW, canvasH, /*bgRgba*/ 0, opts, WebpMetadata.EMPTY)) {
    Files.write(Path.of("anim.webp"), out.toByteArray());
}
```

`encodePngFrames(...)` is the PNG equivalent.

## 8. Attaching metadata

```java
try (Arena arena = Arena.ofConfined()) {
    MemorySegment exif = arena.allocate(exifBytes.length);
    MemorySegment.copy(MemorySegment.ofArray(exifBytes), 0, exif, 0, exifBytes.length);

    WebpMetadata meta = new WebpMetadata(exif, /*iccp*/ MemorySegment.NULL, /*xmp*/ MemorySegment.NULL);
    try (EncodedWebp out = encoder.encodeStillRaw(image, opts, meta)) {
        // encoded WebP now contains an EXIF chunk
    }
}
```

Any of EXIF/ICCP/XMP may be `MemorySegment.NULL` to skip that chunk. Segment
sizes are derived from `byteSize()` — allocate exactly the payload length.

## 9. Consuming `EncodedWebp`

`EncodedWebp` holds one malloc-owned buffer. Call `close()` (usually via
try-with-resources) to release it deterministically. If you forget, a
`java.lang.ref.Cleaner` will free the buffer when the object becomes
unreachable, so the library won't leak, but prefer explicit close for bounded
memory behaviour.

```java
try (EncodedWebp out = encoder.encodeStillRaw(image, opts, WebpMetadata.EMPTY)) {
    MemorySegment view = out.bytes();       // zero-copy view, valid until close()
    long size          = out.size();         // byte length
    byte[] heapCopy    = out.toByteArray();  // copy to a heap byte[]
    // ... write to network/file ...
}
```

Double-close is safe. Accessing `bytes()` / `size()` / `toByteArray()` after
`close()` throws `IllegalStateException`.

## 10. Error handling

All encode methods throw `WebpException` (a `RuntimeException`) on failure.
Native-side failures propagate the libwebp status code:

```java
try {
    encoder.encodeStillJpeg(maybeCorruptJpeg, opts, WebpMetadata.EMPTY);
} catch (WebpException e) {
    e.nativeStatus().ifPresent(status ->
        log.warn("native WebP status={}", status));
    log.warn("encode failed: {}", e.getMessage(), e);
}
```

`nativeStatus()` returns `OptionalInt.empty()` for exceptions raised purely on
the Java side (e.g. invalid arguments before reaching native code).

## 11. Migrating from `uetlib-core`

The legacy `uetlib-core` WebP API shells out to `cwebp` / `img2webp` /
`webpmux` and exposes types like `WebpWriter`, `WebpUtils`, `WebpEncodingConfig`,
`Frame`, `LossyWebp`, `LosslessWebp`, `CompressionMethod`, and `PartialImage`.
This library replaces all of that with in-process libwebp via FFM. Map the old
call sites onto the new API as follows:

| `uetlib-core`                                                     | `uetlib-webp`                                                                              |
|-------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `WebpUtils.fromBufferedImage(image)`                              | `new WebpEncoder().encodeStillRaw(WebpRawImage…, options, WebpMetadata.EMPTY)`             |
| `WebpUtils.animationFromBufferedImages(images, config)`           | `new WebpAnimationEncoder().encodeRawFrames(List<WebpFrame>, w, h, 0, options, metadata)`  |
| `WebpUtils.animationFromExistingPngs(paths, config)`              | `new WebpAnimationEncoder().encodePngFrames(List<WebpCompressedFrame>, …)`                 |
| `WebpUtils.animationFromImagesAsByteArrays(jpegOrPngBytes, cfg)`  | `WebpAnimationEncoder.encodeJpegFrames(...)` / `encodePngFrames(...)` (pick by content)    |
| `WebpUtils.addMetadata(webp, xmp)`                                | Pass `WebpMetadata` at encode time — post-hoc metadata injection is no longer supported    |
| `new WebpWriter(config).encode(framePaths)` → `Path`              | Encode in memory with `WebpAnimationEncoder`; write the returned `byte[]` to disk yourself |
| `WebpEncodingConfig.builder().quality(new LossyWebp(75))`         | `WebpEncodeOptions.builder().quality(75f)`                                                 |
| `WebpEncodingConfig.builder().quality(LosslessWebp.INSTANCE)`     | `WebpEncodeOptions.builder().lossless(true)`                                               |
| `.method(CompressionMethod.M4)`                                   | `.method(4)` (plain `int`, 0–6)                                                            |
| `.compressionFactor(75)`                                          | `.quality(75f)` (lossy) — or drop entirely when `lossless(true)`                           |
| `.duration(Duration.ofMillis(d))` on the config                   | Per-frame `durationMs` on each `WebpFrame` / `WebpCompressedFrame`                         |
| `Frame.BufferedImageFrame` / `Frame.PathFrame`                    | `WebpFrame` (raw RGBA via `MemorySegment`) or `WebpCompressedFrame` (JPEG/PNG bytes)       |
| `PartialImage`                                                    | No equivalent — encode bytes directly, no temp-file marshalling                            |

Notes on behavior changes worth knowing during migration:

- `BufferedImage` → RGBA conversion is now the caller's responsibility; see
  [§4](#4-still-encode--raw-pixels) for the bulk `getRGB(0,0,w,h,…)` pattern.
- For JPEG/PNG inputs, prefer `encodeJpegFrames` / `encodePngFrames` —
  they decode natively (libjpeg-turbo / libpng) and skip the Java decode step.
- `EncodedWebp` is `AutoCloseable` and owns native memory until `close()`. Use
  it inside try-with-resources, or call `toByteArray()` once and discard.
- Errors arrive as `WebpException` with an optional native status code via
  `nativeStatus()` — see [§10](#10-error-handling).

## 12. Thread-safety

`WebpEncoder` and `WebpAnimationEncoder` are stateless — a single instance is
safe to share across threads. Each encode call allocates its own `Arena`
internally and does not mutate shared state. `WebpEncodeOptions` is immutable.

The native layer uses `threadLevel` on the `WebPConfig` to drive libwebp's own
worker threads; this is independent of caller threads.

## 13. Benchmarking

Running the suite:

```bash
./scripts/build-native.sh
./scripts/run-benchmarks.sh
```

See the main [`README.md`](../README.md) for the full benchmark flag reference.
