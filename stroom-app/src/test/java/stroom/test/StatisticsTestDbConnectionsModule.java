package stroom.test;

import com.google.inject.AbstractModule;

public class StatisticsTestDbConnectionsModule extends AbstractModule {
    @Override
    protected void configure() {
        super.configure();

        // All the modules for the DB connections
        install(new stroom.config.global.impl.db.GlobalConfigDbModule());
        install(new stroom.statistics.impl.sql.SQLStatisticsDbModule());
    }
}
