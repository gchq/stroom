package stroom.processor.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.processor.impl.ProcessorConfig;
import stroom.processor.impl.ProcessorDao;
import stroom.processor.impl.ProcessorFilterDao;
import stroom.processor.impl.ProcessorFilterTrackerDao;
import stroom.processor.impl.ProcessorTaskDao;
import stroom.processor.impl.ProcessorTaskDeleteExecutor;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import java.util.function.Function;

public class ProcessorDbModule extends AbstractFlyWayDbModule<ProcessorConfig, ConnectionProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessorDbModule.class);
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
    public Function<HikariConfig, ConnectionProvider> getConnectionProviderConstructor() {
        return ConnectionProvider::new;
    }

    @Override
    public Class<ConnectionProvider> getConnectionProviderType() {
        return ConnectionProvider.class;
    }
}
