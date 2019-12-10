package stroom.meta.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

public class MetaDbConnProvider extends DataSourceProxy {
    MetaDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
