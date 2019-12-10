package stroom.explorer.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class ExplorerDbConnProvider extends DataSourceProxy {
    ExplorerDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
