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

package stroom.login.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.security.client.CurrentUser;
import stroom.security.client.event.ChangePasswordEvent;
import stroom.security.client.event.EmailResetPasswordEvent;
import stroom.security.client.event.LoginEvent;
import stroom.security.client.event.LoginFailedEvent;
import stroom.security.client.event.LogoutEvent;
import stroom.security.shared.AutoLoginAction;
import stroom.security.shared.CanEmailPasswordResetAction;
import stroom.security.shared.EmailPasswordResetForUserNameAction;
import stroom.security.shared.LoginAction;
import stroom.security.shared.LogoutAction;
import stroom.security.shared.User;

public class LoginManager implements HasHandlers {
    private final EventBus eventBus;
    private final CurrentUser currentUser;
    private final ClientDispatchAsync dispatcher;

    @Inject
    public LoginManager(final EventBus eventBus, final CurrentUser currentUser, final ClientDispatchAsync dispatcher) {
        this.eventBus = eventBus;
        this.currentUser = currentUser;
        this.dispatcher = dispatcher;

        // Listen for login events.
        eventBus.addHandler(LoginEvent.getType(), event -> login(event.getUserName(), event.getPassword()));

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> logout());

        // Listen for email reset password events.
        eventBus.addHandler(EmailResetPasswordEvent.getType(), event -> emailResetPassword(event.getUserName()));

    }

    public void autoLogin() {
        // When we start the application we will try and auto login using a client certificates.
        dispatcher.exec(new AutoLoginAction(), "Logging on. Please wait...").onSuccess(userAndPermissions -> {
            if (userAndPermissions != null) {
                currentUser.setUserAndPermissions(userAndPermissions);
            } else if (!GWT.isProdMode()) {
                // If we are in development mode and failed to login
                // with a client certificates then try the default user name and
                // password.
                LoginEvent.fire(LoginManager.this, "admin", "admin");
            } else {
                LoginFailedEvent.fire(LoginManager.this, null);
            }
        }).onFailure(caught -> LoginFailedEvent.fire(LoginManager.this, caught.getMessage()));
    }

    private void login(final String userName, final String password) {
        dispatcher.exec(new LoginAction(userName, password), "Logging on. Please wait...").onSuccess(userAndPermissions -> {
            if (userAndPermissions == null || userAndPermissions.getUser() == null) {
                LoginFailedEvent.fire(LoginManager.this, "Incorrect user name or password!");

            } else {
                final User user = userAndPermissions.getUser();

                // Some accounts never expire (e.g. in dev mode)
                if (user.getPasswordExpiryMs() != null) {
                    final int daysToExpiry = getDaysToExpiry(user.getPasswordExpiryMs());

                    if (daysToExpiry < 1) {
                        ConfirmEvent.fire(LoginManager.this,
                                "Your password has expired.  You must change it now",
                                result -> {
                                    if (result) {
                                        ChangePasswordEvent.fire(LoginManager.this, user, true);
                                    }
                                });

                    } else if (daysToExpiry < 10) {
                        final StringBuilder message = new StringBuilder();
                        message.append("The password for this account will expire in ");
                        message.append(daysToExpiry);
                        if (daysToExpiry == 1) {
                            message.append(" day");
                        } else {
                            message.append(" days");
                        }
                        message.append(". Would you like to change the password now?");
                        ConfirmEvent.fire(LoginManager.this, message.toString(), result -> {
                            if (result) {
                                ChangePasswordEvent.fire(LoginManager.this, user, true);
                            } else {
                                currentUser.setUserAndPermissions(userAndPermissions);
                            }
                        });

                    } else {
                        currentUser.setUserAndPermissions(userAndPermissions);
                    }
                } else {
                    currentUser.setUserAndPermissions(userAndPermissions);
                }
            }
        }).onFailure(caught -> {
//                            // TODO: Sort out what happens when a password
//                            // expires.
//                            // message
//                            // .append(
//                            // " Would you like to change the password now?"
//                            // );
//                            //
//                            // final boolean change =
//                            // ConfirmEvent.fire(this, message.toString());
//                            // if (change) {
//                            // changePassword(user);
//                            // }
            LoginFailedEvent.fire(LoginManager.this, caught.getMessage());
        });
    }

    private void logout() {
        // Clear everything we know about the current user.
        currentUser.clear();
        // When we start the application we will try and auto login using a client certificate.
        dispatcher.exec(new LogoutAction(), null);
    }

    private void emailResetPassword(final String userName) {
        if (userName.length() == 0) {
            AlertEvent.fireWarn(this, "No user name entered!", null);
        } else {
            dispatcher.exec(new CanEmailPasswordResetAction()).onSuccess(ok -> {
                if (Boolean.TRUE.equals(ok.getBoolean())) {
                    ConfirmEvent.fire(LoginManager.this,
                            "Are you sure you want to reset the password for " + userName + "?",
                            result -> {
                                if (result) {
                                    dispatcher.exec(new EmailPasswordResetForUserNameAction(userName));
                                }
                            });
                } else {
                    AlertEvent.fireError(LoginManager.this, "System is not configured to send emails", null);
                }
            });
        }
    }

    private int getDaysToExpiry(final Long expiry) {
        final long milliseconds = expiry - System.currentTimeMillis();
        final int days = (int) (milliseconds / 1000 / 60 / 60 / 24);
        return days;
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
