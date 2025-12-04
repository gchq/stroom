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

package stroom.lmdb2;

import stroom.lmdb.LmdbEnv;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class LmdbEnvDir {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LmdbEnvDir.class);

    private static final String DATA_FILE_NAME = "data.mdb";
    private static final String LOCK_FILE_NAME = "lock.mdb";

    private final Path envDir;
    private final boolean isDedicatedDir;

    public LmdbEnvDir(final Path envDir,
                      final boolean isDedicatedDir) {
        this.envDir = envDir;
        this.isDedicatedDir = isDedicatedDir;
    }

    public boolean dbExists() {
        return Files.exists(envDir.resolve(DATA_FILE_NAME));
    }

    public Path getEnvDir() {
        return envDir;
    }

    public void ensureExists() {
        try {
            Files.createDirectories(envDir);
        } catch (final IOException e) {
            throw new UncheckedIOException(LogUtil.message("Error ensuring directory '{}' exists"), e);
        }
    }

    /**
     * Deletes {@link LmdbEnv} from the filesystem if it is already closed.
     */
    public void delete() {
        LOGGER.debug("Deleting LMDB environment {} and all its contents", this);

        // May be useful to see the sizes of db before they are deleted
        LOGGER.doIfDebugEnabled(this::dumpMdbFileSize);

        if (Files.isDirectory(envDir)) {
            if (isDedicatedDir) {
                // Dir dedicated to the env so can delete the whole dir
                if (!FileUtil.deleteDir(envDir)) {
                    throw new RuntimeException("Unable to delete dir: " + this);
                }
            } else {
                // Not dedicated dir so just delete the files
                deleteEnvFile(LOCK_FILE_NAME);
                deleteEnvFile(DATA_FILE_NAME);
            }
        }
    }

    private void deleteEnvFile(final String filename) {
        final Path file = envDir.resolve(filename);
        if (Files.isRegularFile(file)) {
            try {
                LOGGER.info("Deleting file {}", FileUtil.getCanonicalPath(file));
                Files.delete(file);
            } catch (final IOException e) {
                throw new RuntimeException("Unable to delete file: " + FileUtil.getCanonicalPath(file));
            }
        } else {
            LOGGER.error("LMDB env file {} doesn't exist", FileUtil.getCanonicalPath(file));
        }
    }

    private void dumpMdbFileSize() {
        if (Files.isDirectory(envDir)) {
            try (final Stream<Path> stream = Files.list(envDir)) {
                stream
                        .filter(path ->
                                !Files.isDirectory(path))
                        .filter(file ->
                                file.toString().toLowerCase().endsWith(DATA_FILE_NAME))
                        .map(file -> {
                            try {
                                final long fileSizeBytes = Files.size(file);
                                return envDir.getFileName().resolve(file.getFileName())
                                       + " - file size: "
                                       + ModelStringUtil.formatIECByteSizeString(fileSizeBytes);
                            } catch (final IOException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .forEach(LOGGER::debug);

            } catch (final IOException e) {
                LOGGER.debug("Unable to list dir {} due to {}",
                        envDir.toAbsolutePath().normalize(), e.getMessage());
            }
        }
    }

    @Override
    public String toString() {
        return FileUtil.getCanonicalPath(envDir);
    }

    public static boolean isLmdbDataFile(final Path file) {
        return file != null
               && (file.endsWith(DATA_FILE_NAME) || file.endsWith(LOCK_FILE_NAME));
    }
}
