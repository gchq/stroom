package stroom.guice;

import com.google.inject.AbstractModule;
import io.dropwizard.setup.Environment;
import stroom.cluster.impl.ClusterModule;
import stroom.config.app.AppConfigModule;
import stroom.processor.impl.db.statistic.MetaDataStatisticModule;
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
        install(new JobsModule());

        install(new ClusterModule());
//        install(new stroom.node.NodeTestConfigModule());
        install(new stroom.security.SecurityContextModule());
        install(new stroom.statistics.internal.InternalStatisticsModule());
        install(new MetaDataStatisticModule());

        install(new stroom.statistics.sql.search.SQLStatisticSearchModule());
        install(new stroom.dispatch.DispatchModule());
        install(new stroom.resource.SessionResourceModule());
//        install(new stroom.test.DatabaseTestControlModule());
    }
}
