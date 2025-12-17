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

import stroom.config.common.UriFactory;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.config.TokenConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.ChangePasswordRequest;
import stroom.security.identity.shared.ChangePasswordResponse;
import stroom.security.identity.shared.ConfirmPasswordRequest;
import stroom.security.identity.shared.ConfirmPasswordResponse;
import stroom.security.identity.shared.LoginRequest;
import stroom.security.identity.shared.LoginResponse;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.util.cert.CertificateExtractor;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.servlet.SessionUtil;

import event.logging.AuthenticateOutcomeReason;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class AuthenticationServiceImpl implements AuthenticationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private static final long MIN_CREDENTIAL_CONFIRMATION_INTERVAL = 600000;
    private static final String AUTH_STATE_ATTRIBUTE_NAME = "AUTH_STATE";

    private final UriFactory uriFactory;
    private final IdentityConfig config;
    private final EmailSender emailSender;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final TokenConfig tokenConfig;
    private final CertificateExtractor certificateExtractor;

    @Inject
    public AuthenticationServiceImpl(
            final UriFactory uriFactory,
            final IdentityConfig config,
            final EmailSender emailSender,
            final AccountDao accountDao,
            final AccountService accountService,
            final OpenIdClientFactory openIdClientDetailsFactory,
            final TokenBuilderFactory tokenBuilderFactory,
            final TokenConfig tokenConfig,
            final CertificateExtractor certificateExtractor) {
        this.uriFactory = uriFactory;
        this.config = config;
        this.emailSender = emailSender;
        this.accountDao = accountDao;
        this.accountService = accountService;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.tokenConfig = tokenConfig;
        this.certificateExtractor = certificateExtractor;
    }

    private void setAuthState(final HttpServletRequest request,
                              final AuthStateImpl authStateImpl,
                              final boolean createSession) {
        SessionUtil.setAttribute(request, AUTH_STATE_ATTRIBUTE_NAME, authStateImpl, createSession);

    }

    private AuthStateImpl getAuthState(final HttpServletRequest request) {
        return SessionUtil.getAttribute(
                request,
                AUTH_STATE_ATTRIBUTE_NAME,
                AuthStateImpl.class,
                false);
    }

    @Override
    public AuthStatus currentAuthState(final HttpServletRequest request) {
        LOGGER.debug("currentAuthState");
        final AuthStateImpl authState = getAuthState(request);

        // Now we can check if we're logged in somehow (session or certs) and build the response accordingly
        if (authState != null) {
            LOGGER.debug("Has current auth state");
            return new AuthStatusImpl(authState, false);

        } else if (config.isAllowCertificateAuthentication()) {
            LOGGER.debug("Attempting login with certificate");

            final AuthStatus status = loginWithCertificate(request);
            if (status.getError().isPresent()) {
                LOGGER.error("Failed to log in with certificate, user: " + status.getError().get().getSubject()
                             + ", message: " + status.getError().get().getMessage());
            }

            return status;

        } else {
            final String message = "Certificate authentication is disabled";
            LOGGER.debug(message);
            return new AuthStatusImpl();
        }
    }

    private AuthStatus loginWithCertificate(final HttpServletRequest request) {
        LOGGER.debug("loginWithCertificate");
        final Optional<String> optionalCN = certificateExtractor.getCN(request);
        if (optionalCN.isPresent()) {
            final String cn = optionalCN.get();
            // Check for a certificate
            LOGGER.debug(() -> "Got CN: " + cn);
            final Optional<String> optionalUserId = getIdFromCertificate(cn);

            if (optionalUserId.isEmpty()) {
                return new AuthStatusImpl(new BadRequestException(cn,
                        AuthenticateOutcomeReason.INCORRECT_CA,
                        "Found CN but the identity cannot be extracted (CN = " +
                        cn +
                        ")"), true);

            } else {
                final String userId = optionalUserId.get();
                final Optional<Account> optionalAccount = accountDao.get(userId);
                if (optionalAccount.isEmpty()) {
                    // There's no user, so we can't let them have access.
                    return new AuthStatusImpl(new BadRequestException(userId,
                            AuthenticateOutcomeReason.INCORRECT_USERNAME,
                            "An account for the userId does not exist (userId = " +
                            userId +
                            ")"), true);

                } else {
                    final Account account = optionalAccount.get();
                    try {
                        final String authType = "internal IDP certificate";
                        verifyAccountStateOrThrow(account, "locked", authType, () -> !account.isLocked());
                        verifyAccountStateOrThrow(account, "inactive", authType, () -> !account.isInactive());
                        verifyAccountStateOrThrow(account, "disabled", authType, account::isEnabled);
                    } catch (final BadRequestException badRequestException) {
                        return new AuthStatusImpl(badRequestException, true);
                    }

                    LOGGER.info("Logging user in: {}", userId);
                    final AuthStateImpl newState = new AuthStateImpl(
                            account,
                            false,
                            System.currentTimeMillis());
                    setAuthState(request, newState, true);

                    // Reset last access, login failures, etc...
                    accountDao.recordSuccessfulLogin(userId);

                    return new AuthStatusImpl(newState, true);
                }
            }
        }

        return new AuthStatusImpl();
    }

    public LoginResponse handleLogin(final LoginRequest loginRequest,
                                     final HttpServletRequest request) {
        clearSession(request);

        // Check the credentials
        CredentialValidationResult result = accountDao.validateCredentials(loginRequest.getUserId(),
                loginRequest.getPassword());
        if (!result.isValidCredentials() && !result.isAccountDoesNotExist()) {
            LOGGER.debug("Password for {} is incorrect", loginRequest.getUserId());
            final boolean locked = accountDao.incrementLoginFailures(loginRequest.getUserId());
            result = new CredentialValidationResult(
                    result.isValidCredentials(),
                    result.isAccountDoesNotExist(),
                    locked,
                    result.isDisabled(),
                    result.isInactive(),
                    result.isProcessingAccount());
        }

        final String message = result.toString();

        if (result.isAllOk()) {
            final Optional<Account> optionalAccount = accountService.read(loginRequest.getUserId());
            if (optionalAccount.isPresent()) {
                return processSuccessfulLogin(request, optionalAccount.get(), message);
            }
        }

        return new LoginResponse(false, message, false);
    }

    public String logout(final HttpServletRequest request) {
        final HttpSession httpSession = request.getSession(false);
        if (httpSession != null) {
            final AuthStateImpl authState = getAuthState(request);
            if (authState == null) {
                throw new IllegalStateException("No logged in user found");
            }
            final String userId = authState.getSubject();
            clearSession(request);
            return userId;
        }

        throw new IllegalStateException("No open session found");
    }

    public ConfirmPasswordResponse confirmPassword(final HttpServletRequest request,
                                                   final ConfirmPasswordRequest confirmPasswordRequest) {
        final AuthStateImpl authState = getAuthState(request);
        if (authState == null) {
            return new ConfirmPasswordResponse(false, "No user is currently signed in");
        }
        final CredentialValidationResult result = accountDao.validateCredentials(authState.getSubject(),
                confirmPasswordRequest.getPassword());
        final String message = result.toString();
        if (result.isAllOk()) {
            // Update tha last credential confirmation time.

            final AuthStateImpl newAuthState = authState.withLastCredentialCheckMs(System.currentTimeMillis());
            setAuthState(request, newAuthState, true);
            return new ConfirmPasswordResponse(true, message);
        }

        return new ConfirmPasswordResponse(false, message);
    }

    public String getUserIdFromRequest(final HttpServletRequest request) {
        final AuthState authState = getAuthState(request);
        if (authState == null) {
            return null;
        }
        return authState.getSubject();
    }

    public ChangePasswordResponse changePassword(final HttpServletRequest request,
                                                 final ChangePasswordRequest changePasswordRequest) {
        // Record that the password no longer needs changing.
        final AuthStateImpl authState = getAuthState(request);
        if (authState == null) {
            return new ChangePasswordResponse(false, "No user is currently signed in", true);
        }
        final boolean forceSignIn = shouldForceSignIn(authState);
        if (forceSignIn) {
            return new ChangePasswordResponse(false,
                    "It has been too long since you last signed in, please sign in again",
                    true);
        }

        final String userId = authState.getSubject();
        final CredentialValidationResult result = accountDao.validateCredentials(userId,
                changePasswordRequest.getCurrentPassword());

        PasswordValidator.validateCredentials(result);
        PasswordValidator.validateReuse(changePasswordRequest.getCurrentPassword(),
                changePasswordRequest.getNewPassword());
        PasswordValidator.validateLength(changePasswordRequest.getNewPassword(),
                config.getPasswordPolicyConfig().getMinimumPasswordLength());
        PasswordValidator.validateComplexity(changePasswordRequest.getNewPassword(),
                config.getPasswordPolicyConfig().getPasswordComplexityRegex());
        PasswordValidator.validateConfirmation(changePasswordRequest.getNewPassword(),
                changePasswordRequest.getConfirmNewPassword());

        accountDao.changePassword(userId, changePasswordRequest.getNewPassword());

        if (authState.getSubject().equals(userId)) {
            final AuthStateImpl newAuthState = new AuthStateImpl(
                    authState.getAccount(),
                    false,
                    System.currentTimeMillis());
            setAuthState(request, newAuthState, true);
        }

        return new ChangePasswordResponse(true, null, false);
    }

    public boolean resetEmail(final String emailAddress) {
        final Account account = accountService.read(emailAddress).orElseThrow(() -> new RuntimeException(
                "Account not found for email: " + emailAddress));
        final String token = createResetEmailToken(account,
                openIdClientDetailsFactory.getClient().getClientId());
        emailSender.send(emailAddress, account.getFirstName(), account.getLastName(), token);
        return true;
    }

    private String createResetEmailToken(final Account account, final String clientId) {
        return tokenBuilderFactory
                .builder()
                .expirationTime(Instant.now()
                        .plus(tokenConfig.getEmailResetTokenExpiration()))
                .clientId(clientId)
                .subject(account.getUserId())
                .build();
    }

    private LoginResponse processSuccessfulLogin(final HttpServletRequest request,
                                                 final Account account,
                                                 final String message) {
        // Make sure the session is authenticated and ready for use
        clearSession(request);

        final String userId = account.getUserId();
        LOGGER.debug("Login for {} succeeded", userId);

        final boolean userNeedsToChangePassword = accountDao.needsPasswordChange(
                userId,
                config.getPasswordPolicyConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordPolicyConfig().isForcePasswordChangeOnFirstLogin());

        // Reset last access, login failures, etc...
        accountDao.recordSuccessfulLogin(userId);
        final AuthStateImpl newAuthState = new AuthStateImpl(
                account,
                userNeedsToChangePassword,
                System.currentTimeMillis());
        setAuthState(request, newAuthState, true);

        return new LoginResponse(true, message, userNeedsToChangePassword);
    }

    @Override
    public URI createSignInUri(final String redirectUri) {
        LOGGER.debug("Sending user to login.");
        UriBuilder uriBuilder = UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.SIGN_IN_URL_PATH));
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, "error", "login_required");
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, OpenId.REDIRECT_URI, redirectUri);
        return uriBuilder.build();
    }

    @Override
    public URI createErrorUri(final BadRequestException badRequestException) {
        LOGGER.debug("Sending user to login.");
        UriBuilder uriBuilder = UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.SIGN_IN_URL_PATH));
        uriBuilder = UriBuilderUtil.addParam(uriBuilder, "error", badRequestException.getMessage());
        return uriBuilder.build();
    }

    private Optional<String> getIdFromCertificate(final String cn) {
        LOGGER.debug("getIdFromCertificate");
        LOGGER.debug(() -> "CertificateCnPattern = " + this.config.getCertificateCnPattern());
        final Pattern idExtractionPattern = Pattern.compile(this.config.getCertificateCnPattern());
        final Matcher idExtractionMatcher = idExtractionPattern.matcher(cn);
        if (idExtractionMatcher.find()) {
            LOGGER.debug("Found id");
            final int captureGroupIndex = this.config.getCertificateCnCaptureGroupIndex();
            try {
                if (idExtractionMatcher.groupCount() >= captureGroupIndex) {
                    final String id = idExtractionMatcher.group(captureGroupIndex);
                    LOGGER.debug(() -> "id = " + id);
                    return Optional.of(id);
                }
            } catch (final IllegalStateException ex) {
                LOGGER.error("Unable to extract user ID from CN. CN was {} and pattern was {}", cn,
                        this.config.getCertificateCnPattern());
            }
        }

        LOGGER.debug("id not found");
        return Optional.empty();
    }

    private void clearSession(final HttpServletRequest request) {
        setAuthState(request, null, false);
    }

    public PasswordPolicyConfig getPasswordPolicy() {
        return config.getPasswordPolicyConfig();
    }

    private boolean shouldForceSignIn(final AuthStateImpl authState) {
        // If we haven't verified credentials recently then force the user to do so.
        if (authState == null) {
            return true;
        }
        if (authState.isRequirePasswordChange()) {
            return authState.getLastCredentialCheckMs()
                   < System.currentTimeMillis() - MIN_CREDENTIAL_CONFIRMATION_INTERVAL;
        }
        return false;
    }

    /**
     * @param account              The user account to check
     * @param isValidStateSupplier Should return true for a valid state.
     * @throws BadRequestException if user is in an invalid state
     */
    private void verifyAccountStateOrThrow(final Account account,
                                           final String stateType,
                                           final String authType,
                                           final BooleanSupplier isValidStateSupplier) {
        if (!isValidStateSupplier.getAsBoolean()) {
            LOGGER.warn("User account '{}' attempted {} authentication with the account in a {} state. {}",
                    account.getUserId(), authType, stateType, account);
            throw new BadRequestException(
                    account.getUserId(),
                    AuthenticateOutcomeReason.ACCOUNT_LOCKED,
                    LogUtil.message("User account '{}' is {}.", account.getUserId(), stateType));
        }
    }


    // --------------------------------------------------------------------------------


    private static class AuthStateImpl implements AuthState {

        private final Account account;
        private final boolean requirePasswordChange;
        private final long lastCredentialCheckMs;

        AuthStateImpl(final Account account,
                      final boolean requirePasswordChange,
                      final long lastCredentialCheckMs) {
            this.account = account;
            this.requirePasswordChange = requirePasswordChange;
            this.lastCredentialCheckMs = lastCredentialCheckMs;
        }

        public Account getAccount() {
            return account;
        }

        @Override
        public String getSubject() {
            return account.getUserId();
        }

        @Override
        public boolean isRequirePasswordChange() {
            return requirePasswordChange;
        }

        @Override
        public long getLastCredentialCheckMs() {
            return lastCredentialCheckMs;
        }

        public AuthStateImpl withLastCredentialCheckMs(final long lastCredentialCheckMs) {
            return new AuthStateImpl(account, requirePasswordChange, lastCredentialCheckMs);
        }
    }


    // --------------------------------------------------------------------------------


    private static class AuthStatusImpl implements AuthStatus {

        private final AuthState state;
        private final BadRequestException error;
        private final boolean isNew;

        AuthStatusImpl() {
            state = null;
            error = null;
            isNew = false;
        }

        AuthStatusImpl(final AuthState state, final boolean isNew) {
            this.state = state;
            this.isNew = isNew;
            this.error = null;
        }

        AuthStatusImpl(final BadRequestException error, final boolean isNew) {
            this.error = error;
            this.isNew = isNew;
            this.state = null;
        }

        @Override
        public Optional<AuthState> getAuthState() {
            return Optional.ofNullable(state);
        }

        @Override
        public boolean isNew() {
            return isNew;
        }

        @Override
        public Optional<BadRequestException> getError() {
            return Optional.ofNullable(error);
        }
    }
}
