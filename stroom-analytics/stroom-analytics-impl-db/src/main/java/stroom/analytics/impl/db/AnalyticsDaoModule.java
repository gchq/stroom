package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticRuleDao;
import stroom.analytics.impl.AnalyticRuleStateDao;

import com.google.inject.AbstractModule;

public class AnalyticsDaoModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();

        bind(AnalyticRuleDao.class).to(AnalyticRuleDaoImpl.class);
        bind(AnalyticRuleStateDao.class).to(AnalyticRuleStateDaoImpl.class);
    }
}
