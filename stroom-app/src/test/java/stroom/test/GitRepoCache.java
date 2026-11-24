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

package stroom.test;

import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.TagOpt;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/// A local cache of remote Git repositories, shared between JVMs (e.g. test forks) and between builds.
///
/// Each version of a repository is held in its own directory, named after a hash of the commit IDs of
/// the repository's branches and tags. Asking the remote for those IDs (`git ls-remote`) is a single
/// small request, so we can tell whether we already have the current version without downloading
/// anything.
///
/// A new version is cloned into a temporary directory and then atomically moved into place, so a
/// version directory is always complete, is never changed once it exists, and is named after what
/// was actually cloned. This means a JVM can keep using a version while another JVM downloads a newer
/// one.
///
/// If the remote cannot be reached, the most recently used cached version is used instead.
public class GitRepoCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoCache.class);

    static final String TEMP_DIR_PREFIX = ".tmp-";
    static final String LOCK_FILE_NAME = ".lock";

    /// Unused versions and abandoned temporary directories are deleted once they have not been used for
    /// this long. A version's modified time is updated each time it is used.
    static final Duration RETENTION = Duration.ofDays(1);

    private static final String HEADS_PREFIX = Constants.R_HEADS;
    private static final String TAGS_PREFIX = Constants.R_TAGS;
    private static final String REMOTE_HEADS_PREFIX = Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + "/";

    // A FileLock only excludes other processes, and a second lock on the same file from this JVM
    // throws, so threads in this JVM are excluded by this.
    private static final Object JVM_LOCK = new Object();

    private final Path cacheRoot;

    /// @param cacheRoot The directory to hold the cached repositories.
    public GitRepoCache(final Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    /// @return A cache in `~/.stroom/git-repo-cache`, alongside the build's content pack downloads.
    public static GitRepoCache createDefault() {
        return new GitRepoCache(Path.of(System.getProperty("user.home"), ".stroom", "git-repo-cache"));
    }

    /// Get a local clone of a remote repository that matches the remote's current branches and tags,
    /// downloading it only if we do not already have it.
    ///
    /// The returned directory is never changed once it exists.
    ///
    /// @param gitRepoUrl The URL of the remote repository.
    /// @return The directory holding the local clone.
    /// @throws IOException If the remote cannot be reached and there is no cached version, or the
    /// download fails.
    public Path getRepo(final String gitRepoUrl) throws IOException {
        final Path repoDir = getRepoDir(gitRepoUrl);
        Files.createDirectories(repoDir);

        final Optional<String> optRemoteVersion = getRemoteVersion(gitRepoUrl);
        final Path versionDir;
        if (optRemoteVersion.isEmpty()) {
            versionDir = getMostRecentlyUsedVersion(repoDir).orElseThrow(() -> new IOException(
                    "Unable to reach Git repository '" + gitRepoUrl + "' and there is no cached copy in "
                    + repoDir));
            LOGGER.warn("Unable to reach Git repository '{}', using cached copy {}", gitRepoUrl, versionDir);
        } else {
            final Path remoteVersionDir = repoDir.resolve(optRemoteVersion.get());
            if (Files.isDirectory(remoteVersionDir)) {
                LOGGER.debug("Git repository '{}' is up to date in {}", gitRepoUrl, remoteVersionDir);
                versionDir = remoteVersionDir;
            } else {
                versionDir = downloadOrUseCache(gitRepoUrl, repoDir, remoteVersionDir);
            }
        }

        // Record that it is in use so it is not deleted as unused
        Files.setLastModifiedTime(versionDir, FileTime.from(Instant.now()));
        return versionDir;
    }

    private Path downloadOrUseCache(final String gitRepoUrl,
                                    final Path repoDir,
                                    final Path remoteVersionDir) throws IOException {
        try {
            return downloadUnderLock(gitRepoUrl, repoDir, remoteVersionDir);
        } catch (final IOException e) {
            final Optional<Path> optCachedVersion = getMostRecentlyUsedVersion(repoDir);
            if (optCachedVersion.isEmpty()) {
                throw e;
            }
            LOGGER.warn("Unable to download Git repository '{}', using cached copy {}: {}",
                    gitRepoUrl, optCachedVersion.get(), e.getMessage());
            return optCachedVersion.get();
        }
    }

    private Path downloadUnderLock(final String gitRepoUrl,
                                   final Path repoDir,
                                   final Path remoteVersionDir) throws IOException {
        synchronized (JVM_LOCK) {
            // The lock file is never deleted. Deleting it would let a process waiting on the old file and
            // a process that has just created a new one both hold 'the' lock.
            try (final FileChannel channel = FileChannel.open(repoDir.resolve(LOCK_FILE_NAME),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                    final FileLock ignored = channel.lock()) {

                // Another process may have downloaded it while we waited for the lock
                if (Files.isDirectory(remoteVersionDir)) {
                    LOGGER.debug("Git repository '{}' was downloaded by another process to {}",
                            gitRepoUrl, remoteVersionDir);
                    return remoteVersionDir;
                }
                final Path versionDir = download(gitRepoUrl, repoDir);
                deleteUnused(repoDir, versionDir);
                return versionDir;
            }
        }
    }

    private Path download(final String gitRepoUrl, final Path repoDir) throws IOException {
        final Path tempDir = Files.createTempDirectory(repoDir, TEMP_DIR_PREFIX);
        final Instant startTime = Instant.now();
        try {
            final String clonedVersion;
            // Not a bare repo as we want access to the content-store.yml file.
            // Fetch all tags, not just those on the branches, so that the version matches ls-remote. JGit
            // currently does this by default, unlike the git command line, so be explicit.
            try (final Git git = Git.cloneRepository()
                    .setURI(gitRepoUrl)
                    .setDirectory(tempDir.toFile())
                    .setCloneAllBranches(true)
                    .setTagOption(TagOpt.FETCH_TAGS)
                    .setBare(false)
                    .call()) {
                clonedVersion = getLocalVersion(git);
            }

            // Name it after what was actually cloned, which only differs from what ls-remote said if
            // something was pushed in between, in which case we may already have it.
            // We hold the lock, so nothing else can create it before the move. Check rather than rely on
            // the move failing, as renaming onto an existing empty directory replaces it.
            final Path versionDir = repoDir.resolve(clonedVersion);
            if (Files.isDirectory(versionDir)) {
                LOGGER.debug("Version {} of '{}' already exists", clonedVersion, gitRepoUrl);
                FileUtil.deleteDir(tempDir);
            } else {
                Files.move(tempDir, versionDir, StandardCopyOption.ATOMIC_MOVE);
            }
            LOGGER.info(() -> LogUtil.message("Cloned Git repository '{}' to {} in {}",
                    gitRepoUrl, versionDir, Duration.between(startTime, Instant.now())));
            return versionDir;

        } catch (final GitAPIException | RuntimeException e) {
            FileUtil.deleteDir(tempDir);
            throw new IOException("Error cloning Git repository '" + gitRepoUrl + "': " + e.getMessage(), e);
        }
    }

    /// Delete versions and abandoned temporary directories that have not been used for [#RETENTION].
    /// Must be called under the lock.
    private void deleteUnused(final Path repoDir, final Path keep) throws IOException {
        final Instant threshold = Instant.now().minus(RETENTION);
        try (final Stream<Path> entries = Files.list(repoDir)) {
            for (final Path entry : entries.toList()) {
                if (Files.isDirectory(entry)
                    && !entry.equals(keep)
                    && Files.getLastModifiedTime(entry).toInstant().isBefore(threshold)) {
                    LOGGER.info("Deleting unused Git repository cache entry {}", entry);
                    FileUtil.deleteDir(entry);
                }
            }
        }
    }

    /// @return The hash of the remote's branches and tags, or empty if the remote cannot be reached.
    private Optional<String> getRemoteVersion(final String gitRepoUrl) {
        try {
            final Collection<Ref> refs = Git.lsRemoteRepository()
                    .setRemote(gitRepoUrl)
                    .setHeads(true)
                    .setTags(true)
                    .call();
            final List<String> lines = refs.stream()
                    .filter(ref -> !ref.getName().endsWith("^{}"))
                    .map(ref -> ref.getName() + " " + ref.getObjectId().getName())
                    .toList();
            return Optional.of(hash(lines));
        } catch (final GitAPIException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Error listing refs of '{}': {}", gitRepoUrl, e.getMessage()), e);
            return Optional.empty();
        }
    }

    /// @return The hash of a clone's branches and tags, using the same names as the remote's so it
    /// matches [#getRemoteVersion(String)].
    private static String getLocalVersion(final Git git) throws IOException {
        final List<String> lines = git.getRepository()
                .getRefDatabase()
                .getRefs()
                .stream()
                .filter(ref -> !ref.isSymbolic())
                .map(GitRepoCache::toRemoteRefLine)
                .flatMap(Optional::stream)
                .toList();
        return hash(lines);
    }

    /// @return The ref as the remote would list it, e.g. `refs/remotes/origin/main` as
    /// `refs/heads/main`, or empty if the remote would not list it, e.g. the local branch, which is
    /// just a copy of one of the remote branches.
    private static Optional<String> toRemoteRefLine(final Ref ref) {
        final String name = ref.getName();
        final String remoteName;
        if (name.startsWith(REMOTE_HEADS_PREFIX)) {
            remoteName = HEADS_PREFIX + name.substring(REMOTE_HEADS_PREFIX.length());
        } else if (name.startsWith(TAGS_PREFIX)) {
            remoteName = name;
        } else {
            return Optional.empty();
        }
        return Optional.of(remoteName + " " + ref.getObjectId().getName());
    }

    private static String hash(final List<String> lines) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            lines.stream()
                    .sorted()
                    .forEach(line -> digest.update((line + "\n").getBytes(StandardCharsets.UTF_8)));
            return HexFormat.of().formatHex(digest.digest());
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static Optional<Path> getMostRecentlyUsedVersion(final Path repoDir) throws IOException {
        try (final Stream<Path> entries = Files.list(repoDir)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(entry -> !entry.getFileName().toString().startsWith("."))
                    .max(Comparator.comparing(GitRepoCache::getLastModifiedTime));
        }
    }

    private static FileTime getLastModifiedTime(final Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    /// @return The cache directory for a repository, e.g. `<cacheRoot>/gchq/stroom-content` for
    /// `https://github.com/gchq/stroom-content.git`.
    private Path getRepoDir(final String gitRepoUrl) throws IOException {
        try {
            String path = new URI(gitRepoUrl).getPath();
            if (path.endsWith(".git")) {
                path = path.substring(0, path.length() - ".git".length());
            }
            Path repoDir = cacheRoot;
            for (final String part : path.split("/")) {
                if (NullSafe.isNonBlankString(part)) {
                    repoDir = repoDir.resolve(part);
                }
            }
            return repoDir.toAbsolutePath();
        } catch (final URISyntaxException e) {
            throw new IOException("Invalid Git repository URL '" + gitRepoUrl + "': " + e.getMessage(), e);
        }
    }
}
