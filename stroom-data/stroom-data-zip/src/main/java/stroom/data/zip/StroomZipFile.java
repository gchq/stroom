package stroom.data.zip;

import stroom.data.zip.StroomZipEntries.StroomZipEntryGroup;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

public class StroomZipFile implements Closeable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipFile.class);

    private final Path file;
    private final ZipFile zipFile;
    private final StroomZipEntries stroomZipEntries;

    public StroomZipFile(final Path path) throws IOException {
        this.file = path;
        this.zipFile = new ZipFile(Files.newByteChannel(file));
        stroomZipEntries = new StroomZipEntries();
        final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

        while (entries.hasMoreElements()) {
            final ZipArchiveEntry entry = entries.nextElement();

            // Skip directories.
            if (!entry.isDirectory()) {
                LOGGER.debug("File entry: {}", entry);
                String fileName = entry.getName();
                stroomZipEntries.addFile(fileName);
            }
        }
    }

    public List<String> getBaseNames() throws IOException {
        return stroomZipEntries.getBaseNames();
    }

    public Collection<StroomZipEntryGroup> getGroups() {
        return stroomZipEntries.getGroups();
    }

    @Override
    public void close() throws IOException {
        zipFile.close();
    }

    public InputStream getInputStream(final String baseName, final StroomZipFileType fileType) throws IOException {
        final ZipArchiveEntry entry = getEntry(baseName, fileType);
        if (entry != null) {
            return zipFile.getInputStream(entry);
        }
        return null;
    }

    public boolean containsEntry(final String baseName, final StroomZipFileType fileType) {
        return getEntry(baseName, fileType) != null;
    }

    private ZipArchiveEntry getEntry(final String baseName, final StroomZipFileType fileType) {
        final Optional<StroomZipEntry> optionalStroomZipEntry = stroomZipEntries.getByType(baseName, fileType);
        return optionalStroomZipEntry.map(entry -> zipFile.getEntry(entry.getFullName())).orElse(null);
    }
}
