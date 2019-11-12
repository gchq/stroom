package stroom.cluster.lock.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ClusterLockDbConnectionProvider extends HikariDataSource {
    ClusterLockDbConnectionProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
