package stroom.meta.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.meta.impl.MetaDao;
import stroom.meta.impl.MetaFeedDao;
import stroom.meta.impl.MetaKeyDao;
import stroom.meta.impl.MetaProcessorDao;
import stroom.meta.impl.MetaTypeDao;
import stroom.meta.impl.MetaValueDao;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.sql.DataSource;
import java.util.function.Function;

public class MetaDbModule extends AbstractFlyWayDbModule<MetaServiceConfig, MetaDbConnProvider> {
    private static final String MODULE = "stroom-meta";
    private static final String FLYWAY_LOCATIONS = "stroom/meta/impl/db/migration";
    private static final String FLYWAY_TABLE = "meta_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(MetaFeedDao.class).to(MetaFeedDaoImpl.class);
        bind(MetaTypeDao.class).to(MetaTypeDaoImpl.class);
        bind(MetaProcessorDao.class).to(MetaProcessorDaoImpl.class);
        bind(MetaKeyDao.class).to(MetaKeyDaoImpl.class);
        bind(MetaValueDao.class).to(MetaValueDaoImpl.class);
        bind(MetaDao.class).to(MetaDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(Cleanup.class);
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
    protected Function<DataSource, MetaDbConnProvider> getConnectionProviderConstructor() {
        return MetaDbConnProvider::new;
    }

    @Override
    protected Class<MetaDbConnProvider> getConnectionProviderType() {
        return MetaDbConnProvider.class;
    }
}
