package stroom.storedquery.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class StoredQueryDbConnProvider extends HikariDataSource {
    StoredQueryDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
