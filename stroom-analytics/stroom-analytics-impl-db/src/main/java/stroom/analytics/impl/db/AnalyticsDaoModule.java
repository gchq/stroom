package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticProcessDao;
import stroom.analytics.impl.AnalyticProcessTrackerDao;

import com.google.inject.AbstractModule;

public class AnalyticsDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnalyticProcessDao.class).to(AnalyticProcessDaoImpl.class);
        bind(AnalyticProcessTrackerDao.class).to(AnalyticProcessTrackerDaoImpl.class);
    }
}
