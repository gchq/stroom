package stroom.analytics.impl.db;

import stroom.analytics.impl.AnalyticRule;
import stroom.analytics.impl.AnalyticRuleDao;

import java.util.Optional;
import javax.inject.Singleton;

@Singleton
class AnalyticRuleDaoImpl implements AnalyticRuleDao {

    @Override
    public AnalyticRule create(final AnalyticRule analyticRule) {
        return null;
    }

    @Override
    public Optional<AnalyticRule> fetch(final int id) {
        return Optional.empty();
    }

    @Override
    public AnalyticRule update(final AnalyticRule analyticRule) {
        return null;
    }

    @Override
    public boolean delete(final int id) {
        return false;
    }

    @Override
    public Optional<AnalyticRule> fetchByUuid(final String uuid) {
        return Optional.empty();
    }
}
