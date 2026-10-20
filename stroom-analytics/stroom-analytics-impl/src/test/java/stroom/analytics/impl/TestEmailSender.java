/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.analytics.impl;

import stroom.analytics.api.EmailMessage;
import stroom.analytics.shared.NotificationEmailDestination;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.Recipient;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        emailSender.sendDetection(destination, detection);
    }

    @Test
    void validMessageHasNoErrors() {
        assertThat(EmailSender.validate(new EmailMessage(
                "a@example.com", null, null, "Subject", "Body")))
                .isEmpty();
    }

    @Test
    void ccOrBccAloneIsEnoughRecipients() {
        assertThat(EmailSender.validate(new EmailMessage(null, "a@example.com", null, "Subject", "Body")))
                .isEmpty();
        assertThat(EmailSender.validate(new EmailMessage("", null, "a@example.com", "Subject", "Body")))
                .isEmpty();
    }

    @Test
    void emptyBodyIsAllowed() {
        assertThat(EmailSender.validate(new EmailMessage("a@example.com", null, null, "Subject", "")))
                .isEmpty();
    }

    @Test
    void noRecipientsIsInvalid() {
        assertThat(EmailSender.validate(new EmailMessage(null, "", " ", "Subject", "Body")))
                .singleElement()
                .asString()
                .contains("at least one recipient");
    }

    @Test
    void separatorsWithoutAddressesAreNotRecipients() {
        // An XSLT building the list from empty values gives just separators, which address no one
        assertThat(EmailSender.validate(new EmailMessage(" ; ;", null, null, "Subject", "Body")))
                .singleElement()
                .asString()
                .contains("at least one recipient");
    }

    @Test
    void blankSubjectIsInvalid() {
        assertThat(EmailSender.validate(new EmailMessage("a@example.com", null, null, " ", "Body")))
                .singleElement()
                .asString()
                .contains("Subject cannot be blank");
        assertThat(EmailSender.validate(new EmailMessage("a@example.com", null, null, null, "Body")))
                .singleElement()
                .asString()
                .contains("Subject cannot be blank");
    }

    @Test
    void multiLineSubjectIsInvalid() {
        // A line break in a subject could inject extra headers
        assertThat(EmailSender.validate(new EmailMessage("a@example.com", null, null, "Sub\nBcc: x@y.com", "Body")))
                .singleElement()
                .asString()
                .contains("one line only");
    }

    @Test
    void sendEmailRejectsInvalidMessageBeforeSending() {
        final EmailSender emailSender = new EmailSender(() -> mockAnalyticsConfig, ruleEmailTemplatingService);

        assertThatThrownBy(() -> emailSender.sendEmail(new EmailMessage(null, null, null, "", "Body")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The following errors were found with the email:")
                .hasMessageContaining("Subject cannot be blank")
                .hasMessageContaining("at least one recipient");
        Mockito.verifyNoInteractions(mockAnalyticsConfig);
    }

    @Test
    void buildEmailAddressesAllRecipients() {
        final Email email = EmailSender.buildEmail(
                new EmailConfig(SmtpConfig.unauthenticated("localhost", 25), "stroom@example.com", "Stroom"),
                new EmailMessage(
                        "a@example.com; b@example.com",
                        "c@example.com",
                        "d@example.com;",
                        "Subject",
                        "Plain body"),
                Collections.emptyList());

        assertThat(addresses(email.getToRecipients()))
                .containsExactly("a@example.com", "b@example.com");
        assertThat(addresses(email.getCcRecipients()))
                .containsExactly("c@example.com");
        assertThat(addresses(email.getBccRecipients()))
                .containsExactly("d@example.com");
        assertThat(email.getFromRecipient().getAddress())
                .isEqualTo("stroom@example.com");
        assertThat(email.getFromRecipient().getName())
                .isEqualTo("Stroom");
        assertThat(email.getSubject())
                .isEqualTo("Subject");
        assertThat(email.getPlainText())
                .isEqualTo("Plain body");
        assertThat(email.getHTMLText())
                .isNull();
    }

    @Test
    void buildEmailSendsHtmlBodyAsHtml() {
        final Email email = EmailSender.buildEmail(
                new EmailConfig(SmtpConfig.unauthenticated("localhost", 25), "stroom@example.com", "Stroom"),
                new EmailMessage("a@example.com", null, null, "Subject", "<p>HTML body</p>"),
                Collections.emptyList());

        assertThat(email.getHTMLText())
                .isEqualTo("<p>HTML body</p>");
        assertThat(email.getPlainText())
                .isNull();
    }

    private static List<String> addresses(final List<Recipient> recipients) {
        return recipients.stream()
                .map(Recipient::getAddress)
                .toList();
    }
}
