package stroom.analytics.api;

import stroom.analytics.shared.AnalyticNotificationEmailDestination;

public interface AnalyticsService {

    String testTemplate(final String template);

    void sendTestEmail(final AnalyticNotificationEmailDestination emailDestination);
}
