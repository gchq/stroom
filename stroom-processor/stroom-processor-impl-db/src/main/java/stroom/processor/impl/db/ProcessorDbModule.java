package stroom.processor.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.sql.DataSource;

public class ProcessorDbModule extends AbstractFlyWayDbModule<ProcessorConfig, ProcessorDbConnProvider> {
    private static final String MODULE = "stroom-processor";
    private static final String FLYWAY_LOCATIONS = "stroom/processor/impl/db/migration";
    private static final String FLYWAY_TABLE = "processor_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(ProcessorDao.class).to(ProcessorDaoImpl.class);
        bind(ProcessorFilterDao.class).to(ProcessorFilterDaoImpl.class);
        bind(ProcessorTaskDao.class).to(ProcessorTaskDaoImpl.class);
        bind(ProcessorTaskDeleteExecutor.class).to(ProcessorTaskDeleteExecutorImpl.class);
        bind(ProcessorFilterTrackerDao.class).to(ProcessorFilterTrackerDaoImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(ProcessorNodeCache.class);
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
    protected Class<ProcessorDbConnProvider> getConnectionProviderType() {
        return ProcessorDbConnProvider.class;
    }

    @Override
    protected ProcessorDbConnProvider createConnectionProvider(final DataSource dataSource) {
        return new DataSourceImpl(dataSource);
    }

    private static class DataSourceImpl extends DataSourceProxy implements ProcessorDbConnProvider {
        private DataSourceImpl(final DataSource dataSource) {
            super(dataSource);
        }
    }
}
