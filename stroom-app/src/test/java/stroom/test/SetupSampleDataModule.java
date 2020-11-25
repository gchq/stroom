package stroom.test;

import stroom.app.guice.CoreModule;
import stroom.app.guice.JerseyModule;
import stroom.app.uri.UriFactoryModule;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.db.util.DbModule;
import stroom.index.VolumeTestConfigModule;
import stroom.index.mock.MockIndexShardWriterExecutorModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;

import com.google.inject.AbstractModule;

import java.nio.file.Path;

public class SetupSampleDataModule extends AbstractModule {
    private final Config configuration;
    private final ConfigHolder configHolder;

    public SetupSampleDataModule(final Config configuration,
                                 final Path configFile) {
        this.configuration = configuration;

        configHolder = new ConfigHolder() {
            @Override
            public AppConfig getAppConfig() {
                return configuration.getAppConfig();
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
        install(new AppConfigModule(configHolder));
        install(new DbModule());
        install(new UriFactoryModule());
        install(new CoreModule());
        install(new ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new VolumeTestConfigModule());
        install(new MockSecurityContextModule());
        install(new MockMetaStatisticsModule());
        install(new stroom.test.DatabaseTestControlModule());
        install(new JerseyModule());
        install(new MockIndexShardWriterExecutorModule());
    }
}
