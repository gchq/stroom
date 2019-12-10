package stroom.job.impl.db;

import stroom.db.util.AbstractFlyWayDbModule;
import stroom.job.impl.JobDao;
import stroom.job.impl.JobNodeDao;
import stroom.job.impl.JobSystemConfig;

import javax.sql.DataSource;
import java.util.function.Function;

public class JobDbModule extends AbstractFlyWayDbModule<JobSystemConfig, JobDbConnProvider> {
    private static final String MODULE = "stroom-job";
    private static final String FLYWAY_LOCATIONS = "stroom/job/impl/db/migration";
    private static final String FLYWAY_TABLE = "job_schema_history";

    @Override
    protected void configure() {
        super.configure();
        bind(JobDao.class).to(JobDaoImpl.class);
        bind(JobNodeDao.class).to(JobNodeDaoImpl.class);
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
    protected Function<DataSource, JobDbConnProvider> getConnectionProviderConstructor() {
        return JobDbConnProvider::new;
    }

    @Override
    protected Class<JobDbConnProvider> getConnectionProviderType() {
        return JobDbConnProvider.class;
    }
}
