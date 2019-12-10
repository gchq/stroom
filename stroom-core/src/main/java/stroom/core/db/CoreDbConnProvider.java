package stroom.core.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

public class CoreDbConnProvider extends DataSourceProxy {
    CoreDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
