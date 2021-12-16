package stroom.util.io;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TempDirProviderImpl implements TempDirProvider {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TempDirProviderImpl.class);

    //    private static final String PROP_STROOM_TEMP = "stroom.temp";
//    private static final String ENV_STROOM_TEMP = "STROOM_TEMP";
    private static final String DEFAULT_TEMP_SUB_DIR = "temp";

    private final PathConfig pathConfig;
    private final HomeDirProvider homeDirProvider;

    private Path tempDir;

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

//            String dir = System.getProperty(PROP_STROOM_TEMP);
//            if (dir != null) {
//                LOGGER.info("Using stroom.temp system property: {}", dir);
//            } else {
//                dir = System.getenv(ENV_STROOM_TEMP);
//                if (dir != null) {
//                    LOGGER.info("Using STROOM_TEMP environment variable: {}", dir);
//                } else {
//                    dir = pathConfig.getTemp();
//                    if (dir != null) {
//                        LOGGER.info("Using temp path configuration property: {}", dir);
//                    } else {
//                        dir = System.getProperty(PROP_JAVA_TEMP);
//                        if (dir != null) {
//                            LOGGER.info("Using default Java temp dir: {}", dir);
//                        }
//                    }
//                }
//            }

            String dir = pathConfig.getTemp();
            if (dir != null && !dir.isEmpty()) {
                LOGGER.info("Using temp path configuration property: {}", dir);
                dir = FileUtil.replaceHome(dir);
                path = Paths.get(dir);
            }

            if (path == null) {
                path = homeDirProvider.get()
                        .resolve(DEFAULT_TEMP_SUB_DIR);
            }

            // If this isn't an absolute path then make it so relative to the home path.
            if (!path.startsWith("/") && !path.startsWith("\\")) {
                path = homeDirProvider.get()
                        .resolve(path);
            }

            path = path.toAbsolutePath();

            tempDir = path;
        }
        return tempDir;
    }

    public void setTempDir(final Path tempDir) {
        this.tempDir = tempDir;
    }
}
