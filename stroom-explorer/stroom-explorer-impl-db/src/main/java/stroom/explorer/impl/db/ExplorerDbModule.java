package stroom.explorer.impl.db;

import com.zaxxer.hikari.HikariConfig;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.explorer.impl.ExplorerTreeDao;

import java.util.function.Function;

public class ExplorerDbModule extends AbstractFlyWayDbModule<ExplorerConfig, ExplorerDbConnProvider> {
    private static final String MODULE = "stroom-explorer";
    private static final String FLYWAY_LOCATIONS = "stroom/explorer/impl/db/migration";
    private static final String FLYWAY_TABLE = "explorer_schema_history";

    @Override
    protected void configure() {
        super.configure();

        bind(ExplorerTreeDao.class).to(ExplorerTreeDaoImpl.class);
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
    protected Function<HikariConfig, ExplorerDbConnProvider> getConnectionProviderConstructor() {
        return ExplorerDbConnProvider::new;
    }

    @Override
    protected Class<ExplorerDbConnProvider> getConnectionProviderType() {
        return ExplorerDbConnProvider.class;
    }
}
