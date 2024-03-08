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

import stroom.analytics.shared.AnalyticNotificationEmailDestination;
import stroom.util.NullSafe;
import stroom.util.json.JsonUtil;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.mail.Message.RecipientType;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.email.EmailPopulatingBuilder;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;

class EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);

    private final Provider<AnalyticsConfig> analyticsConfigProvider;

    @Inject
    EmailSender(final Provider<AnalyticsConfig> analyticsConfigProvider) {
        this.analyticsConfigProvider = analyticsConfigProvider;
    }

    public void send(final AnalyticNotificationEmailDestination emailDestination,
                     final Detection detection) {
        final AnalyticsConfig analyticsConfig = analyticsConfigProvider.get();
        final EmailConfig emailConfig = analyticsConfig.getEmailConfig();
        Preconditions.checkNotNull(emailConfig, "Missing 'email' section in config");
        final SmtpConfig smtpConfig = Preconditions.checkNotNull(emailConfig.getSmtpConfig(),
                "Missing 'smtp' section in email config");

        final EmailPopulatingBuilder emailBuilder = EmailBuilder.startingBlank()
                .from(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withReplyTo(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withSubject(detection.getDetectorName());

        addAddresses(emailDestination.getTo(), address ->
                emailBuilder.withRecipient(address, address, RecipientType.TO));
        addAddresses(emailDestination.getCc(), address ->
                emailBuilder.withRecipient(address, address, RecipientType.CC));
        addAddresses(emailDestination.getBcc(), address ->
                emailBuilder.withRecipient(address, address, RecipientType.BCC));

        try {
            final String text = JsonUtil.writeValueAsString(detection);
            emailBuilder.withPlainText(text);
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

        LOGGER.info("Sending alert email {} using user ({}) at {}:{}",
                email,
                smtpConfig.getUsername(),
                smtpConfig.getHost(),
                smtpConfig.getPort());

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
