package stroom.security.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class SecurityDbConnProvider extends HikariDataSource {
    SecurityDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
