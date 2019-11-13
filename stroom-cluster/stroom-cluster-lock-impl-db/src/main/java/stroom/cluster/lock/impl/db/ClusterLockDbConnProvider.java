package stroom.cluster.lock.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ClusterLockDbConnProvider extends HikariDataSource {
    ClusterLockDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
