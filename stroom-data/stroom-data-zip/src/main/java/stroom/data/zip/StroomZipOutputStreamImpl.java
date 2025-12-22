/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.zip;

import stroom.task.api.TaskContext;
import stroom.task.api.TaskProgressHandler;
import stroom.util.io.FileUtil;
import stroom.util.io.WrappedOutputStream;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class StroomZipOutputStreamImpl implements StroomZipOutputStream {

    private static final String LOCK_EXTENSION = ".lock";
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StroomZipOutputStreamImpl.class);
    private final Path file;
    private final Path lockFile;
    private final ZipArchiveOutputStream zipOutputStream;
    private final TaskProgressHandler progressHandler;
    private boolean inEntry = false;
    private long entryCount = 0;

    public StroomZipOutputStreamImpl(final Path file) throws IOException {
        this(file, null);
    }

    public StroomZipOutputStreamImpl(final Path file, final TaskContext taskContext) throws IOException {
        final Path dir = file.getParent();
        final Path lockFile = dir.resolve(file.getFileName().toString() + LOCK_EXTENSION);

        if (Files.deleteIfExists(file)) {
            LOGGER.warn("deleted file " + file);
        }
        if (Files.deleteIfExists(lockFile)) {
            LOGGER.warn("deleted file " + lockFile);
        }

        this.file = file;

        // Ensure the lock file is created so that the parent dir is not cleaned up before we start writing data.
        Path result = null;
        int tryCount = 0;
        while (!Files.exists(lockFile) && tryCount < 100) {
            try {
                LOGGER.debug(() -> "Creating directories: " + FileUtil.getCanonicalPath(dir));
                Files.createDirectories(dir);
                LOGGER.debug(() -> "Creating file: " + FileUtil.getCanonicalPath(lockFile));
                result = Files.createFile(lockFile);
            } catch (final IOException e) {
                LOGGER.debug(e.getMessage(), e);
            }
            tryCount++;
        }

        if (result == null || !Files.exists(lockFile)) {
            LOGGER.error("Unable to create lock file: " + FileUtil.getCanonicalPath(lockFile));
            throw new IOException("Unable to create lock file: " + FileUtil.getCanonicalPath(lockFile));
        }

        this.lockFile = result;

        progressHandler = new TaskProgressHandler(taskContext, "Write");
        final OutputStream rawOutputStream = Files.newOutputStream(lockFile);
        final OutputStream bufferedOutputStream = new BufferedOutputStream(rawOutputStream);
        final OutputStream progressOutputStream = new FilterOutputStreamProgressMonitor(bufferedOutputStream,
                progressHandler);
        zipOutputStream = ZipUtil.createOutputStream(progressOutputStream);
    }

    @Override
    public long getProgressSize() {
        if (progressHandler != null) {
            return progressHandler.getTotalBytes();
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
        zipOutputStream.putArchiveEntry(new ZipArchiveEntry(name));
        return new WrappedOutputStream(zipOutputStream) {
            @Override
            public void close() throws IOException {
                zipOutputStream.closeArchiveEntry();
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
