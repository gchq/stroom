/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.analytics.shared.EmailContent;
import stroom.analytics.shared.NotificationEmailDestination;
import stroom.analytics.shared.ReportDoc;
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.common.base.Preconditions;
import jakarta.activation.FileDataSource;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.mail.Message.RecipientType;
import org.simplejavamail.api.email.AttachmentResource;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.email.Recipient;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public void sendDetection(final NotificationEmailDestination emailDestination,
                              final Detection detection) {
        final EmailContent renderedEmail = ruleEmailTemplatingService.renderDetectionEmail(
                detection,
                emailDestination);
        send(emailDestination, renderedEmail, detection.getDetectorName(), Collections.emptyList());
    }

    public void sendReport(final ReportDoc reportDoc,
                           final NotificationEmailDestination emailDestination,
                           final Path file,
                           final Instant executionTime,
                           final Instant effectiveExecutionTime) {
        final Map<String, Object> context = new HashMap<>();
        context.put("reportName", reportDoc.getName());
        context.put("description", reportDoc.getDescription());
        context.put("executionTime", DateUtil.createNormalDateTimeString(executionTime));
        context.put("effectiveExecutionTime", DateUtil.createNormalDateTimeString(effectiveExecutionTime));

        ruleEmailTemplatingService.renderEmail(emailDestination, context);
        final EmailContent renderedEmail = ruleEmailTemplatingService.renderEmail(emailDestination, context);
        final List<AttachmentResource> attachmentResources = new ArrayList<>();
        attachmentResources.add(new AttachmentResource(file.getFileName().toString(),
                new FileDataSource(file.toFile())));
        send(emailDestination, renderedEmail, reportDoc.getName(), attachmentResources);
    }

    private void send(final NotificationEmailDestination emailDestination,
                      final EmailContent renderedEmail,
                      final String defaultSubject,
                      final List<AttachmentResource> attachmentResources) {
        final AnalyticsConfig analyticsConfig = analyticsConfigProvider.get();
        final EmailConfig emailConfig = analyticsConfig.getEmailConfig();
        Preconditions.checkNotNull(emailConfig, "Missing 'email' section in config");
        final SmtpConfig smtpConfig = Preconditions.checkNotNull(emailConfig.getSmtpConfig(),
                "Missing 'smtp' section in email config");
        final EmailPopulatingBuilder emailBuilder = EmailBuilder.startingBlank();

        try {
            validate(emailDestination);

            final String subject = NullSafe.nonBlankStringElse(
                    renderedEmail.getSubject(),
                    defaultSubject);
            final String body = Objects.requireNonNullElse(renderedEmail.getBody(), "");

            emailBuilder
                    .from(emailConfig.getFromName(), emailConfig.getFromAddress())
                    .withReplyTo(emailConfig.getFromName(), emailConfig.getFromAddress());

            addAddresses(emailDestination.getTo(), address ->
                    emailBuilder.withRecipient(address, address, RecipientType.TO));
            addAddresses(emailDestination.getCc(), address ->
                    emailBuilder.withRecipient(address, address, RecipientType.CC));
            addAddresses(emailDestination.getBcc(), address ->
                    emailBuilder.withRecipient(address, address, RecipientType.BCC));

            emailBuilder.withSubject(subject);
            if (looksLikeHtml(body)) {
                emailBuilder.withHTMLText(body);
            } else {
                emailBuilder.withPlainText(body);
            }
            emailBuilder.withAttachments(attachmentResources);
        } catch (final Exception e) {
            final String message = LogUtil.message("Error sending alert email using user ({}) at {}:{} - {}",
                    smtpConfig.getUsername(),
                    smtpConfig.getHost(),
                    smtpConfig.getPort(),
                    e.getMessage());
            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
        }

        final Email email = emailBuilder.buildEmail();
        try {
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
            try (final Mailer mailer = mailerBuilder.buildMailer()) {
                mailer.sendMail(email);
            }
        } catch (final Exception e) {
            final String message = LogUtil.message("Error sending alert email {} using user ({}) at {}:{} - {}",
                    email,
                    smtpConfig.getUsername(),
                    smtpConfig.getHost(),
                    smtpConfig.getPort(),
                    e.getMessage());
            LOGGER.error(message, e);
            throw new RuntimeException(message, e);
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

        if (NullSafe.isBlankString(subject)) {
            msgs.add("Subject cannot be blank.");
        }
        if (subject.contains("\n") || subject.contains("\r")) {
            msgs.add("Subject contains line breaks or carriage returns. It must be one line only.");
        }
        if (NullSafe.isBlankString(body)) {
            msgs.add("Body cannot be blank.");
        }
        final boolean hasRecipients = Stream.of(
                        emailDestination.getTo(),
                        emailDestination.getCc(),
                        emailDestination.getBcc())
                .anyMatch(str -> !NullSafe.isBlankString(str));

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
