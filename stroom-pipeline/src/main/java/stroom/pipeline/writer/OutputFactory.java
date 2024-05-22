package stroom.pipeline.writer;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.NullSafe;
import stroom.util.io.CompressionUtil;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.OutputStream;

public class OutputFactory {

    private final MetaDataHolder metaDataHolder;
    private boolean useCompression;
    private String compressionMethod;

    public OutputFactory(final MetaDataHolder metaDataHolder) {
        this.metaDataHolder = metaDataHolder;
    }

    public Output create(final OutputStream innerOutputStream) throws IOException {
        final Output output;
        if (useCompression) {
            if (compressionMethod.equalsIgnoreCase("zip")) {
                output = new ZipOutput(metaDataHolder, innerOutputStream);
            } else {
                try {
                    output = new BasicOutput(new CompressorStreamFactory()
                            .createCompressorOutputStream(compressionMethod, innerOutputStream));
                } catch (final CompressorException e) {
                    throw new IOException(e);
                }
            }
        } else {
            output = new BasicOutput(innerOutputStream);
        }
        return output;
    }

    public void setUseCompression(final boolean useCompression) {
        this.useCompression = useCompression;
    }

    public void setCompressionMethod(final String compressionMethod) {
        if (!NullSafe.isBlankString(compressionMethod)) {
            if (CompressionUtil.isSupportedCompressor(compressionMethod)) {
                this.compressionMethod = compressionMethod;
            } else {
                String errorMsg = "Unsupported compression method: " + compressionMethod;
                throw ProcessException.create(errorMsg);
            }
        }
    }

    public boolean isUseCompression() {
        return useCompression;
    }

    public String getCompressionMethod() {
        return compressionMethod;
    }
}
