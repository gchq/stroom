package stroom.explorer.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ExplorerDbConnProvider extends HikariDataSource {
    ExplorerDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
