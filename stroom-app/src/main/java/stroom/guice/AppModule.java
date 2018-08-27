package stroom.guice;

import com.google.inject.AbstractModule;
import stroom.cluster.impl.ClusterModule;
import stroom.config.app.AppConfig;
import stroom.config.app.AppConfigModule;
import stroom.proxy.guice.ProxyConfig;

public class AppModule extends AbstractModule {
    private final AppConfig appConfig;

    public AppModule(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    protected void configure() {
        install(new AppConfigModule(appConfig));

        install(new CoreModule());

        install(new ClusterModule());
//        install(new stroom.node.NodeTestConfigModule());
        install(new stroom.security.SecurityContextModule());
        install(new stroom.statistics.internal.InternalStatisticsModule());
        install(new stroom.streamtask.statistic.MetaDataStatisticModule());

        install(new stroom.statistics.sql.search.SQLStatisticSearchModule());
        install(new stroom.dispatch.DispatchModule());
        install(new stroom.resource.SessionResourceModule());
//        install(new stroom.test.DatabaseTestControlModule());
    }
}
