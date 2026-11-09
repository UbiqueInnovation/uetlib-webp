package ch.uetlib.webp.ffi;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.ValueLayout;

public final class NativeLayouts {
    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_SIZE_T = ValueLayout.JAVA_LONG;
    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS;

    public static final MemoryLayout ENCODE_OPTIONS =
            MemoryLayout.structLayout(
                    C_INT.withName("struct_size"),
                    C_INT.withName("abi_version"),
                    C_INT.withName("lossless"),
                    ValueLayout.JAVA_FLOAT.withName("quality"),
                    C_INT.withName("method"),
                    C_INT.withName("exact"),
                    C_INT.withName("near_lossless"),
                    C_INT.withName("target_size"),
                    ValueLayout.JAVA_FLOAT.withName("target_psnr"),
                    C_INT.withName("thread_level"),
                    C_INT.withName("alpha_quality"),
                    C_INT.withName("loop_count"),
                    C_INT.withName("kmin"),
                    C_INT.withName("kmax"),
                    C_INT.withName("allow_mixed"),
                    C_INT.withName("minimize_size"));

    public static final MemoryLayout METADATA =
            MemoryLayout.structLayout(
                    C_POINTER.withName("exif"),
                    C_SIZE_T.withName("exif_size"),
                    C_POINTER.withName("iccp"),
                    C_SIZE_T.withName("iccp_size"),
                    C_POINTER.withName("xmp"),
                    C_SIZE_T.withName("xmp_size"));

    public static final MemoryLayout RAW_IMAGE =
            MemoryLayout.structLayout(
                    C_INT.withName("struct_size"),
                    C_INT.withName("abi_version"),
                    C_POINTER.withName("pixels"),
                    C_INT.withName("width"),
                    C_INT.withName("height"),
                    C_INT.withName("stride"),
                    C_INT.withName("pixel_format"));

    public static final MemoryLayout ANIM_FRAME =
            MemoryLayout.structLayout(
                    C_INT.withName("struct_size"),
                    C_INT.withName("abi_version"),
                    C_POINTER.withName("pixels"),
                    C_INT.withName("width"),
                    C_INT.withName("height"),
                    C_INT.withName("stride"),
                    C_INT.withName("pixel_format"),
                    C_INT.withName("duration_ms"),
                    MemoryLayout.paddingLayout(4));

    public static final MemoryLayout ANIM_JOB =
            MemoryLayout.structLayout(
                    C_INT.withName("struct_size"),
                    C_INT.withName("abi_version"),
                    C_POINTER.withName("frames"),
                    C_SIZE_T.withName("frame_count"),
                    C_INT.withName("canvas_width"),
                    C_INT.withName("canvas_height"),
                    C_INT.withName("bgcolor_rgba"),
                    MemoryLayout.paddingLayout(4));

    public static final MemoryLayout COMPRESSED_FRAME =
            MemoryLayout.structLayout(
                    C_INT.withName("struct_size"),
                    C_INT.withName("abi_version"),
                    C_POINTER.withName("data"),
                    C_SIZE_T.withName("size"),
                    C_INT.withName("duration_ms"),
                    MemoryLayout.paddingLayout(4));

    public static final MemoryLayout COMPRESSED_ANIM_JOB =
            MemoryLayout.structLayout(
                    C_INT.withName("struct_size"),
                    C_INT.withName("abi_version"),
                    C_POINTER.withName("frames"),
                    C_SIZE_T.withName("frame_count"),
                    C_INT.withName("canvas_width"),
                    C_INT.withName("canvas_height"),
                    C_INT.withName("bgcolor_rgba"),
                    MemoryLayout.paddingLayout(4));

    public static final MemoryLayout RESULT =
            MemoryLayout.structLayout(
                    C_POINTER.withName("data"),
                    C_SIZE_T.withName("size"),
                    C_INT.withName("status"),
                    MemoryLayout.paddingLayout(4),
                    C_POINTER.withName("error_message"));

    private NativeLayouts() {}
}
