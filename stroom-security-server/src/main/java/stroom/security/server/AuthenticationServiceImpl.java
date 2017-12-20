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

import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.stereotype.Component;
import stroom.logging.AuthenticationEventLog;
import stroom.security.Insecure;
import stroom.security.Secured;
import stroom.security.shared.FindUserCriteria;
import stroom.servlet.HttpServletRequestHolder;

import javax.inject.Inject;

@Component
@Secured(FindUserCriteria.MANAGE_USERS_PERMISSION)
public class AuthenticationServiceImpl implements AuthenticationService {
    private final transient HttpServletRequestHolder httpServletRequestHolder;
    private final AuthenticationEventLog eventLog;


    @Inject
    AuthenticationServiceImpl(
            final HttpServletRequestHolder httpServletRequestHolder,
            final AuthenticationEventLog eventLog) {
        this.httpServletRequestHolder = httpServletRequestHolder;
        this.eventLog = eventLog;
    }

    @Override
    @Insecure
    public String logout() {
        // We don't need to call the authentication service to log out - the login manager will
        // redirect the user's browser to the authentication service's logout endpoint.

        String user = null;

        // Remove the user authentication object
        final Subject subject = SecurityUtils.getSubject();
        if (subject != null && subject.getPrincipal() != null) {
            user = subject.getPrincipal().toString();
            subject.logout();
        }

        // Invalidate the current user session
        httpServletRequestHolder.get().getSession().invalidate();

        if (user != null) {
            // Create an event for logout
            eventLog.logoff(user);
        }

        return user;
    }
}
