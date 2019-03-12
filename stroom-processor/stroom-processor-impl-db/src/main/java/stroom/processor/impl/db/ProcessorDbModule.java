package stroom.processor.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorFilterTaskDao;
import stroom.processor.impl.ProcessorFilterTaskDeleteExecutor;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.util.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class ProcessorDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorDbModule.class);
    private static final String FLYWAY_LOCATIONS = "stroom/processor/impl/db";
    private static final String FLYWAY_TABLE = "processor_schema_history";

    @Override
    protected void configure() {
        bind(ProcessorDao.class).to(ProcessorDaoImpl.class);
        bind(ProcessorFilterDao.class).to(ProcessorFilterDaoImpl.class);
        bind(ProcessorFilterTaskDao.class).to(ProcessorFilterTaskDaoImpl.class);
        bind(ProcessorFilterTaskDeleteExecutor.class).to(ProcessorFilterTaskDeleteExecutorImpl.class);
        bind(ProcessorFilterTrackerDao.class).to(ProcessorFilterTrackerDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorNodeCache.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<ProcessorConfig> configProvider) {
        final ConnectionConfig connectionConfig = configProvider.get().getConnectionConfig();
        final ConnectionPoolConfig connectionPoolConfig = configProvider.get().getConnectionPoolConfig();
        final HikariConfig config = HikariUtil.createConfig(connectionConfig, connectionPoolConfig);
        final ConnectionProvider connectionProvider = new ConnectionProvider(config);
        flyway(connectionProvider);
        return connectionProvider;
    }

    private Flyway flyway(final DataSource dataSource) {
        final Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(FLYWAY_LOCATIONS)
                .table(FLYWAY_TABLE)
                .baselineOnMigrate(true)
                .load();
        LOGGER.info("Applying Flyway migrations to processor in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating processor database", e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for processor in {}", FLYWAY_TABLE);
        return flyway;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
