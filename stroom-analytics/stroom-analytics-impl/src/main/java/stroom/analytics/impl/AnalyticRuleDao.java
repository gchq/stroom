package stroom.analytics.impl;

import stroom.util.shared.HasIntCrud;

import java.util.Optional;

public interface AnalyticRuleDao extends HasIntCrud<AnalyticRule> {

    Optional<AnalyticRule> fetchByUuid(String uuid);
}
