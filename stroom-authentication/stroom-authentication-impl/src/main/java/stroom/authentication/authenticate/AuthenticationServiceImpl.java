package stroom.authentication.authenticate;

import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.authentication.account.AccountService;
import stroom.authentication.api.OIDC;
import stroom.authentication.api.OpenIdClientDetailsFactory;
import stroom.authentication.authenticate.api.AuthenticationService;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.PasswordPolicyConfig;
import stroom.authentication.exceptions.BadRequestException;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenService;
import stroom.config.common.UriFactory;
import stroom.security.api.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class AuthenticationServiceImpl implements AuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private static final long MIN_CREDENTIAL_CONFIRMATION_INTERVAL = 600000;
    private static final String AUTH_STATE = "AUTH_STATE";

    private final UriFactory uriFactory;
    private final AuthenticationConfig config;
    private final TokenService tokenService;
    private final EmailSender emailSender;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private final SecurityContext securityContext;
    private final OpenIdClientDetailsFactory openIdClientDetailsFactory;

    @Inject
    public AuthenticationServiceImpl(
            final UriFactory uriFactory,
            final @NotNull AuthenticationConfig config,
            final TokenService tokenService,
            final EmailSender emailSender,
            final AccountDao accountDao,
            final AccountService accountService,
            final SecurityContext securityContext,
            final OpenIdClientDetailsFactory openIdClientDetailsFactory) {
        this.uriFactory = uriFactory;
        this.config = config;
        this.tokenService = tokenService;
        this.emailSender = emailSender;
        this.accountDao = accountDao;
        this.accountService = accountService;
        this.securityContext = securityContext;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
    }

//    @Override
//    public Response.ResponseBuilder handleAuthenticationRequest(
//            final String sessionId,
//            final String nonce,
//            final String state,
//            final String redirectUri,
//            final String clientId,
//            final String prompt,
//            final Optional<String> optionalCn) {
//
//        boolean isAuthenticated = false;
//
//        LOGGER.info("Received an AuthenticationRequest for session " + sessionId);
//
//        // We need to make sure our understanding of the session is correct
//        Optional<Session> optionalSession = sessionManager.get(sessionId);
//        if (optionalSession.isPresent()) {
//            // If we have an authenticated session then the user is logged in
//            isAuthenticated = optionalSession.get().isAuthenticated();
//        } else {
//            // If we've not created a session then we need to create a new, unauthenticated one
//            optionalSession = Optional.of(sessionManager.create(sessionId));
//            optionalSession.get().setAuthenticated(false);
//        }
//
//
//        // We need to make sure we record this relying party against this session
//        RelyingParty relyingParty = optionalSession.get().getOrCreateRelyingParty(clientId);
//        relyingParty.setNonce(nonce);
//        relyingParty.setState(state);
//        relyingParty.setRedirectUrl(redirectUri);
//
//
//        // Now we can check if we're logged in somehow (session or certs) and build the response accordingly
//        Response.ResponseBuilder responseBuilder;
////        Optional<String> optionalCn = certificateManager.getCertificate(httpServletRequest);
//
//        // If the prompt is 'login' then we always want to prompt the user to login in with username and password.
//        boolean requireLoginPrompt = prompt != null && prompt.equalsIgnoreCase("login");
//        boolean loginUsingCertificate = optionalCn.isPresent() && !requireLoginPrompt;
//        if (requireLoginPrompt) {
//            LOGGER.info("Relying party requested a user login page by using 'prompt=login'");
//        }
//
//
//        // Check for an authenticated session
//        if (isAuthenticated) {
//            LOGGER.debug("User has a session, sending them to the RP");
//            String accessCode = SessionManager.createAccessCode();
//            relyingParty.setAccessCode(accessCode);
//            String subject = optionalSession.get().getUserEmail();
//            String idToken = createIdToken(subject, nonce, state, sessionId);
//            relyingParty.setIdToken(idToken);
//            responseBuilder = seeOther(buildRedirectionUrl(redirectUri, accessCode, state));
//        }
//        // Check for a certificate
//        else if (loginUsingCertificate) {
//            String cn = optionalCn.get();
//            LOGGER.debug("User has presented a certificate: {}", cn);
//            Optional<String> optionalSubject = getIdFromCertificate(cn);
//
//            if (!optionalSubject.isPresent()) {
//                String errorMessage = "User is presenting a certificate but this certificate cannot be processed!";
//                LOGGER.error(errorMessage);
//                responseBuilder = status(Response.Status.FORBIDDEN).entity(errorMessage);
//            } else {
//                String subject = optionalSubject.get();
//                if (!accountDao.exists(subject)) {
//                    LOGGER.debug("The user identified by the certificate does not exist in the auth database.");
//                    // There's no user so we can't let them have access.
//                    responseBuilder = seeOther(UriBuilder.fromUri(this.config.getUnauthorisedUrl()).build());
//                } else {
//                    User user = accountDao.get(subject).get();
//                    if (user.getState().equals(User.UserState.ENABLED.getStateText())) {
//                        LOGGER.info("Logging user in using DN with subject {}", subject);
//                        optionalSession.get().setAuthenticated(true);
//                        optionalSession.get().setUserEmail(subject);
//                        String accessCode = SessionManager.createAccessCode();
//                        relyingParty.setAccessCode(accessCode);
//                        String idToken = createIdToken(subject, nonce, state, sessionId);
//                        relyingParty.setIdToken(idToken);
//                        responseBuilder = seeOther(buildRedirectionUrl(redirectUri, accessCode, state));
//                        stroomEventLoggingService.createAction("Logon", "User logged in successfully");
//                        // Reset last access, login failures, etc...
//                        accountDao.recordSuccessfulLogin(subject);
//                    } else {
//                        LOGGER.debug("The user identified by the certificate is not enabled!");
//                        stroomEventLoggingService.createAction(
//                                "Logon",
//                                "User attempted to log in but failed because the account is " + User.UserState.LOCKED.getStateText() + ".");
//                        String failureUrl = this.config.getUnauthorisedUrl() + "?reason=account_locked";
//                        responseBuilder = seeOther(UriBuilder.fromUri(failureUrl).build());
//                    }
//                }
//
//            }
//        }
//        // There's no session and there's no certificate so we'll send them to the login page
//        else {
//            LOGGER.debug("User has no session and no certificate - sending them to login.");
//            final UriBuilder uriBuilder = UriBuilder.fromUri(this.config.getLoginUrl())
//                    .queryParam("error", "login_required")
//                    .queryParam(OIDC.STATE, state)
//                    .queryParam(OIDC.CLIENT_ID, clientId)
//                    .queryParam(OIDC.REDIRECT_URI, redirectUri);
//            responseBuilder = seeOther(uriBuilder.build());
//        }
//
//        return responseBuilder;
//    }

//    private void setSessionUser(final HttpServletRequest request, final User user) {
//        request.getSession(true).setAttribute(AUTHENTICATED_ACCOUNT, user);
//    }

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
        final HttpSession session = request.getSession(false);
        if (session != null) {
            return (AuthStateImpl) session.getAttribute(AUTH_STATE);
        }
        return null;
    }

    @Override
    public Optional<AuthState> currentAuthState(final HttpServletRequest request) {
        AuthStateImpl authState = getAuthState(request);

        // Now we can check if we're logged in somehow (session or certs) and build the response accordingly
        if (authState != null) {
            return Optional.of(authState);

        } else {
            loginWithCertificate(request);
        }

        return Optional.ofNullable(getAuthState(request));
    }

    private void loginWithCertificate(final HttpServletRequest request) {
        String cn = null;
        String dn = CertificateUtil.extractCertificateDN(request);
        if (dn != null) {
            cn = CertificateUtil.extractCNFromDN(dn);
        }

        if (cn != null) {
            // Check for a certificate
            LOGGER.debug("User has presented a certificate: {}", cn);
            Optional<String> optionalSubject = getIdFromCertificate(cn);

            if (optionalSubject.isEmpty()) {
                throw new RuntimeException("User is presenting a certificate but this certificate cannot be processed!");

            } else {
                final String subject = optionalSubject.get();
                final Optional<Account> optionalAccount = accountDao.get(subject);
                if (optionalAccount.isEmpty()) {
                    // There's no user so we can't let them have access.
                    throw new BadRequestException("The user identified by the certificate does not exist in the auth database.");

                } else {
                    final Account account = optionalAccount.get();
                    if (!account.isLocked() && !account.isInactive() && account.isEnabled()) {
                        LOGGER.info("Logging user in using DN with subject {}", subject);
                        setAuthState(request.getSession(true), new AuthStateImpl(account, false, System.currentTimeMillis()));

                        // Reset last access, login failures, etc...
                        accountDao.recordSuccessfulLogin(subject);
                    }
                }
            }
        }
    }

    public LoginResponse handleLogin(final LoginRequest loginRequest,
                                     final HttpServletRequest request) {
        clearSession(request);

        // Check the credentials
        CredentialValidationResult result = accountDao.validateCredentials(loginRequest.getUserId(), loginRequest.getPassword());
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


//    public Boolean logout() {
//        return securityContext.insecureResult(() -> {
//            final HttpSession session = httpServletRequestProvider.get().getSession(false);
//            final UserIdentity userIdentity = UserIdentitySessionUtil.get(session);
//            if (session != null) {
//                // Invalidate the current user session
//                session.invalidate();
//            }
//            if (userIdentity != null) {
//                // Create an event for logout
//                stroomEventLoggingService.createAction("Logoff", "Logging off " + userIdentity.getId());
//            }
//
//            return true;
//        });
//    }

    public String logout(final HttpServletRequest request, final String redirectUri) {
        final HttpSession httpSession = request.getSession(false);
        if (httpSession != null) {
            clearSession(request);
        }
        return redirectUri;
    }

    public ConfirmPasswordResponse confirmPassword(final HttpServletRequest request,
                                                   final ConfirmPasswordRequest confirmPasswordRequest) {
        final AuthStateImpl authState = getAuthState(request);

        final CredentialValidationResult result = accountDao.validateCredentials(
                authState.getSubject(),
                confirmPasswordRequest.getPassword());

        final String message = result.toString();
        if (result.isAllOk()) {
            // Update tha last credential confirmation time.
            setAuthState(request.getSession(true), new AuthStateImpl(authState.getAccount(), authState.isRequirePasswordChange(), System.currentTimeMillis()));
            return new ConfirmPasswordResponse(true, message);
        }

        return new ConfirmPasswordResponse(false, message);
    }

    public ChangePasswordResponse changePassword(final HttpServletRequest request,
                                                 final ChangePasswordRequest changePasswordRequest) {
        // Record that the password no longer needs changing.
        final AuthStateImpl authState = getAuthState(request);
        if (authState == null) {
            return new ChangePasswordResponse(false, "Not user is currently signed in", true);
        }
        final boolean forceSignIn = shouldForceSignIn(authState);
        if (forceSignIn) {
            return new ChangePasswordResponse(false, "It has been too long since you last signed in, please sign in again", true);
        }

        // TODO : @66 At present the change password request doesn't always know the user id.
        final String username = authState.getSubject();

        List<String> failedOn = new ArrayList<>();
        final CredentialValidationResult result = accountDao.validateCredentials(
                username,
                changePasswordRequest.getCurrentPassword());

        PasswordValidator.validateAuthenticity(result)
                .map(PasswordValidationFailureType::name)
                .ifPresent(failedOn::add);
        PasswordValidator.validateReuse(
                changePasswordRequest.getCurrentPassword(),
                changePasswordRequest.getNewPassword())
                .map(PasswordValidationFailureType::name)
                .ifPresent(failedOn::add);
        PasswordValidator.validateLength(
                changePasswordRequest.getNewPassword(),
                config.getPasswordPolicyConfig().getMinimumPasswordLength())
                .map(PasswordValidationFailureType::name)
                .ifPresent(failedOn::add);
        PasswordValidator.validateComplexity(
                changePasswordRequest.getNewPassword(),
                config.getPasswordPolicyConfig().getPasswordComplexityRegex())
                .map(PasswordValidationFailureType::name)
                .ifPresent(failedOn::add);
        if (!Objects.equals(changePasswordRequest.getNewPassword(), changePasswordRequest.getConfirmNewPassword())) {
            failedOn.add("Confirmation password does not match new password");
        }

        if (failedOn.size() == 0) {
            accountDao.changePassword(username, changePasswordRequest.getNewPassword());

            if (authState.getSubject().equals(username)) {
                setAuthState(request.getSession(true), new AuthStateImpl(authState.getAccount(), false, System.currentTimeMillis()));
            }

            return new ChangePasswordResponse(true, null, false);

//            final URI result = URI.create(redirectUri);//service.auth(request, scope, responseType, clientId, redirectUri, nonce, state, prompt);
//            throw new RedirectionException(Status.SEE_OTHER, result);
        }

        return new ChangePasswordResponse(false, failedOn.get(0), false);
    }

    public boolean resetEmail(final String emailAddress) {
        final Account account = accountService.read(emailAddress).orElseThrow(() -> new RuntimeException("Account not found for email: " + emailAddress));
        final Token token = tokenService.createResetEmailToken(account, openIdClientDetailsFactory.getOAuth2Client().getClientId());
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
        List<String> failedOn = new ArrayList<>();
        PasswordPolicyConfig conf = config.getPasswordPolicyConfig();

        PasswordValidator.validateLength(
                resetPasswordRequest.getNewPassword(),
                conf.getMinimumPasswordLength())
                .map(PasswordValidationFailureType::name)
                .ifPresent(failedOn::add);
        PasswordValidator.validateComplexity(
                resetPasswordRequest.getNewPassword(),
                conf.getPasswordComplexityRegex())
                .map(PasswordValidationFailureType::name)
                .ifPresent(failedOn::add);

        if (failedOn.size() == 0) {
            accountDao.changePassword(loggedInUser, resetPasswordRequest.getNewPassword());
            return new ChangePasswordResponse(true, null, forceSignIn);
        }

        return new ChangePasswordResponse(false, failedOn.get(0), forceSignIn);
    }

    public boolean needsPasswordChange(String email) {
        return accountDao.needsPasswordChange(
                email,
                config.getPasswordPolicyConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordPolicyConfig().isForcePasswordChangeOnFirstLogin());
    }

//    public PasswordValidationResponse isPasswordValid(PasswordValidationRequest passwordValidationRequest) {
//        List<PasswordValidationFailureType> failedOn = new ArrayList<>();
//
//        if (passwordValidationRequest.getOldPassword() != null) {
//            final LoginResult loginResult = accountDao.areCredentialsValid(
//                    passwordValidationRequest.getEmail(),
//                    passwordValidationRequest.getOldPassword());
//
//            PasswordValidator.validateAuthenticity(loginResult)
//                    .ifPresent(failedOn::add);
//
//            PasswordValidator.validateReuse(
//                    passwordValidationRequest.getOldPassword(),
//                    passwordValidationRequest.getNewPassword())
//                    .ifPresent(failedOn::add);
//        }
//
//        PasswordValidator.validateLength(
//                passwordValidationRequest.getNewPassword(),
//                config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength())
//                .ifPresent(failedOn::add);
//        PasswordValidator.validateComplexity(
//                passwordValidationRequest.getNewPassword(),
//                config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex())
//                .ifPresent(failedOn::add);
//
//        return new PasswordValidationResponse.PasswordValidationResponseBuilder()
//                .withFailedOn(failedOn.toArray(new PasswordValidationFailureType[0]))
//                .build();
//    }

//    public URI postAuthenticationRedirect(final HttpServletRequest request, final String redirectUri) {
//        final Account account = getSessionUser(request);
//        if (account == null) {
//            throw new BadRequestException("User is not authenticated");
//        }
//
//        final String username = account.getEmail();
//
//        boolean userNeedsToChangePassword = accountDao.needsPasswordChange(
//                username, config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
//                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());
//
//        URI result;
//
//        if (userNeedsToChangePassword) {
//            final String innerRedirectUri = getPostAuthenticationCheckUrl(redirectUri);
//            result = UriBuilder.fromUri(uriFactory.uiUri(config.getChangePasswordUrl()))
//                    .queryParam(OIDC.REDIRECT_URI, innerRedirectUri)
//                    .build();
//        } else {
//            result = UriBuilder.fromUri(redirectUri).build();
//        }
//
//        return result;
//    }

    private LoginResponse processSuccessfulLogin(final HttpServletRequest request,
                                                 final Account account,
                                                 final String message) {
        // Make sure the session is authenticated and ready for use
        clearSession(request);

        final String username = account.getEmail();
        LOGGER.debug("Login for {} succeeded", username);

        final boolean userNeedsToChangePassword = accountDao.needsPasswordChange(
                username,
                config.getPasswordPolicyConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordPolicyConfig().isForcePasswordChangeOnFirstLogin());

        // Reset last access, login failures, etc...
        accountDao.recordSuccessfulLogin(username);
        setAuthState(request.getSession(true), new AuthStateImpl(account, userNeedsToChangePassword, System.currentTimeMillis()));

        return new LoginResponse(true, message, userNeedsToChangePassword);
    }

    @Override
    public URI createSignInUri(final String redirectUri) {
        LOGGER.debug("Sending user to login.");
        final UriBuilder uriBuilder = UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.SIGN_IN_URL_PATH))
                .queryParam("error", "login_required")
                .queryParam(OIDC.REDIRECT_URI, redirectUri);
        return uriBuilder.build();
    }

    @Override
    public URI createConfirmPasswordUri(final String redirectUri) {
        LOGGER.debug("Sending user to confirm password.");
        return UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.CONFIRM_PASSWORD_URL_PATH))
                .queryParam(OIDC.REDIRECT_URI, redirectUri)
                .build();
    }

    @Override
    public URI createChangePasswordUri(final String redirectUri) {
        LOGGER.debug("Sending user to change password.");
        return UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.CHANGE_PASSWORD_URL_PATH))
                .queryParam(OIDC.REDIRECT_URI, redirectUri)
                .build();
    }

    //    private String getPostAuthenticationCheckUrl(final String redirectUri) {
//        final URI uri = UriBuilder
//                .fromUri(uriFactory.publicUri(ResourcePaths.API_ROOT_PATH + AuthenticationResource.BASE_PATH + AuthenticationResource.PATH_POST_AUTHENTICATION_REDIRECT))
//                .queryParam(OIDC.REDIRECT_URI, redirectUri)
//                .build();
//        return uri.toString();
//    }

    private Optional<String> getIdFromCertificate(final String cn) {
        final Pattern idExtractionPattern = Pattern.compile(this.config.getCertificateCnPattern());
        final Matcher idExtractionMatcher = idExtractionPattern.matcher(cn);
        if (idExtractionMatcher.find()) {
            final int captureGroupIndex = this.config.getCertificateCnCaptureGroupIndex();
            try {
                if (idExtractionMatcher.groupCount() >= captureGroupIndex) {
                    final String id = idExtractionMatcher.group(captureGroupIndex);
                    return Optional.of(id);
                }
            } catch (final IllegalStateException ex) {
                LOGGER.error("Unable to extract user ID from CN. CN was {} and pattern was {}", cn,
                        this.config.getCertificateCnPattern());
            }
        }

        return Optional.empty();
    }

//    private URI buildRedirectionUrl(final String redirectUri, final String code, final String state) {
//        return UriBuilder
//                .fromUri(redirectUri)
//                .replaceQueryParam(OIDC.CODE, code)
//                .replaceQueryParam(OIDC.STATE, state)
//                .build();
//    }
//
//    private String createIdToken(final String clientId,
//                                 final String subject,
//                                 final String nonce,
//                                 final String state,
//                                 final String sessionId) {
//        TokenBuilder tokenBuilder = tokenBuilderFactory
//                .newBuilder(Token.TokenType.USER)
//                .clientId(clientId)
//                .subject(subject)
//                .nonce(nonce)
//                .state(state)
//                .authSessionId(sessionId);
//        Instant expiresOn = tokenBuilder.getExpiryDate();
//        String idToken = tokenBuilder.build();
//
//        tokenDao.createIdToken(idToken, subject, new Timestamp(expiresOn.toEpochMilli()));
//        return idToken;
//    }

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
            return authState.getLastCredentialCheckMs() < System.currentTimeMillis() - MIN_CREDENTIAL_CONFIRMATION_INTERVAL;
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
            return account.getEmail();
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
