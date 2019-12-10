package stroom.node.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class NodeDbConnProvider extends DataSourceProxy {
    NodeDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
