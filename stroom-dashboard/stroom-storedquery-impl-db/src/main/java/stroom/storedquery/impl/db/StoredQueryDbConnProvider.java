package stroom.storedquery.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class StoredQueryDbConnProvider extends DataSourceProxy {
    StoredQueryDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
