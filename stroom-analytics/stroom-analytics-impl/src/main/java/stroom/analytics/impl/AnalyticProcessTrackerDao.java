package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessTracker;

import java.util.Optional;

public interface AnalyticProcessTrackerDao {

    Optional<AnalyticProcessTracker> get(String processUuid);

    void create(AnalyticProcessTracker analyticProcessTracker);

    void update(AnalyticProcessTracker analyticProcessTracker);

    void delete(AnalyticProcessTracker analyticProcessTracker);
}
