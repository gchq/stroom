package stroom.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class CoreDbConnectionProvider extends HikariDataSource {
    CoreDbConnectionProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
