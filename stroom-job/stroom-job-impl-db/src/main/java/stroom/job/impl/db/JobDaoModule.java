package stroom.job.impl.db;

import stroom.job.impl.JobDao;
import stroom.job.impl.JobNodeDao;

import com.google.inject.AbstractModule;

public class JobDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(JobDao.class).to(JobDaoImpl.class);
        bind(JobNodeDao.class).to(JobNodeDaoImpl.class);
    }
}
