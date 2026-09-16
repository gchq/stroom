/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Finds a directory on a filesystem other than the one a reference path is on, so a test can arrange
 * a genuine cross-filesystem move. Every fixture in this module puts everything under a single JUnit
 * temp directory, so the hazard cannot be arranged there and has to be found instead.
 */
final class OtherFileSystem {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OtherFileSystem.class);

    /**
     * Candidate roots on a filesystem other than the build directory. Both are conventional on Linux
     * and are tmpfs, so they are a different {@link java.nio.file.FileStore} from a disk-backed
     * working directory.
     */
    private static final String[] ROOTS = {"/dev/shm", "/run/shm"};

    private OtherFileSystem() {
    }

    /**
     * @return a writable directory on a different file store from {@code reference}, or null when this
     * machine has no second filesystem to hand, in which case the caller should skip with
     * {@link #skipReason()}.
     */
    static Path find(final Path reference) throws IOException {
        for (final String root : ROOTS) {
            final Path candidate = Path.of(root);
            if (Files.isDirectory(candidate) && Files.isWritable(candidate)) {
                if (!Files.getFileStore(candidate).equals(Files.getFileStore(reference))) {
                    final Path dir = candidate.resolve("stroom-test-xdev-" + reference.getFileName());
                    Files.createDirectories(dir);
                    LOGGER.info("Using {} for a cross-filesystem test", dir);
                    return dir;
                }
            }
        }
        return null;
    }

    static String skipReason() {
        return "No second filesystem available on this machine, so a cross-filesystem move cannot be "
               + "arranged. Tried " + String.join(", ", ROOTS);
    }
}
