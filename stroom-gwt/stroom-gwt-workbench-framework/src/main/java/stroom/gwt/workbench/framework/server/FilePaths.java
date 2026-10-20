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

package stroom.gwt.workbench.framework.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Helpers for comparing file paths by where they really are, i.e. after following symbolic
/// links, so a link can't be used to reach a file outside a directory.
final class FilePaths {

    private FilePaths() {
        // Static utility
    }

    /// @param path A path.
    /// @return The real path of `path` if it exists, otherwise its normalised absolute path.
    static Path realPath(final Path path) {
        try {
            return path.toRealPath();
        } catch (final IOException | SecurityException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    /// @param file A file.
    /// @param root A directory.
    /// @return True if `file` is a regular file that is really in `root` (or below it) once
    /// symbolic links in either path have been followed.
    static boolean isRegularFileWithin(final Path file, final Path root) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            return file.toRealPath().startsWith(root.toRealPath());
        } catch (final IOException | SecurityException e) {
            return false;
        }
    }
}
