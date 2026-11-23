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

package stroom.util.xml;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/// What XSLT and XPath run by [SaxonSecurity] may read.
///
/// Nothing can be read under [#LOCKED]. A policy made with [#allowing(Collection)] lets `doc()`, `document()`,
/// `unparsed-text()`, `collection()` and `uri-collection()` read local files at or below the listed paths.
/// Only local files can be allowed. URLs are read with Stroom's own HTTP functions instead.
///
/// Symbolic links in a requested URI are resolved each time it is checked, and the file is then read through
/// the resolved path. Links in the allowed paths are resolved again every few seconds, so a link that is
/// changed or a directory that is mounted later is seen. A URI containing a `..` segment is always refused.
public final class SaxonPolicy {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SaxonPolicy.class);
    private static final String FILE_SCHEME = "file";
    private static final String PARENT_SEGMENT = "..";
    private static final long ALLOWED_PATHS_TTL_NANOS = Duration.ofSeconds(5).toNanos();

    /// A policy that allows nothing to be read.
    public static final SaxonPolicy LOCKED = new SaxonPolicy(List.of());

    private final List<Path> allowedPaths;
    // The allowed paths with their links resolved, kept for a few seconds so that checking every file in a
    // large collection doesn't resolve them again each time.
    private volatile ResolvedPaths resolvedAllowedPaths;

    private SaxonPolicy(final List<Path> allowedPaths) {
        this.allowedPaths = List.copyOf(allowedPaths);
    }

    /// Creates a policy that allows local files at or below the given prefixes to be read.
    ///
    /// Each prefix is a `file:` URI or an absolute path, e.g. `file:///data/lookups/` or `/data/lookups`.
    /// A prefix matches whole path segments, so `/data/lookups` does not allow `/data/lookups2`.
    /// Prefixes that are null, blank, relative, contain `..` or use any other scheme are ignored with a warning.
    ///
    /// @param uriPrefixes The configured prefixes, may be null or contain nulls.
    /// @return The policy, [#LOCKED] if no usable prefix is given.
    public static SaxonPolicy allowing(final Collection<String> uriPrefixes) {
        final List<Path> paths = new ArrayList<>();
        NullSafe.forEach(uriPrefixes, prefix -> {
            final Path path = toLocalPath(prefix);
            if (path == null) {
                if (!NullSafe.isBlankString(prefix)) {
                    LOGGER.warn("Ignoring allowed URI prefix '{}'. Only absolute local paths or file: URIs " +
                                "without '..' segments can be allowed.", prefix);
                }
            } else {
                paths.add(path);
            }
        });
        return paths.isEmpty()
                ? LOCKED
                : new SaxonPolicy(paths);
    }

    /// @return The normalised absolute paths that may be read, at or below, as configured.
    public List<Path> getAllowedPaths() {
        return allowedPaths;
    }

    /// Tests whether an absolute URI may be read under this policy.
    ///
    /// @param absoluteUri The absolute URI to read, may be null.
    /// @return True if the URI may be read.
    /// @see #resolveAllowed(String)
    public boolean isAllowed(final String absoluteUri) {
        return resolveAllowed(absoluteUri) != null;
    }

    /// Resolves an absolute URI to the local file that may be read for it.
    ///
    /// The URI must be a `file:` URI with no `..` segments whose path, once symbolic links are resolved, is one
    /// of the allowed paths (with its links resolved) or lies below one. Read the returned path rather than the
    /// URI.
    ///
    /// @param absoluteUri The absolute URI to read, may be null.
    /// @return The real path to read, or null if the URI may not be read.
    public Path resolveAllowed(final String absoluteUri) {
        if (allowedPaths.isEmpty() || NullSafe.isBlankString(absoluteUri)) {
            return null;
        }
        final Path path = toLocalPath(absoluteUri);
        if (path == null) {
            return null;
        }
        final Path realPath = toRealPath(path);
        if (realPath == null) {
            return null;
        }
        return getResolvedAllowedPaths().stream().anyMatch(realPath::startsWith)
                ? realPath
                : null;
    }

    /// Resolves an absolute URI to the real path of a directory, if it is one that may be read under this policy.
    ///
    /// @param absoluteUri The absolute URI to read, may be null.
    /// @return The real path of the directory, or null if the URI may not be read or is not a directory.
    public Path resolveAllowedDirectory(final String absoluteUri) {
        final Path realPath = resolveAllowed(absoluteUri);
        return realPath != null && Files.isDirectory(realPath, LinkOption.NOFOLLOW_LINKS)
                ? realPath
                : null;
    }

    @Override
    public boolean equals(final Object o) {
        return this == o
               || (o instanceof final SaxonPolicy that && allowedPaths.equals(that.allowedPaths));
    }

    @Override
    public int hashCode() {
        return Objects.hash(allowedPaths);
    }

    @Override
    public String toString() {
        return "SaxonPolicy{allowedPaths=" + allowedPaths + '}';
    }

    private List<Path> getResolvedAllowedPaths() {
        final long now = System.nanoTime();
        final ResolvedPaths current = resolvedAllowedPaths;
        if (current != null && now - current.resolvedAtNanos() < ALLOWED_PATHS_TTL_NANOS) {
            return current.paths();
        }
        final List<Path> paths = allowedPaths.stream()
                .map(SaxonPolicy::toRealPath)
                .filter(Objects::nonNull)
                .toList();
        resolvedAllowedPaths = new ResolvedPaths(paths, now);
        return paths;
    }

    // Returns the normalised absolute path for a file: URI or absolute path, or null if it is neither or has
    // a '..' segment.
    private static Path toLocalPath(final String uriOrPath) {
        if (NullSafe.isBlankString(uriOrPath)) {
            return null;
        }
        final String trimmed = uriOrPath.trim();
        try {
            final String pathString;
            if (trimmed.startsWith("/")) {
                pathString = trimmed;
            } else {
                final URI uri = new URI(trimmed);
                if (uri.getScheme() == null
                    || !FILE_SCHEME.equals(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getPath() == null) {
                    return null;
                }
                // Not Path.of(uri) as that refuses a query (e.g. ?recurse=yes) and authorities.
                if (!NullSafe.isEmptyString(uri.getAuthority())
                    && !"localhost".equalsIgnoreCase(uri.getAuthority())) {
                    return null;
                }
                // getPath() is decoded, so an encoded '..' is seen too.
                pathString = uri.getPath();
            }
            final Path path = Path.of(pathString);
            if (!path.isAbsolute() || hasParentSegment(path)) {
                return null;
            }
            return path.normalize();
        } catch (final URISyntaxException | InvalidPathException e) {
            LOGGER.debug("Unable to get a local path for '{}'", uriOrPath, e);
            return null;
        }
    }

    private static boolean hasParentSegment(final Path path) {
        for (final Path segment : path) {
            if (PARENT_SEGMENT.equals(segment.toString())) {
                return true;
            }
        }
        return false;
    }

    // Resolves symbolic links in the deepest part of the path that exists, keeping the rest as it is, so
    // paths that don't exist yet can still be compared. Returns null if the path can't be resolved.
    private static Path toRealPath(final Path normalisedPath) {
        Path existing = normalisedPath;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return normalisedPath;
        }
        try {
            return existing.toRealPath().resolve(existing.relativize(normalisedPath));
        } catch (final IOException e) {
            // E.g. a broken link or a loop.
            LOGGER.debug("Unable to resolve '{}'", normalisedPath, e);
            return null;
        }
    }

    // --------------------------------------------------------------------------------


    private record ResolvedPaths(List<Path> paths, long resolvedAtNanos) {

    }
}
