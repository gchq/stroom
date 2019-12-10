package stroom.job.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class JobDbConnProvider extends DataSourceProxy {
    JobDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
