package stroom.processor.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class ProcessorDbConnProvider extends HikariDataSource {
    ProcessorDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
