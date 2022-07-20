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

package stroom.jooq.codegen;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

public final class FileUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    private FileUtil() {
        // Utility.
    }

    /**
     * Obtains a write lock on lockFilePath then runs the work. Creates lockFilePath
     * if it doesn't exist. Will block if another thread/jvm holds a lock on the same
     * file.
     */
    public static <T> T getUnderFileLock(final Path lockFilePath, final Supplier<T> work) {

        final Instant start = Instant.now();
        LOGGER.debug("Using lock file {}", lockFilePath.toAbsolutePath());

        try (final FileOutputStream fileOutputStream = new FileOutputStream(lockFilePath.toFile());
                final FileChannel channel = fileOutputStream.getChannel()) {
            channel.lock();

            LOGGER.debug("Waited " + Duration.between(start, Instant.now()) + " for lock");

            // Do the work while under the lock
            T result = work.get();

            LOGGER.debug("Work complete, releasing lock");
            return result;
        } catch (IOException e) {
            throw new RuntimeException("Error opening lock file " + lockFilePath.toAbsolutePath(), e);
        }
    }
}
