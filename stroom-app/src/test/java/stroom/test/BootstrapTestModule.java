package stroom.test;

import stroom.app.guice.BootStrapModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.test.common.util.db.DbTestModule;
import stroom.test.common.util.db.DbTestUtil;
import stroom.util.io.FileUtil;

import com.codahale.metrics.MetricRegistry;
import com.google.inject.AbstractModule;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.validation.Validators;
import io.dropwizard.setup.Environment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class BootstrapTestModule extends AbstractModule {

    private final BootStrapModule bootStrapModule;

//    private BootstrapTestModule(final Config config, final ConfigHolder configHolder) {
//        super(config, null, configHolder, DbTestModule::new, AppConfigTestModule::new);
//
//
//        final ConfigHolder configHolder = new ConfigHolderImpl();
//        final Config config = new Config(configHolder.getBootStrapConfig());
//
//        // Delegate to the normal BootStrapModule but use different Db and AppConfig modules
//        bootStrapModule = new BootStrapModule(
//                config,
//                null,
//                configHolder,
//                DbTestModule::new,
//                AppConfigTestModule::new);

//        super(new Config(new ConfigHolderImpl()), null, Path.of("Dummy"),
//        DbTestModule::new, AppConfigTestModule::new);
//        config.setYamlAppConfig(configHolder.getBootStrapConfig());
//    }

    public BootstrapTestModule() {
        final ConfigHolder configHolder = new ConfigHolderImpl();
        final Config config = new Config(configHolder.getBootStrapConfig());
        // TODO: 15/06/2023 This will get replaced by new Environment("TestEnvironment") in the merge up
        //  as that ctor doesn't exist in 7.0
        final Environment environment = new Environment(
                "Test Environment",
                Jackson.newObjectMapper(),
                Validators.newValidatorFactory().getValidator(),
                new MetricRegistry(),
                ClassLoader.getSystemClassLoader());

        // Delegate to the normal BootStrapModule but use different Db and AppConfig modules
        bootStrapModule = new BootStrapModule(
                config,
                environment,
                configHolder,
                DbTestModule::new,
                AppConfigTestModule::new);
    }

//    public static BootstrapTestModule create() {
//        final ConfigHolder configHolder = new ConfigHolderImpl();
//        final Config config = new Config(configHolder.getBootStrapConfig());
//
//        return new BootstrapTestModule(config, configHolder);
//    }

    @Override
    protected void configure() {
        super.configure();

        install(bootStrapModule);
//        bootStrapModule.configure();
//
//        bind(Config.class).toInstance(config);
//
//        install(new AppConfigTestModule(configHolder));
//
//        install(new DbTestModule());
//        install(new DbConnectionsModule());
//
//        // Any DAO/Service modules that we must have
//        install(new GlobalConfigBootstrapModule());
//        install(new GlobalConfigDaoModule());
//        install(new DirProvidersModule());
    }

    private static class ConfigHolderImpl implements ConfigHolder {

        private final Config config;
        private final AppConfig appConfig;
        private final Path path;

        ConfigHolderImpl() {
            try {
                final String gradleWorker = DbTestUtil.getGradleWorker();
                final String prefix = "stroom_" + gradleWorker + "_";
                final Path dir = Files.createTempDirectory(prefix);
                this.path = dir.resolve("test.yml");

                this.appConfig = new AppConfig();
                appConfig.getPathConfig().setTemp(FileUtil.getCanonicalPath(dir));
                appConfig.getPathConfig().setHome(FileUtil.getCanonicalPath(dir));

                this.config = new Config();
                this.config.setYamlAppConfig(appConfig);
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
