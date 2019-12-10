package stroom.index.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexVolumeDao;
import stroom.index.impl.IndexVolumeGroupDao;

import javax.sql.DataSource;
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
    protected Function<DataSource, IndexDbConnProvider> getConnectionProviderConstructor() {
        return IndexDbConnProvider::new;
    }

    @Override
    protected Class<IndexDbConnProvider> getConnectionProviderType() {
        return IndexDbConnProvider.class;
    }
}
