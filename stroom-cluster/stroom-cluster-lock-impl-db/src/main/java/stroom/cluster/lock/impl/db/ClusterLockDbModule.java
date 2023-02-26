package stroom.cluster.lock.impl.db;

import stroom.cluster.lock.impl.db.ClusterLockConfig.ClusterLockDbConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;

import java.util.List;
import javax.sql.DataSource;

public class ClusterLockDbModule extends AbstractFlyWayDbModule<ClusterLockDbConfig, ClusterLockDbConnProvider> {

    private static final String MODULE = "stroom-cluster-lock";
    private static final String FLYWAY_LOCATIONS = "stroom/cluster/lock/impl/db/migration";
    private static final String FLYWAY_TABLE = "cluster_lock_schema_history";

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected List<String> getFlyWayLocations() {
        return List.of(FLYWAY_LOCATIONS);
    }

    @Override
    protected boolean createUniquePool() {
        // We need the cluster lock connection pool to be unique as cluster lock connections are held open while other
        // DB operations are performed and if the pool were not unique then we would run the risk of deadlocks.
        return true;
    }

    @Override
    protected Class<ClusterLockDbConnProvider> getConnectionProviderType() {
        return ClusterLockDbConnProvider.class;
    }

    @Override
    protected ClusterLockDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements ClusterLockDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }

}
