package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessorFilterTracker;

import java.util.Optional;

public interface AnalyticProcessorFilterTrackerDao {

    Optional<AnalyticProcessorFilterTracker> get(String filterUuid);

    void create(AnalyticProcessorFilterTracker analyticProcessorFilterTracker);

    void update(AnalyticProcessorFilterTracker analyticProcessorFilterTracker);

    void delete(AnalyticProcessorFilterTracker analyticProcessorFilterTracker);
}
