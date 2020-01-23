package stroom.app.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.setup.Environment;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.cluster.impl.ClusterModule;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.config.app.AppConfigModule.ConfigHolder;
import stroom.config.app.Config;
import stroom.core.dispatch.DispatchModule;
import stroom.db.util.DbModule;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.lifecycle.impl.LifecycleServiceModule;
import stroom.meta.statistics.impl.MetaStatisticsModule;
import stroom.resource.impl.SessionResourceModule;
import stroom.security.impl.SecurityContextModule;
import stroom.util.guice.HealthCheckBinder;
import stroom.util.shared.BuildInfo;

import javax.inject.Provider;
import javax.ws.rs.client.Client;
import java.nio.file.Path;
import java.util.Optional;

public class AppModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppModule.class);

    // This name is used by dropwizard metrics
    private static final String PROXY_JERSEY_CLIENT_NAME = "stroom_jersey_client";
    private static final String PROXY_JERSEY_CLIENT_USER_AGENT_PREFIX = "stroom/";

    private final Config configuration;
    private final Environment environment;
    private final ConfigHolder configHolder;

    public AppModule(final Config configuration,
                     final Environment environment,
                     final Path configFile) {
        this.configuration = configuration;
        this.environment = environment;
        configHolder = new ConfigHolderImpl(configuration.getAppConfig(), configFile);
    }

    /**
     * Alternative constructor for when we are running the app in the absence of
     * the DW Environment and jetty server, i.e. for DB migrations.
     */
    public AppModule(final Config configuration,
                     final Path configFile) {
        this(configuration, null, configFile);
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);

        // Allows us to load up the app in the absence of a the DW jersey environment
        // e.g. for migrations
        if (environment != null) {
            bind(Environment.class).toInstance(environment);
        }

        install(new AppConfigModule(configHolder));
        install(new DbModule());
        install(new CoreModule());
        install(new LifecycleServiceModule());
        install(new LifecycleModule());
        install(new JobsModule());
        install(new ClusterModule());
        install(new SecurityContextModule());
        install(new MetaStatisticsModule());
        install(new stroom.statistics.impl.sql.search.SQLStatisticSearchModule());
        install(new DispatchModule());
        install(new SessionResourceModule());

        HealthCheckBinder.create(binder())
                .bind(LogLevelInspector.class);
    }

    private static class ConfigHolderImpl implements AppConfigModule.ConfigHolder {
        private final AppConfig appConfig;
        private final Path path;

        ConfigHolderImpl(final AppConfig appConfig, final Path path) {
            this.appConfig = appConfig;
            this.path = path;
        }

        @Override
        public AppConfig getAppConfig() {
            return appConfig;
        }

        @Override
        public Path getConfigFile() {
            return path;
        }
    }

    @Provides
    @Singleton
    Client provideJerseyClient(final JerseyClientConfiguration jerseyClientConfiguration,
                               final Environment environment,
                               final Provider<BuildInfo> buildInfoProvider) {

        // If the userAgent has not been explicitly set in the config then set it based
        // on the build version
        if (!jerseyClientConfiguration.getUserAgent().isPresent()) {
            final String userAgent = PROXY_JERSEY_CLIENT_USER_AGENT_PREFIX + buildInfoProvider.get().getBuildVersion();
            LOGGER.info("Setting jersey client user agent string to [{}]", userAgent);
            jerseyClientConfiguration.setUserAgent(Optional.of(userAgent));
        }

        LOGGER.info("Creating jersey client {}", PROXY_JERSEY_CLIENT_NAME);
        return new JerseyClientBuilder(environment)
                .using(jerseyClientConfiguration)
                .build(PROXY_JERSEY_CLIENT_NAME)
                .register(LoggingFeature.class);
    }
}
