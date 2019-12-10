package stroom.security.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class SecurityDbConnProvider extends DataSourceProxy {
    SecurityDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
