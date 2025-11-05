package stroom.analytics.api;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.util.shared.Message;

import java.util.List;

public interface AnalyticsService {

    String testTemplate(final String template);

    void sendTestEmail(final NotificationEmailDestination emailDestination);

    /**
     * Compares analytic to the currently persisted version and validates those changes.
     *
     * @return A list of validation messages.
     */
    List<Message> validateChanges(final AnalyticRuleDoc analytic);
}
