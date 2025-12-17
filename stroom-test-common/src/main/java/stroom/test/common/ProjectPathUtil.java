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

package stroom.test.common;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ProjectPathUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ProjectPathUtil.class);

    public static Path resolveDir(final String projectDir) {
        final Path root = Paths.get(".").toAbsolutePath().normalize();
        Path dir = root.resolve(projectDir);
        if (!Files.isDirectory(dir)) {
            dir = root.getParent().resolve(projectDir);
            if (!Files.isDirectory(dir)) {
                throw new RuntimeException("Path not found: " + FileUtil.getCanonicalPath(dir));
            }
        }

        return dir;
    }

    /**
     * @return The absolute path to the projects git repo root dir or throw if it cannot be found.
     */
    public static Path getRepoRoot() {

        final Path startDir;
        Path currDir = null;

        // From a PSVM this should be the repo root, but from a test
        // this is likely the gradle module dir
        final String userDirStr = System.getProperty("user.dir");
        if (!NullSafe.isBlankString(userDirStr)) {
            startDir = Paths.get(userDirStr).toAbsolutePath().normalize();
        } else {
            startDir = Paths.get(".").toAbsolutePath().normalize();
        }
        LOGGER.debug("startDir: {}", startDir);
        currDir = startDir;

        final Path gitDit = Paths.get(".git");

        for (; ; ) {
            if (!Files.isDirectory(currDir)) {
                throw new RuntimeException(LogUtil.message("currDir '{}' is not a directory or does not exist",
                        currDir.toAbsolutePath().normalize()));
            }

            // See if there is a .git dir in here to identify it as a git repo.
            // This assumes that the .git dir is in the repo root
            final Path relGitDir = currDir.resolve(gitDit);
            if (Files.isDirectory(relGitDir)) {
                // found the root, happy days
                break;
            }
            // Go up one and have another go
            final Path parent = currDir.getParent();
            if (parent == null) {
                // Reached the top
                throw new RuntimeException(LogUtil.message(
                        "Unable to find project's repo root from start point {}",
                        startDir));
            }
            currDir = parent
                    .toAbsolutePath()
                    .normalize();
        }
        return currDir;
    }
}
