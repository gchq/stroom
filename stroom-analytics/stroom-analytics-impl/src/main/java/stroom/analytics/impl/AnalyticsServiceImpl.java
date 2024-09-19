package stroom.analytics.impl;

import stroom.analytics.api.AnalyticsService;
import stroom.analytics.shared.NotificationEmailDestination;

import jakarta.inject.Inject;

public class AnalyticsServiceImpl implements AnalyticsService {

    private final EmailSender emailSender;
    private final RuleEmailTemplatingService ruleEmailTemplatingService;

    @Inject
    AnalyticsServiceImpl(final EmailSender emailSender,
                         final RuleEmailTemplatingService ruleEmailTemplatingService) {
        this.emailSender = emailSender;
        this.ruleEmailTemplatingService = ruleEmailTemplatingService;
    }

    @Override
    public String testTemplate(final String template) {
        return ruleEmailTemplatingService.renderTemplate(getExampleDetection(), template);
    }

    @Override
    public void sendTestEmail(final NotificationEmailDestination emailDestination) {
        emailSender.send(emailDestination, getExampleDetection());
    }

    // pkg private for testing
    Detection getExampleDetection() {
        final String stroom = "Test Environment";
        final String executionTime = "2024-02-29T17:48:40.396Z";
        final String detectTime = "2024-02-29T17:48:41.582Z";
        final String effectiveExecutionTime = "2024-02-29T16:00:00.000Z";

        // NOTE variables (including keys in maps) cannot use '-'
        return Detection.builder()
                .withDetectTime(detectTime)
                .withDetectorName("Example detector for test use.")
                .withHeadline("The headline for the detection.")
                .withDetailedDescription("A detailed description of what happened.")
                .withFullDescription("A full description of what happened.")
                .withDetectionRevision(123)
                .withDetectorVersion("v4.5.6")
                .withDetectorUuid("8909c01d-e1e7-4d22-b960-09933ddc4469")
                .withDetectionUniqueId("cc048f31-8a7f-41ae-a1d5-b80529de634d")
                .withDefunct(false)
                .withExecutionTime(executionTime)
                .withExecutionSchedule("1hr")
                .withEffectiveExecutionTime(effectiveExecutionTime)
                .addValue("name_1", "value_A")
                .addValue("name_2", "value_B")
                .addValue("name_3", "value_C")
                .addValue("name_4", null)
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 1001L, 1L))
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 1001L, 2L))
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 2001L, 1L))
                .addLinkedEvents(new DetectionLinkedEvent(stroom, 2002L, 2L))
                .build();
    }
}
