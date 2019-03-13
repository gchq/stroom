package stroom.node.impl.db;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import com.zaxxer.hikari.HikariConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.db.util.HikariUtil;
import stroom.entity.shared.EntityEvent;
import stroom.entity.shared.EntityEvent.Handler;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.node.impl.InternalNodeService;
import stroom.node.shared.UpdateNodeAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.inject.Provider;
import javax.inject.Singleton;
import javax.sql.DataSource;

public class NodeDbModule extends AbstractModule {
    private static final Logger LOGGER = LoggerFactory.getLogger(NodeDbModule.class);
    private static final String FLYWAY_LOCATIONS = "stroom/node/impl/db";
    private static final String FLYWAY_TABLE = "node_schema_history";

    @Override
    protected void configure() {
        bind(NodeService.class).to(NodeServiceImpl.class);
        bind(InternalNodeService.class).to(NodeServiceImpl.class);
        bind(NodeInfo.class).to(NodeInfoImpl.class);

//        bind(NodeDbService.class).to(NodeDbServiceImpl.class);
//        bind(CurrentNodeDb.class).to(CurrentNodeDbImpl.class);
//
//        TaskHandlerBinder.create(binder())
//                .bind(CreateNodeDbAction.class, CreateNodeDbHandler.class)
//                .bind(UpdateNodeDbAction.class, UpdateNodeDbHandler.class)
//                .bind(DeleteNodeDbAction.class, DeleteNodeDbHandler.class)
//                .bind(FetchNodeDbAction.class, FetchNodeDbHandler.class)
//                .bind(FindNodeDbAction.class, FindNodeDbHandler.class)
//                .bind(SetCurrentNodeDbAction.class, SetCurrentNodeDbHandler.class);

        TaskHandlerBinder.create(binder())
                .bind(UpdateNodeAction.class, UpdateNodeHandler.class);


        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(NodeInfoImpl.class);

        final Multibinder<Handler> entityEventHandlerBinder = Multibinder.newSetBinder(binder(), EntityEvent.Handler.class);
        entityEventHandlerBinder.addBinding().to(NodeInfoImpl.class);
    }

    @Provides
    @Singleton
    ConnectionProvider getConnectionProvider(final Provider<NodeDbConfig> configProvider) {
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
        LOGGER.info("Applying Flyway migrations to activity in {} from {}", FLYWAY_TABLE, FLYWAY_LOCATIONS);
        try {
            flyway.migrate();
        } catch (FlywayException e) {
            LOGGER.error("Error migrating activity database", e);
            throw e;
        }
        LOGGER.info("Completed Flyway migrations for activity in {}", FLYWAY_TABLE);
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
