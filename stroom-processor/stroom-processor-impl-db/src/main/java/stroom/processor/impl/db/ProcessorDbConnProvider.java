package stroom.processor.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class ProcessorDbConnProvider extends DataSourceProxy {
    ProcessorDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
