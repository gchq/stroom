package stroom.security.identity.authenticate;

import stroom.config.common.UriFactory;
import stroom.security.api.SecurityContext;
import stroom.security.identity.account.Account;
import stroom.security.identity.account.AccountDao;
import stroom.security.identity.account.AccountService;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.PasswordPolicyConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.token.Token;
import stroom.security.identity.token.TokenService;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.net.URI;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;


class AuthenticationServiceImpl implements AuthenticationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private static final long MIN_CREDENTIAL_CONFIRMATION_INTERVAL = 600000;
    private static final String AUTH_STATE = "AUTH_STATE";

    private final UriFactory uriFactory;
    private final IdentityConfig config;
    private final TokenService tokenService;
    private final EmailSender emailSender;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private final SecurityContext securityContext;
    private final OpenIdClientFactory openIdClientDetailsFactory;

    @Inject
    public AuthenticationServiceImpl(
            final UriFactory uriFactory,
            @NotNull final IdentityConfig config,
            final TokenService tokenService,
            final EmailSender emailSender,
            final AccountDao accountDao,
            final AccountService accountService,
            final SecurityContext securityContext,
            final OpenIdClientFactory openIdClientDetailsFactory) {
        this.uriFactory = uriFactory;
        this.config = config;
        this.tokenService = tokenService;
        this.emailSender = emailSender;
        this.accountDao = accountDao;
        this.accountService = accountService;
        this.securityContext = securityContext;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

    public AuthenticationState getAuthenticationState(final HttpServletRequest request) {
        final AuthStateImpl authState = getAuthState(request);
        String userId = null;
        if (authState != null && !authState.isRequirePasswordChange()) {
            userId = authState.getSubject();
        }
        return new AuthenticationState(userId, config.getPasswordPolicyConfig().isAllowPasswordResets());
    }

    private void setAuthState(final HttpSession session, final AuthStateImpl authStateImpl) {
        if (session != null) {
            session.setAttribute(AUTH_STATE, authStateImpl);
        }
    }

    private AuthStateImpl getAuthState(final HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        final HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }

        return (AuthStateImpl) session.getAttribute(AUTH_STATE);
    }

    @Override
    public Optional<AuthState> currentAuthState(final HttpServletRequest request) {
        LOGGER.debug("currentAuthState");
        AuthStateImpl authState = getAuthState(request);

        // Now we can check if we're logged in somehow (session or certs) and build the response accordingly
        if (authState != null) {
            LOGGER.debug("Has current auth state");
            return Optional.of(authState);

        } else {
            loginWithCertificate(request);
        }

        return Optional.ofNullable(getAuthState(request));
    }

    private void loginWithCertificate(final HttpServletRequest request) {
        LOGGER.debug("loginWithCertificate");
        String dn = CertificateUtil.extractCertificateDN(request);
        LOGGER.debug(() -> "DN = " + dn);
        if (dn != null) {
            final String cn = CertificateUtil.extractCNFromDN(dn);
            LOGGER.debug(() -> "CN = " + cn);

            if (cn != null) {
                // Check for a certificate
                LOGGER.debug(() -> "User has presented a certificate: " + cn);
                Optional<String> optionalSubject = getIdFromCertificate(cn);

                if (optionalSubject.isEmpty()) {
                    throw new RuntimeException(
                            "User is presenting a certificate but this certificate cannot be processed!");

                } else {
                    final String subject = optionalSubject.get();
                    final Optional<Account> optionalAccount = accountDao.get(subject);
                    if (optionalAccount.isEmpty()) {
                        // There's no user so we can't let them have access.
                        throw new BadRequestException(
                                "The user identified by the certificate does not exist in the auth database.");

                    } else {
                        final Account account = optionalAccount.get();
                        if (!account.isLocked() && !account.isInactive() && account.isEnabled()) {
                            LOGGER.info("Logging user in using DN with subject {}", subject);
                            setAuthState(request.getSession(true),
                                    new AuthStateImpl(account, false, System.currentTimeMillis()));

                            // Reset last access, login failures, etc...
                            accountDao.recordSuccessfulLogin(subject);
                        }
                    }
                }
            }
        }
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
            clearSession(request);
            return request.getUserPrincipal().getName();
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
            setAuthState(request.getSession(true),
                    new AuthStateImpl(authState.getAccount(),
                            authState.isRequirePasswordChange(),
                            System.currentTimeMillis()));
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
            setAuthState(request.getSession(true),
                    new AuthStateImpl(authState.getAccount(), false, System.currentTimeMillis()));
        }

        return new ChangePasswordResponse(true, null, false);
    }

    public boolean resetEmail(final String emailAddress) {
        final Account account = accountService.read(emailAddress).orElseThrow(() -> new RuntimeException(
                "Account not found for email: " + emailAddress));
        final Token token = tokenService.createResetEmailToken(account,
                openIdClientDetailsFactory.getClient().getClientId());
        final String resetToken = token.getData();
        emailSender.send(emailAddress, account.getFirstName(), account.getLastName(), resetToken);
        return true;
    }

    public ChangePasswordResponse resetPassword(final HttpServletRequest request,
                                                final ResetPasswordRequest resetPasswordRequest) {
        if (!securityContext.isLoggedIn()) {
            throw new RuntimeException("No user is currently logged in.");
        }
        final AuthStateImpl authState = getAuthState(request);
        final boolean forceSignIn = shouldForceSignIn(authState);

        final String loggedInUser = securityContext.getUserId();
        PasswordPolicyConfig conf = config.getPasswordPolicyConfig();

        PasswordValidator.validateLength(resetPasswordRequest.getNewPassword(), conf.getMinimumPasswordLength());
        PasswordValidator.validateComplexity(resetPasswordRequest.getNewPassword(), conf.getPasswordComplexityRegex());
        PasswordValidator.validateConfirmation(resetPasswordRequest.getNewPassword(),
                resetPasswordRequest.getConfirmNewPassword());

        accountDao.changePassword(loggedInUser, resetPasswordRequest.getNewPassword());
        return new ChangePasswordResponse(true, null, forceSignIn);
    }

    public boolean needsPasswordChange(String email) {
        return accountDao.needsPasswordChange(
                email,
                config.getPasswordPolicyConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordPolicyConfig().isForcePasswordChangeOnFirstLogin());
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
        setAuthState(request.getSession(true),
                new AuthStateImpl(account, userNeedsToChangePassword, System.currentTimeMillis()));

        return new LoginResponse(true, message, userNeedsToChangePassword);
    }

    @Override
    public URI createSignInUri(final String redirectUri) {
        LOGGER.debug("Sending user to login.");
        final UriBuilder uriBuilder = UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.SIGN_IN_URL_PATH))
                .queryParam("error", "login_required")
                .queryParam(OpenId.REDIRECT_URI, redirectUri);
        return uriBuilder.build();
    }

    @Override
    public URI createConfirmPasswordUri(final String redirectUri) {
        LOGGER.debug("Sending user to confirm password.");
        return UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.CONFIRM_PASSWORD_URL_PATH))
                .queryParam(OpenId.REDIRECT_URI, redirectUri)
                .build();
    }

    @Override
    public URI createChangePasswordUri(final String redirectUri) {
        LOGGER.debug("Sending user to change password.");
        return UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.CHANGE_PASSWORD_URL_PATH))
                .queryParam(OpenId.REDIRECT_URI, redirectUri)
                .build();
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
        setAuthState(request.getSession(false), null);
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
    }
}
