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
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.UserSessionEvictor;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.account.ResetToken;
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
import stroom.security.identity.shared.ResetPasswordRequest;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.cert.CertificateExtractor;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.net.UrlUtils;
import stroom.util.servlet.SessionUtil;

import event.logging.AuthenticateAction;
import event.logging.AuthenticateEventAction;
import event.logging.AuthenticateOutcomeReason;
import event.logging.MultiObject;
import event.logging.UpdateEventAction;
import event.logging.User;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.UriBuilder;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class AuthenticationServiceImpl implements AuthenticationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private static final long MIN_CREDENTIAL_CONFIRMATION_INTERVAL = 600000;
    private static final String INVALID_RESET_TOKEN_MESSAGE =
            "This password reset link is invalid or has expired. Please request a new one.";
    /**
     * Deliberately says nothing about which state the account is in. Anyone can ask for a reset link, so
     * telling the caller whether an account is disabled, locked or a processing account would hand out
     * information about accounts they may not own. The reason is logged for administrators instead.
     */
    private static final String CANNOT_RESET_MESSAGE =
            "The password for this account cannot be reset. Please contact your administrator.";
    private static final String AUTH_STATE_ATTRIBUTE_NAME = "AUTH_STATE";
    private static final ThreadPool EMAIL_THREAD_POOL = new ThreadPoolImpl("Password Reset Email");

    private final UriFactory uriFactory;
    private final IdentityConfig config;
    private final EmailSender emailSender;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private final Provider<OpenIdConfiguration> openIdConfigurationProvider;
    private final TokenConfig tokenConfig;
    private final CertificateExtractor certificateExtractor;
    private final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider;
    private final Executor emailExecutor;
    private final UserSessionEvictor userSessionEvictor;

    @Inject
    public AuthenticationServiceImpl(
            final UriFactory uriFactory,
            final IdentityConfig config,
            final EmailSender emailSender,
            final AccountDao accountDao,
            final AccountService accountService,
            final Provider<OpenIdConfiguration> openIdConfigurationProvider,
            final TokenConfig tokenConfig,
            final CertificateExtractor certificateExtractor,
            final Provider<StroomEventLoggingService> stroomEventLoggingServiceProvider,
            final ExecutorProvider executorProvider,
            final UserSessionEvictor userSessionEvictor) {
        this.uriFactory = uriFactory;
        this.config = config;
        this.emailSender = emailSender;
        this.accountDao = accountDao;
        this.accountService = accountService;
        this.openIdConfigurationProvider = openIdConfigurationProvider;
        this.tokenConfig = tokenConfig;
        this.certificateExtractor = certificateExtractor;
        this.stroomEventLoggingServiceProvider = stroomEventLoggingServiceProvider;
        this.emailExecutor = executorProvider.get(EMAIL_THREAD_POOL);
        this.userSessionEvictor = userSessionEvictor;
    }

    /**
     * Local accounts, and therefore password resets, exist only when stroom is its own identity provider.
     * With an external IDP, password management belongs to that IDP.
     */
    private boolean isInternalIdp() {
        return IdpType.INTERNAL_IDP.equals(openIdConfigurationProvider.get().getIdentityProviderType());
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
                    // Rotate the session id now the request has gained privilege.
                    SessionUtil.changeSessionId(request);
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
        if (!result.isValidCredentials() && !result.isAccountDoesNotExist() && !result.isLocked()) {
            // Only count a failure while the account is not already locked. This avoids a pointless write
            // per attempt against a locked account and stops continued guessing from extending a lock
            // that will otherwise clear itself when its window passes.
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

        if (shouldReactivateAccount(result)) {
            LOGGER.info("Reactivating inactive account {} following a successful authentication",
                    loginRequest.getUserId());
            accountDao.reactivateAccount(loginRequest.getUserId());
            logAccountReactivation(loginRequest.getUserId());
            result = new CredentialValidationResult(
                    result.isValidCredentials(),
                    result.isAccountDoesNotExist(),
                    result.isLocked(),
                    result.isDisabled(),
                    false,
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

    /**
     * Reactivation changes the state of an account so it must appear in the event log in the same way
     * as an administrator making the same change via {@code AccountResourceImpl.update}. The sign in
     * event that is logged separately does not record that account state was altered.
     */
    private void logAccountReactivation(final String userId) {
        // We only ever get here for an account that is enabled, unlocked and inactive, so the before
        // and after states are known without having to read the account back.
        stroomEventLoggingServiceProvider.get().log(
                "AuthenticationServiceImpl.reactivateAccount",
                "Account reactivated following a successful authentication",
                UpdateEventAction.builder()
                        .withBefore(accountState(userId, stateOf(true, true, false)))
                        .withAfter(accountState(userId, stateOf(true, false, false)))
                        .build());
    }

    private MultiObject accountState(final String userId, final String state) {
        return MultiObject.builder()
                .addUser(User.builder()
                        .withName(userId)
                        .withState(state)
                        .build())
                .build();
    }

    /**
     * Matches the state format used by {@code AccountResourceImpl.userForAccount} so that a change made
     * here and the same change made by an administrator are comparable in the event log.
     */
    private static String stateOf(final boolean enabled, final boolean inactive, final boolean locked) {
        return (enabled
                ? "Enabled"
                : "Disabled")
               + "/"
               + (inactive
                ? "Inactive"
                : "Active")
               + "/"
               + (locked
                ? "Locked"
                : "Unlocked");
    }

    /**
     * An inactive account is only reactivated when the user has proved who they are by supplying the
     * correct password and being inactive is the only thing preventing them from signing in. This is
     * why reactivation happens here rather than as part of a password reset.
     */
    private boolean shouldReactivateAccount(final CredentialValidationResult result) {
        return config.isReactivateInactiveAccountsOnLogin()
               && result.isValidCredentials()
               && result.isInactive()
               && !result.isAccountDoesNotExist()
               && !result.isLocked()
               && !result.isDisabled()
               && !result.isProcessingAccount();
    }

    public String logout(final HttpServletRequest request) {
        final HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            throw new IllegalStateException("No open session found");
        }
        final AuthStateImpl authState = getAuthState(request);
        final String userId = authState != null ? authState.getSubject() : null;
        // Invalidate the whole session so the app-user identity the security filter authenticates from is
        // cleared too, not only the IdP auth state.
        httpSession.invalidate();
        return userId;
    }

    /**
     * Whether a logout request may proceed, i.e. it is not a forced cross-context request driven by another
     * site. Logout is a GET with no body, so the only CSRF signal an attacker's page cannot forge is the
     * request's own metadata.
     * <p>
     * Modern browsers send fetch metadata ({@code Sec-Fetch-*}) that the embedding page cannot influence, used
     * here to admit only:
     * <ul>
     *     <li>a same-origin request from our own UI ({@code Sec-Fetch-Site: same-origin});</li>
     *     <li>a user-initiated request such as a typed URL or bookmark ({@code Sec-Fetch-Site: none});</li>
     *     <li>a genuine top-level document navigation from a sibling subdomain ({@code Sec-Fetch-Site:
     *     same-site} and {@code Sec-Fetch-Dest: document}) - which a split UI/API deployment legitimately
     *     produces.</li>
     * </ul>
     * Everything else is rejected. A nested/embedded load such as {@code <iframe src=".../logout">} or
     * {@code <img src=".../logout">} carries {@code Sec-Fetch-Dest: iframe}/{@code image} rather than
     * {@code document}, so it is refused even from a sibling subdomain (gating on {@code Sec-Fetch-Mode:
     * navigate} would not catch the iframe, because a nested-context navigation is also a "navigate"). A
     * {@code cross-site} request is refused outright, even as a top-level {@code document} navigation, because
     * a {@code SameSite=Lax} session cookie is still sent on a cross-site top-level navigation; the internal
     * identity provider has a single client (itself) and no external relying parties, so no legitimate
     * cross-site logout navigation exists.
     * <p>
     * When fetch metadata is absent (an older browser, or a non-browser client) the check falls back to
     * comparing the Origin, then the Referer, against this application's origin, allowing the request when
     * neither header is present.
     */
    public boolean isSameOrigin(final HttpServletRequest request) {
        final String fetchSite = request.getHeader("Sec-Fetch-Site");
        if (fetchSite != null && !fetchSite.isBlank()) {
            // "same-origin" is our own UI; "none" is user-initiated (typed URL / bookmark). Both are trusted.
            if ("same-origin".equals(fetchSite) || "none".equals(fetchSite)) {
                return true;
            }
            // "same-site" (a sibling subdomain, e.g. a split UI/API deployment): allow a genuine top-level
            // document navigation, but never a nested/embedded load such as an <iframe>, <img> or a fetch.
            if ("same-site".equals(fetchSite)) {
                return "document".equals(request.getHeader("Sec-Fetch-Dest"));
            }
            // "cross-site" (or any other value): reject outright - a cross-site request must not drive logout
            // even as a top-level navigation, because a SameSite=Lax session cookie is still sent on one.
            return false;
        }

        // No fetch metadata, so fall back to the Origin/Referer check.
        final String allowedOrigin = UrlUtils.toOrigin(uriFactory.publicUri("/").toString());
        String source = request.getHeader("Origin");
        if (source == null || source.isBlank()) {
            source = request.getHeader("Referer");
        }
        if (source == null || source.isBlank()) {
            return true;
        }
        return allowedOrigin != null && Objects.equals(UrlUtils.toOrigin(source), allowedOrigin);
    }

    /**
     * Validate a post-logout redirect target against this application's origin, falling back to the public
     * root when it is absent or off-origin, so the logout endpoint cannot be used as an open redirect.
     */
    public URI getValidatedPostLogoutRedirectUri(final String postLogoutRedirectUri) {
        final URI fallback = uriFactory.publicUri("/");
        if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
            final String candidateOrigin = UrlUtils.toOrigin(postLogoutRedirectUri);
            if (candidateOrigin != null && Objects.equals(candidateOrigin, UrlUtils.toOrigin(fallback.toString()))) {
                return URI.create(postLogoutRedirectUri);
            }
            LOGGER.warn(() -> "Rejecting off-origin post_logout_redirect_uri '"
                              + postLogoutRedirectUri + "'; redirecting to '" + fallback + "' instead");
        }
        return fallback;
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

        final Optional<String> violation = findNewPasswordViolation(
                userId,
                changePasswordRequest.getCurrentPassword(),
                changePasswordRequest.getNewPassword(),
                changePasswordRequest.getConfirmNewPassword());
        if (violation.isPresent()) {
            throw new RuntimeException(violation.get());
        }

        accountDao.changePassword(userId, changePasswordRequest.getNewPassword());

        // This endpoint only ever changes the signed-in user's own password, so refresh the session's auth
        // state to clear the "password change required" flag.
        final AuthStateImpl newAuthState = new AuthStateImpl(
                authState.getAccount(),
                false,
                System.currentTimeMillis());
        setAuthState(request, newAuthState, true);

        return new ChangePasswordResponse(true, null, false);
    }

    /**
     * Set a new password for the user identified by an emailed password reset token. Possession of the
     * token proves control of the account's email address, so no current password is needed.
     */
    public ChangePasswordResponse resetPasswordUsingToken(final ResetPasswordRequest request) {
        if (!isInternalIdp() || !config.getPasswordPolicyConfig().isAllowPasswordResets()) {
            return new ChangePasswordResponse(false, "Password resets are not allowed", true);
        }

        final PasswordResetLink.Parsed parsed = PasswordResetLink.parse(request.getToken()).orElse(null);
        if (parsed == null) {
            // Deliberately vague. The caller does not need to know whether the token was expired,
            // already used or simply malformed.
            return new ChangePasswordResponse(false, INVALID_RESET_TOKEN_MESSAGE, true);
        }

        final String userId = parsed.userId();
        final Account account = accountDao.get(userId).orElse(null);
        if (account == null) {
            return new ChangePasswordResponse(false, INVALID_RESET_TOKEN_MESSAGE, true);
        }

        // Only the most recently issued link is accepted, and it is single use: the token is cleared when
        // a password is set by any route, so a used link, or one made stale by a password change, fails
        // here too.
        final ResetToken resetToken = accountDao.getPasswordResetToken(userId).orElse(null);
        if (resetToken == null || !resetToken.hash().equals(parsed.tokenHash())) {
            LOGGER.debug(() -> "Password reset link for " + userId
                               + " has been used, or a newer one has since been issued");
            return new ChangePasswordResponse(false, INVALID_RESET_TOKEN_MESSAGE, true);
        }

        // Links expire, so an intercepted old one stops working.
        if (resetToken.isExpired(System.currentTimeMillis())) {
            LOGGER.debug(() -> "Password reset link for " + userId + " has expired");
            return new ChangePasswordResponse(false, INVALID_RESET_TOKEN_MESSAGE, true);
        }

        final String stateRefusal = getResetRefusalForAccountState(account);
        if (stateRefusal != null) {
            LOGGER.warn(() -> "Refusing to reset the password for account " + userId + ". " + stateRefusal);
            return new ChangePasswordResponse(false, CANNOT_RESET_MESSAGE, true);
        }

        final Optional<String> violation = findNewPasswordViolation(
                userId,
                null,
                request.getNewPassword(),
                request.getConfirmNewPassword());
        if (violation.isPresent()) {
            // This endpoint is unauthenticated and anyone can get a password wrong, so report it like
            // every other refusal here rather than letting it become a 500 with a stack trace in the log
            // and nothing useful for the user.
            LOGGER.debug(() -> "Password policy violation resetting the password for " + userId
                               + ": " + violation.get());
            return new ChangePasswordResponse(false, violation.get(), true);
        }

        // Clears the locked state but deliberately not the inactive state, which only a successful
        // authentication may clear. Conditional on the nonce still being outstanding, so this is the
        // point at which the link is consumed and two requests using the same one cannot both succeed.
        // The nonce read above only produces a better message; this is what actually decides.
        if (!accountDao.unlockAndSetPassword(userId, request.getNewPassword(), parsed.tokenHash())) {
            LOGGER.debug(() -> "Password reset link for " + userId + " was consumed before we could use it");
            return new ChangePasswordResponse(false, INVALID_RESET_TOKEN_MESSAGE, true);
        }
        logPasswordReset(account);

        // A reset is account recovery: end every existing session for this user across the cluster.
        userSessionEvictor.evictUserSessions(userId, null);

        // The user is not signed in here. They must now sign in with the new password, which is also
        // what reactivates the account if it happens to be inactive.
        return new ChangePasswordResponse(true, null, true);
    }

    /**
     * The password policy applied by every route that sets a password, so that changing a password and
     * resetting a forgotten one cannot drift apart.
     * <p>
     * Returns the reason rather than throwing it so that each caller can report it in its own way, and so
     * that a failure of the account lookup below is not mistaken for a rejected password.
     * </p>
     *
     * @param currentPassword The user's current password if they supplied it, otherwise null. A reset
     *                        cannot supply it, which is the whole point of a reset.
     * @return Why the password is not acceptable, in words for the user, or empty if it is fine.
     */
    private Optional<String> findNewPasswordViolation(final String userId,
                                                      final String currentPassword,
                                                      final String newPassword,
                                                      final String confirmNewPassword) {
        try {
            PasswordValidator.validateLength(newPassword,
                    config.getPasswordPolicyConfig().getMinimumPasswordLength());
            PasswordValidator.validateStrength(newPassword,
                    config.getPasswordPolicyConfig().getMinimumPasswordStrength());
            PasswordValidator.validateConfirmation(newPassword, confirmNewPassword);

            // Where the caller knows the current password we can also refuse one that differs from it
            // only by case, which comparing against the stored hash cannot detect.
            if (currentPassword != null) {
                PasswordValidator.validateReuse(currentPassword, newPassword);
            }
        } catch (final RuntimeException e) {
            // PasswordValidator signals a violation by throwing, and only ever throws for that.
            return Optional.of(e.getMessage());
        }

        // Compared against the stored hash so that reuse is refused even for a reset, where the user does
        // not know their current password. Last because it hashes, and there is no point paying for that
        // on a password already rejected above.
        if (accountDao.validateCredentials(userId, newPassword).isValidCredentials()) {
            return Optional.of("You cannot reuse the previous password");
        }

        return Optional.empty();
    }

    /**
     * A locked account may only complete a reset when configured to allow it, otherwise every deployment
     * would get self service unlocking. A disabled or processing account may never be reset this way. An
     * inactive account may, because the reset leaves the inactive state alone.
     *
     * @return The reason the reset must be refused, for the log rather than the caller, or null if it is
     * allowed.
     */
    private String getResetRefusalForAccountState(final Account account) {
        if (!account.isEnabled()) {
            return "The account is disabled";
        }
        if (account.isProcessingAccount()) {
            return "The account is a processing account";
        }
        if (account.isLocked() && !config.isAllowLockedAccountPasswordReset()) {
            return "The account is locked and allowLockedAccountPasswordReset is false";
        }
        return null;
    }

    private void logPasswordReset(final Account account) {
        stroomEventLoggingServiceProvider.get().log(
                "AuthenticationServiceImpl.resetPassword",
                "User set a new password using an emailed password reset token",
                AuthenticateEventAction.builder()
                        .withAction(AuthenticateAction.RESET_PASSWORD)
                        .withAuthenticationEntity(User.builder()
                                .withName(account.getUserId())
                                .build())
                        .build());

        if (account.isLocked()) {
            // The lock is a security control in its own right, so record that it was cleared and not
            // just that the password changed.
            stroomEventLoggingServiceProvider.get().log(
                    "AuthenticationServiceImpl.unlockAccount",
                    "Account unlocked by setting a new password using an emailed password reset token",
                    UpdateEventAction.builder()
                            .withBefore(accountState(account.getUserId(),
                                    stateOf(account.isEnabled(), account.isInactive(), true)))
                            .withAfter(accountState(account.getUserId(),
                                    stateOf(account.isEnabled(), account.isInactive(), false)))
                            .build());
        }
    }

    public boolean resetEmail(final String emailAddress) {
        // With an external IDP there are no local passwords to reset. Report success anyway so the
        // response is no more revealing than for an unknown address.
        if (!isInternalIdp() || !config.getPasswordPolicyConfig().isAllowPasswordResets()) {
            LOGGER.debug(() -> "Ignoring a password reset request; local password resets are not enabled");
            return true;
        }

        // Always report success, whether or not the account exists and whether or not we are about to
        // send anything. This endpoint is unauthenticated so telling the caller which email addresses
        // have accounts would let anyone enumerate our users.
        //
        // Everything after the lookup happens on another thread. Signing a token and sending mail take
        // far longer than the lookup, so doing them here would let a caller tell a known address from an
        // unknown one by how long we took to answer.
        final Account account = accountDao.getByEmail(emailAddress).orElse(null);
        if (account == null) {
            LOGGER.debug(() -> "Ignoring a password reset request for unknown email address " + emailAddress);
            return true;
        }

        emailExecutor.execute(() -> sendResetEmail(account));
        return true;
    }

    private void sendResetEmail(final Account account) {
        try {
            final long now = System.currentTimeMillis();
            final long earliestPreviousRequest = now
                                                 - config.getPasswordResetRequestCooldown().toMillis();
            if (!accountDao.tryRecordResetEmailRequest(account.getUserId(), now, earliestPreviousRequest)) {
                LOGGER.debug(() -> "Throttling a password reset request for " + account.getUserId());
                return;
            }

            // A fresh opaque token. Storing only the hash of its secret invalidates any link issued
            // earlier and means the raw token cannot be recovered from the database.
            final PasswordResetLink.Issued issued = PasswordResetLink.issue(account.getUserId());
            final long expiryTimeMs = System.currentTimeMillis()
                                      + tokenConfig.getEmailResetTokenExpiration().toMillis();
            accountDao.setPasswordResetToken(
                    account.getUserId(), new ResetToken(issued.tokenHash(), expiryTimeMs));

            // Send to the address held against the account rather than whatever the caller typed. They
            // are the same string today because that is what we matched on, but the account is the
            // authority.
            emailSender.send(account.getEmail(), account.getFirstName(), account.getLastName(),
                    issued.token());

        } catch (final RuntimeException e) {
            // Nothing is waiting on this, so log rather than let it escape into the executor.
            LOGGER.error(() -> "Unable to send a password reset email for " + account.getUserId()
                               + ": " + e.getMessage(), e);
        }
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
        // Rotate the session id now the request has gained privilege.
        SessionUtil.changeSessionId(request);
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
