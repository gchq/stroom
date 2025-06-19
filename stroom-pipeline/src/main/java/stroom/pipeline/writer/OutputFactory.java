package stroom.pipeline.writer;

import stroom.meta.api.AttributeMap;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.io.CompressionUtil;
import stroom.util.shared.NullSafe;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.OutputStream;

public class OutputFactory {

    public static String COMPRESSION_ZIP = "zip";

    private final MetaDataHolder metaDataHolder;
    private boolean useCompression;
    private String compressionMethod;

    public OutputFactory(final MetaDataHolder metaDataHolder) {
        this.metaDataHolder = metaDataHolder;
    }

    public Output create(final OutputStream innerOutputStream) throws IOException {
        return create(innerOutputStream, null);
    }

    /**
     * @param attributeMapOverride If supplied these attributes will be used rather than those in the metaHolder
     */
    public Output create(final OutputStream innerOutputStream,
                         final AttributeMap attributeMapOverride) throws IOException {
        final Output output;
        if (useCompression) {
            if (compressionMethod.equalsIgnoreCase(COMPRESSION_ZIP)) {
                if (attributeMapOverride != null) {
                    output = new ZipOutput(attributeMapOverride, innerOutputStream);
                } else {
                    output = new ZipOutput(metaDataHolder, innerOutputStream);
                }
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
                final String errorMsg = "Unsupported compression method: " + compressionMethod;
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
