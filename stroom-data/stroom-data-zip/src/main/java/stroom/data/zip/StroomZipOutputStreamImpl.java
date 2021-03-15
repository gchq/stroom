package stroom.data.zip;

import stroom.task.api.TaskContext;
import stroom.util.io.WrappedOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class StroomZipOutputStreamImpl implements StroomZipOutputStream {

    private static final String LOCK_EXTENSION = ".lock";
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomZipOutputStreamImpl.class);
    private final Path file;
    private final Path lockFile;
    private final ZipOutputStream zipOutputStream;
    private final StreamProgressMonitor streamProgressMonitor;
    private StroomZipNameSet stroomZipNameSet;
    private boolean inEntry = false;
    private long entryCount = 0;

    public StroomZipOutputStreamImpl(final Path file) throws IOException {
        this(file, null);
    }

    public StroomZipOutputStreamImpl(final Path file, final TaskContext taskContext) throws IOException {
        this(file, taskContext, true);
    }

    public StroomZipOutputStreamImpl(final Path file, final TaskContext taskContext, final boolean monitorEntries)
            throws IOException {
        Path lockFile = file.getParent().resolve(file.getFileName().toString() + LOCK_EXTENSION);

        if (Files.deleteIfExists(file)) {
            LOGGER.warn("deleted file " + file);
        }
        if (Files.deleteIfExists(lockFile)) {
            LOGGER.warn("deleted file " + lockFile);
        }

        this.file = file;

        // Ensure the lock file is created so that the parent dir is not cleaned up before we start writing data.
        this.lockFile = Files.createFile(lockFile);

        streamProgressMonitor = new StreamProgressMonitor(taskContext, "Write");
        final OutputStream rawOutputStream = Files.newOutputStream(lockFile);
        final OutputStream bufferedOutputStream = new BufferedOutputStream(rawOutputStream);
        final OutputStream progressOutputStream = new FilterOutputStreamProgressMonitor(bufferedOutputStream,
                streamProgressMonitor);
        zipOutputStream = new ZipOutputStream(progressOutputStream);
        if (monitorEntries) {
            stroomZipNameSet = new StroomZipNameSet(false);
        }
    }

    @Override
    public long getProgressSize() {
        if (streamProgressMonitor != null) {
            return streamProgressMonitor.getTotalBytes();
        }
        return -1;
    }

    @Override
    public OutputStream addEntry(final String name) throws IOException {
        if (inEntry) {
            throw new RuntimeException("Failed to close last entry");
        }
        entryCount++;
        inEntry = true;
        if (Thread.currentThread().isInterrupted()) {
            throw new IOException("Progress Stopped");
        }
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("addEntry() - " + file + " - " + name + " - adding");
        }
        if (stroomZipNameSet != null) {
            stroomZipNameSet.add(name);
        }
        zipOutputStream.putNextEntry(new ZipEntry(name));
        return new WrappedOutputStream(zipOutputStream) {
            @Override
            public void close() throws IOException {
                zipOutputStream.closeEntry();
                inEntry = false;
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("addEntry() - " + file + " - " + name + " - closed");
                }
            }
        };
    }

    public long getEntryCount() {
        return entryCount;
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
                } catch (final RuntimeException e) {
                    throw new IOException("Failed to rename file " + lockFile + " to " + file);
                }
            }
        }
    }

    @Override
    public void closeDelete() throws IOException {
        // ZIP's don't like to be empty !
        if (entryCount == 0) {
            final OutputStream os = addEntry("NULL.DAT");
            os.write("NULL".getBytes(CharsetConstants.DEFAULT_CHARSET));
            os.close();
        }

        zipOutputStream.close();
        if (lockFile != null) {
            try {
                Files.delete(lockFile);
            } catch (final RuntimeException e) {
                throw new IOException("Failed to delete file " + lockFile);
            }
        }
    }

    public Path getFile() {
        return file;
    }

    public Path getLockFile() {
        return lockFile;
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
