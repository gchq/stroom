package stroom.app.guice;

import stroom.app.uri.UriFactoryModule;
import stroom.cluster.impl.ClusterModule;
import stroom.dropwizard.common.LogLevelInspector;
import stroom.index.impl.IndexShardWriterExecutorProvider;
import stroom.index.impl.IndexShardWriterExecutorProviderImpl;
import stroom.lifecycle.impl.LifecycleServiceModule;
import stroom.meta.statistics.impl.MetaStatisticsModule;
import stroom.resource.impl.SessionResourceModule;
import stroom.security.impl.SecurityContextModule;
import stroom.statistics.impl.sql.search.SQLStatisticSearchModule;
import stroom.util.guice.HasSystemInfoBinder;

import com.google.inject.AbstractModule;

public class AppModule extends AbstractModule {

//    private final Config configuration;
//    private final Environment environment;
//    private final ConfigHolder configHolder;

//    public AppModule(final Config configuration,
//                     final Environment environment,
//                     final Path configFile) {
////        this.configuration = configuration;
////        this.environment = environment;
////
////        configHolder = new ConfigHolder() {
////            @Override
////            public AppConfig getBootStrapConfig() {
////                return configuration.getAppConfig();
////            }
////
////            @Override
////            public Path getConfigFile() {
////                return configFile;
////            }
////        };
//    }

    /**
     * Alternative constructor for when we are running the app in the absence of
     * the DW Environment and jetty server, i.e. for DB migrations.
     */
//    public AppModule(final Config configuration,
//                     final Path configFile) {
//        this(configuration, null, configFile);
//    }
    @Override
    protected void configure() {

//        final HealthCheckRegistry healthCheckRegistry;
//        final MetricRegistry metricRegistry;
//        if (environment != null) {
//            // Make the various DW objects available, bind them individually so
//            // modules don't need to pull in all of DW just for metrics.
//            bind(Environment.class).toInstance(environment);
//            metricRegistry = environment.metrics();
//            healthCheckRegistry = environment.healthChecks();
//        } else {
//            // Allows us to load up the app in the absence of a the DW jersey environment
//            // e.g. for migrations
//            // Just use brand new registries so code works. We don't care what gets written to
//            // those registries.
//            metricRegistry = new MetricRegistry();
//            healthCheckRegistry = new HealthCheckRegistry();
//        }
//        bind(MetricRegistry.class).toInstance(metricRegistry);
//        bind(HealthCheckRegistry.class).toInstance(healthCheckRegistry);

        install(new UriFactoryModule());
        install(new CoreModule());
        install(new LifecycleServiceModule());
        install(new JobsModule());
        install(new ClusterModule());
        install(new SecurityContextModule());
        install(new MetaStatisticsModule());
        install(new SQLStatisticSearchModule());
        install(new SessionResourceModule());
        install(new JerseyModule());
        bind(IndexShardWriterExecutorProvider.class).to(IndexShardWriterExecutorProviderImpl.class);

        HasSystemInfoBinder.create(binder())
                .bind(LogLevelInspector.class);
    }

}
