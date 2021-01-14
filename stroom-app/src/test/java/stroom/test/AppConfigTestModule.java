package stroom.test;

import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.util.io.FileUtil;

import com.google.inject.Provides;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AppConfigTestModule extends AppConfigModule {
    public AppConfigTestModule() {
        super(new ConfigHolderImpl());
    }

    @Provides
    public Config getConfig() {
        return ((ConfigHolderImpl) getConfigHolder()).getConfig();
    }

    private static class ConfigHolderImpl implements ConfigHolder {
        private final Config config;
        private final AppConfig appConfig;
        private final Path path;

        ConfigHolderImpl() {
            try {
                final Path dir = Files.createTempDirectory("stroom");
                this.path = dir.resolve("test.yml");

                this.appConfig = new AppConfig();
                appConfig.getPathConfig().setTemp(FileUtil.getCanonicalPath(dir));
                appConfig.getPathConfig().setHome(FileUtil.getCanonicalPath(dir));

                this.config = new Config();
                this.config.setAppConfig(appConfig);
            } catch (final IOException e) {
                throw new UncheckedIOException(e.getMessage(), e);
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
