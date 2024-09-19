package stroom.analytics.impl;

import stroom.analytics.impl.Detection.Builder;
import stroom.analytics.shared.EmailContent;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRuleEmailTemplatingService {

    @Test
    void renderAlertEmail() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final Detection detection = getExampleDetection(true, true);
        NotificationEmailDestination emailDestination = NotificationEmailDestination.builder()
                .subjectTemplate("{{ headline }}")
                .bodyTemplate("{{ detectorVersion }} - {{ detectionRevision }}")
                .build();
        final EmailContent emailContent = templatingService.renderAlertEmail(detection, emailDestination);

        assertThat(emailContent.getSubject())
                .isEqualTo(detection.getHeadline());

        assertThat(emailContent.getBody())
                .isEqualTo(detection.getDetectorVersion() + " - " + detection.getDetectionRevision());
    }

    @Test
    void renderTemplate() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final Detection detection = getExampleDetection(true, true);
        final String template = "{{ detectorVersion }} - {{ detectionRevision }}";
        final String output = templatingService.renderTemplate(detection, template);

        assertThat(output)
                .isEqualTo(detection.getDetectorVersion() + " - " + detection.getDetectionRevision());
    }

    @Test
    void renderDefaultTemplate() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final Detection detection = getExampleDetection(true, true);
        @SuppressWarnings("checkstyle:LineLength") final String template = new AnalyticUiDefaultConfig()
                .getDefaultBodyTemplate();

        final String output = templatingService.renderTemplate(detection, template);

        assertThat(output)
                .contains("name-4")
                .contains(detection.getDetectorName());
    }

    @Test
    void renderDefaultTemplate_noValues_noLinkedEvents() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final Detection detection = getExampleDetection(false, false);
        @SuppressWarnings("checkstyle:LineLength") final String template = new AnalyticUiDefaultConfig()
                .getDefaultBodyTemplate();

        final String output = templatingService.renderTemplate(detection, template);
        assertThat(output)
                .doesNotContain("name-4")
                .contains(detection.getDetectorName());
    }

    private Detection getExampleDetection(final boolean includeValues,
                                          final boolean includeLinkedEvents) {
        final String stroom = "Test Environment";
        final String executionTime = "2024-02-29T17:48:40.396Z";
        final String detectTime = "2024-02-29T17:48:41.582Z";
        final String effectiveExecutionTime = "2024-02-29T16:00:00.000Z";

        final Builder builder = Detection.builder()
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
                .withEffectiveExecutionTime(effectiveExecutionTime);

        if (includeValues) {
            builder
                    .addValue("name-1", "value-A")
                    .addValue("name-2", "value-B")
                    .addValue("name-3", "value-C")
                    .addValue("name-4", null);
        }
        if (includeLinkedEvents) {
            builder
                    .addLinkedEvents(new DetectionLinkedEvent(stroom, 1001L, 1L))
                    .addLinkedEvents(new DetectionLinkedEvent(stroom, 1001L, 2L))
                    .addLinkedEvents(new DetectionLinkedEvent(stroom, 2001L, 1L))
                    .addLinkedEvents(new DetectionLinkedEvent(stroom, 2002L, 2L));
        }
        return builder.build();
    }
}
