/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.destination;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.pipeline.server.writer.PathCreator;
import stroom.util.io.FileUtil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class RollingFileDestination extends RollingDestination {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileDestination.class);

    private static final int MAX_FAILED_RENAME_ATTEMPTS = 100;
    private static final int ONE_MINUTE = 60000;

    private final String key;

    private final String fileName;
    private final String rolledFileName;
    private final long frequency;
    private final long maxSize;

    private final Path dir;
    private final Path file;
    private final ByteCountOutputStream outputStream;
    private final long creationTime;

    private volatile long lastFlushTime;
    private byte[] footer;

    private volatile boolean rolled;

    public RollingFileDestination(final String key, final String fileName, final String rolledFileName,
                                  final long frequency, final long maxSize, final Path dir, final Path file, final long creationTime)
            throws IOException {
        this.key = key;

        this.fileName = fileName;
        this.rolledFileName = rolledFileName;
        this.frequency = frequency;
        this.maxSize = maxSize;

        this.dir = dir;
        this.file = file;
        this.creationTime = creationTime;

        // Make sure we can create this path.
        try {
            if (Files.isRegularFile(file)) {
                LOGGER.debug("File exists for key={}", key);

                // I have a feeling that the OS might sometimes report that a
                // file exists that has actually just been rolled.
                LOGGER.warn("File exists for key={} so rolling immediately", key);
                outputStream = new ByteCountOutputStream(new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)));

                // Roll the file.
                roll();

            } else {
                outputStream = new ByteCountOutputStream(new BufferedOutputStream(Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)));
            }
        } catch (final IOException e) {
            try {
                close();
            } catch (final IOException t) {
                LOGGER.error("Unable to close the output stream.");
            }
            throw e;
        }
    }

    @Override
    Object getKey() {
        return key;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return getOutputStream(null, null);
    }

    @Override
    public OutputStream getOutputStream(final byte[] header, final byte[] footer) throws IOException {
        try {
            if (!rolled) {
                this.footer = footer;

                // If we haven't written yet then create the output stream and
                // write a header if we have one.
                if (header != null && outputStream != null && outputStream.getBytesWritten() == 0) {
                    // Write the header.
                    write(header);
                }
                return outputStream;
            }
        } catch (final Throwable e) {
            throw handleException(null, e);
        }

        return null;
    }

    @Override
    boolean tryFlushAndRoll(final boolean force, final long currentTime) throws IOException {
        IOException exception = null;

        try {
            if (!rolled) {
                // Flush the output if we need to.
                if (force || shouldFlush(currentTime)) {
                    try {
                        flush();
                    } catch (final Throwable e) {
                        exception = handleException(exception, e);
                    }
                }

                // Roll the output if we need to.
                if (force || shouldRoll(currentTime)) {
                    try {
                        roll();
                    } catch (final Throwable e) {
                        exception = handleException(exception, e);
                    }
                }
            }
        } catch (final Throwable t) {
            exception = handleException(exception, t);
        }

        if (exception != null) {
            throw exception;
        }

        return rolled;
    }

    private boolean shouldFlush(final long currentTime) {
        final long lastFlushTime = this.lastFlushTime;
        this.lastFlushTime = currentTime;
        return lastFlushTime > 0 && currentTime - lastFlushTime > ONE_MINUTE;
    }

    private boolean shouldRoll(final long currentTime) {
        final long oldestAllowed = currentTime - frequency;
        return creationTime < oldestAllowed || outputStream.getBytesWritten() > maxSize;
    }

    private void roll() throws IOException {
        rolled = true;

        boolean success = false;
        IOException exception = null;

        // If we have written then write a footer if we have one.
        if (footer != null && outputStream != null && outputStream.getBytesWritten() > 0) {
            // Write the footer.
            try {
                write(footer);
            } catch (final Throwable e) {
                exception = handleException(exception, e);
            }
        }
        // Try and close the output stream.
        try {
            close();
        } catch (final Throwable e) {
            exception = handleException(exception, e);
        }

        String destFileName = rolledFileName;
        destFileName = PathCreator.replaceTimeVars(destFileName);
        destFileName = PathCreator.replaceUUIDVars(destFileName);
        destFileName = PathCreator.replaceFileName(destFileName, fileName);

        // Create the destination file.
        final Path destFile = dir.resolve(destFileName);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Rolling file '{}' to '{}'", getFullPath(file), getFullPath(destFile));
        }

        // Create source path.
        Path source = file;

        // Create destination path.
        Path dest = destFile;

        // If we have got valid paths for source and dest then attempt move.
        if (source != null) {
            if (Files.isRegularFile(dest)) {
                LOGGER.error("Failed to roll file '{}' to '{}' as target exists", getFullPath(file),
                        getFullPath(destFile));
            } else {
                try {
                    Files.move(source, dest);
                    success = true;
                } catch (final Throwable t) {
                    exception = handleRollException(file, destFile, exception, t);
                }
            }
        }

        if (source != null && !success) {
            try {
                int attempt = 1;
                while (!success && attempt <= MAX_FAILED_RENAME_ATTEMPTS) {
                    // Try to rename the file to something else.
                    final String suffix = StringUtils.leftPad(String.valueOf(attempt), 3, '0');
                    final Path failedFile = dir.resolve(file.getFileName().toString() + "." + suffix);
                    dest = failedFile;

                    if (!Files.isRegularFile(dest)) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Renaming file '{}' to '{}'", getFullPath(file), getFullPath(failedFile));
                        }
                        try {
                            Files.move(source, dest);
                            success = true;
                        } catch (final Throwable t) {
                            LOGGER.debug(t.getMessage(), t);
                        }
                    }
                    attempt++;
                }

                // If we didn't succeed in renaming the file to a failed file
                // then try and delete the file.
                if (!success) {
                    // Try to delete the file so we can continue to create a
                    // destination.
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Deleting file '{}'", getFullPath(file));
                    }
                    if (!Files.deleteIfExists(source)) {
                        LOGGER.error("Failed to delete file '{}'", getFullPath(file));
                    }
                }
            } catch (final Throwable t) {
                LOGGER.debug(t.getMessage(), t);
            }
        }

        if (exception != null) {
            throw exception;
        }
    }

    private String getFullPath(final Path file) {
        return FileUtil.getCanonicalPath(file);
    }

    private void write(final byte[] bytes) throws IOException {
        outputStream.write(bytes, 0, bytes.length);
    }

    private void flush() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Flushing: {}", key);
        }
        outputStream.flush();
    }

    private void close() throws IOException {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Closing: {}", key);
        }
        if (outputStream != null) {
            outputStream.close();
        }
    }

    @Override
    public String toString() {
        return key;
    }

    private IOException handleException(final IOException existingException, final Throwable newException) {
        LOGGER.error(newException.getMessage(), newException);

        if (existingException != null) {
            return existingException;
        }

        if (newException instanceof IOException) {
            return (IOException) newException;
        }

        return new IOException(newException.getMessage(), newException);
    }

    private IOException handleRollException(final Path sourceFile, final Path destFile,
                                            final IOException existingException, final Throwable newException) {
        final String message = "Failed to roll file '" +
                getFullPath(sourceFile) +
                "' to '" +
                getFullPath(destFile) +
                "' - " +
                newException.getMessage();

        LOGGER.error(message, newException);

        if (existingException != null) {
            return existingException;
        }

        if (newException instanceof IOException) {
            return (IOException) newException;
        }

        return new IOException(newException.getMessage(), newException);
    }
}
