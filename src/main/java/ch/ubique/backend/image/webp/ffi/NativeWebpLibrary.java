package ch.ubique.backend.image.webp.ffi;

import static ch.ubique.backend.image.webp.ffi.NativeLayouts.ANIM_FRAME;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.ANIM_JOB;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.COMPRESSED_ANIM_JOB;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.COMPRESSED_FRAME;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.C_INT;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.C_POINTER;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.C_SIZE_T;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.ENCODE_OPTIONS;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.METADATA;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.RAW_IMAGE;
import static ch.ubique.backend.image.webp.ffi.NativeLayouts.RESULT;

import ch.ubique.backend.image.webp.EncodedWebp;
import ch.ubique.backend.image.webp.NativeLibraryLoader;
import ch.ubique.backend.image.webp.WebpCompressedFrame;
import ch.ubique.backend.image.webp.WebpEncodeOptions;
import ch.ubique.backend.image.webp.WebpException;
import ch.ubique.backend.image.webp.WebpFrame;
import ch.ubique.backend.image.webp.WebpMetadata;
import ch.ubique.backend.image.webp.WebpRawImage;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Hand-written FFM bridge to the {@code uetlib_webp_native} library.
 *
 * <p>Field accessors are exposed as nested classes — one per C struct — that look like jextract
 * output: each field has a {@code VarHandle} derived from the struct layout via {@link
 * MemoryLayout#varHandle(PathElement...)}, with typed setters/getters that hide the {@code long}
 * offset bookkeeping. Future migration to a real jextract run should be a near-mechanical
 * replacement of these accessor classes.
 */
public final class NativeWebpLibrary {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP;

    private static final MethodHandle ENCODE_STILL_RAW;
    private static final MethodHandle ENCODE_STILL_JPEG;
    private static final MethodHandle ENCODE_STILL_PNG;
    private static final MethodHandle ENCODE_ANIM_RAW;
    private static final MethodHandle ENCODE_ANIM_JPEGS;
    private static final MethodHandle ENCODE_ANIM_PNGS;
    private static final MethodHandle FREE_BUFFER;
    private static final MethodHandle STREAM_OPEN;
    private static final MethodHandle STREAM_ADD_JPEG;
    private static final MethodHandle STREAM_ADD_PNG;
    private static final MethodHandle STREAM_ADD_WEBP;
    private static final MethodHandle STREAM_FINISH;
    private static final MethodHandle STREAM_ABORT;

    static {
        NativeLibraryLoader.ensureLoaded();
        LOOKUP = SymbolLookup.loaderLookup();
        ENCODE_STILL_RAW =
                downcall(
                        "uetlib_webp_encode_still_raw",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
        ENCODE_STILL_JPEG =
                downcall(
                        "uetlib_webp_encode_still_jpeg",
                        FunctionDescriptor.of(
                                C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER, C_POINTER));
        ENCODE_STILL_PNG =
                downcall(
                        "uetlib_webp_encode_still_png",
                        FunctionDescriptor.of(
                                C_INT, C_POINTER, C_SIZE_T, C_POINTER, C_POINTER, C_POINTER));
        ENCODE_ANIM_RAW =
                downcall(
                        "uetlib_webp_encode_anim_raw",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
        ENCODE_ANIM_JPEGS =
                downcall(
                        "uetlib_webp_encode_anim_jpegs",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
        ENCODE_ANIM_PNGS =
                downcall(
                        "uetlib_webp_encode_anim_pngs",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
        FREE_BUFFER = downcall("uetlib_webp_free_buffer", FunctionDescriptor.ofVoid(C_POINTER));
        STREAM_OPEN =
                downcall(
                        "uetlib_webp_anim_stream_open",
                        FunctionDescriptor.of(
                                C_INT, C_INT, C_INT, C_INT, C_POINTER, C_POINTER, C_POINTER));
        STREAM_ADD_JPEG =
                downcall(
                        "uetlib_webp_anim_stream_add_jpeg",
                        FunctionDescriptor.of(
                                C_INT, C_POINTER, C_POINTER, C_SIZE_T, C_INT, C_POINTER));
        STREAM_ADD_PNG =
                downcall(
                        "uetlib_webp_anim_stream_add_png",
                        FunctionDescriptor.of(
                                C_INT, C_POINTER, C_POINTER, C_SIZE_T, C_INT, C_POINTER));
        STREAM_ADD_WEBP =
                downcall(
                        "uetlib_webp_anim_stream_add_webp",
                        FunctionDescriptor.of(
                                C_INT, C_POINTER, C_POINTER, C_SIZE_T, C_INT, C_POINTER));
        STREAM_FINISH =
                downcall(
                        "uetlib_webp_anim_stream_finish",
                        FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER));
        STREAM_ABORT =
                downcall("uetlib_webp_anim_stream_abort", FunctionDescriptor.ofVoid(C_POINTER));
    }

    private NativeWebpLibrary() {}

    public static EncodedWebp encodeStillRaw(
            final WebpRawImage image,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        try {
            final Arena scope = Arena.ofShared();
            final MemorySegment raw = scope.allocate(RAW_IMAGE);
            final MemorySegment opts = scope.allocate(ENCODE_OPTIONS);
            final MemorySegment meta = scope.allocate(METADATA);
            final MemorySegment result = scope.allocate(RESULT);

            RawImage.fill(raw, image);
            EncodeOptions.fill(opts, options);
            Metadata.fill(meta, metadata, scope);

            final int status = (int) ENCODE_STILL_RAW.invoke(raw, opts, meta, result);
            return toEncodedOrThrow(status, result, scope);
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    public static EncodedWebp encodeStillJpeg(
            final MemorySegment jpegBytes,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        return encodeStillCompressed(ENCODE_STILL_JPEG, jpegBytes, options, metadata);
    }

    public static EncodedWebp encodeStillPng(
            final MemorySegment pngBytes,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        return encodeStillCompressed(ENCODE_STILL_PNG, pngBytes, options, metadata);
    }

    private static EncodedWebp encodeStillCompressed(
            final MethodHandle handle,
            final MemorySegment compressedBytes,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        if (compressedBytes == null
                || compressedBytes.equals(MemorySegment.NULL)
                || compressedBytes.byteSize() <= 0) {
            throw new WebpException("compressed bytes must be non-null and non-empty");
        }
        try {
            final Arena scope = Arena.ofShared();
            final MemorySegment opts = scope.allocate(ENCODE_OPTIONS);
            final MemorySegment meta = scope.allocate(METADATA);
            final MemorySegment result = scope.allocate(RESULT);
            EncodeOptions.fill(opts, options);
            Metadata.fill(meta, metadata, scope);

            final int status =
                    (int)
                            handle.invoke(
                                    compressedBytes,
                                    compressedBytes.byteSize(),
                                    opts,
                                    meta,
                                    result);
            return toEncodedOrThrow(status, result, scope);
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    public static EncodedWebp encodeAnimRaw(
            final List<WebpFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        return encodeRawAnimation(
                ENCODE_ANIM_RAW, frames, canvasWidth, canvasHeight, bgcolorRgba, options, metadata);
    }

    public static EncodedWebp encodeAnimJpegs(
            final List<WebpCompressedFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        return encodeCompressedAnimation(
                ENCODE_ANIM_JPEGS,
                frames,
                canvasWidth,
                canvasHeight,
                bgcolorRgba,
                options,
                metadata);
    }

    public static EncodedWebp encodeAnimPngs(
            final List<WebpCompressedFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        return encodeCompressedAnimation(
                ENCODE_ANIM_PNGS,
                frames,
                canvasWidth,
                canvasHeight,
                bgcolorRgba,
                options,
                metadata);
    }

    private static EncodedWebp encodeRawAnimation(
            final MethodHandle handle,
            final List<WebpFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        try {
            final Arena scope = Arena.ofShared();
            final MemorySegment frameArray = scope.allocate(ANIM_FRAME, frames.size());
            for (int i = 0; i < frames.size(); i++) {
                AnimFrame.fill(
                        frameArray.asSlice(i * ANIM_FRAME.byteSize(), ANIM_FRAME.byteSize()),
                        frames.get(i));
            }
            final MemorySegment job = scope.allocate(ANIM_JOB);
            final MemorySegment opts = scope.allocate(ENCODE_OPTIONS);
            final MemorySegment meta = scope.allocate(METADATA);
            final MemorySegment result = scope.allocate(RESULT);
            AnimJob.fill(job, frameArray, frames.size(), canvasWidth, canvasHeight, bgcolorRgba);
            EncodeOptions.fill(opts, options);
            Metadata.fill(meta, metadata, scope);

            final int status = (int) handle.invoke(job, opts, meta, result);
            return toEncodedOrThrow(status, result, scope);
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    private static EncodedWebp encodeCompressedAnimation(
            final MethodHandle handle,
            final List<WebpCompressedFrame> frames,
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options,
            final WebpMetadata metadata) {
        try {
            final Arena scope = Arena.ofShared();
            final MemorySegment frameArray = scope.allocate(COMPRESSED_FRAME, frames.size());
            for (int i = 0; i < frames.size(); i++) {
                CompressedFrame.fill(
                        frameArray.asSlice(
                                i * COMPRESSED_FRAME.byteSize(), COMPRESSED_FRAME.byteSize()),
                        frames.get(i));
            }
            final MemorySegment job = scope.allocate(COMPRESSED_ANIM_JOB);
            final MemorySegment opts = scope.allocate(ENCODE_OPTIONS);
            final MemorySegment meta = scope.allocate(METADATA);
            final MemorySegment result = scope.allocate(RESULT);
            CompressedAnimJob.fill(
                    job, frameArray, frames.size(), canvasWidth, canvasHeight, bgcolorRgba);
            EncodeOptions.fill(opts, options);
            Metadata.fill(meta, metadata, scope);

            final int status = (int) handle.invoke(job, opts, meta, result);
            return toEncodedOrThrow(status, result, scope);
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    /* ---------- Streaming animation encoder ---------------------------------------------- */

    /**
     * Opens a native streaming animation encoder. Returns the opaque handle address as a {@code
     * long}; the caller (typically {@code WebpAnimationStream}) is responsible for eventually
     * calling {@link #streamFinish} or {@link #streamAbort} on it.
     */
    public static long streamOpen(
            final int canvasWidth,
            final int canvasHeight,
            final int bgcolorRgba,
            final WebpEncodeOptions options) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment opts = arena.allocate(ENCODE_OPTIONS);
            final MemorySegment outStream = arena.allocate(C_POINTER);
            final MemorySegment outErr = arena.allocate(RESULT);
            EncodeOptions.fill(opts, options);

            final int status =
                    (int)
                            STREAM_OPEN.invoke(
                                    canvasWidth,
                                    canvasHeight,
                                    bgcolorRgba,
                                    opts,
                                    outStream,
                                    outErr);
            if (status != 0) {
                final String err = readErrorMessage(outErr);
                freeBuffer(Result.errorMessage(outErr).address());
                throw new WebpException(
                        "Failed to open WebP animation stream (status=" + status + "): " + err,
                        status);
            }
            // success path may also have set error_message to NULL — defensively free
            freeBuffer(Result.errorMessage(outErr).address());

            final MemorySegment handle = outStream.get(C_POINTER, 0L);
            if (handle.equals(MemorySegment.NULL) || handle.address() == 0L) {
                throw new WebpException("Native stream open returned NULL handle");
            }
            return handle.address();
        } catch (final WebpException e) {
            throw e;
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    public static void streamAddJpeg(
            final long handleAddress, final MemorySegment jpegBytes, final int durationMs) {
        streamAddCompressed(STREAM_ADD_JPEG, handleAddress, jpegBytes, durationMs, "JPEG");
    }

    public static void streamAddPng(
            final long handleAddress, final MemorySegment pngBytes, final int durationMs) {
        streamAddCompressed(STREAM_ADD_PNG, handleAddress, pngBytes, durationMs, "PNG");
    }

    public static void streamAddWebp(
            final long handleAddress, final MemorySegment webpBytes, final int durationMs) {
        streamAddCompressed(STREAM_ADD_WEBP, handleAddress, webpBytes, durationMs, "WebP");
    }

    private static void streamAddCompressed(
            final MethodHandle handle,
            final long handleAddress,
            final MemorySegment compressedBytes,
            final int durationMs,
            final String label) {
        if (handleAddress == 0L) {
            throw new WebpException("stream handle is closed");
        }
        if (compressedBytes == null
                || compressedBytes.equals(MemorySegment.NULL)
                || compressedBytes.byteSize() <= 0) {
            throw new WebpException(label + " bytes must be non-null and non-empty");
        }
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment outErr = arena.allocate(RESULT);
            final MemorySegment streamPtr = MemorySegment.ofAddress(handleAddress);
            final int status =
                    (int)
                            handle.invoke(
                                    streamPtr,
                                    compressedBytes,
                                    compressedBytes.byteSize(),
                                    durationMs,
                                    outErr);
            if (status != 0) {
                final String err = readErrorMessage(outErr);
                freeBuffer(Result.errorMessage(outErr).address());
                throw new WebpException(
                        "stream_add_"
                                + label.toLowerCase()
                                + " failed (status="
                                + status
                                + "): "
                                + err,
                        status);
            }
            freeBuffer(Result.errorMessage(outErr).address());
        } catch (final WebpException e) {
            throw e;
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    /**
     * Finalises a native streaming encoder, returns the assembled WebP buffer as an {@link
     * EncodedWebp}. The native handle is destroyed regardless of success/failure.
     */
    public static EncodedWebp streamFinish(final long handleAddress, final @Nullable WebpMetadata metadata) {
        if (handleAddress == 0L) {
            throw new WebpException("stream handle is closed");
        }
        try {
            final Arena scope = Arena.ofShared();
            final MemorySegment meta = scope.allocate(METADATA);
            final MemorySegment result = scope.allocate(RESULT);
            Metadata.fill(meta, metadata == null ? WebpMetadata.EMPTY : metadata, scope);

            final MemorySegment streamPtr = MemorySegment.ofAddress(handleAddress);
            final int status = (int) STREAM_FINISH.invoke(streamPtr, meta, result);
            return toEncodedOrThrow(status, result, scope);
        } catch (final Throwable t) {
            throw wrapNativeFailure(t);
        }
    }

    /** Best-effort destruction; never throws. */
    public static void streamAbort(final long handleAddress) {
        if (handleAddress == 0L) {
            return;
        }
        try {
            STREAM_ABORT.invoke(MemorySegment.ofAddress(handleAddress));
        } catch (final Throwable ignored) {
            // intentionally swallowed: abort is a cleanup helper.
        }
    }

    private static String readErrorMessage(final MemorySegment errResult) {
        final MemorySegment errPtr = Result.errorMessage(errResult);
        if (errPtr.equals(MemorySegment.NULL) || errPtr.address() == 0L) {
            return "Unknown error";
        }
        // C_POINTER VarHandle returns an unbound segment (byteSize=0); reinterpret so
        // getString() can scan for the null terminator.
        return errPtr.reinterpret(Long.MAX_VALUE).getString(0);
    }

    public static void freeBuffer(final long address) {
        if (address == 0L) {
            return;
        }
        try {
            FREE_BUFFER.invoke(MemorySegment.ofAddress(address));
        } catch (final Throwable t) {
            throw new WebpException("Failed to free native buffer: " + t.getMessage(), t);
        }
    }

    private static WebpException wrapNativeFailure(final Throwable t) {
        if (t instanceof final WebpException e) {
            return e;
        }
        return new WebpException("Native call failed: " + t.getMessage(), t);
    }

    /**
     * Drains the native result struct into Java-owned values, immediately frees the struct's
     * metadata side (error_message), and closes the staging arena. The encoded data buffer is
     * malloc-owned and lives on inside the returned {@link EncodedWebp}.
     */
    private static EncodedWebp toEncodedOrThrow(
            final int status, final MemorySegment result, final Arena scope) {
        try {
            final int resultStatus = Result.status(result);
            final MemorySegment errPtr = Result.errorMessage(result);
            final MemorySegment data = Result.data(result);
            final long size = Result.size(result);

            if (status != 0 || resultStatus != 0) {
                final String err =
                        errPtr.equals(MemorySegment.NULL)
                                ? "Unknown error"
                                : errPtr.reinterpret(Long.MAX_VALUE).getString(0);
                freeBuffer(data.address());
                freeBuffer(errPtr.address());
                throw new WebpException(
                        "WebP encode failed (status=" + resultStatus + "): " + err, resultStatus);
            }

            freeBuffer(errPtr.address());

            if (data.equals(MemorySegment.NULL) || size <= 0) {
                throw new WebpException("Native result is empty", resultStatus);
            }
            return new EncodedWebp(data, size);
        } finally {
            scope.close();
        }
    }

    private static MethodHandle downcall(
            final String symbolName, final FunctionDescriptor descriptor) {
        final MemorySegment symbol =
                LOOKUP.find(symbolName)
                        .orElseThrow(
                                () -> new IllegalStateException("Missing symbol: " + symbolName));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    /* -----------------------------------------------------------------------
     * Struct accessor classes — jextract-style. Each class corresponds to one
     * C struct: VarHandles derived from the layout, plus a small typed
     * {@code fill()} that mirrors the FFM/jextract pattern of "set every
     * field on a fresh allocation".
     * ----------------------------------------------------------------------- */

    private static VarHandle vh(final MemoryLayout layout, final String field) {
        // Outer empty PathElement strips the synthetic leading offset element so the
        // returned handle binds against (segment, offset, value) instead of the
        // (segment, baseOffset, fieldOffset, value) shape.
        return layout.varHandle(PathElement.groupElement(field));
    }

    /** Accessors for {@code uetlib_webp_encode_options}. */
    static final class EncodeOptions {
        private static final VarHandle STRUCT_SIZE = vh(ENCODE_OPTIONS, "struct_size");
        private static final VarHandle ABI_VERSION = vh(ENCODE_OPTIONS, "abi_version");
        private static final VarHandle LOSSLESS = vh(ENCODE_OPTIONS, "lossless");
        private static final VarHandle QUALITY = vh(ENCODE_OPTIONS, "quality");
        private static final VarHandle METHOD = vh(ENCODE_OPTIONS, "method");
        private static final VarHandle EXACT = vh(ENCODE_OPTIONS, "exact");
        private static final VarHandle NEAR_LOSSLESS = vh(ENCODE_OPTIONS, "near_lossless");
        private static final VarHandle TARGET_SIZE = vh(ENCODE_OPTIONS, "target_size");
        private static final VarHandle TARGET_PSNR = vh(ENCODE_OPTIONS, "target_psnr");
        private static final VarHandle THREAD_LEVEL = vh(ENCODE_OPTIONS, "thread_level");
        private static final VarHandle ALPHA_QUALITY = vh(ENCODE_OPTIONS, "alpha_quality");
        private static final VarHandle LOOP_COUNT = vh(ENCODE_OPTIONS, "loop_count");
        private static final VarHandle KMIN = vh(ENCODE_OPTIONS, "kmin");
        private static final VarHandle KMAX = vh(ENCODE_OPTIONS, "kmax");
        private static final VarHandle ALLOW_MIXED = vh(ENCODE_OPTIONS, "allow_mixed");
        private static final VarHandle MINIMIZE_SIZE = vh(ENCODE_OPTIONS, "minimize_size");

        private EncodeOptions() {}

        static void fill(final MemorySegment segment, final WebpEncodeOptions o) {
            STRUCT_SIZE.set(segment, 0L, (int) ENCODE_OPTIONS.byteSize());
            ABI_VERSION.set(segment, 0L, WebpEncodeOptions.ABI_VERSION);
            LOSSLESS.set(segment, 0L, o.lossless() ? 1 : 0);
            QUALITY.set(segment, 0L, o.quality());
            METHOD.set(segment, 0L, o.method());
            EXACT.set(segment, 0L, o.exact() ? 1 : 0);
            NEAR_LOSSLESS.set(segment, 0L, o.nearLossless());
            TARGET_SIZE.set(segment, 0L, o.targetSize());
            TARGET_PSNR.set(segment, 0L, o.targetPsnr());
            THREAD_LEVEL.set(segment, 0L, o.threadLevel());
            ALPHA_QUALITY.set(segment, 0L, o.alphaQuality());
            LOOP_COUNT.set(segment, 0L, o.loopCount());
            KMIN.set(segment, 0L, o.kmin());
            KMAX.set(segment, 0L, o.kmax());
            ALLOW_MIXED.set(segment, 0L, o.allowMixed() ? 1 : 0);
            MINIMIZE_SIZE.set(segment, 0L, o.minimizeSize() ? 1 : 0);
        }
    }

    /** Accessors for {@code uetlib_webp_metadata}. */
    static final class Metadata {
        private static final VarHandle EXIF = vh(METADATA, "exif");
        private static final VarHandle EXIF_SIZE = vh(METADATA, "exif_size");
        private static final VarHandle ICCP = vh(METADATA, "iccp");
        private static final VarHandle ICCP_SIZE = vh(METADATA, "iccp_size");
        private static final VarHandle XMP = vh(METADATA, "xmp");
        private static final VarHandle XMP_SIZE = vh(METADATA, "xmp_size");

        private Metadata() {}

        static void fill(
                final MemorySegment segment, final WebpMetadata metadata, final Arena arena) {
            final MemorySegment exif = toNative(metadata.exif(), arena);
            final MemorySegment iccp = toNative(metadata.iccp(), arena);
            final MemorySegment xmp = toNative(metadata.xmp(), arena);
            EXIF.set(segment, 0L, exif);
            EXIF_SIZE.set(segment, 0L, exif.equals(MemorySegment.NULL) ? 0L : exif.byteSize());
            ICCP.set(segment, 0L, iccp);
            ICCP_SIZE.set(segment, 0L, iccp.equals(MemorySegment.NULL) ? 0L : iccp.byteSize());
            XMP.set(segment, 0L, xmp);
            XMP_SIZE.set(segment, 0L, xmp.equals(MemorySegment.NULL) ? 0L : xmp.byteSize());
        }

        /**
         * Accepts either an already-native segment (passed through) or a heap-backed segment from
         * {@link MemorySegment#ofArray(byte[])}; in the latter case the bytes are copied into
         * {@code arena} and the native-backed copy is returned. This lets {@link WebpMetadata}
         * expose heap-friendly static factories without forcing callers to manage an Arena.
         */
        private static MemorySegment toNative(final @Nullable MemorySegment segment, final Arena arena) {
            if (segment == null || segment.equals(MemorySegment.NULL) || segment.byteSize() == 0L) {
                return MemorySegment.NULL;
            }
            if (segment.isNative()) {
                return segment;
            }
            final MemorySegment copy = arena.allocate(segment.byteSize());
            MemorySegment.copy(segment, 0L, copy, 0L, segment.byteSize());
            return copy;
        }
    }

    /** Accessors for {@code uetlib_webp_raw_image}. */
    static final class RawImage {
        private static final VarHandle STRUCT_SIZE = vh(RAW_IMAGE, "struct_size");
        private static final VarHandle ABI_VERSION = vh(RAW_IMAGE, "abi_version");
        private static final VarHandle PIXELS = vh(RAW_IMAGE, "pixels");
        private static final VarHandle WIDTH = vh(RAW_IMAGE, "width");
        private static final VarHandle HEIGHT = vh(RAW_IMAGE, "height");
        private static final VarHandle STRIDE = vh(RAW_IMAGE, "stride");
        private static final VarHandle PIXEL_FORMAT = vh(RAW_IMAGE, "pixel_format");

        private RawImage() {}

        static void fill(final MemorySegment segment, final WebpRawImage image) {
            STRUCT_SIZE.set(segment, 0L, (int) RAW_IMAGE.byteSize());
            ABI_VERSION.set(segment, 0L, WebpEncodeOptions.ABI_VERSION);
            PIXELS.set(segment, 0L, image.pixels());
            WIDTH.set(segment, 0L, image.width());
            HEIGHT.set(segment, 0L, image.height());
            STRIDE.set(segment, 0L, image.stride());
            PIXEL_FORMAT.set(segment, 0L, image.pixelFormat().nativeCode());
        }
    }

    /** Accessors for {@code uetlib_webp_anim_frame}. */
    static final class AnimFrame {
        private static final VarHandle STRUCT_SIZE = vh(ANIM_FRAME, "struct_size");
        private static final VarHandle ABI_VERSION = vh(ANIM_FRAME, "abi_version");
        private static final VarHandle PIXELS = vh(ANIM_FRAME, "pixels");
        private static final VarHandle WIDTH = vh(ANIM_FRAME, "width");
        private static final VarHandle HEIGHT = vh(ANIM_FRAME, "height");
        private static final VarHandle STRIDE = vh(ANIM_FRAME, "stride");
        private static final VarHandle PIXEL_FORMAT = vh(ANIM_FRAME, "pixel_format");
        private static final VarHandle DURATION = vh(ANIM_FRAME, "duration_ms");

        private AnimFrame() {}

        static void fill(final MemorySegment segment, final WebpFrame frame) {
            STRUCT_SIZE.set(segment, 0L, (int) ANIM_FRAME.byteSize());
            ABI_VERSION.set(segment, 0L, WebpEncodeOptions.ABI_VERSION);
            PIXELS.set(segment, 0L, frame.pixels());
            WIDTH.set(segment, 0L, frame.width());
            HEIGHT.set(segment, 0L, frame.height());
            STRIDE.set(segment, 0L, frame.stride());
            PIXEL_FORMAT.set(segment, 0L, frame.pixelFormat().nativeCode());
            DURATION.set(segment, 0L, frame.durationMs());
        }
    }

    /** Accessors for {@code uetlib_webp_anim_job}. */
    static final class AnimJob {
        private static final VarHandle STRUCT_SIZE = vh(ANIM_JOB, "struct_size");
        private static final VarHandle ABI_VERSION = vh(ANIM_JOB, "abi_version");
        private static final VarHandle FRAMES = vh(ANIM_JOB, "frames");
        private static final VarHandle FRAME_COUNT = vh(ANIM_JOB, "frame_count");
        private static final VarHandle CANVAS_WIDTH = vh(ANIM_JOB, "canvas_width");
        private static final VarHandle CANVAS_HEIGHT = vh(ANIM_JOB, "canvas_height");
        private static final VarHandle BGCOLOR = vh(ANIM_JOB, "bgcolor_rgba");

        private AnimJob() {}

        static void fill(
                final MemorySegment segment,
                final MemorySegment frameArray,
                final int frameCount,
                final int canvasWidth,
                final int canvasHeight,
                final int bgcolorRgba) {
            STRUCT_SIZE.set(segment, 0L, (int) ANIM_JOB.byteSize());
            ABI_VERSION.set(segment, 0L, WebpEncodeOptions.ABI_VERSION);
            FRAMES.set(segment, 0L, frameArray);
            FRAME_COUNT.set(segment, 0L, (long) frameCount);
            CANVAS_WIDTH.set(segment, 0L, canvasWidth);
            CANVAS_HEIGHT.set(segment, 0L, canvasHeight);
            BGCOLOR.set(segment, 0L, bgcolorRgba);
        }
    }

    /** Accessors for {@code uetlib_webp_compressed_frame}. */
    static final class CompressedFrame {
        private static final VarHandle STRUCT_SIZE = vh(COMPRESSED_FRAME, "struct_size");
        private static final VarHandle ABI_VERSION = vh(COMPRESSED_FRAME, "abi_version");
        private static final VarHandle DATA = vh(COMPRESSED_FRAME, "data");
        private static final VarHandle SIZE = vh(COMPRESSED_FRAME, "size");
        private static final VarHandle DURATION = vh(COMPRESSED_FRAME, "duration_ms");

        private CompressedFrame() {}

        static void fill(final MemorySegment segment, final WebpCompressedFrame frame) {
            STRUCT_SIZE.set(segment, 0L, (int) COMPRESSED_FRAME.byteSize());
            ABI_VERSION.set(segment, 0L, WebpEncodeOptions.ABI_VERSION);
            DATA.set(segment, 0L, frame.data());
            SIZE.set(segment, 0L, frame.data().byteSize());
            DURATION.set(segment, 0L, frame.durationMs());
        }
    }

    /** Accessors for {@code uetlib_webp_compressed_anim_job}. */
    static final class CompressedAnimJob {
        private static final VarHandle STRUCT_SIZE = vh(COMPRESSED_ANIM_JOB, "struct_size");
        private static final VarHandle ABI_VERSION = vh(COMPRESSED_ANIM_JOB, "abi_version");
        private static final VarHandle FRAMES = vh(COMPRESSED_ANIM_JOB, "frames");
        private static final VarHandle FRAME_COUNT = vh(COMPRESSED_ANIM_JOB, "frame_count");
        private static final VarHandle CANVAS_WIDTH = vh(COMPRESSED_ANIM_JOB, "canvas_width");
        private static final VarHandle CANVAS_HEIGHT = vh(COMPRESSED_ANIM_JOB, "canvas_height");
        private static final VarHandle BGCOLOR = vh(COMPRESSED_ANIM_JOB, "bgcolor_rgba");

        private CompressedAnimJob() {}

        static void fill(
                final MemorySegment segment,
                final MemorySegment frameArray,
                final int frameCount,
                final int canvasWidth,
                final int canvasHeight,
                final int bgcolorRgba) {
            STRUCT_SIZE.set(segment, 0L, (int) COMPRESSED_ANIM_JOB.byteSize());
            ABI_VERSION.set(segment, 0L, WebpEncodeOptions.ABI_VERSION);
            FRAMES.set(segment, 0L, frameArray);
            FRAME_COUNT.set(segment, 0L, (long) frameCount);
            CANVAS_WIDTH.set(segment, 0L, canvasWidth);
            CANVAS_HEIGHT.set(segment, 0L, canvasHeight);
            BGCOLOR.set(segment, 0L, bgcolorRgba);
        }
    }

    /** Accessors for {@code uetlib_webp_result}. */
    static final class Result {
        private static final VarHandle DATA = vh(RESULT, "data");
        private static final VarHandle SIZE = vh(RESULT, "size");
        private static final VarHandle STATUS = vh(RESULT, "status");
        private static final VarHandle ERROR_MESSAGE = vh(RESULT, "error_message");

        private Result() {}

        static MemorySegment data(final MemorySegment segment) {
            return (MemorySegment) DATA.get(segment, 0L);
        }

        static long size(final MemorySegment segment) {
            return (long) SIZE.get(segment, 0L);
        }

        static int status(final MemorySegment segment) {
            return (int) STATUS.get(segment, 0L);
        }

        static MemorySegment errorMessage(final MemorySegment segment) {
            return (MemorySegment) ERROR_MESSAGE.get(segment, 0L);
        }
    }
}
