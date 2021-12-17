package stroom.test.common;

import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * A {@link PathCreator} that is useful for tests. It create a temporary dir
 * and a home/temp dir within it. {@link AutoCloseable} to make it easy to destroy afterwards.
 * Delegates most methods to {@link SimplePathCreator}.
 */
public class TemporaryPathCreator implements PathCreator, AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemporaryPathCreator.class);

    private final SimplePathCreator delegate;
    private final Path baseDir;
    private final Path homeDir;
    private final Path tempDir;
    private final HomeDirProvider homeDirProvider;
    private final TempDirProvider tempDirProvider;

    /**
     * Creates a temporary directory and builds home and temp sub dirs in it.
     */
    public TemporaryPathCreator() {
        this(createTempDir());
    }

    /**
     * Builds home and temp sub dirs in tempBaseDir
     */
    public TemporaryPathCreator(final Path tempBaseDir) {
        try {
            baseDir = tempBaseDir;
            LOGGER.debug(() -> "Created directory " + baseDir.toAbsolutePath().normalize());
            homeDir = baseDir.resolve("home");
            tempDir = baseDir.resolve("temp");
            Files.createDirectories(homeDir);
            Files.createDirectories(tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Error creating temp dir with prefix 'stroom'", e);
        }
        homeDirProvider = () -> homeDir;
        tempDirProvider = () -> tempDir;
        delegate = new SimplePathCreator(homeDirProvider, tempDirProvider);
    }

    public Path getBaseTempDir() {
        return baseDir;
    }

    public void delete() {
        FileUtil.deleteDir(baseDir);
    }

    public TempDirProvider getTempDirProvider() {
        return tempDirProvider;
    }

    public HomeDirProvider getHomeDirProvider() {
        return homeDirProvider;
    }

    @Override
    public String replaceTimeVars(final String path) {
        return delegate.replaceTimeVars(path);
    }

    @Override
    public String replaceTimeVars(final String path, final ZonedDateTime dateTime) {
        return delegate.replaceTimeVars(path, dateTime);
    }

    @Override
    public String replaceSystemProperties(final String path) {
        return delegate.replaceSystemProperties(path);
    }

    @Override
    public Path toAppPath(final String pathString) {
        return delegate.toAppPath(pathString);
    }

    @Override
    public String replaceUUIDVars(final String path) {
        return delegate.replaceUUIDVars(path);
    }

    @Override
    public String replaceFileName(final String path, final String fileName) {
        return delegate.replaceFileName(path, fileName);
    }

    @Override
    public String[] findVars(final String path) {
        return delegate.findVars(path);
    }

    @Override
    public String replace(final String path,
                          final String type,
                          final LongSupplier replacementSupplier,
                          final int pad) {
        return delegate.replace(path, type, replacementSupplier, pad);
    }

    @Override
    public String replace(final String path,
                          final String type,
                          final Supplier<String> replacementSupplier) {
        return delegate.replace(path, type, replacementSupplier);
    }

    @Override
    public String replaceAll(final String path) {
        return delegate.replaceAll(path);
    }

    @Override
    public String replaceContextVars(final String path) {
        return delegate.replaceContextVars(path);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    @Override
    public void close() {
        delete();
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("stroom");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
