package stroom.explorer.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.explorer.impl.ExplorerTreeDao;

import java.util.function.Function;

public class ExplorerDbModule extends AbstractFlyWayDbModule<ExplorerConfig, ExplorerDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerDbModule.class);
    private static final String MODULE = "stroom-explorer";
    private static final String FLYWAY_LOCATIONS = "stroom/explorer/impl/db/migration";
    private static final String FLYWAY_TABLE = "explorer_schema_history";

    @Override
    protected void configure() {
        super.configure();

        bind(ExplorerTreeDao.class).to(ExplorerTreeDaoImpl.class);
    }

    @Override
    public String getFlyWayTableName() {
        return FLYWAY_TABLE;
    }

    @Override
    public String getModuleName() {
        return MODULE;
    }

    @Override
    public String getFlyWayLocation() {
        return FLYWAY_LOCATIONS;
    }

    @Override
    public Function<HikariConfig, ExplorerDbConnProvider> getConnectionProviderConstructor() {
        return ExplorerDbConnProvider::new;
    }

    @Override
    public Class<ExplorerDbConnProvider> getConnectionProviderType() {
        return ExplorerDbConnProvider.class;
    }
}
