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

package stroom.security.identity.authenticate;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.openid.api.OpenId;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.net.UrlUtils;
import stroom.util.shared.PermissionException;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Strings;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateLogonType;
import event.logging.AuthenticateOutcome;
import event.logging.AuthenticateOutcomeReason;
import event.logging.Data;
import event.logging.Event;
import event.logging.EventSource;
import event.logging.Outcome;
import event.logging.User;
import event.logging.ViewEventAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.RedirectionException;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriBuilder;

@Singleton
@AutoLogged(OperationType.MANUALLY_LOGGED)
class AuthenticationResourceImpl implements AuthenticationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResourceImpl.class);

    private final Provider<AuthenticationServiceImpl> serviceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;

    @Inject
    AuthenticationResourceImpl(final Provider<AuthenticationServiceImpl> serviceProvider,
                               final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider) {
        this.serviceProvider = serviceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
    }

    @Timed
    @Override
    public AuthenticationState getAuthenticationState(final HttpServletRequest request) {
        return serviceProvider.get().getAuthenticationState(request);
    }

    @Timed
    @Override
    public LoginResponse login(final HttpServletRequest request,
                               final LoginRequest loginRequest) {
        LOGGER.debug("Received a login request");
        if (loginRequest != null) {
            final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                    .withLogonType(AuthenticateLogonType.INTERACTIVE)
                    .withAction(AuthenticateAction.LOGON)
                    .withAuthenticationEntity(event.logging.User.builder()
                            .withId(loginRequest.getUserId())
                            .build());

            try {
                final LoginResponse response = serviceProvider.get().handleLogin(loginRequest, request);
                if (response != null && !response.isLoginSuccessful()) {
                    eventBuilder.withOutcome(AuthenticateOutcome.builder()
                            .withSuccess(false)
                            .withReason(AuthenticateOutcomeReason.INCORRECT_USERNAME_OR_PASSWORD)
                            .build());
                }
                stroomEventLoggingServiceProvider.get().log(
                        "AuthenticationResourceImpl.LoginInteractive",
                        "Stroom user login",
                        eventBuilder.build());
                return response;
            } catch (Throwable e) {
                eventBuilder.withOutcome(AuthenticateOutcome.builder()
                        .withSuccess(false)
                        .withReason(AuthenticateOutcomeReason.OTHER)
                        .withDescription(e.getMessage())
                        .withData(Data.builder()
                                .withName("Error")
                                .withValue(e.getMessage())
                                .build())
                        .build());
                stroomEventLoggingServiceProvider.get().log(
                        "AuthenticationResourceImpl.LoginInteractive",
                        "Stroom user login",
                        eventBuilder.build());
                return new LoginResponse(false, e.getMessage(), false);
            }
        } else {
            throw new NoSuchUserException("loginRequest cannot be null");
        }
    }

    /**
     * Called as part of logout flow when using internal identity provider.
     */
    @Timed
    @Override
    public Boolean logout(final HttpServletRequest request,
                          final String postLogoutRedirectUri,
                          final String state) {
        LOGGER.debug("Received a logout request");
        final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                .withLogonType(AuthenticateLogonType.INTERACTIVE)
                .withAction(AuthenticateAction.LOGOFF);

        try {
            final String userId = serviceProvider.get().logout(request);
            eventBuilder.withUser(event.logging.User.builder().withId(userId).build())
                    .withAuthenticationEntity(event.logging.User.builder()
                            .withId(userId)
                            .build());

        } catch (Throwable e) {
            eventBuilder.withOutcome(AuthenticateOutcome.builder()
                    .withSuccess(false)
                    .withReason(AuthenticateOutcomeReason.OTHER)
                    .withDescription(e.getMessage())
                    .withData(Data.builder()
                            .withName("Error")
                            .withValue(e.getMessage())
                            .build())
                    .build());
        }

        try {
            UriBuilder uriBuilder = UriBuilder.fromUri(postLogoutRedirectUri);
            uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.STATE, state);
            throw new RedirectionException(Status.TEMPORARY_REDIRECT, uriBuilder.build());
        } finally {
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationResourceImpl.Logout",
                    "Stroom user logout",
                    eventBuilder.build());
        }
    }

    @Timed
    @Override
    @AutoLogged(OperationType.UNLOGGED)
    public ConfirmPasswordResponse confirmPassword(final HttpServletRequest request,
                                                   final ConfirmPasswordRequest confirmPasswordRequest) {
        return serviceProvider.get().confirmPassword(request, confirmPasswordRequest);
    }

    @Timed
    @Override
    public final ChangePasswordResponse changePassword(final HttpServletRequest request,
                                                       final ChangePasswordRequest changePasswordRequest) {
        final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                .withUser(event.logging.User.builder().withId(getUserId(request)).build())
                .withAuthenticationEntity(event.logging.User.builder()
                        .withId(changePasswordRequest.getUserId())
                        .build())
                .withAction(AuthenticateAction.CHANGE_PASSWORD);
        final String userId = getUserId(request);

        try {
            final ChangePasswordResponse response =
                    serviceProvider.get().changePassword(request, changePasswordRequest);

            if (!response.isChangeSucceeded()) {
                eventBuilder.withOutcome(AuthenticateOutcome.builder()
                        .withSuccess(false)
                        .withReason(AuthenticateOutcomeReason.INCORRECT_USERNAME_OR_PASSWORD)
                        .withDescription(response.getMessage())
                        .build());
            }
            return response;

        } catch (Throwable e) {
            eventBuilder.withOutcome(AuthenticateOutcome.builder()
                    .withSuccess(false)
                    .withReason(AuthenticateOutcomeReason.OTHER)
                    .withDescription(e.getMessage())
                    .withData(Data.builder()
                            .withName("Error")
                            .withValue(e.getMessage())
                            .build())
                    .build());
            throw e;
        } finally {
            final String description;
            if (userId == null) {
                description = "An unauthenticated user is changing a user's password";
            } else if (userId.equals(changePasswordRequest.getUserId())) {
                description = "User is changing their own password";
            } else {
                description = "User is changing another user's password";
            }
            //Need to set the user id explictly as this method runs as INTERNAL_PROCESSING_USER
            final Event event = stroomEventLoggingServiceProvider.get().createEvent(
                    "AuthenticationResourceImpl.ChangePassword",
                    description,
                    eventBuilder.build());

            stroomEventLoggingServiceProvider.get().log(
                    userId == null
                            ? event
                            //Unauthenticated case
                            : Event.builder().copyOf(event)
                                    .withEventSource(EventSource.builder().copyOf(event.getEventSource())
                                            .withUser(User.builder().withId(userId).build())
                                            .build())
                                    .build()
            );
        }
    }

    private String getUserId(final HttpServletRequest request) {
        return serviceProvider.get().getUserIdFromRequest(request);
    }

    @Timed
    @Override
    public Boolean resetEmail(final HttpServletRequest request, final String emailAddress) throws NoSuchUserException {

        final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                .withAuthenticationEntity(event.logging.User.builder()
                        .withEmailAddress(emailAddress)
                        .build())
                .withAction(AuthenticateAction.RESET_PASSWORD);

        try {
            final boolean resetEmailSent = serviceProvider.get().resetEmail(emailAddress);
            if (resetEmailSent) {
                return true;
            }
            throw new NotFoundException("User does not exist");
        } catch (Throwable e) {
            eventBuilder.withOutcome(AuthenticateOutcome.builder()
                    .withSuccess(false)
                    .withPermitted(false)
                    .withReason(AuthenticateOutcomeReason.OTHER)
                    .withData(Data.builder()
                            .withName("Error")
                            .withValue(e.getMessage())
                            .build())
                    .withDescription(e.getMessage())
                    .build());
            throw e;
        } finally {
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationResourceImpl.resetEmail",
                    "User requested a password email to be sent.",
                    eventBuilder.build());
        }
    }

    @Timed
    @Override
    public final ChangePasswordResponse resetPassword(final HttpServletRequest request,
                                                      final ResetPasswordRequest resetPasswordRequest) {
        final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                .withUser(event.logging.User.builder().withId(getUserId(request)).build())
                .withAuthenticationEntity(event.logging.User.builder()
                        .withId(getUserId(request))
                        .build())
                .withAction(AuthenticateAction.RESET_PASSWORD);

        try {
            final ChangePasswordResponse changePasswordResponse = serviceProvider.get().resetPassword(request,
                    resetPasswordRequest);
            if (changePasswordResponse != null) {
                return changePasswordResponse;
            }
            throw new NotAuthorizedException("Not authorised");
        } catch (Throwable e) {
            eventBuilder.withOutcome(AuthenticateOutcome.builder()
                    .withSuccess(false)
                    .withPermitted(false)
                    .withReason(AuthenticateOutcomeReason.OTHER)
                    .withData(Data.builder()
                            .withName("Error")
                            .withValue(e.getMessage())
                            .build())
                    .withDescription(e.getMessage())
                    .build());
            throw e;
        } finally {
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationResourceImpl.resetPassword",
                    "User password reset/change",
                    eventBuilder.build());
        }
    }

    @Timed
    @Override
    public final Boolean needsPasswordChange(final String email) {
        ViewEventAction.Builder<Void> eventBuilder = event.logging.ViewEventAction.builder()
                .withObjects(event.logging.User.builder().withEmailAddress(email).build());
        try {
            return serviceProvider.get().needsPasswordChange(email);

        } catch (Throwable e) {
            Outcome.Builder<Void> outcomeBuilder = Outcome.builder()
                    .withSuccess(false)
                    .withPermitted(false)
                    .withDescription(e.getMessage());
            if (e instanceof PermissionException) {
                outcomeBuilder.withPermitted(false);
            }
            eventBuilder.withOutcome(outcomeBuilder.build());
            throw e;
        } finally {
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationResourceImpl.needsPasswordChange",
                    "Check whether password needs to be changed",
                    eventBuilder.build());
        }
    }

    @Timed
    @Override
    @AutoLogged(OperationType.VIEW)
    public PasswordPolicyConfig fetchPasswordPolicy() {
        return serviceProvider.get().getPasswordPolicy();
    }
}
