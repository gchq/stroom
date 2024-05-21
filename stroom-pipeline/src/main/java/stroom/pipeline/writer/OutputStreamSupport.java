package stroom.pipeline.writer;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.NullSafe;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.io.CompressionUtil;
import stroom.util.shared.ModelStringUtil;

import jakarta.ws.rs.ProcessingException;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorStreamFactory;

import java.io.IOException;
import java.io.OutputStream;

public class OutputStreamSupport {

    public static final String DATA_EXTENSION = ".dat";
    public static final String META_EXTENSION = ".meta";

    private final MetaDataHolder metaDataHolder;
    private boolean useCompression;
    private String compressionMethod;
    private ZipArchiveEntry currentZipEntry;
    private long count;

    private ZipArchiveOutputStream zipOutputStream;
    private ByteCountOutputStream currentOutputStream;

    public OutputStreamSupport(final MetaDataHolder metaDataHolder) {
        this.metaDataHolder = metaDataHolder;
    }

    public ByteCountOutputStream createOutputStream(final OutputStream innerOutputStream) throws IOException {
        if (currentOutputStream != null) {
            throw new RuntimeException("Expected null currentOutputStream");
        }

        if (useCompression) {
            if (compressionMethod.equalsIgnoreCase("zip")) {
                if (currentZipEntry != null) {
                    throw new RuntimeException("Expected null zip entry");
                }
                if (zipOutputStream != null) {
                    throw new RuntimeException("Expected null zip output stream");
                }

                count = 0;
                zipOutputStream = new ZipArchiveOutputStream(innerOutputStream) {
                    @Override
                    public void close() throws IOException {
                        OutputStreamSupport.this.endZipEntry();
                        super.close();
                    }
                };

                currentOutputStream = new ByteCountOutputStream(zipOutputStream);
            } else {
                try {
                    currentOutputStream = new ByteCountOutputStream(new CompressorStreamFactory()
                            .createCompressorOutputStream(compressionMethod, innerOutputStream));
                } catch (final CompressorException e) {
                    throw new IOException(e);
                }
            }
        } else {
            currentOutputStream = new ByteCountOutputStream(innerOutputStream);
        }
        return currentOutputStream;
    }

    public void startZipEntry() throws IOException {
        if (isZip()) {
            endZipEntry();

            count++;

            // Write meta.
            final String base = ModelStringUtil.zeroPad(3, String.valueOf(count));
            String dataFileName = base + DATA_EXTENSION;
            String metaFileName = base + META_EXTENSION;

            if (metaDataHolder != null) {
                final AttributeMap attributeMap = metaDataHolder.getMetaData();

                // TODO : I'm not sure where/who is setting fileName in meta so will leave for now.
                final String fileName = attributeMap.get("fileName");
                if (!NullSafe.isBlankString(fileName)) {
                    dataFileName = fileName;
                    final int index = fileName.lastIndexOf(".");
                    if (index != -1) {
                        metaFileName = fileName.substring(0, index) + META_EXTENSION;
                    } else {
                        metaFileName = fileName + META_EXTENSION;
                    }
                }

                zipOutputStream.putArchiveEntry(new ZipArchiveEntry(metaFileName));
                AttributeMapUtil.write(attributeMap, zipOutputStream);
                zipOutputStream.closeArchiveEntry();
            }

            currentZipEntry = new ZipArchiveEntry(dataFileName);
            zipOutputStream.putArchiveEntry(currentZipEntry);
        }
    }

    public void endZipEntry() throws IOException {
        if (currentZipEntry != null) {
            zipOutputStream.closeArchiveEntry();
            currentZipEntry = null;
        }
    }

    public boolean isZip() {
        return zipOutputStream != null;
    }

    public long getCurrentOutputSize() {
        if (currentOutputStream == null) {
            return 0;
        }
        return currentOutputStream.getCount();
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
