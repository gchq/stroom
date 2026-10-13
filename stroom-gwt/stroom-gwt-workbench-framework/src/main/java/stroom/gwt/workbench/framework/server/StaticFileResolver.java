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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Resolves a request path to a regular file in one of an ordered list of root directories.
///
/// Paths that would escape a root directory (e.g. using `..` or a symbolic link to a file
/// outside it) are never resolved.
public class StaticFileResolver {

    private final List<Path> roots;

    /// @param roots The root directories to look in, in order of precedence.
    public StaticFileResolver(final List<Path> roots) {
        Objects.requireNonNull(roots);
        this.roots = roots.stream()
                .map(root -> root.toAbsolutePath().normalize())
                .toList();
    }

    /// Finds the file for a request path.
    ///
    /// @param requestPath The decoded path part of the request URI, e.g. `/ui/css/app.css`.
    /// @return The first regular file matching the path in the roots, or empty if there is no
    /// such file or the path is not allowed.
    public Optional<Path> resolve(final String requestPath) {
        if (requestPath == null || requestPath.isBlank() || requestPath.indexOf('\0') >= 0) {
            return Optional.empty();
        }

        // Make the path relative so it resolves against each root
        String relativePath = requestPath;
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        if (relativePath.isEmpty()) {
            return Optional.empty();
        }

        for (final Path root : roots) {
            final Path candidate = root.resolve(relativePath).normalize();
            // Guard against path traversal out of the root, including by a symbolic link
            if (candidate.startsWith(root) && FilePaths.isRegularFileWithin(candidate, root)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /// @return The root directories, in order of precedence.
    public List<Path> getRoots() {
        return roots;
    }
}
