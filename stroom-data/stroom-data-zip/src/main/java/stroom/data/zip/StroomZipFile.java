package stroom.data.zip;

import stroom.data.zip.StroomZipEntries.StroomZipEntryGroup;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

public class StroomZipFile implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipFile.class);

    private final ZipFile zipFile;
    private final StroomZipEntries stroomZipEntries;

    public StroomZipFile(final Path path) throws IOException {
        this.zipFile = ZipFile.builder().setSeekableByteChannel(Files.newByteChannel(path)).get();
        stroomZipEntries = new StroomZipEntries();
        final Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();

        while (entries.hasMoreElements()) {
            final ZipArchiveEntry entry = entries.nextElement();

            // Skip directories.
            if (!entry.isDirectory()) {
                LOGGER.debug("File entry: {}", entry);
                final String fileName = entry.getName();
                checkZipEntry(path, fileName);
                stroomZipEntries.addFile(fileName);
            }
        }
    }

    private static void checkZipEntry(final Path zipFile, final String entryName) {
        if (!ZipUtil.isSafeZipPath(Path.of(entryName))) {
            // Only a warning as we do not use the zip entry name when extracting from the zip.
            LOGGER.warn("Zip file '{}' contains a path that would extract to outside the " +
                        "target directory '{}'. Stroom will not use this path but this is " +
                        "dangerous behaviour. Enable DEBUG for stacktrace",
                    zipFile.toAbsolutePath(), entryName);
            LOGGER.debug(() -> LogUtil.message(
                    "Zip file '{}' contains a path that would extract to outside the " +
                    "target directory '{}'. Stacktrace:\n{}",
                    zipFile.toAbsolutePath(), entryName, Thread.currentThread().getStackTrace()));
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
