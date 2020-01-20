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

package stroom.auth;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.simplejavamail.email.Email;
import org.simplejavamail.mailer.Mailer;
import org.simplejavamail.mailer.config.ServerConfig;
import org.simplejavamail.mailer.config.TransportStrategy;
import stroom.auth.config.Config;
import stroom.auth.config.EmailConfig;
import stroom.auth.config.SmtpConfig;
import stroom.auth.resources.user.v1.User;
import stroom.auth.util.logging.LambdaLogger;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.mail.Message;

@Singleton
public class EmailSender {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EmailSender.class);
    private Config config;
    private final ServerConfig serverConfig;
    private final TransportStrategy transportStrategy;

    @Inject
    public EmailSender(Config config) {
        this.config = config;
        final SmtpConfig smtpConfig = Preconditions.checkNotNull(config.getEmailConfig(),
                "Missing 'email' section in config")
                .getSmtpConfig();

        if (!Strings.isNullOrEmpty(smtpConfig.getUsername()) && !Strings.isNullOrEmpty(smtpConfig.getPassword())) {
            LOGGER.info("Sending reset email using username and password");
            serverConfig = new ServerConfig(
                    config.getEmailConfig().getSmtpConfig().getHost(),
                    config.getEmailConfig().getSmtpConfig().getPort(),
                    config.getEmailConfig().getSmtpConfig().getUsername(),
                    config.getEmailConfig().getSmtpConfig().getPassword());
        } else {
            serverConfig = new ServerConfig(
                    config.getEmailConfig().getSmtpConfig().getHost(),
                    config.getEmailConfig().getSmtpConfig().getPort());
        }

        transportStrategy = config.getEmailConfig().getSmtpConfig().getTransportStrategy();
    }

    public void send(User user, String resetToken) {
        Preconditions.checkNotNull(user, "No User object supplied");
        Preconditions.checkNotNull(user.getEmail(), LambdaLogger.buildMessage("User {} has no email address", user));
        Preconditions.checkNotNull(config.getEmailConfig(), "Missing 'email' section in config");

        final EmailConfig emailConfig = config.getEmailConfig();
        final String resetName = user.getFirstName() == null ? "[Name not available]" : user.getFirstName() + "" + user.getLastName();
        final String resetUrl = String.format(emailConfig.getPasswordResetUrl(), user.getEmail(), resetToken);
        final String passwordResetEmailText = String.format(emailConfig.getPasswordResetText(), resetUrl);

        final Email email = new Email();
        email.setFromAddress(emailConfig.getFromName(), emailConfig.getFromAddress());
        email.setReplyToAddress(emailConfig.getFromName(), emailConfig.getFromAddress());
        email.addRecipient(resetName, user.getEmail(), Message.RecipientType.TO);
        email.setSubject(emailConfig.getPasswordResetSubject());
        email.setText(passwordResetEmailText);

        new Mailer(serverConfig, transportStrategy).sendMail(email);
    }
}
