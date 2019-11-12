package stroom.annotation.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class AnnotationDbConnProvider extends HikariDataSource {
    AnnotationDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
