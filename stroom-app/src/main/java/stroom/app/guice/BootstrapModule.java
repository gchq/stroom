package stroom.app.guice;

import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.Config;
import stroom.config.app.ConfigHolder;
import stroom.config.global.impl.GlobalConfigBootstrapModule;
import stroom.config.global.impl.db.GlobalConfigDaoModule;
import stroom.db.util.DbModule;
import stroom.util.io.DirProvidersModule;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;

import java.nio.file.Path;

public class BootstrapModule extends AbstractModule {

    private final Config configuration;
    private final Environment environment;
    private final ConfigHolder configHolder;

    public BootstrapModule(final Config configuration,
                           final Environment environment,
                           final Path configFile) {
        this.configuration = configuration;
        this.environment = environment;

        this.configHolder = new ConfigHolder() {
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

    public BootstrapModule(final Config configuration,
                           final Path configFile) {
        this(configuration, null, configFile);
    }

    @Override
    protected void configure() {
        super.configure();

        // The binds in here need to be the absolute bare minimum to get the DB
        // datasources connected and read all the DB based config props.

        bind(Config.class).toInstance(configuration);

        install(new AppConfigModule(configHolder));

        // These are needed so the Hikari pools can register metrics/health checks
        bindMetricsAndHealthChecksRegistries();

        install(new DbModule());

        install(new DbConnectionsModule());

        // Any DAO/Service modules that we must have
        install(new GlobalConfigBootstrapModule());
        install(new GlobalConfigDaoModule());
        install(new DirProvidersModule());
    }

    private void bindMetricsAndHealthChecksRegistries() {
        final HealthCheckRegistry healthCheckRegistry;
        final MetricRegistry metricRegistry;
        if (environment != null) {
            // Make the various DW objects available, bind them individually so
            // modules don't need to pull in all of DW just for metrics.
            bind(Environment.class).toInstance(environment);
            metricRegistry = environment.metrics();
            healthCheckRegistry = environment.healthChecks();
        } else {
            // Allows us to load up the app in the absence of a the DW jersey environment
            // e.g. for migrations
            // Just use brand new registries so code works. We don't care what gets written to
            // those registries.
            metricRegistry = new MetricRegistry();
            healthCheckRegistry = new HealthCheckRegistry();
        }
        bind(MetricRegistry.class).toInstance(metricRegistry);
        bind(HealthCheckRegistry.class).toInstance(healthCheckRegistry);
    }
}
