package stroom.proxy.app.handler;

import stroom.data.zip.StroomZipFileType;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;

public class TransferUtil {

    /**
     * Take a stream to another stream.
     */
    public static long transfer(final InputStream inputStream,
                                final OutputStream outputStream,
                                final byte[] buffer) {
        long bytesWritten = 0;
        try {
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
                bytesWritten += len;
            }
            return bytesWritten;
        } catch (final IOException ioEx) {
            // Wrap it
            throw new UncheckedIOException(ioEx);
        }
    }

    public static ZipEntryGroup transferZipEntryGroup(final ZipFile zipFile,
                                                      final ZipWriter zipWriter,
                                                      final ZipEntryGroup zipEntryGroup,
                                                      final String baseNameOut) throws IOException {
        final ZipEntryGroup.Entry manifestEntry =
                transferEntry(zipEntryGroup.getManifestEntry(),
                        zipFile,
                        zipWriter,
                        StroomZipFileType.MANIFEST,
                        baseNameOut);
        final ZipEntryGroup.Entry metaEntry =
                transferEntry(zipEntryGroup.getMetaEntry(),
                        zipFile,
                        zipWriter,
                        StroomZipFileType.META,
                        baseNameOut);
        final ZipEntryGroup.Entry contextEntry =
                transferEntry(zipEntryGroup.getContextEntry(),
                        zipFile,
                        zipWriter,
                        StroomZipFileType.CONTEXT,
                        baseNameOut);
        final ZipEntryGroup.Entry dataEntry =
                transferEntry(zipEntryGroup.getDataEntry(),
                        zipFile,
                        zipWriter,
                        StroomZipFileType.DATA,
                        baseNameOut);

        return new ZipEntryGroup(
                zipEntryGroup.getFeedName(),
                zipEntryGroup.getTypeName(),
                manifestEntry,
                metaEntry,
                contextEntry,
                dataEntry);
    }

    private static ZipEntryGroup.Entry transferEntry(final ZipEntryGroup.Entry entry,
                                                     final ZipFile zipFile,
                                                     final ZipWriter zipWriter,
                                                     final StroomZipFileType stroomZipFileType,
                                                     final String baseNameOut) throws IOException {
        if (entry != null) {
            final ZipArchiveEntry zipEntry = zipFile.getEntry(entry.getName());
            if (zipEntry == null) {
                throw new RuntimeException("Expected entry: " + entry.getName());
            }
            if (!zipEntry.getName().equals(entry.getName())) {
                throw new RuntimeException("Unexpected entry: " + zipEntry.getName() + " expected " + entry.getName());
            }

            final String outEntryName = baseNameOut + stroomZipFileType.getDotExtension();
            final long bytes = zipWriter.writeStream(outEntryName, zipFile.getInputStream(zipEntry));
            return new Entry(outEntryName, bytes);
        }
        return null;
    }
}
