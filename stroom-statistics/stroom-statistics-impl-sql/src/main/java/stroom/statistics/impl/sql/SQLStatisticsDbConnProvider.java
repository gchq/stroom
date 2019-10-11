package stroom.statistics.impl.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class SQLStatisticsDbConnProvider extends HikariDataSource {
    SQLStatisticsDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
