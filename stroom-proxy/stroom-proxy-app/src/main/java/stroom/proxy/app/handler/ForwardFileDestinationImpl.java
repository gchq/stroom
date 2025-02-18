package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.util.NullSafe;
import stroom.util.concurrent.LazyValue;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class ForwardFileDestinationImpl implements ForwardFileDestination {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ForwardFileDestinationImpl.class);
    private final Path storeDir;
    private final String subPathTemplate;
    private final PathCreator pathCreator;

    private final AtomicLong writeId = new AtomicLong();

    public ForwardFileDestinationImpl(final Path storeDir,
                                      final String subPathTemplate,
                                      final PathCreator pathCreator) {
        this.storeDir = storeDir;
        this.subPathTemplate = subPathTemplate;
        this.pathCreator = pathCreator;

        // Initialise the store id.
        final long maxId = DirUtil.getMaxDirId(storeDir);
        writeId.set(maxId);
    }

    @Override
    public void add(final Path sourceDir) {
        // Record the sequence id for future use.
        final long commitId = writeId.incrementAndGet();

        final Path targetDir = createTargetDir(commitId, () -> getAttributeMap(sourceDir));
        try {
            move(sourceDir, targetDir);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
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

    private Path createTargetDir(final long commitId,
                                 final Supplier<AttributeMap> attributeMapSupplier) {
        String subPathStr = pathCreator.replaceTimeVars(subPathTemplate);
        // Wrap the attributeMapSupplier in a LazyValue as we don't want to supply it multiple times
        // in case it is costly, and we don't know if we even need the attributeMap
        final LazyValue<AttributeMap> lazyAttributeMap = LazyValue.initialisedBy(attributeMapSupplier);
        subPathStr = replaceAttribute(subPathStr, "feed", lazyAttributeMap);
        subPathStr = replaceAttribute(subPathStr, "type", lazyAttributeMap);
        final Path subPath = Path.of(subPathStr);
        final Path targetDir = DirUtil.createPath(storeDir, commitId).resolve(subPath);
        if (subPath.isAbsolute()) {
            throw new IllegalArgumentException(
                    LogUtil.message("subPath '{}' cannot be an absolute path", subPath));
        } else if (!targetDir.normalize().toAbsolutePath().endsWith(subPath)) {
            // Stop people doing things like ../../../another/path
            throw new IllegalArgumentException(LogUtil.message(
                    "The value ('{}') of property {} must be a child of property {} ('{}')",
                    subPathTemplate, targetDir));
        }
        LOGGER.debug("Using targetDir '{}', subPathStr '{}'", targetDir, subPathStr);
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
        while (!success) {
            try {
                Files.move(source,
                        target,
                        StandardCopyOption.ATOMIC_MOVE);
                success = true;
            } catch (final NoSuchFileException e) {
                if (!Files.exists(source)) {
                    throw e;
                }
                DirUtil.ensureDirExists(target.getParent());
            }
        }


        try {
            Files.move(source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (final NoSuchFileException e) {
            DirUtil.ensureDirExists(target.getParent());
            Files.move(source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
