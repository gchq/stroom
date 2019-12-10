package stroom.data.store.impl.fs.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class FsDataStoreDbConnProvider extends DataSourceProxy {
    FsDataStoreDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
