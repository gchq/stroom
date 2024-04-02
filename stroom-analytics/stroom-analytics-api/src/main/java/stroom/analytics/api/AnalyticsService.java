package stroom.analytics.api;

import stroom.analytics.shared.NotificationEmailDestination;

public interface AnalyticsService {

    String testTemplate(final String template);

    void sendTestEmail(final NotificationEmailDestination emailDestination);
}
