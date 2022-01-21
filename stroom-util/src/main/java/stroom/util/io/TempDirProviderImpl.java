package stroom.util.io;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TempDirProviderImpl implements TempDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TempDirProviderImpl.class);

    private final PathConfig pathConfig;
    private final HomeDirProvider homeDirProvider;

    private volatile Path tempDir;

    @Inject
    public TempDirProviderImpl(final PathConfig pathConfig,
                               final HomeDirProvider homeDirProvider) {
        // PathConfig is all RestartRequired so no need for a provider
        this.pathConfig = pathConfig;
        this.homeDirProvider = homeDirProvider;
    }

    @Override
    public Path get() {
        if (tempDir == null) {
            Path path = null;

            String dir = pathConfig.getTemp();

            if (dir != null && !dir.isEmpty()) {
                LOGGER.info("Using temp path configuration property: {}", dir);
                dir = FileUtil.replaceHome(dir);
                path = Paths.get(dir);
            }

            if (path == null) {
                path = getDefaultTempDir()
                        .orElse(null);
            }

            if (path == null) {
                path = homeDirProvider.get()
                        .resolve("temp");
                LOGGER.info("Using stroom home sub directory for temp path {}",
                        path.toAbsolutePath().normalize());
            }

            // If this isn't an absolute path then make it so relative to the home path.
            if (!path.isAbsolute()) {
                path = homeDirProvider.get()
                        .resolve(path);
            }

            path = path.toAbsolutePath();

            tempDir = path;
        }
        return tempDir;
    }

    private Optional<Path> getDefaultTempDir() {
        final String systemTempDirStr = System.getProperty("java.io.tmpdir");
        if (systemTempDirStr == null) {
            return Optional.empty();
        } else {
            final Path systemTempDir = Paths.get(systemTempDirStr);
            if (Files.isDirectory(systemTempDir)) {
                final String subDirName = pathConfig.getClass()
                        .getSimpleName()
                        .toLowerCase()
                        .contains("proxy")
                        ? "stroom-proxy"
                        : "stroom";

                final Path dir = Paths.get(systemTempDirStr).resolve(subDirName);
                LOGGER.info("Using system temp path {}", dir.toAbsolutePath().normalize());
                return Optional.of(dir);
            } else {
                LOGGER.debug("{} does not exist or is not a directory",
                        systemTempDir.toAbsolutePath().normalize());
                return Optional.empty();
            }
        }
    }

    public void setTempDir(final Path tempDir) {
        this.tempDir = tempDir;
    }
}
