package stroom.config.global.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class GlobalConfigDbConnProvider extends DataSourceProxy {
    GlobalConfigDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
