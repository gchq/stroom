package stroom.test;

import stroom.app.guice.BootStrapModule;
import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockUserSecurityContextModule;

import com.google.inject.AbstractModule;
import io.dropwizard.core.setup.Environment;

import java.nio.file.Path;

public class SetupSampleDataModule extends AbstractModule {

    private final Config configuration;
    private final ConfigHolder configHolder;

    public SetupSampleDataModule(final Config configuration,
                                 final Path configFile) {
        this.configuration = configuration;

        configHolder = new ConfigHolder() {
            @Override
            public AppConfig getBootStrapConfig() {
                return configuration.getYamlAppConfig();
            }

            @Override
            public Path getConfigFile() {
                return configFile;
            }
        };
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        install(new BootStrapModule(configuration, new Environment("Test Environment"), configHolder.getConfigFile()));
        install(new UriFactoryModule());
        install(new CoreModule());
        install(new ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new VolumeTestConfigModule());
        install(new MockUserSecurityContextModule());
        install(new MockMetaStatisticsModule());
        install(new stroom.test.DatabaseTestControlModule());
        install(new JerseyModule());
    }
}
