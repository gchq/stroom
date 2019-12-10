package stroom.index.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class IndexDbConnProvider extends DataSourceProxy {
    IndexDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
