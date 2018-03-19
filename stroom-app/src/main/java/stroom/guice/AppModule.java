package stroom.guice;

import com.google.inject.AbstractModule;
import stroom.streamtask.statistic.MetaDataStatisticModule;

public class AppModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CoreModule());

        install(new stroom.cluster.ClusterModule());
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
