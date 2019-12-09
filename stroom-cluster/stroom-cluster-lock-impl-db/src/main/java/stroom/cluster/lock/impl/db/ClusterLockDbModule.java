package stroom.cluster.lock.impl.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import java.util.function.Function;

public class ClusterLockDbModule extends AbstractFlyWayDbModule<ClusterLockConfig, ClusterLockDbConnProvider> {
    private static final String MODULE = "stroom-cluster-lock";
    private static final String FLYWAY_LOCATIONS = "stroom/cluster/lock/impl/db/migration";
    private static final String FLYWAY_TABLE = "cluster_lock_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(ClusterLockService.class).to(ClusterLockServiceImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(DbClusterLock.class);

        TaskHandlerBinder.create(binder())
                .bind(ClusterLockClusterTask.class, ClusterLockClusterHandler.class)
                .bind(ClusterLockTask.class, ClusterLockHandler.class);
    }

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    protected Function<HikariConfig, ClusterLockDbConnProvider> getConnectionProviderConstructor() {
        return ClusterLockDbConnProvider::new;
    }

    @Override
    protected Class<ClusterLockDbConnProvider> getConnectionProviderType() {
        return ClusterLockDbConnProvider.class;
    }
}
