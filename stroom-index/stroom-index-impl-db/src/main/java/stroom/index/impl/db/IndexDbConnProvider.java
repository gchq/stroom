package stroom.index.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class IndexDbConnProvider extends HikariDataSource {
    IndexDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
