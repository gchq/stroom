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
import stroom.alert.client.presenter.ConfirmCallback;
import stroom.dispatch.client.AsyncCallbackAdaptor;
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
import stroom.security.shared.UserAndPermissions;
import stroom.util.shared.SharedBoolean;

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
        eventBus.addHandler(LoginEvent.getType(), new LoginEvent.LoginHandler() {
            @Override
            public void onLogin(final LoginEvent event) {
                login(event.getUserName(), event.getPassword());
            }
        });

        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), new LogoutEvent.LogoutHandler() {
            @Override
            public void onLogout(final LogoutEvent event) {
                logout();
            }
        });

        // Listen for email reset password events.
        eventBus.addHandler(EmailResetPasswordEvent.getType(), new EmailResetPasswordEvent.EmailResetPasswordHandler() {
            @Override
            public void onResetPassword(final EmailResetPasswordEvent event) {
                emailResetPassword(event.getUserName());
            }
        });

    }

    public void autoLogin() {
        // When we start the application we will try and auto login using a client certificates.
        dispatcher.execute(new AutoLoginAction(), "Logging on. Please wait...",
                new AsyncCallbackAdaptor<UserAndPermissions>() {
                    @Override
                    public void onSuccess(final UserAndPermissions userAndPermissions) {
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
                    }

                    @Override
                    public void onFailure(final Throwable caught) {
                        LoginFailedEvent.fire(LoginManager.this, caught.getMessage());
                    }

                    @Override
                    public boolean handlesFailure() {
                        return true;
                    }
                });
    }

    private void login(final String userName, final String password) {
        dispatcher.execute(new LoginAction(userName, password), "Logging on. Please wait...",
                new AsyncCallbackAdaptor<UserAndPermissions>() {
                    @Override
                    public void onSuccess(final UserAndPermissions userAndPermissions) {
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
                                            new ConfirmCallback() {
                                                @Override
                                                public void onResult(final boolean result) {
                                                    if (result) {
                                                        ChangePasswordEvent.fire(LoginManager.this, user, true);
                                                    }
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
                                    ConfirmEvent.fire(LoginManager.this, message.toString(), new ConfirmCallback() {
                                        @Override
                                        public void onResult(final boolean result) {
                                            if (result) {
                                                ChangePasswordEvent.fire(LoginManager.this, user, true);
                                            } else {
                                                currentUser.setUserAndPermissions(userAndPermissions);
                                            }
                                        }
                                    });

                                } else {
                                    currentUser.setUserAndPermissions(userAndPermissions);
                                }
                            } else {
                                currentUser.setUserAndPermissions(userAndPermissions);
                            }
                        }
                    }

                    @Override
                    public void onFailure(final Throwable caught) {
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
                    }
                });
    }

    private void logout() {
        // Clear everything we know about the current user.
        currentUser.clear();
        // When we start the application we will try and auto login using a client certificate.
        dispatcher.execute(new LogoutAction(), null);
    }

    private void emailResetPassword(final String userName) {
        if (userName.length() == 0) {
            AlertEvent.fireWarn(this, "No user name entered!", null);
        } else {
            dispatcher.execute(new CanEmailPasswordResetAction(), new AsyncCallbackAdaptor<SharedBoolean>() {
                @Override
                public void onSuccess(final SharedBoolean ok) {
                    if (Boolean.TRUE.equals(ok.getBoolean())) {
                        ConfirmEvent.fire(LoginManager.this,
                                "Are you sure you want to reset the password for " + userName + "?",
                                new ConfirmCallback() {
                                    @Override
                                    public void onResult(final boolean result) {
                                        if (result) {
                                            dispatcher.execute(new EmailPasswordResetForUserNameAction(userName),
                                                    new AsyncCallbackAdaptor<SharedBoolean>() {
                                                        @Override
                                                        public void onSuccess(final SharedBoolean Ok) {
                                                        }
                                                    });
                                        }
                                    }
                                });
                    } else {
                        AlertEvent.fireError(LoginManager.this, "System is not configured to send emails", null);
                    }
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
