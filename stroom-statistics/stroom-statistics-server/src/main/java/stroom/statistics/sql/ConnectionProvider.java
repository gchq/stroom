package stroom.statistics.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class ConnectionProvider extends HikariDataSource {
    ConnectionProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
