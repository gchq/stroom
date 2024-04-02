/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.analytics.impl;

import stroom.analytics.shared.EmailContent;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.GwtNullSafe;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.mail.Message.RecipientType;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.email.Recipient;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EmailSender {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EmailSender.class);

    // Detects tags in a string
    private static final Pattern HTML_PATTERN = Pattern.compile("<[^<>]+>");
    private static final Predicate<String> HTML_PREDICATE = HTML_PATTERN.asPredicate();

    private final Provider<AnalyticsConfig> analyticsConfigProvider;
    private final RuleEmailTemplatingService ruleEmailTemplatingService;

    @Inject
    EmailSender(final Provider<AnalyticsConfig> analyticsConfigProvider,
                final RuleEmailTemplatingService ruleEmailTemplatingService) {
        this.analyticsConfigProvider = analyticsConfigProvider;
        this.ruleEmailTemplatingService = ruleEmailTemplatingService;
    }

    public void send(final NotificationEmailDestination emailDestination,
                     final Detection detection) {
        final AnalyticsConfig analyticsConfig = analyticsConfigProvider.get();
        final EmailConfig emailConfig = analyticsConfig.getEmailConfig();
        Preconditions.checkNotNull(emailConfig, "Missing 'email' section in config");
        final SmtpConfig smtpConfig = Preconditions.checkNotNull(emailConfig.getSmtpConfig(),
                "Missing 'smtp' section in email config");

        final EmailPopulatingBuilder emailBuilder = EmailBuilder.startingBlank()
                .from(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withReplyTo(emailConfig.getFromName(), emailConfig.getFromAddress());

        addAddresses(emailDestination.getTo(), address ->
                emailBuilder.withRecipient(address, address, RecipientType.TO));
        addAddresses(emailDestination.getCc(), address ->
                emailBuilder.withRecipient(address, address, RecipientType.CC));
        addAddresses(emailDestination.getBcc(), address ->
                emailBuilder.withRecipient(address, address, RecipientType.BCC));

        try {
            validate(emailDestination);

            final EmailContent renderedEmail = ruleEmailTemplatingService.renderAlertEmail(
                    detection,
                    emailDestination);

            final String subject = NullSafe.nonBlankStringElse(
                    renderedEmail.getSubject(),
                    detection.getDetectorName());
            final String body = Objects.requireNonNullElse(renderedEmail.getBody(), "");

            emailBuilder.withSubject(subject);
            if (looksLikeHtml(body)) {
                emailBuilder.withHTMLText(body);
            } else {
                emailBuilder.withPlainText(body);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }

        final Email email = emailBuilder.buildEmail();

        final MailerRegularBuilderImpl mailerBuilder = MailerBuilder
                .withTransportStrategy(smtpConfig.getTransportStrategy());

        if (!NullSafe.isEmptyString(smtpConfig.getUsername())
                && !NullSafe.isEmptyString(smtpConfig.getPassword())) {
            mailerBuilder.withSMTPServer(
                    smtpConfig.getHost(),
                    smtpConfig.getPort(),
                    smtpConfig.getUsername(),
                    smtpConfig.getPassword());
        } else {
            mailerBuilder.withSMTPServer(
                    smtpConfig.getHost(),
                    smtpConfig.getPort());
        }

        LOGGER.info(() -> LogUtil.message(
                "Sending alert email to recipients {} with subject '{}'. See DEBUG for full detail.",
                recipientsToString(email), email.getSubject()));

        LOGGER.debug(() -> LogUtil.message("Sending alert email {} using user ({}) at {}:{}",
                email,
                smtpConfig.getUsername(),
                smtpConfig.getHost(),
                smtpConfig.getPort()));
        try (Mailer mailer = mailerBuilder.buildMailer()) {
            mailer.sendMail(email);
        } catch (Exception e) {
            LOGGER.error("Error sending alert email {} using user ({}) at {}:{} - {}",
                    email,
                    smtpConfig.getUsername(),
                    smtpConfig.getHost(),
                    smtpConfig.getPort(),
                    e.getMessage(),
                    e);
        }
    }

    private boolean looksLikeHtml(final String str) {
        if (NullSafe.isBlankString(str)) {
            return false;
        } else {
            return HTML_PREDICATE.test(str);
        }
    }

    private String recipientsToString(final Email email) {
        final List<String> strings = new ArrayList<>();
        if (NullSafe.hasItems(email.getToRecipients())) {
            strings.add("To: " + recipientsToString(email.getToRecipients()));
        }
        if (NullSafe.hasItems(email.getCcRecipients())) {
            strings.add("Cc: " + recipientsToString(email.getCcRecipients()));
        }
        if (NullSafe.hasItems(email.getBccRecipients())) {
            strings.add("Bcc: " + recipientsToString(email.getBccRecipients()));
        }
        return String.join(", ", strings);
    }

    private String recipientsToString(final List<Recipient> recipients) {
        return NullSafe.stream(recipients)
                .map(Recipient::getAddress)
                .collect(Collectors.joining(";"));
    }

    private void validate(final NotificationEmailDestination emailDestination) {
        final List<String> msgs = new ArrayList<>();
        final String subject = emailDestination.getSubjectTemplate();
        final String body = emailDestination.getBodyTemplate();

        if (GwtNullSafe.isBlankString(subject)) {
            msgs.add("Subject cannot be blank.");
        }
        if (subject.contains("\n") || subject.contains("\r")) {
            msgs.add("Subject contains line breaks or carriage returns. It must be one line only.");
        }
        if (GwtNullSafe.isBlankString(body)) {
            msgs.add("Body cannot be blank.");
        }
        final boolean hasRecipients = Stream.of(
                        emailDestination.getTo(),
                        emailDestination.getCc(),
                        emailDestination.getBcc())
                .anyMatch(str -> !GwtNullSafe.isBlankString(str));

        if (!hasRecipients) {
            msgs.add("You must enter at least one recipient (To, Cc, Bcc).");
        }

        if (!msgs.isEmpty()) {
            final String prefix = msgs.size() > 1
                    ? "The following errors were found with the email notification settings:"
                    : "The following error was found with the email notification settings:";
            final String msg = prefix + "\n\n" + String.join("\n", msgs);

            throw new RuntimeException(msg);
        }
    }

    private void addAddresses(final String addresses,
                              final Consumer<String> consumer) {
        if (addresses != null) {
            final String[] emailAddresses = addresses.split(";");
            for (final String emailAddress : emailAddresses) {
                final String trimmed = emailAddress.trim();
                if (!trimmed.isEmpty()) {
                    consumer.accept(trimmed);
                }
            }
        }
    }
}
