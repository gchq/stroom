package stroom.data.zip;

import stroom.util.io.FileNameUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class StroomZipFile implements Closeable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipFile.class);

    private final Path file;
    private ZipFile zipFile;
    private StroomZipEntries stroomZipEntries;
    private List<String> baseNameList;

    public StroomZipFile(final Path path) {
        this.file = path;
    }

    private ZipFile getZipFile() throws IOException {
        if (zipFile == null) {
            this.zipFile = new ZipFile(Files.newByteChannel(file));
        }
        return zipFile;
    }

    public Path getFile() {
        return file;
    }

    StroomZipEntries getStroomZipFileGroups() throws IOException {
        if (stroomZipEntries == null) {
            stroomZipEntries = new StroomZipEntries();
            final Enumeration<ZipArchiveEntry> entries = getZipFile().getEntries();

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
        return stroomZipEntries;
    }

    public List<String> getBaseNames() throws IOException {
        if (baseNameList == null) {
            baseNameList = new ArrayList<>();
            final Enumeration<ZipArchiveEntry> entries = getZipFile().getEntries();

            final Set<String> baseNameSet = new HashSet<>();
            while (entries.hasMoreElements()) {
                final ZipArchiveEntry entry = entries.nextElement();

                // Skip directories.
                if (!entry.isDirectory()) {
                    LOGGER.debug("File entry: {}", entry);
                    String fileName = entry.getName();
                    final String baseName = FileNameUtil.getBaseName(fileName);
                    if (!baseNameSet.contains(baseName)) {
                        baseNameSet.add(baseName);
                        baseNameList.add(baseName);
                    }
                }
            }
        }
        return baseNameList;
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
            zipFile = null;
        }
        stroomZipEntries = null;

    }

    public InputStream getInputStream(String baseName, StroomZipFileType fileType) throws IOException {
        final ZipArchiveEntry entry = getEntry(baseName, fileType);
        if (entry != null) {
            return getZipFile().getInputStream(entry);
        }
        return null;
    }

    public long getSize(String baseName, StroomZipFileType fileType) throws IOException {
        final ZipArchiveEntry entry = getEntry(baseName, fileType);
        if (entry != null) {
            return entry.getSize();
        }
        return 0;
    }

    public boolean containsEntry(String baseName, StroomZipFileType fileType) throws IOException {
        return getEntry(baseName, fileType) != null;
    }

    private ZipArchiveEntry getEntry(String baseName, StroomZipFileType fileType) throws IOException {
        final Optional<StroomZipEntry> optionalStroomZipEntry =
                getStroomZipFileGroups().getByType(baseName, fileType);
        if (optionalStroomZipEntry.isEmpty()) {
            return null;
        }
        return getZipFile().getEntry(optionalStroomZipEntry.get().getFullName());
    }
}
