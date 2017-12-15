package stroom.proxy.repo;

import stroom.feed.MetaMap;
import stroom.util.io.FilterOutputStreamProgressMonitor;
import stroom.util.io.StreamProgressMonitor;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Monitor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class StroomZipOutputStreamImpl implements StroomZipOutputStream {
    private static final String LOCK_EXTENSION = ".lock";
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StroomZipOutputStreamImpl.class);
    private final Path file;
    private final Path lockFile;
    private final Monitor monitor;
    private final ZipOutputStream zipOutputStream;
    private final StreamProgressMonitor streamProgressMonitor;
    private StroomZipNameSet stroomZipNameSet;
    private boolean inEntry = false;
    private long entryCount = 0;

    public StroomZipOutputStreamImpl(final Path file) throws IOException {
        this(file, null);
    }

    public StroomZipOutputStreamImpl(final Path file, final Monitor monitor) throws IOException {
        this(file, monitor, true);
    }

    public StroomZipOutputStreamImpl(final Path path, final Monitor monitor, final boolean monitorEntries) throws IOException {
        this.monitor = monitor;

        Path file = path;
        Path lockFile = path.getParent().resolve(path.getFileName().toString() + LOCK_EXTENSION);

        if (Files.deleteIfExists(file)) {
            LOGGER.warn("deleted file " + file);
        }
        if (Files.deleteIfExists(lockFile)) {
            LOGGER.warn("deleted file " + lockFile);
        }

        this.file = file;

        // Ensure the lock file is created so that the parent dir is not cleaned up before we start writing data.
        this.lockFile = Files.createFile(lockFile);

        streamProgressMonitor = new StreamProgressMonitor(monitor, "Write");
        zipOutputStream = new ZipOutputStream(new FilterOutputStreamProgressMonitor(Files.newOutputStream(lockFile), streamProgressMonitor));
        if (monitorEntries) {
            stroomZipNameSet = new StroomZipNameSet(false);
        }
    }

//    public StroomZipOutputStreamImpl(final OutputStream outputStream) throws IOException {
//        this(outputStream, null);
//    }
//
//    public StroomZipOutputStreamImpl(final OutputStream outputStream, final Monitor monitor) throws IOException {
//        this.monitor = monitor;
//
//        file = null;
//        lockFile = null;
//        streamProgressMonitor = new StreamProgressMonitor(monitor, "Write");
//        zipOutputStream = new ZipOutputStream(
//                new FilterOutputStreamProgressMonitor(new BufferedOutputStream(outputStream), streamProgressMonitor));
//        stroomZipNameSet = new StroomZipNameSet(false);
//    }

    @Override
    public long getProgressSize() {
        if (streamProgressMonitor != null) {
            return streamProgressMonitor.getTotalBytes();
        }
        return -1;
    }

    @Override
    public OutputStream addEntry(final StroomZipEntry entry) throws IOException {
        if (inEntry) {
            throw new RuntimeException("Failed to close last entry");
        }
        entryCount++;
        inEntry = true;
        if (monitor != null) {
            if (monitor.isTerminated()) {
                throw new IOException("Progress Stopped");
            }
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("addEntry() - " + file + " - " + entry + " - adding");
        }
        if (stroomZipNameSet != null) {
            stroomZipNameSet.add(entry.getFullName());
        }
        zipOutputStream.putNextEntry(new ZipEntry(entry.getFullName()));
        return new WrappedOutputStream(zipOutputStream) {
            @Override
            public void close() throws IOException {
                zipOutputStream.closeEntry();
                inEntry = false;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("addEntry() - " + file + " - " + entry + " - closed");
                }
            }
        };
    }

    public long getEntryCount() {
        return entryCount;
    }

    @Override
    public void addMissingMetaMap(final MetaMap metaMap) throws IOException {
        if (stroomZipNameSet == null) {
            throw new RuntimeException("You can only add missing meta data if you are monitoring entries");

        }
        for (final String baseName : stroomZipNameSet.getBaseNameList()) {
            if (stroomZipNameSet.getName(baseName, StroomZipFileType.Meta) == null) {
                zipOutputStream.putNextEntry(new ZipEntry(baseName + StroomZipFileType.Meta.getExtension()));
                metaMap.write(zipOutputStream, false);
                zipOutputStream.closeEntry();
            }
        }
    }

    @Override
    public void close() throws IOException {
        // ZIP's don't like to be empty !
        if (entryCount == 0) {
            closeDelete();
        } else {
            zipOutputStream.close();
            if (lockFile != null) {
                try {
                    Files.move(lockFile, file);
                } catch (final Exception e) {
                    throw new IOException("Failed to rename file " + lockFile + " to " + file);
                }
            }
        }
    }

    @Override
    public void closeDelete() throws IOException {
        // ZIP's don't like to be empty !
        if (entryCount == 0) {
            final OutputStream os = addEntry(new StroomZipEntry("NULL.DAT", "NULL", StroomZipFileType.Data));
            os.write("NULL".getBytes(CharsetConstants.DEFAULT_CHARSET));
            os.close();
        }

        zipOutputStream.close();
        if (lockFile != null) {
            try {
                Files.delete(lockFile);
            } catch (final Exception e) {
                throw new IOException("Failed to delete file " + lockFile);
            }
        }
    }

    Path getFile() {
        return file;
    }

    Path getLockFile() {
        return lockFile;
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
