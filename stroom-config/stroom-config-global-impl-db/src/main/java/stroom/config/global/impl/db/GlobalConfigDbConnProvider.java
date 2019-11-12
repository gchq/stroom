package stroom.config.global.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class GlobalConfigDbConnProvider extends HikariDataSource {
    GlobalConfigDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
