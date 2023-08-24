package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

@Singleton
public class NotificationStateService {

    private final Map<String, NotificationStateImpl> map = new ConcurrentHashMap<>();

    public NotificationState getState(final AnalyticRuleDoc doc) {
        final NotificationStateImpl analyticState = map.computeIfAbsent(doc.getUuid(), k ->
                new NotificationStateImpl());
        analyticState.update(doc.getAnalyticNotificationConfig());
        return analyticState;
    }
}
