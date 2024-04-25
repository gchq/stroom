package stroom.analytics.impl;

import stroom.analytics.shared.NotificationEmailDestination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestEmailSender {

    @Mock
    private AnalyticsConfig mockAnalyticsConfig;

    private final RuleEmailTemplatingService ruleEmailTemplatingService = new RuleEmailTemplatingService();

    @Disabled // manual only due to reliance on smtp.freesmtpservers.com
    @Test
    void send() {
        // See https://www.wpoven.com/tools/free-smtp-server-for-testing to check the email sent
        Mockito.when(mockAnalyticsConfig.getEmailConfig())
                .thenReturn(new EmailConfig(
                        SmtpConfig.unauthenticated("smtp.freesmtpservers.com", 25),
                        "foo@foo.com",
                        "Mr Foo"));

        final EmailSender emailSender = new EmailSender(() ->
                mockAnalyticsConfig, ruleEmailTemplatingService);

        final NotificationEmailDestination destination = NotificationEmailDestination.builder()
                .to("bar@bar.com")
                .subjectTemplate("{{ headline }}")
                .bodyTemplate("Detector {{ detectorName }} spotted something")
                .build();

        final Detection detection = Detection.builder()
                .withRandomDetectionUniqueId()
                .withRandomDetectorUuid()
                .withHeadline("Headline")
                .withDetectorName("DetectorName")
                .build();

        emailSender.send(destination, detection);
    }
}
