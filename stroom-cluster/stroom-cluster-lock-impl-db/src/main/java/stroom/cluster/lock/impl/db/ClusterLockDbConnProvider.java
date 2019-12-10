package stroom.cluster.lock.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class ClusterLockDbConnProvider extends DataSourceProxy {
    ClusterLockDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
