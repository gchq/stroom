package stroom.node.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.node.impl.NodeConfig;
import stroom.node.impl.NodeDao;

import javax.sql.DataSource;
import java.util.function.Function;

public class NodeDbModule extends AbstractFlyWayDbModule<NodeConfig, NodeDbConnProvider> {
    private static final String MODULE = "stroom-node";
    private static final String FLYWAY_LOCATIONS = "stroom/node/impl/db/migration";
    private static final String FLYWAY_TABLE = "node_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(NodeDao.class).to(NodeDaoImpl.class);

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

    }

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    protected Function<DataSource, NodeDbConnProvider> getConnectionProviderConstructor() {
        return NodeDbConnProvider::new;
    }

    @Override
    protected Class<NodeDbConnProvider> getConnectionProviderType() {
        return NodeDbConnProvider.class;
    }
}
