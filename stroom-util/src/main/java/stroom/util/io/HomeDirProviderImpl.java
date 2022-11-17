package stroom.util.io;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class HomeDirProviderImpl implements HomeDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HomeDirProviderImpl.class);

//    private static final String ENV_STROOM_HOME = "STROOM_HOME";

    private final PathConfig pathConfig;

    private Path homeDir;

    @Inject
    public HomeDirProviderImpl(final PathConfig pathConfig) {
        // PathConfig is all RestartRequired so no need for a provider
        this.pathConfig = pathConfig;
    }

    @Override
    public Path get() {
        if (homeDir == null) {
            Path path = null;

            // This is to allow system tests to function as they don't have a yaml file to read the home/temp
            // from, but the validator is set up before the config object is passed in.
            path = NullSafe.get(
                    System.getProperty(HomeDirProvider.PROP_STROOM_HOME),
                    Paths::get);
            if (path != null) {
                LOGGER.warn("Using system property {} for stroom home: {}. " +
                        "This overrides the value in the config file and is only intended for testing.",
                        HomeDirProvider.PROP_STROOM_HOME, path);
            }

            if (path == null) {
                String dir = pathConfig.getHome();
                if (!NullSafe.isEmptyString(dir)) {
                    LOGGER.info("Using home path from configuration file property: {}", dir);
                    dir = FileUtil.replaceHome(dir);
                    path = Paths.get(dir);
                }
            }

            if (path == null) {
                path = getApplicationJarDir()
                        .orElse(null);
                if (path != null) {
                    LOGGER.info("Using application JAR directory for stroom home: {}", path);
                }
            }

            if (path == null) {
                path = getDefaultStroomHomeDir()
                        .orElse(null);
                if (path != null) {
                    LOGGER.info("Using default directory for stroom home: {}", path);
                }
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
            String codeSourceLocation = this.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath();
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
