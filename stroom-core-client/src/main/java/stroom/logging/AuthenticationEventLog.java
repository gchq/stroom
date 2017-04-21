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

package stroom.logging;

import event.logging.AuthenticateAction;
import event.logging.AuthenticateOutcome;
import event.logging.AuthenticateOutcomeReason;
import event.logging.Event;
import event.logging.Event.EventDetail.Authenticate;
import event.logging.User;
import org.springframework.stereotype.Component;
import stroom.util.logging.StroomLogger;

import javax.annotation.Resource;

@Component
public class AuthenticationEventLog {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(AuthenticationEventLog.class);

    @Resource
    private StroomEventLoggingService eventLoggingService;

    public void logon(final String userName) {
        logon(userName, true, null, null);
    }

    public void logon(final String userName, final boolean success, final String outcomeDescription,
            final AuthenticateOutcomeReason reason) {
        authenticationEvent("Logon", "User is logging on", AuthenticateAction.LOGON, userName, success,
                outcomeDescription, reason);
    }

    public void logoff(final String userName) {
        logoff(userName, true, null, null);
    }

    public void logoff(final String userName, final boolean success, final String outcomeDescription,
            final AuthenticateOutcomeReason reason) {
        authenticationEvent("Logoff", "User is logging off", AuthenticateAction.LOGOFF, userName, success,
                outcomeDescription, reason);
    }

    public void changePassword(final String userName) {
        changePassword(userName, true, null, null);
    }

    public void changePassword(final String userName, final boolean success, final String outcomeDescription,
            final AuthenticateOutcomeReason reason) {
        authenticationEvent("ChangePassword", "User is changing password", AuthenticateAction.CHANGE_PASSWORD, userName,
                success, outcomeDescription, reason);
    }

    public void resetPassword(final String userName, final boolean email) {
        resetPassword(userName, email, true, null, null);
    }

    public void resetPassword(final String userName, final boolean email, final boolean success,
            final String outcomeDescription, final AuthenticateOutcomeReason reason) {
        authenticationEvent("ResetPasswordEmail", "User is resetting password by email",
                AuthenticateAction.RESET_PASSWORD, userName, success, outcomeDescription, reason);
    }

    private void authenticationEvent(final String typeId, final String description, final AuthenticateAction action,
            final String userName, final boolean success, final String outcomeDescription,
            final AuthenticateOutcomeReason reason) {
        try {
            final Event event = eventLoggingService.createAction(typeId, description);

            final Authenticate authenticate = new Authenticate();
            authenticate.setAction(action);
            event.getEventDetail().setAuthenticate(authenticate);

            final User user = new User();
            user.setId(userName);
            authenticate.setUser(user);

            if (!success) {
                final AuthenticateOutcome outcome = new AuthenticateOutcome();
                outcome.setSuccess(success);
                outcome.setDescription(outcomeDescription);
                outcome.setReason(reason);

                authenticate.setOutcome(outcome);
            }

            eventLoggingService.log(event);
        } catch (final Exception e) {
            LOGGER.error(e, e);
        }
    }
}
