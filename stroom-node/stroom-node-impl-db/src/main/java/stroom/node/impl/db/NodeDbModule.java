package stroom.node.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.node.impl.NodeConfig.NodeDbConfig;

import java.util.List;
import javax.sql.DataSource;

public class NodeDbModule extends AbstractFlyWayDbModule<NodeDbConfig, NodeDbConnProvider> {

    private static final String MODULE = "stroom-node";
    private static final String FLYWAY_LOCATIONS = "stroom/node/impl/db/migration";
    private static final String FLYWAY_TABLE = "node_schema_history";

    @Override
    protected String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    protected String getModuleName() {
        return MODULE;
    }

    @Override
    protected List<String> getFlyWayLocations() {
        return List.of(FLYWAY_LOCATIONS);
    }

    @Override
    protected Class<NodeDbConnProvider> getConnectionProviderType() {
        return NodeDbConnProvider.class;
    }

    @Override
    protected NodeDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements NodeDbConnProvider {

        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource, MODULE);
        }
    }
}
