package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticNotificationEmailDestination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class TestEmailSender {

    @Mock
    private AnalyticsConfig mockAnalyticsConfig;

    @Disabled // manual only due to reliance on smtp.freesmtpservers.com
    @Test
    void send() {
        // See https://www.wpoven.com/tools/free-smtp-server-for-testing to check the email sent
        Mockito.when(mockAnalyticsConfig.getEmailConfig())
                .thenReturn(new EmailConfig(
                        SmtpConfig.unauthenticated("smtp.freesmtpservers.com", 25),
                        "foo@foo.com",
                        "Mr Foo"));

        final EmailSender emailSender = new EmailSender(() -> mockAnalyticsConfig);

        final AnalyticNotificationEmailDestination destination = AnalyticNotificationEmailDestination.builder()
                .to("bar@bar.com")
                .build();

        final Detection detection = new Detection(
                null,
                "DetectorName",
                UUID.randomUUID().toString(),
                null,
                null,
                "Headline",
                null,
                null,
                UUID.randomUUID().toString(),
                null,
                null,
                null,
                null);

        emailSender.send(destination, detection);
    }
}
