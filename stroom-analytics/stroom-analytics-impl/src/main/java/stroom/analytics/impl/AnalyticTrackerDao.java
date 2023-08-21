package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticTracker;

import java.util.Optional;

public interface AnalyticTrackerDao {

    Optional<AnalyticTracker> get(String analyticUuid);

    void create(AnalyticTracker analyticTracker);

    void update(AnalyticTracker analyticTracker);

    void delete(AnalyticTracker analyticTracker);
}
