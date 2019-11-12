package stroom.annotation.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public class AnnotationDbConnectionProvider extends HikariDataSource {
    AnnotationDbConnectionProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
