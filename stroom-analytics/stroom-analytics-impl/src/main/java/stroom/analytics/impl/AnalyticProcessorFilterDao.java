package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessorFilter;

import java.util.Optional;

public interface AnalyticProcessorFilterDao {

    Optional<AnalyticProcessorFilter> getByUuid(String uuid);

    Optional<AnalyticProcessorFilter> getByAnalyticUuid(String analyticUuid);

    AnalyticProcessorFilter create(AnalyticProcessorFilter analyticProcessorFilter);

    AnalyticProcessorFilter update(AnalyticProcessorFilter analyticProcessorFilter);

    boolean delete(AnalyticProcessorFilter analyticProcessorFilter);
}
