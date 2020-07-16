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

package stroom.authentication.authenticate;

import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.EmailConfig;
import stroom.authentication.config.SmtpConfig;
import stroom.config.common.UriFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.simplejavamail.email.Email;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.config.ServerConfig;
import org.simplejavamail.mailer.config.TransportStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.mail.Message;

class EmailSender {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailSender.class);

    private final ServerConfig serverConfig;
    private final TransportStrategy transportStrategy;
    private final UriFactory uriFactory;
    private final AuthenticationConfig authenticationConfig;

    @Inject
    EmailSender(final UriFactory uriFactory,
                final AuthenticationConfig authenticationConfig) {
        this.uriFactory = uriFactory;
        this.authenticationConfig = authenticationConfig;
        final SmtpConfig smtpConfig = Preconditions.checkNotNull(authenticationConfig.getEmailConfig(),
                "Missing 'email' section in config")
                .getSmtpConfig();

        if (!Strings.isNullOrEmpty(smtpConfig.getUsername()) && !Strings.isNullOrEmpty(smtpConfig.getPassword())) {
            serverConfig = new ServerConfig(
                    authenticationConfig.getEmailConfig().getSmtpConfig().getHost(),
                    authenticationConfig.getEmailConfig().getSmtpConfig().getPort(),
                    authenticationConfig.getEmailConfig().getSmtpConfig().getUsername(),
                    authenticationConfig.getEmailConfig().getSmtpConfig().getPassword());
        } else {
            serverConfig = new ServerConfig(
                    authenticationConfig.getEmailConfig().getSmtpConfig().getHost(),
                    authenticationConfig.getEmailConfig().getSmtpConfig().getPort());
        }

        transportStrategy = authenticationConfig.getEmailConfig().getSmtpConfig().getTransportStrategy();
    }

    public void send(final String emailAddress, final String firstName, final String lastName, String resetToken) {
        Preconditions.checkNotNull(authenticationConfig.getEmailConfig(), "Missing 'email' section in config");

        final EmailConfig emailConfig = authenticationConfig.getEmailConfig();
        final String resetName = firstName == null ? "[Name not available]" : firstName + "" + lastName;
        String resetUrl = String.format(emailConfig.getPasswordResetUrl(), emailAddress, resetToken);
        resetUrl = uriFactory.publicUri(resetUrl).toString();
        final String passwordResetEmailText = String.format(emailConfig.getPasswordResetText(), resetUrl);

        final Email email = new Email();
        email.setFromAddress(emailConfig.getFromName(), emailConfig.getFromAddress());
        email.setReplyToAddress(emailConfig.getFromName(), emailConfig.getFromAddress());
        email.addRecipient(resetName, emailAddress, Message.RecipientType.TO);
        email.setSubject(emailConfig.getPasswordResetSubject());
        email.setText(passwordResetEmailText);

        LOGGER.info("Sending reset email to user {} at {}:{}",
                serverConfig.getHost(), serverConfig.getPort(), serverConfig.getUsername());
        new Mailer(serverConfig, transportStrategy).sendMail(email);
    }
}
