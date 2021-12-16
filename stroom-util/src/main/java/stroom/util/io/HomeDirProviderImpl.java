package stroom.util.io;

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

//    private static final String PROP_STROOM_HOME = "stroom.home";
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

//            String dir = System.getProperty(PROP_STROOM_HOME);
//            if (dir != null) {
//                LOGGER.info("Using stroom.home system property: {}", dir);
//            } else {
//                dir = System.getenv(ENV_STROOM_HOME);
//                if (dir != null) {
//                    LOGGER.info("Using STROOM_HOME environment variable: {}", dir);
//                } else {
//                    dir = pathConfig.getHome();
//                    if (dir != null) {
//                        LOGGER.info("Using home path configuration property: {}", dir);
//                    }
//                }
//            }

            String dir = pathConfig.getHome();
            if (dir != null && !dir.isEmpty()) {
                LOGGER.info("Using home path configuration property: {}", dir);
                dir = FileUtil.replaceHome(dir);
                path = Paths.get(dir);
            }

            if (path == null) {
                path = getApplicationJarDir()
                        .orElse(null);
                LOGGER.info("Using application JAR directory for stroom home: {}", path);
            }

            if (path == null) {
                path = getDefaultStroomHomeDir()
                        .orElse(null);
                LOGGER.info("Using default directory for stroom home: {}", path);
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
