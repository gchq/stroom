package stroom.job.impl.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

class JobDbConnProvider extends HikariDataSource {
    JobDbConnProvider(final HikariConfig configuration) {
        super(configuration);
    }
}
