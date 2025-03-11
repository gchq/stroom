package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.ForwardFileConfig.LivenessCheckMode;
import stroom.util.NullSafe;
import stroom.util.concurrent.LazyValue;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class ForwardFileDestinationImpl implements ForwardFileDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationImpl.class);
    private static final int MAX_MOVE_ATTEMPTS = 1_000;
    private final Path storeDir;
    private final String name;
    private final String subPathTemplate;
    private final TemplatingMode templatingMode;
    private final Set<String> varsInTemplate;
    private final String livenessCheckPath;
    private final LivenessCheckMode livenessCheckMode;
    private final PathCreator pathCreator;
    private final Path staticBaseDir;

    private final AtomicLong writeId = new AtomicLong();

    public ForwardFileDestinationImpl(final Path storeDir,
                                      final String name,
                                      final PathCreator pathCreator) {
        this(storeDir,
                name,
                null,
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
                forwardFileConfig.getTemplatingMode(),
                forwardFileConfig.getLivenessCheckPath(),
                forwardFileConfig.getLivenessCheckMode(),
                pathCreator);
    }

    public ForwardFileDestinationImpl(final Path storeDir,
                                      final String name,
                                      final String subPathTemplate,
                                      final TemplatingMode templatingMode,
                                      final String livenessCheckPath,
                                      final LivenessCheckMode livenessCheckMode,
                                      final PathCreator pathCreator) {

        this.storeDir = Objects.requireNonNull(storeDir);
        this.name = name;
        this.subPathTemplate = subPathTemplate;
        this.templatingMode = Objects.requireNonNullElse(
                templatingMode, ForwardFileConfig.DEFAULT_TEMPLATING_MODE);
        this.livenessCheckPath = livenessCheckPath;
        this.livenessCheckMode = livenessCheckMode;
        this.pathCreator = pathCreator;

        if (NullSafe.isNonEmptyString(subPathTemplate)) {
            final String[] vars = pathCreator.findVars(subPathTemplate);
            if (NullSafe.hasItems(vars)) {
                staticBaseDir = null;
                varsInTemplate = Set.of(vars);
            } else {
                staticBaseDir = resolveSubPath(subPathTemplate);
                FileUtil.ensureDirExists(staticBaseDir);
                varsInTemplate = null;
            }
        } else {
            staticBaseDir = storeDir;
            varsInTemplate = null;
        }

        // Initialise the store id.
        FileUtil.ensureDirExists(storeDir);
        final long maxId = DirUtil.getMaxDirId(storeDir);
        writeId.set(maxId);
    }

    @Override
    public void add(final Path sourceDir) {
        // Record the sequence id for future use.
        final long commitId = writeId.incrementAndGet();
        final Path targetDir = createTargetDir(commitId, sourceDir);
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
    public boolean performLivenessCheck() {
        boolean isLive = false;
        if (NullSafe.isNonBlankString(livenessCheckPath)) {
            try {
                Path path = Path.of(livenessCheckPath);
                if (!path.isAbsolute()) {
                    path = storeDir.resolve(path);
                }
                isLive = switch (livenessCheckMode) {
                    case WRITE -> canWriteToFile(path);
                    case READ -> Files.exists(path);
                    case null -> throw new IllegalArgumentException(
                            "Unexpected value of livenessCheckMode " + livenessCheckMode);
                };
            } catch (Exception e) {
                LOGGER.debug("'{}' - Error during liveness check", name, e);
            }
        } else {
            isLive = true;
        }
        LOGGER.debug("'{}' - isLive: {}", name, isLive);
        return isLive;
    }

//    private boolean canWriteToFile(final Path path) {
//        Objects.requireNonNull(path);
//        try {
//            FileUtil.touch(path);
//            return true;
//        } catch (IOException e) {
//            final Path parent = path.getParent();
//            try {
//                FileUtil.mkdirs(parent);
//            } catch (Exception ex) {
//                LOGGER.debug("Error creating path {}", parent, ex);
//                return false;
//            }
//            try {
//                FileUtil.touch(path);
//                return true;
//            } catch (IOException ex) {
//                LOGGER.debug("Error touching file {}", path, ex);
//                return false;
//            }
//        }
//    }

    private boolean canWriteToFile(final Path path) {
        Objects.requireNonNull(path);
        try {
            if (Files.isRegularFile(path)) {
                FileUtil.touch(path);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            LOGGER.debug("Error trying to write to file {}", path, e);
            return false;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDestinationDescription() {
        String str = storeDir.toString();
        if (NullSafe.isNonBlankString(subPathTemplate)) {
            str += "/" + subPathTemplate;
        }
        return str;
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
        } catch (IOException e) {
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
        String subPathStr = pathCreator.replaceTimeVars(subPathTemplate);
        // Wrap the attributeMapSupplier in a LazyValue as we don't want to supply it multiple times
        // in case it is costly, and we don't know if we even need the attributeMap
        final LazyValue<AttributeMap> lazyAttributeMap = LazyValue.initialisedBy(() ->
                getAttributeMap(sourceDir));
        subPathStr = replaceAttribute(subPathStr, "feed", lazyAttributeMap);
        subPathStr = replaceAttribute(subPathStr, "type", lazyAttributeMap);

        subPathStr = switch (templatingMode) {
            case IGNORE_UNKNOWN -> subPathStr;
            case REPLACE_UNKNOWN -> replaceAllUnusedVars(subPathStr, "XXX");
            // If that means we get a/path////sub/dir, then Path with remove the extra slashes
            case REMOVE_UNKNOWN -> replaceAllUnusedVars(subPathStr, "");
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

    private Path createTargetDir(final long commitId,
                                 final Path sourceDir) {
        final Path baseDir;
        // dynamic templating of the subdir
        baseDir = Objects.requireNonNullElseGet(
                staticBaseDir,
                () -> getBaseDirWithTemplatedSubDir(sourceDir));
        final Path targetDir = DirUtil.createPath(baseDir, commitId);
        LOGGER.debug("Using targetDir '{}' (subPathTemplate: '{}', commitId: {})",
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
                Files.move(source,
                        target,
                        StandardCopyOption.ATOMIC_MOVE);
                success = true;
                break;
            } catch (final NoSuchFileException e) {
                if (!Files.exists(source)) {
                    throw e;
                }
                DirUtil.ensureDirExists(target.getParent());
            }
        }
        if (!success) {
            throw new RuntimeException(LogUtil.message("Unable to move {} to {} after {} attempts {}",
                    source, target, tryCount));
        }
    }

    @Override
    public Path getStoreDir() {
        return storeDir;
    }
}
