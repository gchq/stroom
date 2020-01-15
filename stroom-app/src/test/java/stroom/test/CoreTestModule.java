package stroom.test;

import com.google.inject.AbstractModule;
import stroom.app.guice.CoreModule;
import stroom.index.VolumeTestConfigModule;
import stroom.meta.statistics.impl.MockMetaStatisticsModule;
import stroom.resource.impl.ResourceModule;
import stroom.security.mock.MockSecurityContextModule;

public class CoreTestModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new AppConfigTestModule());
        install(new CoreModule());
        install(new ResourceModule());
        install(new stroom.cluster.impl.MockClusterModule());
        install(new VolumeTestConfigModule());
        install(new MockSecurityContextModule());
        install(new MockMetaStatisticsModule());
        install(new stroom.test.DatabaseTestControlModule());
    }
}
