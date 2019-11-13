package stroom.core.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class CoreDbConnProvider extends HikariDataSource {
    CoreDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
