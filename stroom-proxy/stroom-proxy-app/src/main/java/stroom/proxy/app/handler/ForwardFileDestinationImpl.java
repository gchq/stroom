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
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

public class ForwardFileDestinationImpl implements ForwardFileDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationImpl.class);
    private static final int MAX_MOVE_ATTEMPTS = 1_000;
    private final Path storeDir;
    private final String name;
    private final PathTemplateConfig subPathTemplate;
    private final Set<String> varsInTemplate;
    private final String livenessCheckPath;
    private final LivenessCheckMode livenessCheckMode;
    private final PathCreator pathCreator;
    private final Path staticBaseDir;

    // Because we have templated dirs, we need one commitId per base path, but the templating
    // may mean MANY path variations, so use one AtomicLong per base dir. We could use one
    // but, then we would have to scan every base dir to find the max on boot, so this seems easier.
    private final LoadingCache<Path, AtomicLong> writeIdsCache;
    private final AtomicLong staticPathCommitId;
    private final Function<Path, Path> targetDirCreationFunc;

    public ForwardFileDestinationImpl(final Path storeDir,
                                      final String name,
                                      final PathCreator pathCreator) {
        this(storeDir,
                name,
                null,
                null,
                null,
                pathCreator);
    }

    public ForwardFileDestinationImpl(final Path storeDir,
                                      final ForwardFileConfig forwardFileConfig,
                                      final PathCreator pathCreator) {
        this(storeDir,
                forwardFileConfig.getName(),
                forwardFileConfig.getSubPathTemplate(),
                forwardFileConfig.getLivenessCheckPath(),
                forwardFileConfig.getLivenessCheckMode(),
                pathCreator);
    }

    public ForwardFileDestinationImpl(final Path storeDir,
                                      final String name,
                                      final PathTemplateConfig pathTemplateConfig,
                                      final String livenessCheckPath,
                                      final LivenessCheckMode livenessCheckMode,
                                      final PathCreator pathCreator) {

        this.storeDir = Objects.requireNonNull(storeDir);
        this.name = name;
        this.subPathTemplate = pathTemplateConfig;
        this.livenessCheckPath = livenessCheckPath;
        this.livenessCheckMode = livenessCheckMode;
        this.pathCreator = pathCreator;

        if (pathTemplateConfig != null && pathTemplateConfig.hasPathTemplate()) {
            final String pathTemplate = pathTemplateConfig.getPathTemplate();
            final String[] vars = pathCreator.findVars(pathTemplate);
            if (NullSafe.hasItems(vars)) {
                staticBaseDir = null;
                varsInTemplate = Set.of(vars);
            } else {
                staticBaseDir = resolveSubPath(pathTemplate);
                FileUtil.ensureDirExists(staticBaseDir);
                varsInTemplate = null;
            }
        } else {
            staticBaseDir = storeDir;
            varsInTemplate = null;
        }

        // Initialise the store id.
        FileUtil.ensureDirExists(storeDir);

        if (staticBaseDir != null) {
            // base dir is static, so we don't need the cost of hitting the cache
            writeIdsCache = null;
            final long maxId = DirUtil.getMaxDirId(staticBaseDir);
            staticPathCommitId = new AtomicLong(maxId);
            LOGGER.debug("'{}' - Initialising maxId at {} in '{}'", name, maxId, staticBaseDir);
            targetDirCreationFunc = this::createStaticTargetDir;
        } else {
            // Templated base dirs, so need a cache of the commitId counters, one per templated path.
            // No need to age them off.
            writeIdsCache = Caffeine.newBuilder()
                    .maximumSize(1_000)
                    .removalListener((final Path key, final AtomicLong value, final RemovalCause cause) -> {
                        if (value != null) {
                            // In case any other thread is holding onto the AtomicLong
                            value.set(-1);
                        }
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
    public void add(final Path sourceDir) {
        // Record the sequence id for future use.
        // The func is dependent on whether the base dir is templated or not
        final Path targetDir = targetDirCreationFunc.apply(sourceDir);
        try {
            move(sourceDir, targetDir);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean hasLivenessCheck() {
        return NullSafe.isNonBlankString(livenessCheckPath) && livenessCheckMode != null;
    }

    @Override
    public boolean performLivenessCheck() throws Exception {
        boolean isLive = false;
        if (NullSafe.isNonBlankString(livenessCheckPath)) {
            Path path = null;
            try {
                path = Path.of(livenessCheckPath);
                if (!path.isAbsolute()) {
                    path = storeDir.resolve(path);
                }
                isLive = switch (livenessCheckMode) {
                    case WRITE -> canWriteToFile(path);
                    case READ -> {
                        final boolean exists = Files.exists(path);
                        if (!exists) {
                            throw new Exception(LogUtil.message("Path '{}' does not exist", path));
                        } else {
                            yield true;
                        }
                    }
                    case null -> throw new IllegalArgumentException(
                            "Unexpected value of livenessCheckMode " + livenessCheckMode);
                };
            } catch (final Exception e) {
                LOGGER.debug("'{}' - Error during liveness check", name, e);
                throw e;
            }
        } else {
            isLive = true;
        }
        LOGGER.debug("'{}' - isLive: {}", name, isLive);
        return isLive;
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
    public String getDestinationDescription() {
        final String storeDirStr = storeDir.toString();
        return subPathTemplate.hasPathTemplate()
                ? storeDirStr + "/" + subPathTemplate.getPathTemplate()
                : storeDirStr;
    }

    @Override
    public String toString() {
        return asString();
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
        final Path resolvedDir = storeDir.resolve(subPath).normalize().toAbsolutePath();
        if (subPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    LogUtil.message("subPath '{}' cannot be an absolute path", subPath));
        } else if (!resolvedDir.endsWith(subPath)) {
            // Stop people abusing the params to break out of the storeDir and do
            // stuff like ../../../another/path
            throw new IllegalArgumentException(LogUtil.message(
                    "The path '{}' resolved from template '{}' must be a child of path '{}'",
                    resolvedDir,
                    subPathTemplate,
                    storeDir));
        }
        return resolvedDir;
    }

    private long getNextCommitIdForTemplatedPath(final Path path) {
        int retryCount = 0;
        long nextId = -1;
        while (retryCount++ < 100) {
            final AtomicLong writeId = writeIdsCache.get(path);
            // It is a loading cache so
            Objects.requireNonNull(writeId, () -> LogUtil.message(
                    "writeId should not be null for path {}", path));

            nextId = writeId.incrementAndGet();
            if (nextId != -1) {
                break;
            }
        }
        // AtomicLong is set to -1 on removal from the cache, so if we happen to hold the object that
        // is being removed, ignore it and get the cache to load it again.
        if (nextId == -1) {
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
        LOGGER.debug(() -> LogUtil.message("Moving '{}' to '{}",
                LogUtil.path(source), LogUtil.path(target)));

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
            throw new RuntimeException(LogUtil.message("Unable to move {} to {} after {} attempts {}",
                    source, target, tryCount));
        }
    }

    private void doMove(final Path source, final Path target) throws IOException {
        try {
            // If the target is on a remote FS then chances are ATOMIC_MOVE will not be supported
            // so, we need a fallback, accepting that we lose the guarantee of exactly once.
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (final AtomicMoveNotSupportedException e) {
            LOGGER.warn(() -> LogUtil.message(
                    "'{}' - Atomic move not supported, falling back to non-atomic move. "
                    + "To stop seeing this warning set the config property {} to false."
                    + "Moving '{}' to '{}",
                    getDestinationDescription(),
                    ForwardFileConfig.PROP_NAME_ATOMIC_MOVE_ENABLED,
                    LogUtil.path(source),
                    LogUtil.path(target)));
            // Non-atomic move
            Files.move(source, target);
        }
    }

    @Override
    public Path getStoreDir() {
        return storeDir;
    }
}
