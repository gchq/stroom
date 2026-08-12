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
import stroom.util.shared.ResourcePaths;

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

import java.util.IllegalFormatException;
import java.util.stream.Collectors;
import java.util.stream.Stream;


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

    /**
     * Only the parts of the name we have. Joining unconditionally produced "John null" for an account with
     * no last name, which is what the recipient then saw themselves addressed as.
     */
    // Package-private for testing.
    static String buildRecipientName(final String firstName, final String lastName) {
        final String name = Stream.of(firstName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
        return name.isEmpty()
                ? "[Name not available]"
                : name;
    }

    /**
     * The body is operator-supplied text, so it cannot be trusted as a format string: a stray percent sign
     * throws, and the throw happens on the executor after the user has been told their email is on its way.
     * A missing placeholder is worse than an exception, because the mail arrives with no link in it.
     */
    // Package-private for testing.
    static String buildResetText(final String template, final String resetUrl) {
        if (template == null) {
            return resetUrl;
        }
        if (!template.contains("%s")) {
            LOGGER.warn("The password reset email text has no '%s' placeholder for the reset link, so the " +
                        "link has been appended instead. Add '%s' to it to control where the link appears.");
            return template + System.lineSeparator() + resetUrl;
        }
        try {
            return String.format(template, resetUrl);
        } catch (final IllegalFormatException e) {
            LOGGER.error("The password reset email text is not a valid format string, so the link has been " +
                         "sent on its own. Percent signs in it must be written as '%%'. {}", e.getMessage(), e);
            return resetUrl;
        }
    }

    /**
     * The link a user follows to set a new password. Built from the path the reset page is actually
     * served on, rather than from configuration, so that the two cannot disagree; the sign in page URL
     * works the same way. Only the token is needed, as the account it is for is inside it.
     */
    private String buildResetUrl(final String resetToken) {
        return uriFactory.publicUri(ResourcePaths.builder()
                .addPathPart(ResourcePaths.RESET_PASSWORD_PATH)
                .addQueryParam("token", resetToken)
                .build()).toString();
    }

    public void send(final String emailAddress,
                     final String firstName,
                     final String lastName, final String resetToken) {
        final EmailConfig emailConfig = Preconditions.checkNotNull(
                authenticationConfig.getEmailConfig(), "Missing 'email' section in config");
        sendPlainText(
                emailAddress,
                buildRecipientName(firstName, lastName),
                emailConfig.getPasswordResetSubject(),
                buildResetText(emailConfig.getPasswordResetText(), buildResetUrl(resetToken)));
    }

    /**
     * Tells the account's owner that a reset was requested but refused, without saying why.
     * <p>
     * Sent instead of silence so that a user whose account cannot be reset is not left waiting for a link
     * that will never come, and does not complete a reset only to be refused at sign in. The wording is
     * deliberately one fixed sentence for every refused state: it goes to the account's own mailbox, so it
     * discloses nothing to whoever made the request, but it also volunteers nothing - for a locked account
     * the situation is usually temporary, which "currently" and "if this continues" cover honestly.
     * </p>
     */
    public void sendCannotResetEmail(final String emailAddress,
                                     final String firstName,
                                     final String lastName) {
        final EmailConfig emailConfig = Preconditions.checkNotNull(
                authenticationConfig.getEmailConfig(), "Missing 'email' section in config");
        sendPlainText(
                emailAddress,
                buildRecipientName(firstName, lastName),
                emailConfig.getPasswordResetSubject(),
                "A password reset was requested for your account, but it cannot currently be completed. "
                + "If this continues, contact your administrator.");
    }

    private void sendPlainText(final String emailAddress,
                               final String recipientName,
                               final String subject,
                               final String body) {
        final EmailConfig emailConfig = authenticationConfig.getEmailConfig();
        final SmtpConfig smtpConfig = emailConfig.getSmtpConfig();

        final Email email = EmailBuilder.startingBlank()
                .from(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withReplyTo(emailConfig.getFromName(), emailConfig.getFromAddress())
                .withRecipient(recipientName, emailAddress, RecipientType.TO)
                .withSubject(subject)
                .withPlainText(body)
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

        // The recipient, not the account the mail server authenticates with.
        LOGGER.info("Sending email to {} at {}:{}",
                emailAddress,
                smtpConfig.getHost(),
                smtpConfig.getPort());

        try (final Mailer mailer = mailerBuilder.buildMailer()) {
            mailer.sendMail(email);
        } catch (final Exception e) {
            LOGGER.error("Error sending email to {} at {}:{} - {}",
                    emailAddress,
                    smtpConfig.getHost(),
                    smtpConfig.getPort(),
                    e.getMessage(),
                    e);
        }
    }
}
