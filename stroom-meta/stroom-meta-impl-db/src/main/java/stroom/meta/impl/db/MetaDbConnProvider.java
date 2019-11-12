package stroom.meta.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class MetaDbConnProvider extends HikariDataSource {
    MetaDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
