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

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;
import stroom.logging.AuthenticationEventLog;
import stroom.security.shared.User;
import stroom.servlet.HttpServletRequestHolder;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AuthenticationServiceMailSender {
    private final MailSender mailSender;
    private final String userDomain;
    private final SimpleMailMessage resetPasswordTemplate;
    private final AuthenticationEventLog eventLog;
    private final transient HttpServletRequestHolder httpServletRequestHolder;

    @Inject
    public AuthenticationServiceMailSender(final MailSender mailSender,
                                           @Value("#{propertyConfigurer.getProperty('stroom.mail.userDomain')}") final String userDomain,
                                           final SimpleMailMessage resetPasswordTemplate,
                                           final AuthenticationEventLog eventLog,
                                           final HttpServletRequestHolder httpServletRequestHolder) {
        this.mailSender = mailSender;
        this.userDomain = userDomain;
        this.resetPasswordTemplate = resetPasswordTemplate;
        this.eventLog = eventLog;
        this.httpServletRequestHolder = httpServletRequestHolder;
    }

    public void emailPasswordReset(final User user, final String password) {
        if (canEmailPasswordReset()) {
            // Add event log data for reset password.
            eventLog.resetPassword(user.getName(), true);

            final SimpleMailMessage mailMessage = new SimpleMailMessage();
            resetPasswordTemplate.copyTo(mailMessage);
            mailMessage.setTo(user.getName() + "@" + userDomain);

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
        return StringUtils.isNotBlank(userDomain);
    }
}
