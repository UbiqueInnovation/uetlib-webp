#ifndef UETLIB_WEBP_H
#define UETLIB_WEBP_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define UETLIB_WEBP_ABI_VERSION 1u

typedef enum uetlib_webp_status {
    UETLIB_WEBP_STATUS_OK = 0,
    UETLIB_WEBP_STATUS_INVALID_ARGUMENT = 1,
    UETLIB_WEBP_STATUS_ENCODE_ERROR = 2,
    UETLIB_WEBP_STATUS_DECODE_ERROR = 3,
    UETLIB_WEBP_STATUS_INTERNAL_ERROR = 4,
    UETLIB_WEBP_STATUS_OOM = 5,
    UETLIB_WEBP_STATUS_UNSUPPORTED = 6
} uetlib_webp_status;

typedef enum uetlib_webp_pixel_format {
    UETLIB_WEBP_PIXEL_RGBA = 1,
    UETLIB_WEBP_PIXEL_RGB = 2,
    UETLIB_WEBP_PIXEL_BGRA = 3,
    UETLIB_WEBP_PIXEL_BGR = 4
} uetlib_webp_pixel_format;

typedef struct uetlib_webp_encode_options {
    uint32_t struct_size;
    uint32_t abi_version;
    int lossless;
    float quality;
    int method;
    int exact;
    int near_lossless;
    int target_size;
    float target_psnr;
    int thread_level;
    int alpha_quality;
    int loop_count;
    int kmin;
    int kmax;
    int allow_mixed;
    int minimize_size;
} uetlib_webp_encode_options;

typedef struct uetlib_webp_metadata {
    const uint8_t* exif;
    size_t exif_size;
    const uint8_t* iccp;
    size_t iccp_size;
    const uint8_t* xmp;
    size_t xmp_size;
} uetlib_webp_metadata;

typedef struct uetlib_webp_raw_image {
    uint32_t struct_size;
    uint32_t abi_version;
    const uint8_t* pixels;
    int width;
    int height;
    int stride;
    int pixel_format;
} uetlib_webp_raw_image;

typedef struct uetlib_webp_anim_frame {
    uint32_t struct_size;
    uint32_t abi_version;
    const uint8_t* pixels;
    int width;
    int height;
    int stride;
    int pixel_format;
    int duration_ms;
} uetlib_webp_anim_frame;

typedef struct uetlib_webp_anim_job {
    uint32_t struct_size;
    uint32_t abi_version;
    const uetlib_webp_anim_frame* frames;
    size_t frame_count;
    int canvas_width;
    int canvas_height;
    uint32_t bgcolor_rgba;
} uetlib_webp_anim_job;

typedef struct uetlib_webp_compressed_frame {
    uint32_t struct_size;
    uint32_t abi_version;
    const uint8_t* data;
    size_t size;
    int duration_ms;
} uetlib_webp_compressed_frame;

typedef struct uetlib_webp_compressed_anim_job {
    uint32_t struct_size;
    uint32_t abi_version;
    const uetlib_webp_compressed_frame* frames;
    size_t frame_count;
    int canvas_width;
    int canvas_height;
    uint32_t bgcolor_rgba;
} uetlib_webp_compressed_anim_job;

typedef struct uetlib_webp_result {
    uint8_t* data;
    size_t size;
    int status;
    char* error_message;
} uetlib_webp_result;

uetlib_webp_status uetlib_webp_encode_still_raw(
    const uetlib_webp_raw_image* image,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

uetlib_webp_status uetlib_webp_encode_anim_raw(
    const uetlib_webp_anim_job* job,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

uetlib_webp_status uetlib_webp_encode_anim_jpegs(
    const uetlib_webp_compressed_anim_job* job,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

uetlib_webp_status uetlib_webp_encode_anim_pngs(
    const uetlib_webp_compressed_anim_job* job,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

/* Convenience: decode JPEG bytes via libjpeg-turbo and encode as a still WebP. */
uetlib_webp_status uetlib_webp_encode_still_jpeg(
    const uint8_t* jpeg, size_t jpeg_size,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

/* Convenience: decode PNG bytes via libpng and encode as a still WebP. */
uetlib_webp_status uetlib_webp_encode_still_png(
    const uint8_t* png, size_t png_size,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

/* ---------- Streaming animation encoder ----------------------------------------------------
 *
 * Push frames one at a time without buffering them in caller memory. The native side decodes
 * each frame, hands it to the WebPAnimEncoder, and immediately frees the per-frame work
 * buffer. Peak memory becomes O(one decoded frame + accumulated WebP output), independent of
 * frame count.
 *
 * Lifecycle:
 *   open()                 -> opaque handle
 *   add_jpeg() / add_png() -> repeated, in capture order
 *   finish()               -> assembles + muxes metadata, frees handle, returns bytes
 *   abort()                -> frees handle without producing output (use on error path)
 *
 * The handle is NOT thread-safe: a single stream must be driven by a single thread.
 * Distinct streams are independent.
 */
typedef struct uetlib_webp_anim_stream uetlib_webp_anim_stream;

uetlib_webp_status uetlib_webp_anim_stream_open(
    int canvas_width, int canvas_height, uint32_t bgcolor_rgba,
    const uetlib_webp_encode_options* options,
    uetlib_webp_anim_stream** out_stream,
    uetlib_webp_result* out_error);

uetlib_webp_status uetlib_webp_anim_stream_add_jpeg(
    uetlib_webp_anim_stream* stream,
    const uint8_t* jpeg, size_t jpeg_size,
    int duration_ms,
    uetlib_webp_result* out_error);

uetlib_webp_status uetlib_webp_anim_stream_add_png(
    uetlib_webp_anim_stream* stream,
    const uint8_t* png, size_t png_size,
    int duration_ms,
    uetlib_webp_result* out_error);

uetlib_webp_status uetlib_webp_anim_stream_finish(
    uetlib_webp_anim_stream* stream,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result);

void uetlib_webp_anim_stream_abort(uetlib_webp_anim_stream* stream);

void uetlib_webp_free_result(uetlib_webp_result* result);

/* Free a single buffer that was returned in uetlib_webp_result.data /
 * uetlib_webp_result.error_message. Use when you have already extracted
 * the pointers and no longer hold the result struct. */
void uetlib_webp_free_buffer(void* ptr);

#ifdef __cplusplus
}
#endif

#endif
