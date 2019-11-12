package stroom.job.impl.db;

import com.zaxxer.hikari.HikariConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.db.util.AbstractFlyWayDbModule;
import stroom.job.impl.JobDao;
import stroom.job.impl.JobNodeDao;
import stroom.job.impl.JobSystemConfig;

import java.util.function.Function;

public class JobDbModule extends AbstractFlyWayDbModule<JobSystemConfig, JobDbConnProvider> {
    private static final Logger LOGGER = LoggerFactory.getLogger(JobDbModule.class);
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
    public Function<HikariConfig, JobDbConnProvider> getConnectionProviderConstructor() {
        return JobDbConnProvider::new;
    }

    @Override
    public Class<JobDbConnProvider> getConnectionProviderType() {
        return JobDbConnProvider.class;
    }
}
