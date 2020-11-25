package stroom.util.io;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;

@Singleton
public class HomeDirProviderImpl implements HomeDirProvider {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HomeDirProviderImpl.class);

    private final PathConfig pathConfig;

    private Path homeDir;

    @Inject
    public HomeDirProviderImpl(final PathConfig pathConfig) {
        this.pathConfig = pathConfig;
    }

    @Override
    public Path get() {
        if (homeDir == null) {
            Path path = null;

            String dir = pathConfig.getHome();
            if (dir != null) {
                dir = FileUtil.replaceHome(dir);
                path = Paths.get(dir);
            }

            if (path == null) {
                path = getApplicationJarDir().orElse(null);
            }

            if (path == null) {
                path = getDefaultStroomHomeDir().orElse(null);
            }

            if (path == null) {
                throw new NullPointerException("Home dir is null");
            }

            path = path.toAbsolutePath();

            homeDir = path;
        }
        return homeDir;
    }

    private Optional<Path> getDefaultStroomHomeDir() {
        final String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return Optional.empty();
        } else {
            final Path dir = Paths.get(userHome).resolve(".stroom");
            if (Files.isDirectory(dir)) {
                return Optional.of(dir);
            } else {
                return Optional.empty();
            }
        }
    }

    private Optional<Path> getApplicationJarDir() {
        try {
            String codeSourceLocation = this.getClass().getProtectionDomain().getCodeSource().getLocation().getPath();
            if (Pattern.matches(".*/stroom[^/]*.jar$", codeSourceLocation)) {
                return Optional.of(Paths.get(codeSourceLocation).getParent());
            } else {
                return Optional.empty();
            }
        } catch (final RuntimeException e) {
            LOGGER.warn("Unable to determine application jar directory due to: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void setHomeDir(final Path homeDir) {
        this.homeDir = homeDir;
    }
}
