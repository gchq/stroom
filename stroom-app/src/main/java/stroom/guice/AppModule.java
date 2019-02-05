package stroom.guice;

import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;
import stroom.cluster.impl.ClusterModule;
import stroom.config.app.AppConfigModule;
import stroom.lifecycle.impl.LifecycleServiceModule;
import stroom.resource.impl.SessionResourceModule;
import stroom.startup.Config;

public class AppModule extends AbstractModule {
    private final Config configuration;
    private final Environment environment;

    public AppModule(final Config configuration, final Environment environment) {
        this.configuration = configuration;
        this.environment = environment;
    }

    @Override
    protected void configure() {
        bind(Config.class).toInstance(configuration);
        bind(Environment.class).toInstance(environment);

        install(new AppConfigModule(configuration.getAppConfig()));

        install(new CoreModule());
        install(new LifecycleServiceModule());
        install(new LifecycleModule());
        install(new JobsModule());

        install(new ClusterModule());
//        install(new stroom.node.NodeTestConfigModule());
        install(new stroom.security.SecurityContextModule());
        install(new stroom.statistics.internal.InternalStatisticsModule());
        install(new stroom.processor.impl.db.statistic.MetaDataStatisticModule());

        install(new stroom.statistics.sql.search.SQLStatisticSearchModule());
        install(new stroom.dispatch.DispatchModule());
        install(new SessionResourceModule());
//        install(new stroom.test.DatabaseTestControlModule());
    }
}
