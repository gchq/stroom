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

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.exceptions.NoSuchUserException;
import stroom.security.identity.shared.AuthenticationResource;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.ChangePasswordResponse;
import stroom.security.identity.shared.ConfirmPasswordRequest;
import stroom.security.identity.shared.ConfirmPasswordResponse;
import stroom.security.identity.shared.InternalIdpPasswordPolicyConfig;
import stroom.security.identity.shared.LoginRequest;
import stroom.security.identity.shared.LoginResponse;
import stroom.security.identity.shared.ResetPasswordRequest;
import stroom.util.servlet.HttpServletRequestHolder;
import stroom.util.shared.Unauthenticated;

import com.codahale.metrics.annotation.Timed;
import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateLogonType;
import event.logging.AuthenticateOutcome;
import event.logging.AuthenticateOutcomeReason;
import event.logging.Data;
import event.logging.Event;
import event.logging.EventSource;
import event.logging.User;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.RedirectionException;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

@Singleton
@AutoLogged(OperationType.MANUALLY_LOGGED)
class AuthenticationResourceImpl implements AuthenticationResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationResourceImpl.class);

    private final Provider<AuthenticationServiceImpl> serviceProvider;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider;

    @Inject
    AuthenticationResourceImpl(final Provider<AuthenticationServiceImpl> serviceProvider,
                               final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
                               final Provider<HttpServletRequestHolder> httpServletRequestHolderProvider) {
        this.serviceProvider = serviceProvider;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.httpServletRequestHolderProvider = httpServletRequestHolderProvider;
    }

    @Unauthenticated
    @Timed
    @Override
    public LoginResponse login(final LoginRequest loginRequest) {
        LOGGER.debug("Received a login request");
        if (loginRequest != null) {
            final AuthenticateEventAction.Builder<Void> eventBuilder = AuthenticateEventAction.builder()
                    .withLogonType(AuthenticateLogonType.INTERACTIVE)
                    .withAction(AuthenticateAction.LOGON)
                    .withAuthenticationEntity(User.builder()
                            .withId(loginRequest.getUserId())
                            .build());

            try {
                final HttpServletRequest request = httpServletRequestHolderProvider.get().get();
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
            } catch (final Throwable e) {
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
    @Unauthenticated
    @Timed
    @Override
    public Boolean logout(final String postLogoutRedirectUri) {
        LOGGER.debug("Received a logout request");
        final HttpServletRequest request = httpServletRequestHolderProvider.get().get();
        final AuthenticationServiceImpl service = serviceProvider.get();

        // CSRF: a cross-site page must not be able to force a logout, so reject cross-origin requests.
        if (!service.isSameOrigin(request)) {
            LOGGER.warn("Rejecting cross-origin logout request");
            throw new ForbiddenException("Cross-origin logout request rejected");
        }

        // Validate the post-logout redirect against this origin so the endpoint cannot be used as an open
        // redirect; anything absent or off-origin falls back to the application root.
        final URI redirectUri = service.getValidatedPostLogoutRedirectUri(postLogoutRedirectUri);

        final AuthenticateEventAction.Builder<Void> eventBuilder = AuthenticateEventAction.builder()
                .withLogonType(AuthenticateLogonType.INTERACTIVE)
                .withAction(AuthenticateAction.LOGOFF);

        try {
            final String userId = service.logout(request);
            eventBuilder.withUser(User.builder().withId(userId).build())
                    .withAuthenticationEntity(User.builder()
                            .withId(userId)
                            .build());

        } catch (final Throwable e) {
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
            throw new RedirectionException(Status.TEMPORARY_REDIRECT, redirectUri);
        } finally {
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationResourceImpl.Logout",
                    "Stroom user logout",
                    eventBuilder.build());
        }
    }

    @Unauthenticated
    @Override
    public ConfirmPasswordResponse confirmPassword(final ConfirmPasswordRequest confirmPasswordRequest) {
        final HttpServletRequest request = httpServletRequestHolderProvider.get().get();
        final AuthenticationServiceImpl service = serviceProvider.get();
        return service.confirmPassword(request, confirmPasswordRequest);
    }

    @Unauthenticated
    @Override
    public ChangePasswordResponse changePassword(final ChangePasswordRequest changePasswordRequest) {
        final HttpServletRequest request = httpServletRequestHolderProvider.get().get();
        final AuthenticationServiceImpl service = serviceProvider.get();
        final String userId = service.getUserIdFromRequest(request);
        final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                .withUser(event.logging.User.builder().withId(userId).build())
                .withAuthenticationEntity(event.logging.User.builder()
                        .withId(changePasswordRequest.getUserId())
                        .build())
                .withAction(AuthenticateAction.CHANGE_PASSWORD);

        try {
            final ChangePasswordResponse response =
                    service.changePassword(request, changePasswordRequest);

            if (!response.isChangeSucceeded()) {
                eventBuilder.withOutcome(AuthenticateOutcome.builder()
                        .withSuccess(false)
                        .withReason(AuthenticateOutcomeReason.INCORRECT_USERNAME_OR_PASSWORD)
                        .withDescription(response.getMessage())
                        .build());
            }
            return response;

        } catch (final Throwable e) {
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
            // This endpoint only ever changes the signed-in user's own password; the request body user id
            // is recorded on the event but is not authoritative, so the description must not imply it can
            // target another account.
            final String description = userId == null
                    ? "An unauthenticated user attempted to change a password"
                    : "User is changing their own password";
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

    @Unauthenticated
    @Timed
    @Override
    public Boolean resetEmail(final String emailAddress) {
        final AuthenticateEventAction.Builder<Void> eventBuilder = event.logging.AuthenticateEventAction.builder()
                .withAuthenticationEntity(event.logging.User.builder()
                        .withEmailAddress(emailAddress)
                        .build())
                .withAction(AuthenticateAction.RESET_PASSWORD);

        try {
            // Always reports success, even for an unknown email address, so that the response does not
            // say which email addresses have accounts. See resetEmail for the timing side channel that
            // this does not close.
            return serviceProvider.get().resetEmail(emailAddress);
        } catch (final Throwable e) {
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

    @Unauthenticated
    @Timed
    @Override
    public ChangePasswordResponse resetPassword(final ResetPasswordRequest resetPasswordRequest) {
        // The user this is for is inside the signed token, so nothing useful can be logged about them
        // until the service has verified it. The service logs the reset itself once it knows who it is
        // for; this event records the attempt.
        final AuthenticateEventAction.Builder<Void> eventBuilder = AuthenticateEventAction.builder()
                .withAction(AuthenticateAction.RESET_PASSWORD);

        try {
            final ChangePasswordResponse response = serviceProvider.get()
                    .resetPasswordUsingToken(resetPasswordRequest);

            if (!response.isChangeSucceeded()) {
                eventBuilder.withOutcome(AuthenticateOutcome.builder()
                        .withSuccess(false)
                        .withReason(AuthenticateOutcomeReason.OTHER)
                        .withDescription(response.getMessage())
                        .build());
            }
            return response;

        } catch (final Throwable e) {
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
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationResourceImpl.resetPassword",
                    "A user attempted to set a new password using an emailed password reset token.",
                    eventBuilder.build());
        }
    }

    @Unauthenticated
    @Override
    public InternalIdpPasswordPolicyConfig fetchPasswordPolicy() {
        final PasswordPolicyConfig passwordPolicyConfig = serviceProvider.get().getPasswordPolicy();
        return new InternalIdpPasswordPolicyConfig(
                passwordPolicyConfig.isAllowPasswordResets(),
                passwordPolicyConfig.getMinimumPasswordStrength(),
                passwordPolicyConfig.getMinimumPasswordLength(),
                passwordPolicyConfig.getPasswordPolicyMessage());
    }
}
