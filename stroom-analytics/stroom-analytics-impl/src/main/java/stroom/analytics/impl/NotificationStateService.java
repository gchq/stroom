package stroom.analytics.impl;

import stroom.analytics.api.NotificationState;
import stroom.analytics.shared.AbstractAnalyticRuleDoc;
import stroom.analytics.shared.NotificationConfig;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class NotificationStateService {

    private final Map<String, NotificationStateImpl> map = new ConcurrentHashMap<>();

    public NotificationState getState(final AbstractAnalyticRuleDoc doc,
                                      final NotificationConfig analyticNotificationConfig) {
        final NotificationStateImpl analyticState = map
                .computeIfAbsent(doc.getUuid() + "_" + analyticNotificationConfig.getUuid(), k ->
                        new NotificationStateImpl());
        analyticState.update(analyticNotificationConfig);
        return analyticState;
    }
}
