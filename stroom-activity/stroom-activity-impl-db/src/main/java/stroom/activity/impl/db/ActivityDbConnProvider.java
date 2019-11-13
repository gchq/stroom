package stroom.activity.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ActivityDbConnProvider extends HikariDataSource {
    ActivityDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
