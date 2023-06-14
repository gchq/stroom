package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticNotificationDao;
import stroom.analytics.impl.AnalyticNotificationStateDao;
import stroom.analytics.impl.AnalyticProcessorFilterDao;
import stroom.analytics.impl.AnalyticProcessorFilterTrackerDao;
import stroom.analytics.impl.AnalyticRuleDao;

import com.google.inject.AbstractModule;

public class AnalyticsDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnalyticRuleDao.class).to(AnalyticRuleDaoImpl.class);
        bind(AnalyticProcessorFilterDao.class).to(AnalyticProcessorFilterDaoImpl.class);
        bind(AnalyticProcessorFilterTrackerDao.class).to(AnalyticProcessorFilterTrackerDaoImpl.class);
        bind(AnalyticNotificationDao.class).to(AnalyticNotificationDaoImpl.class);
        bind(AnalyticNotificationStateDao.class).to(AnalyticNotificationStateDaoImpl.class);
    }
}
