# Architecture

## Repository layout

- `src/main/java/ch/ubique/uetlib/image/webp`
  - Java API surface (`WebpEncoder`, `WebpAnimationEncoder`, options/frames/metadata)
  - `EncodedWebp` lifecycle wrapper for native-owned bytes
- `src/main/java/ch/ubique/uetlib/image/webp/ffi`
  - FFM bridge (`NativeWebpLibrary`, struct layouts, downcalls)
- `native/include/uetlib_webp.h`
  - Stable C ABI header
- `native/src/uetlib_webp.c`
  - Native implementation using `libwebp` + `libwebpmux`
- `native/CMakeLists.txt`
  - Native build configuration
- `src/jmh/java/ch/ubique/uetlib/image/webp/bench`
  - JMH benchmark suite (fast path vs CLI+temp-file baseline)
- `docs/*.md`
  - Plan, benchmark plan, build matrix

## Memory ownership and lifecycle

- Input pixel buffers are owned by Java caller.
- Output bytes are allocated in native (`malloc`) and returned via `uetlib_webp_result`.
- Java wraps the result in `EncodedWebp` (`AutoCloseable`).
- `EncodedWebp.close()` calls `uetlib_webp_free_result(...)` exactly once.

## Native ABI summary

- `uetlib_webp_encode_still_raw(...)`
- `uetlib_webp_encode_anim_raw(...)`
- `uetlib_webp_free_result(...)`
- Metadata attachment supported for EXIF/ICCP/XMP through `libwebpmux`.
- ABI versioned via `abi_version` field in every major struct.

## Performance-critical choices

- Entire encode pipeline is in-process and in-memory.
- Animation encoding uses one native call for full job.
- Avoids Java image stack in hot path (`BufferedImage`/`ImageIO` not required).
- Raw pixel path is first-class and minimal conversion.
