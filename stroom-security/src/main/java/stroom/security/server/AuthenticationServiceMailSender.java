/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.server;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

import com.google.inject.Inject;

import stroom.logging.AuthenticationEventLog;
import stroom.security.shared.User;
import stroom.servlet.HttpServletRequestHolder;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AuthenticationServiceMailSender {
    @Inject
    private SimpleMailMessage resetPasswordTemplate;
    @Inject
    private MailSender mailSender;
    @Resource
    private transient HttpServletRequestHolder httpServletRequestHolder;

    private String smtpUserDomain;

    @Resource
    private AuthenticationEventLog eventLog;

    public AuthenticationServiceMailSender(
            @Value("#{propertyConfigurer.getProperty('stroom.smtpUserDomain')}") String smtpUserDomain,
            MailSender mailSender, SimpleMailMessage resetPasswordTemplate,
            HttpServletRequestHolder httpServletRequestHolder) {
        this.smtpUserDomain = smtpUserDomain;
        this.mailSender = mailSender;
        this.resetPasswordTemplate = resetPasswordTemplate;
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    public void emailPasswordReset(final User user, final String password) {
        if (canEmailPasswordReset()) {
            // Add event log data for reset password.
            eventLog.resetPassword(user.getName(), true);

            final SimpleMailMessage mailMessage = new SimpleMailMessage();
            resetPasswordTemplate.copyTo(mailMessage);
            mailMessage.setTo(user.getName() + "@" + smtpUserDomain);

            String message = mailMessage.getText();
            message = StringUtils.replace(message, "\\n", "\n");
            message = StringUtils.replace(message, "${username}", user.getName());
            message = StringUtils.replace(message, "${password}", password);
            final HttpServletRequest req = httpServletRequestHolder.get();
            message = StringUtils.replace(message, "${hostname}", req.getServerName());

            mailMessage.setText(message);

            mailSender.send(mailMessage);
        }
    }

    public boolean canEmailPasswordReset() {
        return StringUtils.isNotBlank(smtpUserDomain);
    }

    @Required
    public void setResetPasswordTemplate(final SimpleMailMessage msg) {
        resetPasswordTemplate = msg;
    }

    @Required
    public void setMailSender(final MailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Required
    public void setSmtpUserDomain(final String smtpUserDomain) {
        this.smtpUserDomain = smtpUserDomain;
    }
}
