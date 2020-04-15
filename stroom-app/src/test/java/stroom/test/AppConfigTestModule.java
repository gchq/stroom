package stroom.test;

import com.google.inject.Provides;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.app.YamlUtil;
import stroom.util.io.FileUtil;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class AppConfigTestModule extends AppConfigModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigTestModule.class);

    public AppConfigTestModule() {
        super(new ConfigHolderImpl());
    }

    @Provides
    public Config getConfig() {
        return ((ConfigHolderImpl) getConfigHolder()).getConfig();
    }

    private static class ConfigHolderImpl implements AppConfigModule.ConfigHolder {
        private final Config config;
        private final AppConfig appConfig;
        private final Path path;

        ConfigHolderImpl() {
            final String codeSourceLocation = AppConfigTestModule.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath();

            Path path = Paths.get(codeSourceLocation);
            while (path != null && !path.getFileName().toString().equals("stroom-app")) {
                path = path.getParent();
            }

            // resolve local.yml in the root of the repo
            if (path != null) {
                path = path.getParent();
                path = path.resolve("local.yml");
            }

            if (path == null) {
                throw new RuntimeException("Unable to find local.yml, try running local.yml.sh in the root of the repo " +
                        "to create one.");
            }

            LOGGER.info("Using config from: " + FileUtil.getCanonicalPath(path));
            this.path = path;

            try {
                this.appConfig = YamlUtil.readAppConfig(path);

                this.config = new Config();
                this.config.setAppConfig(appConfig);
            } catch (final IOException e) {
                throw new UncheckedIOException("Error opening local.yml, try running local.yml.sh in the root of " +
                        "the repo to create one.", e);
            }
        }

        @Override
        public AppConfig getAppConfig() {
            return appConfig;
        }

        @Override
        public Path getConfigFile() {
            return path;
        }

        public Config getConfig() {
            return config;
        }
    }
}
