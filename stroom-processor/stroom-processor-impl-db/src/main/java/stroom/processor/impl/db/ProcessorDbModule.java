package stroom.processor.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.db.util.DataSourceProxy;
import stroom.processor.impl.ProcessorConfig.ProcessorDbConfig;
import stroom.util.guice.GuiceUtil;

import javax.sql.DataSource;

public class ProcessorDbModule extends AbstractFlyWayDbModule<ProcessorDbConfig, ProcessorDbConnProvider> {

    private static final String MODULE = "stroom-processor";
    private static final String FLYWAY_LOCATIONS = "stroom/processor/impl/db/migration";
    private static final String FLYWAY_TABLE = "processor_schema_history";

    @Override
    protected void configure() {
        super.configure();

        // MultiBind the connection provider so we can see status for all databases.
        GuiceUtil.buildMultiBinder(binder(), DataSource.class)
                .addBinding(ProcessorDbConnProvider.class);
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
            super(dataSource, MODULE);
        }
    }
}
