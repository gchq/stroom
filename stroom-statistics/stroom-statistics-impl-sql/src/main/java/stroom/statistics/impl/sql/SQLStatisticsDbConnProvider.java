package stroom.statistics.impl.sql;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

public class SQLStatisticsDbConnProvider extends DataSourceProxy {
    SQLStatisticsDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
