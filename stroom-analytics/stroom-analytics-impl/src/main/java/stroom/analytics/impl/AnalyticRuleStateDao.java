package stroom.analytics.impl;

import java.util.Optional;

public interface AnalyticRuleStateDao {

    Optional<AnalyticRuleState> getState(String analyticUuid);

    void createState(AnalyticRuleState analyticRuleState);

    void updateState(AnalyticRuleState analyticRuleState);
}
