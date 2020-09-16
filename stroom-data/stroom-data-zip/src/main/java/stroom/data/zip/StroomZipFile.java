package stroom.data.zip;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;

public class StroomZipFile implements Closeable {
    private final static String SINGLE_ENTRY_ZIP_BASE_NAME = "001";

    public final static StroomZipEntry SINGLE_DATA_ENTRY = new StroomZipEntry(null, SINGLE_ENTRY_ZIP_BASE_NAME,
            StroomZipFileType.Data);
    public final static StroomZipEntry SINGLE_META_ENTRY = new StroomZipEntry(null, SINGLE_ENTRY_ZIP_BASE_NAME,
            StroomZipFileType.Meta);

    private final Path file;
    private ZipFile zipFile;
    private StroomZipNameSet stroomZipNameSet;

    public StroomZipFile(Path path) {
        this.file = path;
    }

    private ZipFile getZipFile() throws IOException {
        if (zipFile == null) {
            this.zipFile = new ZipFile(file.toFile());
        }
        return zipFile;
    }

    public Path getFile() {
        return file;
    }

    public StroomZipNameSet getStroomZipNameSet() throws IOException {
        if (stroomZipNameSet == null) {
            stroomZipNameSet = new StroomZipNameSet(false);
            Enumeration<ZipArchiveEntry> entryE = getZipFile().getEntries();

            while (entryE.hasMoreElements()) {
                ZipArchiveEntry entry = entryE.nextElement();

                // Skip Dir's
                if (!entry.isDirectory()) {
                    String fileName = entry.getName();
                    stroomZipNameSet.add(fileName);
                }
            }
        }
        return stroomZipNameSet;
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
            zipFile = null;
        }
        stroomZipNameSet = null;

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
        final String fullName = getStroomZipNameSet().getName(baseName, fileType);
        if (fullName == null) {
            return null;
        }
        return getZipFile().getEntry(fullName);
    }
}
