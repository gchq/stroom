package stroom.explorer.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ConnectionProvider extends HikariDataSource {
    ConnectionProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
