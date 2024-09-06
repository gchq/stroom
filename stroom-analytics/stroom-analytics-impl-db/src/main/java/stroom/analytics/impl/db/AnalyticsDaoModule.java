package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticTrackerDao;
import stroom.analytics.impl.ExecutionScheduleDao;

import com.google.inject.AbstractModule;

public class AnalyticsDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnalyticTrackerDao.class).to(AnalyticTrackerDaoImpl.class);
        bind(ExecutionScheduleDao.class).to(ExecutionScheduleDaoImpl.class);
    }
}
