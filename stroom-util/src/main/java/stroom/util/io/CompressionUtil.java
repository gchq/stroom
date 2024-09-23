package stroom.util.io;

import java.util.Set;

public final class CompressionUtil {

    public static final String SUPPORTED_COMPRESSORS = "" +
            "bzip2, " +
            "deflate, " +
            "gz, " +
            "lz4-block, " +
            "lz4-framed, " +
            "lzma, " +
            "pack200, " +
            "snappy-framed, " +
            "xz, " +
            "zip, " +
            "zstd";

    public static final Set<String> COMPRESSORS = Set.of(SUPPORTED_COMPRESSORS.split(", "));

    public static boolean isSupportedCompressor(final String compressorName) {
        return COMPRESSORS.contains(compressorName.toLowerCase());
    }
}
