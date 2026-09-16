/*
 * Copyright 2023 Crown Copyright
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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ForwardFileConfig.LivenessCheckMode;
import stroom.util.concurrent.LazyValue;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Renames a file group into a templated directory tree that another process collects from, copying
 * beside the target first where a rename cannot cross a filesystem boundary. The source is gone
 * only once the rename has succeeded, so an interruption duplicates rather than loses
 * ({@code designs/stages/forward.md} §4.5).
 * <p>
 * Target directories are numbered from an in-memory counter seeded once from the directory, which
 * is safe only while this process is the directory's one writer. In shared mode the directory is a
 * mount several nodes deliver into - a shared give-up directory is required there - so, exactly as
 * the file stores do, each process writes under its own <em>writer root</em>,
 * {@code <path>/<uuid>/}, fresh per start. Two nodes then never count in the same directory, and a
 * node restarting never continues a tree it might have left half-numbered.
 * </p>
 */
public class FileDestination implements Destination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FileDestination.class);

    /**
     * Written into a cached counter when it is evicted, so a thread holding the evicted object
     * can tell and ask the cache to load a fresh one.
     */
    private static final long EVICTED_WRITE_ID = -1;
    private static final int MAX_MOVE_ATTEMPTS = 1_000;
    /** The configured directory: what the validator checks and monitoring reports. */
    private final Path storeDir;
    /** Where this process delivers: {@code storeDir} itself, or a per-process directory under it. */
    private final Path writerRoot;
    private final String name;
    private final PathTemplateConfig subPathTemplate;
    private final Set<String> varsInTemplate;
    private final String livenessCheckPath;
    private final LivenessCheckMode livenessCheckMode;
    private final PathCreator pathCreator;
    private final Path staticBaseDir;
    private final boolean isAtomicMoveEnabled;

    // Because we have templated dirs, we need one commitId per base path, but the templating
    // may mean MANY path variations, so use one AtomicLong per base dir. We could use one
    // but, then we would have to scan every base dir to find the max on boot, so this seems easier.
    private final LoadingCache<Path, AtomicLong> writeIdsCache;
    private final AtomicLong staticPathCommitId;
    private final Function<Path, Path> targetDirCreationFunc;

    public FileDestination(final Path storeDir,
                               final String name,
                               final PathCreator pathCreator,
                               final boolean isAtomicMoveEnabled) {
        this(storeDir,
                name,
                null,
                null,
                null,
                pathCreator,
                isAtomicMoveEnabled);
    }

    /**
     * @param sharedWriters True when other processes deliver into {@code storeDir} too - shared
     *                      pipeline mode - so this one must write under a writer root of its own.
     */
    public FileDestination(final Path storeDir,
                               final ForwardFileConfig forwardFileConfig,
                               final PathCreator pathCreator,
                               final boolean sharedWriters) {
        this(storeDir,
                forwardFileConfig.getName(),
                forwardFileConfig.getSubPathTemplate(),
                forwardFileConfig.getLivenessCheckPath(),
                forwardFileConfig.getLivenessCheckMode(),
                pathCreator,
                forwardFileConfig.isAtomicMoveEnabled(),
                sharedWriters);
    }

    public FileDestination(final Path storeDir,
                               final String name,
                               final PathTemplateConfig pathTemplateConfig,
                               final String livenessCheckPath,
                               final LivenessCheckMode livenessCheckMode,
                               final PathCreator pathCreator,
                               final boolean isAtomicMoveEnabled) {
        this(storeDir, name, pathTemplateConfig, livenessCheckPath, livenessCheckMode, pathCreator,
                isAtomicMoveEnabled, false);
    }

    /**
     * @param sharedWriters True when other processes deliver into {@code storeDir} too - shared
     *                      pipeline mode - so this one must write under a writer root of its own.
     */
    public FileDestination(final Path storeDir,
                               final String name,
                               final PathTemplateConfig pathTemplateConfig,
                               final String livenessCheckPath,
                               final LivenessCheckMode livenessCheckMode,
                               final PathCreator pathCreator,
                               final boolean isAtomicMoveEnabled,
                               final boolean sharedWriters) {

        this.storeDir = Objects.requireNonNull(storeDir);
        // The counter below is seeded from a directory listing and never re-read, which is only
        // correct while nothing else numbers directories under it. On a shared mount that is other
        // nodes, so each process gets a directory of its own to count in.
        this.writerRoot = sharedWriters
                ? storeDir.resolve(UUID.randomUUID().toString())
                : storeDir;
        this.name = name;
        this.subPathTemplate = pathTemplateConfig;
        this.livenessCheckPath = livenessCheckPath;
        this.livenessCheckMode = livenessCheckMode;
        this.pathCreator = pathCreator;
        this.isAtomicMoveEnabled = isAtomicMoveEnabled;

        if (pathTemplateConfig != null && pathTemplateConfig.hasPathTemplate()) {
            final String pathTemplate = pathTemplateConfig.getPathTemplate();
            final String[] vars = pathCreator.findVars(pathTemplate);
            if (NullSafe.hasItems(vars)) {
                staticBaseDir = null;
                // Do this rather than Set.of() because vars can be repeated in the arr
                // which caused Set.of() to throw.
                varsInTemplate = NullSafe.stream(vars)
                        .collect(Collectors.toSet());
            } else {
                staticBaseDir = resolveSubPath(pathTemplate);
                FileUtil.ensureDirExists(staticBaseDir);
                varsInTemplate = null;
            }
        } else {
            staticBaseDir = writerRoot;
            varsInTemplate = null;
        }

        // Initialise the store id.
        FileUtil.ensureDirExists(writerRoot);

        if (staticBaseDir != null) {
            // base dir is static, so we don't need the cost of hitting the cache
            writeIdsCache = null;
            final long maxId = DirUtil.getMaxDirId(staticBaseDir);
            staticPathCommitId = new AtomicLong(maxId);
            LOGGER.debug("'{}' - Initialising maxId for static dir at {} in '{}'", name, maxId, staticBaseDir);
            targetDirCreationFunc = this::createStaticTargetDir;
        } else {
            // Templated base dirs, so need a cache of the commitId counters, one per templated path.
            // No need to age them off.
            writeIdsCache = Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .removalListener((final Path ignoredKey,
                                      final AtomicLong value,
                                      final RemovalCause ignoredCause) -> {
                        // In case any other thread is holding onto the AtomicLong
                        value.set(EVICTED_WRITE_ID);
                    })
                    .build(this::getMaxIdForPath);
            staticPathCommitId = null;
            targetDirCreationFunc = this::createTemplatedTargetDir;
        }
    }

    private AtomicLong getMaxIdForPath(final Path path) {
        FileUtil.ensureDirExists(path);
        final long maxId = DirUtil.getMaxDirId(path);
        LOGGER.debug("'{}' - Initialising maxId at {} in '{}'", name, maxId, path);
        return new AtomicLong(maxId);
    }

    @Override
    public void deliver(final Path group) throws IOException {
        // The target depends on whether the base dir is templated or not
        final Path targetDir = targetDirCreationFunc.apply(group);
        move(group, targetDir);
    }

    @Override
    public Optional<LivenessCheck> livenessCheck() {
        return NullSafe.isNonBlankString(livenessCheckPath) && livenessCheckMode != null
                ? Optional.of(this::checkLive)
                : Optional.empty();
    }

    private void checkLive() throws Exception {
        Path path = Path.of(livenessCheckPath);
        if (!path.isAbsolute()) {
            path = storeDir.resolve(path);
        }
        try {
            switch (livenessCheckMode) {
                case WRITE -> canWriteToFile(path);
                case READ -> {
                    if (!Files.exists(path)) {
                        throw new Exception(LogUtil.message("Path '{}' does not exist", path));
                    }
                }
                case null -> throw new IllegalArgumentException(
                        "Unexpected value of livenessCheckMode " + livenessCheckMode);
            }
        } catch (final Exception e) {
            LOGGER.debug("'{}' - Error during liveness check", name, e);
            throw e;
        }
        LOGGER.debug("'{}' - is live", name);
    }

    private boolean canWriteToFile(final Path path) throws Exception {
        Objects.requireNonNull(path);
        try {
            if (Files.isRegularFile(path)) {
                FileUtil.touch(path);
                return true;
            } else {
                throw new Exception(LogUtil.message("Path '{}' is not a regular file", path));
            }
        } catch (final Exception e) {
            LOGGER.debug("Error trying to write to file {}", path, e);
            throw new Exception(LogUtil.message("Error trying to write to file '{}': {}", path, e.getMessage()));
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        final String storeDirStr = writerRoot.toString();
        // Null-guarded: the 4-arg constructor passes no template, and this is called from doMove's
        // warning, an error-reporting path where an NPE would replace the diagnostic.
        return subPathTemplate != null && subPathTemplate.hasPathTemplate()
                ? storeDirStr + "/" + subPathTemplate.getPathTemplate()
                : storeDirStr;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " " + name + " - " + getDescription();
    }

    private AttributeMap getAttributeMap(final Path dir) {
        final FileGroup fileGroup = new FileGroup(dir);
        final AttributeMap attributeMap = new AttributeMap();
        try {
            AttributeMapUtil.read(fileGroup.getMeta(), attributeMap);
            return attributeMap;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String replaceAllUnusedVars(final String path, final String replacement) {
        Objects.requireNonNull(replacement);
        String str = path;
        for (final String var : varsInTemplate) {
            str = pathCreator.replace(str, var, () -> replacement);
        }
        // Replacement with empty string may have made the path absolute, so remove any leading /
        if (str.startsWith("/")) {
            str = str.substring(1);
        }
        return str;
    }

    private Path getBaseDirWithTemplatedSubDir(final Path sourceDir) {
        final String pathTemplate = subPathTemplate.getPathTemplate();
        String subPathStr = pathCreator.replaceTimeVars(pathTemplate);
        // Wrap the attributeMapSupplier in a LazyValue as we don't want to supply it multiple times
        // in case it is costly, and we don't know if we even need the attributeMap
        final LazyValue<AttributeMap> lazyAttributeMap = LazyValue.initialisedBy(() ->
                getAttributeMap(sourceDir));
        subPathStr = replaceAttribute(subPathStr, "feed", lazyAttributeMap);
        subPathStr = replaceAttribute(subPathStr, "type", lazyAttributeMap);

        subPathStr = switch (subPathTemplate.getTemplatingMode()) {
            case IGNORE_UNKNOWN_PARAMS -> subPathStr;
            case REPLACE_UNKNOWN_PARAMS -> replaceAllUnusedVars(subPathStr, "XXX");
            // If that means we get a/path////sub/dir, then Path with remove the extra slashes
            case REMOVE_UNKNOWN_PARAMS -> replaceAllUnusedVars(subPathStr, "");
        };

        return resolveSubPath(subPathStr);
    }

    /**
     * @param subPathStr AFTER template resolution
     */
    private Path resolveSubPath(final String subPathStr) {
        final Path subPath = Path.of(subPathStr);
        final Path resolvedDir = writerRoot.resolve(subPath).normalize().toAbsolutePath();
        if (subPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    LogUtil.message("subPath '{}' cannot be an absolute path", subPath));
        } else if (!resolvedDir.endsWith(subPath)) {
            // Stop people abusing the params to break out of the writer root and do
            // stuff like ../../../another/path
            throw new IllegalArgumentException(LogUtil.message(
                    "The path '{}' resolved from template '{}' must be a child of path '{}'",
                    resolvedDir,
                    subPathTemplate,
                    writerRoot));
        }
        return resolvedDir;
    }

    private long getNextCommitIdForTemplatedPath(final Path path) {
        int retryCount = 0;
        long nextId = EVICTED_WRITE_ID;
        while (retryCount++ < 100) {
            final AtomicLong writeId = writeIdsCache.get(path);
            // It is a loading cache so
            Objects.requireNonNull(writeId, () -> LogUtil.message(
                    "writeId should not be null for path {}", path));

            // The removal listener sets an evicted AtomicLong to the sentinel. It has to be read
            // before it is incremented: incremented first, an evicted counter would read as 0 and
            // hand out a commit id that may already exist.
            if (writeId.get() == EVICTED_WRITE_ID) {
                continue;
            }
            nextId = writeId.incrementAndGet();
            if (nextId != EVICTED_WRITE_ID) {
                break;
            }
        }
        if (nextId == EVICTED_WRITE_ID) {
            throw new RuntimeException(LogUtil.message("Unable to get next ID for path {} after {} attempts",
                    path, retryCount));
        }
        return nextId;
    }

    private Path createStaticTargetDir(final Path sourceDir) {
        final long commitId = staticPathCommitId.incrementAndGet();
        final Path targetDir = DirUtil.createPath(staticBaseDir, commitId);
        LOGGER.debug("Using static targetDir '{}' (subPathTemplate: '{}', commitId: {})",
                targetDir, subPathTemplate, commitId);
        return targetDir;
    }

    private Path createTemplatedTargetDir(final Path sourceDir) {
        // dynamic templating of the subdir
        final Path baseDir = getBaseDirWithTemplatedSubDir(sourceDir);
        final long commitId = getNextCommitIdForTemplatedPath(baseDir);
        final Path targetDir = DirUtil.createPath(baseDir, commitId);
        LOGGER.debug("Using templated targetDir '{}' (subPathTemplate: '{}', commitId: {})",
                targetDir, subPathTemplate, commitId);
        return targetDir;
    }

    private String replaceAttribute(final String template,
                                    final String attributeName,
                                    final LazyValue<AttributeMap> lazyAttributeMap) {
        // getValueWithoutLocks as we are a single thread so locks not needed
        return pathCreator.replace(template, attributeName, () ->
                NullSafe.get(
                        lazyAttributeMap,
                        LazyValue::getValueWithoutLocks,
                        attrMap ->
                                attrMap.getOrDefault(attributeName, "")));
    }

    private void move(final Path source, final Path target) throws IOException {
        LOGGER.debug(() -> LogUtil.message("Moving '{}' to '{}', isAtomicMoveEnabled: {}",
                LogUtil.path(source), LogUtil.path(target), isAtomicMoveEnabled));

        boolean success = false;
        int tryCount = 0;
        // It is possible other processes will be deleting parts of the dest path
        // so use a loop to keep trying.
        while (tryCount++ < MAX_MOVE_ATTEMPTS) {
            try {
                doMove(source, target);
                success = true;
                break;
            } catch (final NoSuchFileException e) {
                if (!Files.exists(source)) {
                    throw e;
                }
                Files.createDirectories(target.getParent());
            }
        }
        if (!success) {
            throw new RuntimeException(LogUtil.message("Unable to move '{}' to '{}' after {} attempts {}",
                    LogUtil.path(source), LogUtil.path(target), tryCount));
        }
    }

    /**
     * {@code source} is always a file group directory, so the fallback cannot be a plain
     * {@link Files#move(Path, Path, java.nio.file.CopyOption...)}: the JDK can only rename a directory,
     * so across filesystems - the very case the fallback exists for - it fails with
     * {@link java.nio.file.DirectoryNotEmptyException}. {@link DirUtil#moveDirAcrossFileStores} copies
     * the file group to a staging directory beside the target instead, so the rename that publishes it
     * is within one filesystem and stays atomic, and the source is deleted only once that rename has
     * succeeded. An interruption therefore duplicates the file group rather than losing it.
     */
    private void doMove(final Path source, final Path target) throws IOException {
        if (isAtomicMoveEnabled) {
            try {
                // If the target is on a remote FS then chances are ATOMIC_MOVE will not be supported
                // so, we need a fallback.
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
                return;
            } catch (final AtomicMoveNotSupportedException e) {
                LOGGER.warn(() -> LogUtil.message(
                        "'{}' - Atomic move not supported, falling back to a copy beside the target "
                        + "followed by an atomic rename into place. To stop seeing this warning set the "
                        + "config property {} to false. Moving '{}' to '{}'",
                        getDescription(),
                        ForwardFileConfig.PROP_NAME_ATOMIC_MOVE_ENABLED,
                        LogUtil.path(source),
                        LogUtil.path(target)));
            }
        }

        // Either the operator has told us this destination cannot do an atomic move, or we have just
        // found out that it cannot.
        DirUtil.moveDirAcrossFileStores(source, target);
    }

    /**
     * The configured root of the tree groups are delivered into; the give-up destination's is what
     * the validator checks is shared in shared mode.
     */
    public Path getStoreDir() {
        return storeDir;
    }

    /**
     * Where this process actually delivers: {@link #getStoreDir()} itself, or the per-process
     * directory under it that keeps this process's numbering apart from other nodes'.
     */
    public Path getWriterRoot() {
        return writerRoot;
    }
}
