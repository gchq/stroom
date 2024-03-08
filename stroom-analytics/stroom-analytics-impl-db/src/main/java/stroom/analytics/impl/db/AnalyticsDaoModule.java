package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticTrackerDao;

import com.google.inject.AbstractModule;

public class AnalyticsDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnalyticTrackerDao.class).to(AnalyticTrackerDaoImpl.class);
    }
}
