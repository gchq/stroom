package stroom.index.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;

import java.util.function.Function;

public class IndexDbModule extends AbstractFlyWayDbModule<IndexConfig, IndexDbConnProvider> {
    private static final String MODULE = "stroom-index";
    private static final String FLYWAY_LOCATIONS = "stroom/index/impl/db/migration";
    private static final String FLYWAY_TABLE = "index_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(IndexShardDao.class).to(IndexShardDaoImpl.class);
        bind(IndexVolumeDao.class).to(IndexVolumeDaoImpl.class);
        bind(IndexVolumeGroupDao.class).to(IndexVolumeGroupDaoImpl.class);
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
    public Function<HikariConfig, IndexDbConnProvider> getConnectionProviderConstructor() {
        return IndexDbConnProvider::new;
    }

    @Override
    public Class<IndexDbConnProvider> getConnectionProviderType() {
        return IndexDbConnProvider.class;
    }
}
