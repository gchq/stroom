package stroom.activity.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class ActivityDbConnProvider extends DataSourceProxy {
    ActivityDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
