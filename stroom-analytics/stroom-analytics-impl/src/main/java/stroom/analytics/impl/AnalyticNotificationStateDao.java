package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotificationState;

import java.util.Optional;

public interface AnalyticNotificationStateDao {

    Optional<AnalyticNotificationState> get(String notificationUuid);

    void create(AnalyticNotificationState notificationState);

    void update(AnalyticNotificationState notificationState);

    void delete(AnalyticNotificationState notificationState);
}
