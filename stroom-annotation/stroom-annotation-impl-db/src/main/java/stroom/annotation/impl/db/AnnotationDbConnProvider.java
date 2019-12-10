package stroom.annotation.impl.db;

import stroom.db.util.DataSourceProxy;

import javax.sql.DataSource;

class AnnotationDbConnProvider extends DataSourceProxy {
    AnnotationDbConnProvider(final DataSource dataSource) {
        super(dataSource);
    }
}
