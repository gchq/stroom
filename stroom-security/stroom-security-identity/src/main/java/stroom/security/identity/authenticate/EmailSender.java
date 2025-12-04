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

package stroom.security.identity.authenticate;

import stroom.config.common.UriFactory;
import stroom.security.identity.config.EmailConfig;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.SmtpConfig;
import stroom.util.shared.NullSafe;

import com.google.common.base.Preconditions;
import jakarta.inject.Inject;
import jakarta.mail.Message.RecipientType;
import org.simplejavamail.api.email.Email;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;
import org.simplejavamail.mailer.internal.MailerRegularBuilderImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class EmailSender {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);

    private final UriFactory uriFactory;
    private final IdentityConfig authenticationConfig;

    @Inject
    EmailSender(final UriFactory uriFactory,
                final IdentityConfig authenticationConfig) {
        this.uriFactory = uriFactory;
        this.authenticationConfig = authenticationConfig;
    }

    public void send(final String emailAddress,
                     final String firstName,
                     final String lastName, final String resetToken) {

        final EmailConfig emailConfig = authenticationConfig.getEmailConfig();
        final SmtpConfig smtpConfig = Preconditions.checkNotNull(
                        emailConfig, "Missing 'email' section in config")
                .getSmtpConfig();

        final String resetName = firstName == null
                ? "[Name not available]"
                : firstName + " " + lastName;
        String resetUrl = String.format(emailConfig.getPasswordResetUrl(), emailAddress, resetToken);
        resetUrl = uriFactory.publicUri(resetUrl).toString();
        final String passwordResetEmailText = String.format(emailConfig.getPasswordResetText(), resetUrl);

        final Email email = EmailBuilder.startingBlank()
                .from(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withReplyTo(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withRecipient(resetName, emailAddress, RecipientType.TO)
                .withSubject(emailConfig.getPasswordResetSubject())
                .withPlainText(passwordResetEmailText)
                .buildEmail();

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

        LOGGER.info("Sending reset email to user {} ({}) at {}:{}",
                smtpConfig.getUsername(),
                emailAddress,
                smtpConfig.getHost(),
                smtpConfig.getPort());

        try (final Mailer mailer = mailerBuilder.buildMailer()) {
            mailer.sendMail(email);
        } catch (final Exception e) {
            LOGGER.error("Error sending reset email to user {} ({}) at {}:{} - {}",
                    smtpConfig.getUsername(),
                    emailAddress,
                    smtpConfig.getHost(),
                    smtpConfig.getPort(),
                    e.getMessage(),
                    e);
        }
    }
}
