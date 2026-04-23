#include "uetlib_webp.h"

#include <stdlib.h>
#include <string.h>

#include <png.h>
#include <turbojpeg.h>
#include <webp/encode.h>
#include <webp/mux.h>
#include <webp/mux_types.h>

typedef struct decoded_image {
    uint8_t* pixels;
    int width;
    int height;
    int stride;
    int pixel_format;
} decoded_image;

typedef struct png_memory_reader {
    const uint8_t* data;
    size_t size;
    size_t offset;
} png_memory_reader;

static void init_result(uetlib_webp_result* out_result) {
    out_result->data = NULL;
    out_result->size = 0;
    out_result->status = UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    out_result->error_message = NULL;
}

static char* copy_error(const char* message) {
    size_t n = strlen(message);
    char* out = (char*)malloc(n + 1);
    if (out == NULL) {
        return NULL;
    }
    memcpy(out, message, n + 1);
    return out;
}

static void set_error(uetlib_webp_result* out_result, uetlib_webp_status status, const char* message) {
    out_result->status = status;
    free(out_result->error_message);
    out_result->error_message = copy_error(message);
}

static int init_config(const uetlib_webp_encode_options* options, WebPConfig* config) {
    if (!WebPConfigInit(config)) {
        return 0;
    }

    config->lossless = options->lossless;
    config->quality = options->quality;
    config->method = options->method;
    config->exact = options->exact;
    config->near_lossless = options->near_lossless;
    config->target_size = options->target_size;
    config->target_PSNR = options->target_psnr;
    config->thread_level = options->thread_level;
    config->alpha_quality = options->alpha_quality;

    return WebPValidateConfig(config);
}

static int import_picture(const uetlib_webp_raw_image* image, WebPPicture* picture) {
    picture->width = image->width;
    picture->height = image->height;
    picture->use_argb = 1;

    if (!WebPPictureAlloc(picture)) {
        return 0;
    }

    switch (image->pixel_format) {
        case UETLIB_WEBP_PIXEL_RGBA:
            return WebPPictureImportRGBA(picture, image->pixels, image->stride);
        case UETLIB_WEBP_PIXEL_RGB:
            return WebPPictureImportRGB(picture, image->pixels, image->stride);
        case UETLIB_WEBP_PIXEL_BGRA:
            return WebPPictureImportBGRA(picture, image->pixels, image->stride);
        case UETLIB_WEBP_PIXEL_BGR:
            return WebPPictureImportBGR(picture, image->pixels, image->stride);
        default:
            return 0;
    }
}

/*
 * Transfer ownership of an already-malloc'd buffer to the result. The buffer
 * must be free()-compatible; on success the source pointer is cleared so the
 * caller's cleanup is a no-op.
 */
static void transfer_to_result(uint8_t** owned, size_t size, uetlib_webp_result* out_result) {
    out_result->data = *owned;
    out_result->size = size;
    out_result->status = UETLIB_WEBP_STATUS_OK;
    *owned = NULL;
}

static uetlib_webp_status copy_webp_data_to_result(const WebPData* data, uetlib_webp_result* out_result) {
    uint8_t* out = (uint8_t*)malloc(data->size);
    if (out == NULL) {
        return UETLIB_WEBP_STATUS_OOM;
    }
    memcpy(out, data->bytes, data->size);
    out_result->data = out;
    out_result->size = data->size;
    out_result->status = UETLIB_WEBP_STATUS_OK;
    return UETLIB_WEBP_STATUS_OK;
}

static int apply_metadata(WebPMux* mux, const uetlib_webp_metadata* metadata) {
    WebPMuxError mux_error;
    WebPData chunk;

    if (metadata == NULL) {
        return 1;
    }

    if (metadata->exif != NULL && metadata->exif_size > 0) {
        chunk.bytes = metadata->exif;
        chunk.size = metadata->exif_size;
        mux_error = WebPMuxSetChunk(mux, "EXIF", &chunk, 1);
        if (mux_error != WEBP_MUX_OK) {
            return 0;
        }
    }

    if (metadata->iccp != NULL && metadata->iccp_size > 0) {
        chunk.bytes = metadata->iccp;
        chunk.size = metadata->iccp_size;
        mux_error = WebPMuxSetChunk(mux, "ICCP", &chunk, 1);
        if (mux_error != WEBP_MUX_OK) {
            return 0;
        }
    }

    if (metadata->xmp != NULL && metadata->xmp_size > 0) {
        chunk.bytes = metadata->xmp;
        chunk.size = metadata->xmp_size;
        mux_error = WebPMuxSetChunk(mux, "XMP ", &chunk, 1);
        if (mux_error != WEBP_MUX_OK) {
            return 0;
        }
    }

    return 1;
}

static int has_any_metadata(const uetlib_webp_metadata* metadata) {
    return metadata != NULL &&
        ((metadata->exif != NULL && metadata->exif_size > 0) ||
         (metadata->iccp != NULL && metadata->iccp_size > 0) ||
         (metadata->xmp != NULL && metadata->xmp_size > 0));
}

/*
 * Apply metadata (if any) to an already-encoded WebP buffer that we own.
 * On success the result owns the final bytes and *encoded is freed.
 */
static uetlib_webp_status mux_metadata_owned(
    uint8_t** encoded, size_t encoded_size,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {

    if (!has_any_metadata(metadata)) {
        transfer_to_result(encoded, encoded_size, out_result);
        return UETLIB_WEBP_STATUS_OK;
    }

    WebPData input_data;
    input_data.bytes = *encoded;
    input_data.size = encoded_size;
    WebPMux* mux = WebPMuxCreate(&input_data, 1);
    if (mux == NULL) {
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }

    if (!apply_metadata(mux, metadata)) {
        WebPMuxDelete(mux);
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }

    WebPData assembled;
    WebPDataInit(&assembled);
    if (WebPMuxAssemble(mux, &assembled) != WEBP_MUX_OK) {
        WebPMuxDelete(mux);
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }

    uetlib_webp_status status = copy_webp_data_to_result(&assembled, out_result);
    WebPDataClear(&assembled);
    WebPMuxDelete(mux);
    return status;
}

static int valid_options(const uetlib_webp_encode_options* options) {
    return options != NULL &&
           options->struct_size >= sizeof(uetlib_webp_encode_options) &&
           options->abi_version == UETLIB_WEBP_ABI_VERSION;
}

static int valid_image(const uetlib_webp_raw_image* image) {
    return image != NULL &&
           image->struct_size >= sizeof(uetlib_webp_raw_image) &&
           image->abi_version == UETLIB_WEBP_ABI_VERSION &&
           image->pixels != NULL &&
           image->width > 0 &&
           image->height > 0 &&
           image->stride > 0;
}

static void png_read_from_memory(png_structp png_ptr, png_bytep out_bytes, png_size_t byte_count) {
    png_memory_reader* reader = (png_memory_reader*)png_get_io_ptr(png_ptr);
    if (reader == NULL || reader->offset + byte_count > reader->size) {
        png_error(png_ptr, "PNG read beyond input buffer");
        return;
    }
    memcpy(out_bytes, reader->data + reader->offset, byte_count);
    reader->offset += byte_count;
}

static int decode_png_rgba(const uint8_t* data, size_t size, decoded_image* out_image) {
    png_structp png_ptr = NULL;
    png_infop info_ptr = NULL;
    png_bytep* rows = NULL;
    png_memory_reader reader;
    png_uint_32 width;
    png_uint_32 height;
    int bit_depth;
    int color_type;
    int interlace_type;
    size_t rowbytes;
    uint8_t* pixels = NULL;
    png_uint_32 y;

    if (data == NULL || size < 8 || out_image == NULL || png_sig_cmp((png_bytep)data, 0, 8) != 0) {
        return 0;
    }

    png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    if (png_ptr == NULL) {
        return 0;
    }

    info_ptr = png_create_info_struct(png_ptr);
    if (info_ptr == NULL) {
        png_destroy_read_struct(&png_ptr, NULL, NULL);
        return 0;
    }

    if (setjmp(png_jmpbuf(png_ptr))) {
        free(rows);
        free(pixels);
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return 0;
    }

    reader.data = data;
    reader.size = size;
    reader.offset = 0;
    png_set_read_fn(png_ptr, &reader, png_read_from_memory);
    png_read_info(png_ptr, info_ptr);

    png_get_IHDR(
        png_ptr,
        info_ptr,
        &width,
        &height,
        &bit_depth,
        &color_type,
        &interlace_type,
        NULL,
        NULL);

    if (width == 0 || height == 0 || width > INT32_MAX || height > INT32_MAX) {
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return 0;
    }

    if (bit_depth == 16) {
        png_set_strip_16(png_ptr);
    }
    if (color_type == PNG_COLOR_TYPE_PALETTE) {
        png_set_palette_to_rgb(png_ptr);
    }
    if (color_type == PNG_COLOR_TYPE_GRAY && bit_depth < 8) {
        png_set_expand_gray_1_2_4_to_8(png_ptr);
    }
    if (png_get_valid(png_ptr, info_ptr, PNG_INFO_tRNS)) {
        png_set_tRNS_to_alpha(png_ptr);
    }
    if (color_type == PNG_COLOR_TYPE_GRAY || color_type == PNG_COLOR_TYPE_GRAY_ALPHA) {
        png_set_gray_to_rgb(png_ptr);
    }
    if ((color_type & PNG_COLOR_MASK_ALPHA) == 0) {
        png_set_filler(png_ptr, 0xFF, PNG_FILLER_AFTER);
    }

    /*
     * Adam7-interlaced PNGs require multiple read passes; without enabling
     * interlace handling png_read_image yields garbage on those inputs.
     */
    (void)png_set_interlace_handling(png_ptr);

    png_read_update_info(png_ptr, info_ptr);
    rowbytes = png_get_rowbytes(png_ptr, info_ptr);
    if (rowbytes < (size_t)width * 4u) {
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return 0;
    }

    rows = (png_bytep*)malloc(sizeof(png_bytep) * (size_t)height);
    pixels = (uint8_t*)malloc(rowbytes * (size_t)height);
    if (rows == NULL || pixels == NULL) {
        free(rows);
        free(pixels);
        png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
        return 0;
    }

    for (y = 0; y < height; y++) {
        rows[y] = pixels + (size_t)y * rowbytes;
    }
    png_read_image(png_ptr, rows);
    png_read_end(png_ptr, NULL);
    free(rows);
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);

    out_image->pixels = pixels;
    out_image->width = (int)width;
    out_image->height = (int)height;
    out_image->stride = (int)rowbytes;
    out_image->pixel_format = UETLIB_WEBP_PIXEL_RGBA;
    return 1;
}

static void apply_anim_options(WebPAnimEncoderOptions* anim_options,
                               const uetlib_webp_encode_options* options,
                               uint32_t bgcolor_rgba) {
    anim_options->anim_params.loop_count = options->loop_count;
    anim_options->anim_params.bgcolor = bgcolor_rgba;
    /*
     * Only override libwebp's keyframe-distance defaults (kmin=9, kmax=17 from
     * WebPAnimEncoderOptionsInit) when the caller explicitly set a non-zero value.
     * kmin == kmax == 0 forces a keyframe at every frame, which roughly doubles
     * the output size for slow-changing content like webcam timelapses — matching
     * img2webp's defaults requires letting libwebp keep its built-ins.
     */
    if (options->kmin > 0) {
        anim_options->kmin = options->kmin;
    }
    if (options->kmax > 0) {
        anim_options->kmax = options->kmax;
    }
    anim_options->allow_mixed = options->allow_mixed;
    anim_options->minimize_size = options->minimize_size;
}

/*
 * Common tail for animation encoders: flush the trailing NULL frame, assemble,
 * mux metadata if any, write to result, free everything.
 */
static uetlib_webp_status finalize_anim(WebPAnimEncoder* encoder,
                                        int timestamp_ms,
                                        const uetlib_webp_metadata* metadata,
                                        uetlib_webp_result* out_result,
                                        const char* assemble_error_label) {
    if (!WebPAnimEncoderAdd(encoder, NULL, timestamp_ms, NULL)) {
        WebPAnimEncoderDelete(encoder);
        set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoder flush failed");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }

    WebPData data;
    WebPDataInit(&data);
    if (!WebPAnimEncoderAssemble(encoder, &data)) {
        WebPAnimEncoderDelete(encoder);
        set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoderAssemble failed");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }

    uetlib_webp_status status;
    if (has_any_metadata(metadata)) {
        WebPMux* mux = WebPMuxCreate(&data, 1);
        if (mux == NULL) {
            WebPDataClear(&data);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, assemble_error_label);
            return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
        }
        if (!apply_metadata(mux, metadata)) {
            WebPMuxDelete(mux);
            WebPDataClear(&data);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, assemble_error_label);
            return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
        }
        WebPData assembled;
        WebPDataInit(&assembled);
        if (WebPMuxAssemble(mux, &assembled) != WEBP_MUX_OK) {
            WebPMuxDelete(mux);
            WebPDataClear(&data);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, assemble_error_label);
            return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
        }
        status = copy_webp_data_to_result(&assembled, out_result);
        WebPDataClear(&assembled);
        WebPMuxDelete(mux);
    } else {
        status = copy_webp_data_to_result(&data, out_result);
    }

    WebPDataClear(&data);
    WebPAnimEncoderDelete(encoder);

    if (status != UETLIB_WEBP_STATUS_OK) {
        set_error(out_result, status, assemble_error_label);
    }
    return status;
}

/*
 * Encode an already-described raw image to a standalone still WebP, then mux
 * metadata onto it if any was supplied.
 */
static uetlib_webp_status encode_still_raw_internal(
    const uetlib_webp_raw_image* image,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {

    WebPConfig config;
    if (!init_config(options, &config)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid encoder config");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    WebPPicture picture;
    WebPPictureInit(&picture);
    if (!import_picture(image, &picture)) {
        WebPPictureFree(&picture);
        set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "Failed to import image");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }

    WebPMemoryWriter writer;
    WebPMemoryWriterInit(&writer);
    picture.writer = WebPMemoryWrite;
    picture.custom_ptr = &writer;

    if (!WebPEncode(&config, &picture)) {
        WebPMemoryWriterClear(&writer);
        WebPPictureFree(&picture);
        set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPEncode failed");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }

    /* Take ownership of writer.mem so we can transfer it (or mux+free). */
    uint8_t* encoded = writer.mem;
    size_t encoded_size = writer.size;
    writer.mem = NULL;
    WebPMemoryWriterClear(&writer);
    WebPPictureFree(&picture);

    uetlib_webp_status status = mux_metadata_owned(&encoded, encoded_size, metadata, out_result);
    free(encoded); /* no-op on success (transfer cleared *encoded); frees on mux fallback */

    if (status != UETLIB_WEBP_STATUS_OK) {
        set_error(out_result, status, "Failed to finalize encoded output");
    }
    return status;
}

uetlib_webp_status uetlib_webp_encode_still_raw(
    const uetlib_webp_raw_image* image,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {
    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    if (!valid_image(image) || !valid_options(options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid image/options struct");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    return encode_still_raw_internal(image, options, metadata, out_result);
}

uetlib_webp_status uetlib_webp_encode_still_jpeg(
    const uint8_t* jpeg, size_t jpeg_size,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {
    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    if (jpeg == NULL || jpeg_size == 0 || !valid_options(options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid JPEG input/options");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    tjhandle decompressor = tjInitDecompress();
    if (decompressor == NULL) {
        set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to allocate TurboJPEG decompressor");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }

    int width = 0, height = 0, jpeg_subsamp = 0, jpeg_colorspace = 0;
    if (tjDecompressHeader3(decompressor, jpeg, (unsigned long)jpeg_size,
                            &width, &height, &jpeg_subsamp, &jpeg_colorspace) != 0) {
        const char* err = tjGetErrorStr2(decompressor);
        set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, err);
        tjDestroy(decompressor);
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }

    if (width <= 0 || height <= 0) {
        set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, "Invalid JPEG dimensions");
        tjDestroy(decompressor);
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }

    uint8_t* rgb = (uint8_t*)malloc((size_t)width * (size_t)height * 3u);
    if (rgb == NULL) {
        set_error(out_result, UETLIB_WEBP_STATUS_OOM, "Out of memory for JPEG decode buffer");
        tjDestroy(decompressor);
        return UETLIB_WEBP_STATUS_OOM;
    }

    if (tjDecompress2(decompressor, jpeg, (unsigned long)jpeg_size, rgb,
                      width, width * 3, height, TJPF_RGB, 0) != 0) {
        const char* err = tjGetErrorStr2(decompressor);
        set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, err);
        free(rgb);
        tjDestroy(decompressor);
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }
    tjDestroy(decompressor);

    uetlib_webp_raw_image raw;
    raw.struct_size = sizeof(uetlib_webp_raw_image);
    raw.abi_version = UETLIB_WEBP_ABI_VERSION;
    raw.pixels = rgb;
    raw.width = width;
    raw.height = height;
    raw.stride = width * 3;
    raw.pixel_format = UETLIB_WEBP_PIXEL_RGB;

    uetlib_webp_status status = encode_still_raw_internal(&raw, options, metadata, out_result);
    free(rgb);
    return status;
}

uetlib_webp_status uetlib_webp_encode_still_png(
    const uint8_t* png, size_t png_size,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {
    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    if (png == NULL || png_size == 0 || !valid_options(options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid PNG input/options");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    decoded_image decoded;
    if (!decode_png_rgba(png, png_size, &decoded)) {
        set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, "Failed to decode PNG");
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }

    uetlib_webp_raw_image raw;
    raw.struct_size = sizeof(uetlib_webp_raw_image);
    raw.abi_version = UETLIB_WEBP_ABI_VERSION;
    raw.pixels = decoded.pixels;
    raw.width = decoded.width;
    raw.height = decoded.height;
    raw.stride = decoded.stride;
    raw.pixel_format = decoded.pixel_format;

    uetlib_webp_status status = encode_still_raw_internal(&raw, options, metadata, out_result);
    free(decoded.pixels);
    return status;
}

uetlib_webp_status uetlib_webp_encode_anim_raw(
    const uetlib_webp_anim_job* job,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {
    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    if (job == NULL ||
        job->struct_size < sizeof(uetlib_webp_anim_job) ||
        job->abi_version != UETLIB_WEBP_ABI_VERSION ||
        job->frames == NULL ||
        job->frame_count == 0 ||
        job->canvas_width <= 0 ||
        job->canvas_height <= 0 ||
        !valid_options(options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid animation job/options struct");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    WebPConfig config;
    if (!init_config(options, &config)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid encoder config");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    WebPAnimEncoderOptions anim_options;
    if (!WebPAnimEncoderOptionsInit(&anim_options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to init animation options");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }
    apply_anim_options(&anim_options, options, job->bgcolor_rgba);

    WebPAnimEncoder* encoder = WebPAnimEncoderNew(job->canvas_width, job->canvas_height, &anim_options);
    if (encoder == NULL) {
        set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to allocate animation encoder");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }

    int timestamp_ms = 0;
    for (size_t i = 0; i < job->frame_count; i++) {
        const uetlib_webp_anim_frame* frame = &job->frames[i];
        if (frame->struct_size < sizeof(uetlib_webp_anim_frame) ||
            frame->abi_version != UETLIB_WEBP_ABI_VERSION ||
            frame->pixels == NULL ||
            frame->width != job->canvas_width ||
            frame->height != job->canvas_height ||
            frame->duration_ms <= 0) {
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid frame in animation job");
            return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
        }

        WebPPicture picture;
        WebPPictureInit(&picture);
        if (!import_picture((const uetlib_webp_raw_image*)frame, &picture)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "Failed to import animation frame");
            return UETLIB_WEBP_STATUS_ENCODE_ERROR;
        }

        if (!WebPAnimEncoderAdd(encoder, &picture, timestamp_ms, &config)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoderAdd failed");
            return UETLIB_WEBP_STATUS_ENCODE_ERROR;
        }

        timestamp_ms += frame->duration_ms;
        WebPPictureFree(&picture);
    }

    return finalize_anim(encoder, timestamp_ms, metadata, out_result, "Failed to finalize animation output");
}

/*
 * Validates the compressed-frame job header and the first frame, peeks the
 * canvas dimensions via the supplied probe callback, and (on success) leaves
 * canvas_w/canvas_h populated.
 */
typedef int (*compressed_canvas_probe_fn)(const uint8_t* data, size_t size,
                                          int* out_width, int* out_height,
                                          void* ctx, const char** error_message);

static uetlib_webp_status start_compressed_anim(
    const uetlib_webp_compressed_anim_job* job,
    const uetlib_webp_encode_options* options,
    compressed_canvas_probe_fn probe,
    void* probe_ctx,
    uetlib_webp_result* out_result,
    WebPAnimEncoder** out_encoder,
    WebPConfig* out_config,
    int* out_canvas_width,
    int* out_canvas_height) {

    if (job == NULL ||
        job->struct_size < sizeof(uetlib_webp_compressed_anim_job) ||
        job->abi_version != UETLIB_WEBP_ABI_VERSION ||
        job->frames == NULL ||
        job->frame_count == 0 ||
        !valid_options(options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid compressed animation job/options struct");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    const uetlib_webp_compressed_frame* first = &job->frames[0];
    if (first->struct_size < sizeof(uetlib_webp_compressed_frame) ||
        first->abi_version != UETLIB_WEBP_ABI_VERSION ||
        first->data == NULL ||
        first->size == 0 ||
        first->duration_ms <= 0) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid first compressed frame");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    int canvas_width = 0;
    int canvas_height = 0;
    const char* error_message = "Failed to read first compressed frame header";
    if (!probe(first->data, first->size, &canvas_width, &canvas_height, probe_ctx, &error_message)) {
        set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, error_message);
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }
    if (canvas_width <= 0 || canvas_height <= 0) {
        set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, "Invalid compressed frame dimensions");
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }
    if ((job->canvas_width > 0 && job->canvas_width != canvas_width) ||
        (job->canvas_height > 0 && job->canvas_height != canvas_height)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Compressed frame dimensions do not match requested canvas");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    if (!init_config(options, out_config)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid encoder config");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    WebPAnimEncoderOptions anim_options;
    if (!WebPAnimEncoderOptionsInit(&anim_options)) {
        set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to init animation options");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }
    apply_anim_options(&anim_options, options, job->bgcolor_rgba);

    *out_encoder = WebPAnimEncoderNew(canvas_width, canvas_height, &anim_options);
    if (*out_encoder == NULL) {
        set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to allocate animation encoder");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }
    *out_canvas_width = canvas_width;
    *out_canvas_height = canvas_height;
    return UETLIB_WEBP_STATUS_OK;
}

static int probe_jpeg(const uint8_t* data, size_t size, int* out_width, int* out_height,
                      void* ctx, const char** error_message) {
    tjhandle jpeg = (tjhandle)ctx;
    int subsamp = 0, colorspace = 0;
    if (tjDecompressHeader3(jpeg, data, (unsigned long)size,
                            out_width, out_height, &subsamp, &colorspace) != 0) {
        *error_message = "Failed to read first JPEG frame";
        return 0;
    }
    return 1;
}

static int probe_png(const uint8_t* data, size_t size, int* out_width, int* out_height,
                     void* ctx, const char** error_message) {
    (void)ctx;
    decoded_image probe;
    if (!decode_png_rgba(data, size, &probe)) {
        *error_message = "Failed to decode first PNG frame";
        return 0;
    }
    *out_width = probe.width;
    *out_height = probe.height;
    free(probe.pixels);
    return 1;
}

uetlib_webp_status uetlib_webp_encode_anim_jpegs(
    const uetlib_webp_compressed_anim_job* job,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {
    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    tjhandle jpeg = tjInitDecompress();
    if (jpeg == NULL) {
        set_error(out_result, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to allocate TurboJPEG decompressor");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }

    WebPAnimEncoder* encoder = NULL;
    WebPConfig config;
    int canvas_width = 0;
    int canvas_height = 0;
    uetlib_webp_status start = start_compressed_anim(job, options, probe_jpeg, jpeg,
                                                     out_result, &encoder, &config,
                                                     &canvas_width, &canvas_height);
    if (start != UETLIB_WEBP_STATUS_OK) {
        tjDestroy(jpeg);
        return start;
    }

    int timestamp_ms = 0;
    for (size_t i = 0; i < job->frame_count; i++) {
        const uetlib_webp_compressed_frame* frame = &job->frames[i];
        if (frame->struct_size < sizeof(uetlib_webp_compressed_frame) ||
            frame->abi_version != UETLIB_WEBP_ABI_VERSION ||
            frame->data == NULL ||
            frame->size == 0 ||
            frame->duration_ms <= 0) {
            WebPAnimEncoderDelete(encoder);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid JPEG frame in animation job");
            return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
        }

        int width = 0, height = 0, subsamp = 0, colorspace = 0;
        if (tjDecompressHeader3(jpeg, frame->data, (unsigned long)frame->size,
                                &width, &height, &subsamp, &colorspace) != 0) {
            WebPAnimEncoderDelete(encoder);
            const char* err = tjGetErrorStr2(jpeg);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, err);
            return UETLIB_WEBP_STATUS_DECODE_ERROR;
        }

        if (width != canvas_width || height != canvas_height) {
            WebPAnimEncoderDelete(encoder);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "JPEG frame dimensions differ");
            return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
        }

        uint8_t* rgb = (uint8_t*)malloc((size_t)width * (size_t)height * 3u);
        if (rgb == NULL) {
            WebPAnimEncoderDelete(encoder);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_OOM, "Out of memory for JPEG decode buffer");
            return UETLIB_WEBP_STATUS_OOM;
        }

        if (tjDecompress2(jpeg, frame->data, (unsigned long)frame->size, rgb,
                          width, width * 3, height, TJPF_RGB, 0) != 0) {
            free(rgb);
            WebPAnimEncoderDelete(encoder);
            const char* err = tjGetErrorStr2(jpeg);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, err);
            return UETLIB_WEBP_STATUS_DECODE_ERROR;
        }

        uetlib_webp_raw_image raw;
        raw.struct_size = sizeof(uetlib_webp_raw_image);
        raw.abi_version = UETLIB_WEBP_ABI_VERSION;
        raw.pixels = rgb;
        raw.width = width;
        raw.height = height;
        raw.stride = width * 3;
        raw.pixel_format = UETLIB_WEBP_PIXEL_RGB;

        WebPPicture picture;
        WebPPictureInit(&picture);
        if (!import_picture(&raw, &picture)) {
            free(rgb);
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(encoder);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "Failed to import decoded JPEG frame");
            return UETLIB_WEBP_STATUS_ENCODE_ERROR;
        }
        free(rgb);

        if (!WebPAnimEncoderAdd(encoder, &picture, timestamp_ms, &config)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(encoder);
            tjDestroy(jpeg);
            set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoderAdd failed for JPEG frame");
            return UETLIB_WEBP_STATUS_ENCODE_ERROR;
        }

        timestamp_ms += frame->duration_ms;
        WebPPictureFree(&picture);
    }

    uetlib_webp_status final_status = finalize_anim(encoder, timestamp_ms, metadata, out_result,
                                                    "Failed to finalize JPEG animation output");
    tjDestroy(jpeg);
    return final_status;
}

uetlib_webp_status uetlib_webp_encode_anim_pngs(
    const uetlib_webp_compressed_anim_job* job,
    const uetlib_webp_encode_options* options,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {
    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    WebPAnimEncoder* encoder = NULL;
    WebPConfig config;
    int canvas_width = 0;
    int canvas_height = 0;
    uetlib_webp_status start = start_compressed_anim(job, options, probe_png, NULL,
                                                     out_result, &encoder, &config,
                                                     &canvas_width, &canvas_height);
    if (start != UETLIB_WEBP_STATUS_OK) {
        return start;
    }

    int timestamp_ms = 0;
    for (size_t i = 0; i < job->frame_count; i++) {
        const uetlib_webp_compressed_frame* frame = &job->frames[i];
        if (frame->struct_size < sizeof(uetlib_webp_compressed_frame) ||
            frame->abi_version != UETLIB_WEBP_ABI_VERSION ||
            frame->data == NULL ||
            frame->size == 0 ||
            frame->duration_ms <= 0) {
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid PNG frame in animation job");
            return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
        }

        decoded_image decoded;
        if (!decode_png_rgba(frame->data, frame->size, &decoded)) {
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_DECODE_ERROR, "Failed to decode PNG frame");
            return UETLIB_WEBP_STATUS_DECODE_ERROR;
        }

        if (decoded.width != canvas_width || decoded.height != canvas_height) {
            free(decoded.pixels);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "PNG frame dimensions differ");
            return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
        }

        uetlib_webp_raw_image raw;
        raw.struct_size = sizeof(uetlib_webp_raw_image);
        raw.abi_version = UETLIB_WEBP_ABI_VERSION;
        raw.pixels = decoded.pixels;
        raw.width = decoded.width;
        raw.height = decoded.height;
        raw.stride = decoded.stride;
        raw.pixel_format = decoded.pixel_format;

        WebPPicture picture;
        WebPPictureInit(&picture);
        if (!import_picture(&raw, &picture)) {
            free(decoded.pixels);
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "Failed to import decoded PNG frame");
            return UETLIB_WEBP_STATUS_ENCODE_ERROR;
        }
        free(decoded.pixels);

        if (!WebPAnimEncoderAdd(encoder, &picture, timestamp_ms, &config)) {
            WebPPictureFree(&picture);
            WebPAnimEncoderDelete(encoder);
            set_error(out_result, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoderAdd failed for PNG frame");
            return UETLIB_WEBP_STATUS_ENCODE_ERROR;
        }

        timestamp_ms += frame->duration_ms;
        WebPPictureFree(&picture);
    }

    return finalize_anim(encoder, timestamp_ms, metadata, out_result,
                         "Failed to finalize PNG animation output");
}

/* ---------- Streaming animation encoder --------------------------------------------------- */

struct uetlib_webp_anim_stream {
    WebPAnimEncoder* encoder;
    WebPConfig config;
    int canvas_width;
    int canvas_height;
    int timestamp_ms;
    tjhandle jpeg_decoder; /* lazily created on first add_jpeg */
};

static void stream_destroy(uetlib_webp_anim_stream* stream) {
    if (stream == NULL) {
        return;
    }
    if (stream->encoder != NULL) {
        WebPAnimEncoderDelete(stream->encoder);
    }
    if (stream->jpeg_decoder != NULL) {
        tjDestroy(stream->jpeg_decoder);
    }
    free(stream);
}

/*
 * The non-finish entry points report errors via out_error->error_message only;
 * they never produce a data buffer. init_result is used so callers see a clean
 * struct on the success path too.
 */
uetlib_webp_status uetlib_webp_anim_stream_open(
    int canvas_width, int canvas_height, uint32_t bgcolor_rgba,
    const uetlib_webp_encode_options* options,
    uetlib_webp_anim_stream** out_stream,
    uetlib_webp_result* out_error) {

    if (out_error == NULL || out_stream == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_error);
    *out_stream = NULL;

    if (canvas_width <= 0 || canvas_height <= 0 || !valid_options(options)) {
        set_error(out_error, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid stream open arguments");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    uetlib_webp_anim_stream* stream = (uetlib_webp_anim_stream*)calloc(1, sizeof(*stream));
    if (stream == NULL) {
        set_error(out_error, UETLIB_WEBP_STATUS_OOM, "Out of memory for stream handle");
        return UETLIB_WEBP_STATUS_OOM;
    }

    if (!init_config(options, &stream->config)) {
        free(stream);
        set_error(out_error, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid encoder config");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    WebPAnimEncoderOptions anim_options;
    if (!WebPAnimEncoderOptionsInit(&anim_options)) {
        free(stream);
        set_error(out_error, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to init animation options");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }
    apply_anim_options(&anim_options, options, bgcolor_rgba);

    stream->encoder = WebPAnimEncoderNew(canvas_width, canvas_height, &anim_options);
    if (stream->encoder == NULL) {
        free(stream);
        set_error(out_error, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to allocate animation encoder");
        return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
    }
    stream->canvas_width = canvas_width;
    stream->canvas_height = canvas_height;
    stream->timestamp_ms = 0;
    stream->jpeg_decoder = NULL;

    *out_stream = stream;
    return UETLIB_WEBP_STATUS_OK;
}

/*
 * Decode one JPEG via TurboJPEG into a transient RGB buffer, hand it to the encoder,
 * release the buffer immediately. Caller's jpeg bytes are read once and not retained.
 */
uetlib_webp_status uetlib_webp_anim_stream_add_jpeg(
    uetlib_webp_anim_stream* stream,
    const uint8_t* jpeg, size_t jpeg_size,
    int duration_ms,
    uetlib_webp_result* out_error) {

    if (out_error == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_error);

    if (stream == NULL || jpeg == NULL || jpeg_size == 0 || duration_ms <= 0) {
        set_error(out_error, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid add_jpeg arguments");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    if (stream->jpeg_decoder == NULL) {
        stream->jpeg_decoder = tjInitDecompress();
        if (stream->jpeg_decoder == NULL) {
            set_error(out_error, UETLIB_WEBP_STATUS_INTERNAL_ERROR, "Failed to allocate TurboJPEG decompressor");
            return UETLIB_WEBP_STATUS_INTERNAL_ERROR;
        }
    }

    int width = 0, height = 0, subsamp = 0, colorspace = 0;
    if (tjDecompressHeader3(stream->jpeg_decoder, jpeg, (unsigned long)jpeg_size,
                            &width, &height, &subsamp, &colorspace) != 0) {
        set_error(out_error, UETLIB_WEBP_STATUS_DECODE_ERROR, tjGetErrorStr2(stream->jpeg_decoder));
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }
    if (width != stream->canvas_width || height != stream->canvas_height) {
        set_error(out_error, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "JPEG frame dimensions differ from canvas");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    uint8_t* rgb = (uint8_t*)malloc((size_t)width * (size_t)height * 3u);
    if (rgb == NULL) {
        set_error(out_error, UETLIB_WEBP_STATUS_OOM, "Out of memory for JPEG decode buffer");
        return UETLIB_WEBP_STATUS_OOM;
    }
    if (tjDecompress2(stream->jpeg_decoder, jpeg, (unsigned long)jpeg_size, rgb,
                      width, width * 3, height, TJPF_RGB, 0) != 0) {
        const char* err = tjGetErrorStr2(stream->jpeg_decoder);
        free(rgb);
        set_error(out_error, UETLIB_WEBP_STATUS_DECODE_ERROR, err);
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }

    uetlib_webp_raw_image raw;
    raw.struct_size = sizeof(uetlib_webp_raw_image);
    raw.abi_version = UETLIB_WEBP_ABI_VERSION;
    raw.pixels = rgb;
    raw.width = width;
    raw.height = height;
    raw.stride = width * 3;
    raw.pixel_format = UETLIB_WEBP_PIXEL_RGB;

    WebPPicture picture;
    WebPPictureInit(&picture);
    if (!import_picture(&raw, &picture)) {
        free(rgb);
        WebPPictureFree(&picture);
        set_error(out_error, UETLIB_WEBP_STATUS_ENCODE_ERROR, "Failed to import decoded JPEG frame");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }
    free(rgb);

    if (!WebPAnimEncoderAdd(stream->encoder, &picture, stream->timestamp_ms, &stream->config)) {
        WebPPictureFree(&picture);
        set_error(out_error, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoderAdd failed for JPEG frame");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }
    WebPPictureFree(&picture);
    stream->timestamp_ms += duration_ms;
    return UETLIB_WEBP_STATUS_OK;
}

uetlib_webp_status uetlib_webp_anim_stream_add_png(
    uetlib_webp_anim_stream* stream,
    const uint8_t* png, size_t png_size,
    int duration_ms,
    uetlib_webp_result* out_error) {

    if (out_error == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_error);

    if (stream == NULL || png == NULL || png_size == 0 || duration_ms <= 0) {
        set_error(out_error, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Invalid add_png arguments");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    decoded_image decoded;
    if (!decode_png_rgba(png, png_size, &decoded)) {
        set_error(out_error, UETLIB_WEBP_STATUS_DECODE_ERROR, "Failed to decode PNG frame");
        return UETLIB_WEBP_STATUS_DECODE_ERROR;
    }
    if (decoded.width != stream->canvas_width || decoded.height != stream->canvas_height) {
        free(decoded.pixels);
        set_error(out_error, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "PNG frame dimensions differ from canvas");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    uetlib_webp_raw_image raw;
    raw.struct_size = sizeof(uetlib_webp_raw_image);
    raw.abi_version = UETLIB_WEBP_ABI_VERSION;
    raw.pixels = decoded.pixels;
    raw.width = decoded.width;
    raw.height = decoded.height;
    raw.stride = decoded.stride;
    raw.pixel_format = decoded.pixel_format;

    WebPPicture picture;
    WebPPictureInit(&picture);
    if (!import_picture(&raw, &picture)) {
        free(decoded.pixels);
        WebPPictureFree(&picture);
        set_error(out_error, UETLIB_WEBP_STATUS_ENCODE_ERROR, "Failed to import decoded PNG frame");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }
    free(decoded.pixels);

    if (!WebPAnimEncoderAdd(stream->encoder, &picture, stream->timestamp_ms, &stream->config)) {
        WebPPictureFree(&picture);
        set_error(out_error, UETLIB_WEBP_STATUS_ENCODE_ERROR, "WebPAnimEncoderAdd failed for PNG frame");
        return UETLIB_WEBP_STATUS_ENCODE_ERROR;
    }
    WebPPictureFree(&picture);
    stream->timestamp_ms += duration_ms;
    return UETLIB_WEBP_STATUS_OK;
}

uetlib_webp_status uetlib_webp_anim_stream_finish(
    uetlib_webp_anim_stream* stream,
    const uetlib_webp_metadata* metadata,
    uetlib_webp_result* out_result) {

    if (out_result == NULL) {
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }
    init_result(out_result);

    if (stream == NULL) {
        set_error(out_result, UETLIB_WEBP_STATUS_INVALID_ARGUMENT, "Stream handle is NULL");
        return UETLIB_WEBP_STATUS_INVALID_ARGUMENT;
    }

    /* finalize_anim takes ownership of `encoder` and deletes it. We must NOT also
     * delete it in stream_destroy below — null it out first. */
    WebPAnimEncoder* encoder = stream->encoder;
    stream->encoder = NULL;

    uetlib_webp_status status = finalize_anim(encoder, stream->timestamp_ms, metadata, out_result,
                                              "Failed to finalize streamed animation output");
    stream_destroy(stream);
    return status;
}

void uetlib_webp_anim_stream_abort(uetlib_webp_anim_stream* stream) {
    stream_destroy(stream);
}

void uetlib_webp_free_result(uetlib_webp_result* result) {
    if (result == NULL) {
        return;
    }
    free(result->data);
    result->data = NULL;
    result->size = 0;
    free(result->error_message);
    result->error_message = NULL;
    result->status = UETLIB_WEBP_STATUS_OK;
}

void uetlib_webp_free_buffer(void* ptr) {
    free(ptr);
}
