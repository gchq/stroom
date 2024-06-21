package stroom.pipeline.writer;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.pipeline.state.MetaDataHolder;
import stroom.util.NullSafe;
import stroom.util.io.ByteCountOutputStream;
import stroom.util.shared.ModelStringUtil;

import com.google.common.base.Strings;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.IOException;
import java.io.OutputStream;

public class ZipOutput implements Output {

    public static final String DATA_EXTENSION = ".dat";
    public static final String META_EXTENSION = ".meta";

    private final MetaDataHolder metaDataHolder;
    private ZipArchiveEntry currentZipEntry;
    private long count;

    private final ZipArchiveOutputStream zipOutputStream;
    private final ByteCountOutputStream outputStream;

    public ZipOutput(final MetaDataHolder metaDataHolder,
                     final OutputStream innerOutputStream) {
        this.metaDataHolder = metaDataHolder;

        count = 0;
        zipOutputStream = new ZipArchiveOutputStream(innerOutputStream) {
            @Override
            public void close() throws IOException {
                ZipOutput.this.endZipEntry();
                super.close();
            }
        };

        outputStream = new ByteCountOutputStream(zipOutputStream);
    }

    @Override
    public void startZipEntry() throws IOException {
        endZipEntry();

        count++;

        // Write meta.
        final String base = Strings.padStart(Long.toString(count), 10, '0');
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

    @Override
    public void endZipEntry() throws IOException {
        if (currentZipEntry != null) {
            zipOutputStream.closeArchiveEntry();
            currentZipEntry = null;
        }
    }

    @Override
    public boolean isZip() {
        return true;
    }

    @Override
    public long getCurrentOutputSize() {
        return outputStream.getCount();
    }

    @Override
    public boolean getHasBytesWritten() {
        return outputStream.getHasBytesWritten();
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes);
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
    }
}
