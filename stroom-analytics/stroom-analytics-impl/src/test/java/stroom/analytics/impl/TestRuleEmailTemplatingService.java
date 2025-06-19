package stroom.analytics.impl;

import stroom.analytics.impl.Detection.Builder;
import stroom.analytics.shared.EmailContent;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.ui.config.shared.AnalyticUiDefaultConfig;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRuleEmailTemplatingService {

    @Test
    void renderDetectionEmail() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final Detection detection = getExampleDetection(true, true);
        final NotificationEmailDestination emailDestination = NotificationEmailDestination.builder()
                .subjectTemplate("{{ headline }}")
                .bodyTemplate("{{ detectorVersion }} - {{ detectionRevision }}")
                .build();
        final EmailContent emailContent = templatingService.renderDetectionEmail(detection, emailDestination);

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
        final String template = new AnalyticUiDefaultConfig().getDefaultBodyTemplate();

        final String output = templatingService.renderTemplate(detection, template);

        assertThat(output)
                .contains("name_4")
                .contains("value_C")
                .doesNotContain("value_C2") // The dup one
                .contains(detection.getDetectorName());
    }

    @Test
    void renderDefaultTemplate_noValues_noLinkedEvents() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final Detection detection = getExampleDetection(false, false);
        final String template = new AnalyticUiDefaultConfig().getDefaultBodyTemplate();

        final String output = templatingService.renderTemplate(detection, template);
        assertThat(output)
                .doesNotContain("name_4")
                .contains(detection.getDetectorName());
    }

    @Test
    void variableNames() {
        final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();
        final String template = """
                    {{ values['key-1'] }}
                    {{ values['key_2'] }}
                    {{ values['key/3'] }}
                    {{ values['key.4'] }}
                """;

        final Detection detection = Detection.builder()
                .withDetectorName("MyName")
                .addValue("key-1", "val_1")
                .addValue("key_2", "val_2")
                .addValue("key/3", "val_3")
                .addValue("key.4", "val_4")
                .build();

        final String output = templatingService.renderTemplate(detection, template);

        assertThat(output)
                .contains("val_1")
                .contains("val_2")
                .contains("val_3")
                .contains("val_4");
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
                    .addValue("name_1", "value_A")
                    .addValue("name_2", "value_B")
                    .addValue("name_3", "value_C")
                    .addValue("name_4", null)
                    .addValue("name_3", "value_C2");  // Dup
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
