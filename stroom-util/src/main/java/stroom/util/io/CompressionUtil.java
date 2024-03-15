package stroom.util.io;

import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.util.Set;

public final class CompressionUtil {

    private static final String SEPARATOR = ", ";
    public static final String SUPPORTED_COMPRESSORS = CompressorStreamFactory.BROTLI + SEPARATOR +
            CompressorStreamFactory.BZIP2 + SEPARATOR +
            CompressorStreamFactory.GZIP + SEPARATOR +
            CompressorStreamFactory.PACK200 + SEPARATOR +
            CompressorStreamFactory.XZ + SEPARATOR +
            CompressorStreamFactory.LZMA + SEPARATOR +
            CompressorStreamFactory.SNAPPY_FRAMED + SEPARATOR +
            CompressorStreamFactory.SNAPPY_RAW + SEPARATOR +
            CompressorStreamFactory.Z + SEPARATOR +
            CompressorStreamFactory.DEFLATE + SEPARATOR +
            CompressorStreamFactory.DEFLATE64 + SEPARATOR +
            CompressorStreamFactory.LZ4_BLOCK + SEPARATOR +
            CompressorStreamFactory.LZ4_FRAMED + SEPARATOR +
            CompressorStreamFactory.ZSTANDARD;

    public static boolean isSupportedCompressor(String compressorName) {
        Set<String> outputCompressors = new CompressorStreamFactory().getOutputStreamCompressorNames();
        return outputCompressors.contains(compressorName);
    }
}
