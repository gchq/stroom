package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcess;

import java.util.Optional;

public interface AnalyticProcessDao {

    Optional<AnalyticProcess> getByUuid(String uuid);

    Optional<AnalyticProcess> getByAnalyticUuid(String analyticUuid);

    AnalyticProcess create(AnalyticProcess analyticProcess);

    AnalyticProcess update(AnalyticProcess analyticProcess);

    boolean delete(AnalyticProcess analyticProcess);
}
