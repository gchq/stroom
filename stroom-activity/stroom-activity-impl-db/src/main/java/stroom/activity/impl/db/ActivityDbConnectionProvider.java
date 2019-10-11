package stroom.activity.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ActivityDbConnectionProvider extends HikariDataSource {
    ActivityDbConnectionProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
