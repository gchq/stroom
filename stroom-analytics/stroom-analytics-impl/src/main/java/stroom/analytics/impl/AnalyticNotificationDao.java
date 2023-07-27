package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotification;

import java.util.List;
import java.util.Optional;

public interface AnalyticNotificationDao {

    Optional<AnalyticNotification> getByUuid(String uuid);

    List<AnalyticNotification> getByAnalyticUuid(String analyticUuid);

    AnalyticNotification create(AnalyticNotification notification);

    AnalyticNotification update(AnalyticNotification notification);

    boolean delete(AnalyticNotification notification);
}
