package stroom.test;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.cluster.lock.mock.MockClusterLockService;
import stroom.config.global.impl.ConfigProvidersModule;
import stroom.config.global.impl.GlobalConfigBootstrapModule;
import stroom.config.global.impl.db.GlobalConfigDaoModule;
import stroom.security.mock.MockSecurityContextModule;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.SimpleTaskContextFactory;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.util.db.ForceLegacyMigration;
import stroom.util.io.DirProvidersModule;

import com.google.inject.AbstractModule;

/**
 * A module for running DB integration tests with just SQL stats tables but none
 * of the statistic entity stuff. Speeds up testing of stat flushing/aggregation
 * as it only needs to build the stats db on each run.
 */
public class StatisticsCoreTestModule extends AbstractModule {

    @Override
    protected void configure() {
        install(new AppConfigTestModule());
        install(new ConfigProvidersModule());
        install(new DirProvidersModule());
        install(new GlobalConfigBootstrapModule());
        install(new GlobalConfigDaoModule());
        install(new MockSecurityContextModule());
        install(new StatisticsTestDbConnectionsModule());

        bind(ClusterLockService.class).to(MockClusterLockService.class);
        bind(CommonTestControl.class).to(MockCommonTestControl.class);
        bind(ForceLegacyMigration.class).toInstance(new ForceLegacyMigration() {
        });
        bind(TaskContext.class).to(SimpleTaskContext.class);
        bind(TaskContextFactory.class).to(SimpleTaskContextFactory.class);

    }
}
