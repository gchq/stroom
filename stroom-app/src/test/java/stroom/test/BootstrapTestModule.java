package stroom.test;

import stroom.app.guice.DbConnectionsModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.config.global.impl.GlobalConfigBootstrapModule;
import stroom.config.global.impl.db.GlobalConfigDaoModule;
import stroom.test.common.util.db.DbTestModule;
import stroom.util.io.DirProvidersModule;
import stroom.util.io.FileUtil;

import com.google.inject.AbstractModule;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BootstrapTestModule extends AbstractModule {

    private final Config config = new Config();
    private final ConfigHolder configHolder = new ConfigHolderImpl();

    public BootstrapTestModule() {
        config.setAppConfig(configHolder.getBootStrapConfig());
    }

    @Override
    protected void configure() {
        super.configure();

        bind(Config.class).toInstance(config);

        install(new AppConfigTestModule(configHolder));

        install(new DbTestModule());
        install(new DbConnectionsModule());

        // Any DAO/Service modules that we must have
        install(new GlobalConfigBootstrapModule());
        install(new GlobalConfigDaoModule());
        install(new DirProvidersModule());
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
        public AppConfig getBootStrapConfig() {
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
