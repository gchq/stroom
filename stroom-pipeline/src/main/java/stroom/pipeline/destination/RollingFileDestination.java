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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public class RollingFileDestination extends RollingDestination {
    private static final Logger LOGGER = LoggerFactory.getLogger(RollingFileDestination.class);

    private static final int MAX_FAILED_RENAME_ATTEMPTS = 100;

    private final String fileName;
    private final String rolledFileName;

    private final File dir;
    private final File file;

    public RollingFileDestination(final String key,
                                  final long frequency,
                                  final long maxSize,
                                  final long creationTime,
                                  final String fileName,
                                  final String rolledFileName,
                                  final File dir,
                                  final File file)
            throws IOException {
        super(key, frequency, maxSize, creationTime);

        this.fileName = fileName;
        this.rolledFileName = rolledFileName;

        this.dir = dir;
        this.file = file;

        // Make sure we can create this path.
        try {
            if (file.exists()) {
                LOGGER.debug("File exists for key={}", key);

                // I have a feeling that the OS might sometimes report that a
                // file exists that has actually just been rolled.
                LOGGER.warn("File exists for key={} so rolling immediately", key);
                setOutputStream(new ByteCountOutputStream(new BufferedOutputStream(new FileOutputStream(file, true))));

                // Roll the file.
                roll();

            } else {
                setOutputStream(new ByteCountOutputStream(new BufferedOutputStream(new FileOutputStream(file, true))));
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
    void afterRoll(Consumer<Throwable> exceptionConsumer) {
        boolean success = false;

        String destFileName = rolledFileName;
        destFileName = PathCreator.replaceTimeVars(destFileName);
        destFileName = PathCreator.replaceUUIDVars(destFileName);
        destFileName = PathCreator.replaceFileName(destFileName, fileName);

        // Create the destination file.
        final File destFile = new File(dir, destFileName);
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Rolling file '{}' to '{}'", getFullPath(file), getFullPath(destFile));
        }

        // Create source path.
        Path source = null;
        try {
            source = file.toPath();
        } catch (final Throwable t) {
            exceptionConsumer.accept(wrapRollException(file, destFile, t));
        }

        // Create destination path.
        Path dest = null;
        try {
            dest = destFile.toPath();
        } catch (final Throwable t) {
            exceptionConsumer.accept(wrapRollException(file, destFile, t));
        }

        // If we have got valid paths for source and dest then attempt move.
        if (source != null && dest != null) {
            if (Files.isRegularFile(dest)) {
                LOGGER.error("Failed to roll file '{}' to '{}' as target exists", getFullPath(file),
                        getFullPath(destFile));
            } else {
                try {
                    Files.move(source, dest);
                    success = true;
                } catch (final Throwable t) {
                    exceptionConsumer.accept(wrapRollException(file, destFile, t));
                }
            }
        }

        if (source != null && !success) {
            try {
                int attempt = 1;
                while (!success && attempt <= MAX_FAILED_RENAME_ATTEMPTS) {
                    // Try to rename the file to something else.
                    final String suffix = StringUtils.leftPad(String.valueOf(attempt), 3, '0');
                    final File failedFile = new File(dir, file.getName() + "." + suffix);
                    try {
                        dest = null;
                        dest = failedFile.toPath();
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    }

                    if (dest != null && !Files.isRegularFile(dest)) {
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
    }

    private String getFullPath(final File file) {
        try {
            return file.getCanonicalPath();
        } catch (final IOException e) {
            return file.getAbsolutePath();
        }
    }

    private Throwable wrapRollException(final File sourceFile,
                                        final File destFile,
                                        final Throwable e) {
        final String msg = String.format("Failed to roll file '%s' to '%s' - %s",
                getFullPath(sourceFile),
                getFullPath(destFile),
                e.getMessage());

        return new IOException(msg, e);
    }
}
