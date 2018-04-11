package stroom.test;

import com.google.inject.AbstractModule;
import stroom.guice.CoreModule;

public class CoreTestModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new CoreModule());

        install(new stroom.resource.ResourceModule());
        install(new stroom.cluster.MockClusterModule());
        install(new stroom.node.NodeTestConfigModule());
        install(new stroom.security.MockSecurityContextModule());
        install(new stroom.statistics.internal.MockInternalStatisticsModule());
        install(new stroom.streamtask.statistic.MockMetaDataStatisticModule());
        install(new stroom.test.DatabaseTestControlModule());
    }
}
