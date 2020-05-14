package stroom.authentication.authenticate;

import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.authentication.account.AccountService;
import stroom.authentication.api.OIDC;
import stroom.authentication.api.OpenIdClientDetailsFactory;
import stroom.authentication.authenticate.api.AuthenticationService;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.PasswordIntegrityChecksConfig;
import stroom.authentication.exceptions.BadRequestException;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenService;
import stroom.config.common.UriFactory;
import stroom.security.api.SecurityContext;

import org.apache.commons.lang3.NotImplementedException;
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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


class AuthenticationServiceImpl implements AuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationServiceImpl.class);

    private static final String AUTH_STATE = "AUTH_STATE";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String ACCOUNT_LOCKED_MESSAGE = "This account is locked. Please contact your administrator";
    private static final String ACCOUNT_DISABLED_MESSAGE = "This account is disabled. Please contact your administrator";
    private static final String ACCOUNT_INACTIVE_MESSAGE = "This account is marked as inactive. Please contact your administrator";

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
                        setAuthState(request.getSession(true), new AuthStateImpl(account, false));

                        // Reset last access, login failures, etc...
                        accountDao.recordSuccessfulLogin(subject);
                    }
                }
            }
        }
    }

    public LoginResponse handleLogin(final Credentials credentials,
                                     final HttpServletRequest request,
                                     final String redirectUri) {
        LoginResponse loginResponse;

        clearSession(request);

        // Check the credentials
        LoginResult loginResult = accountDao.areCredentialsValid(credentials.getEmail(), credentials.getPassword());
        switch (loginResult) {
            case BAD_CREDENTIALS:
                LOGGER.debug("Password for {} is incorrect", credentials.getEmail());
                boolean shouldLock = accountDao.incrementLoginFailures(credentials.getEmail());

                if (shouldLock) {
                    loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                } else {
                    loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                }
                break;
            case GOOD_CREDENTIALS:
                final Optional<Account> optionalAccount = accountService.read(credentials.getEmail());
                String redirectionUrl = processSuccessfulLogin(request, optionalAccount.get(), redirectUri);
                loginResponse = new LoginResponse(true, "", redirectionUrl, 200);
                break;
            case USER_DOES_NOT_EXIST:
                // We don't want to let the user know the account they tried to log in with doesn't exist.
                // If we did we'd be revealing the presence or absence of an account or email address and
                // we don't want to do this.
                loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                break;
            case LOCKED_BAD_CREDENTIALS:
                loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                break;
            case LOCKED_GOOD_CREDENTIALS:
                // If the credentials are bad we don't want to reveal the status of the account to the user.
                loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                break;
            case DISABLED_BAD_CREDENTIALS:
                // If the credentials are bad we don't want to reveal the status of the account to the user.
                loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                break;
            case DISABLED_GOOD_CREDENTIALS:
                loginResponse = new LoginResponse(false, ACCOUNT_DISABLED_MESSAGE, null, 200);
                break;
            case INACTIVE_BAD_CREDENTIALS:
                // If the credentials are bad we don't want to reveal the status of the account to the user.
                loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                break;
            case INACTIVE_GOOD_CREDENTIALS:
                loginResponse = new LoginResponse(false, ACCOUNT_INACTIVE_MESSAGE, null, 200);
                break;
            default:
                String errorMessage = String.format("%s does not support a LoginResult of %s",
                        this.getClass().getSimpleName(), loginResult.toString());
                throw new NotImplementedException(errorMessage);
        }

        return loginResponse;
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

    public boolean resetEmail(final String emailAddress) {
        final Account account = accountService.read(emailAddress).orElseThrow(() -> new RuntimeException("Account not found for email: " + emailAddress));
        final Token token = tokenService.createResetEmailToken(account, openIdClientDetailsFactory.getOAuth2Client().getClientId());
        final String resetToken = token.getData();
        emailSender.send(emailAddress, account.getFirstName(), account.getLastName(), resetToken);
        return true;
    }

    public ChangePasswordResponse changePassword(final HttpServletRequest request,
                                                 final ChangePasswordRequest changePasswordRequest) {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();
        final LoginResult loginResult = accountDao.areCredentialsValid(
                changePasswordRequest.getEmail(),
                changePasswordRequest.getOldPassword());

        PasswordValidator.validateAuthenticity(loginResult)
                .ifPresent(failedOn::add);
        PasswordValidator.validateReuse(
                changePasswordRequest.getOldPassword(),
                changePasswordRequest.getNewPassword())
                .ifPresent(failedOn::add);
        PasswordValidator.validateLength(
                changePasswordRequest.getNewPassword(),
                config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength())
                .ifPresent(failedOn::add);
        PasswordValidator.validateComplexity(
                changePasswordRequest.getNewPassword(),
                config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex())
                .ifPresent(failedOn::add);

        final ChangePasswordResponse.ChangePasswordResponseBuilder responseBuilder = new ChangePasswordResponse.ChangePasswordResponseBuilder();
        if (failedOn.size() == 0) {
            accountDao.changePassword(changePasswordRequest.getEmail(), changePasswordRequest.getNewPassword());

            responseBuilder.withSuccess();

            // Record that the password no longer needs changing.
            final AuthStateImpl authState = getAuthState(request);
            if (authState != null) {
                if (authState.getSubject().equals(changePasswordRequest.getEmail())) {
                    setAuthState(request.getSession(true), new AuthStateImpl(authState.getAccount(), false));
                }
            }
        } else {
            responseBuilder.withFailedOn(failedOn);
        }

        return responseBuilder.build();
    }

    public ChangePasswordResponse resetPassword(ResetPasswordRequest request) {
        if (!securityContext.isLoggedIn()) {
            throw new RuntimeException("No user is currently logged in.");
        }

        final String loggedInUser = securityContext.getUserId();
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();
        PasswordIntegrityChecksConfig conf = config.getPasswordIntegrityChecksConfig();

        PasswordValidator.validateLength(
                request.getNewPassword(),
                conf.getMinimumPasswordLength())
                .ifPresent(failedOn::add);
        PasswordValidator.validateComplexity(
                request.getNewPassword(),
                conf.getPasswordComplexityRegex())
                .ifPresent(failedOn::add);

        final ChangePasswordResponse.ChangePasswordResponseBuilder responseBuilder = new ChangePasswordResponse.ChangePasswordResponseBuilder();

        if (responseBuilder.failedOn.size() == 0) {
            responseBuilder.withSuccess();
            accountDao.changePassword(loggedInUser, request.getNewPassword());
        } else {
            responseBuilder.withFailedOn(failedOn);
        }

        return responseBuilder.build();
    }

    public boolean needsPasswordChange(String email) {
        return accountDao.needsPasswordChange(
                email,
                config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());
    }

    public PasswordValidationResponse isPasswordValid(PasswordValidationRequest passwordValidationRequest) {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();

        if (passwordValidationRequest.getOldPassword() != null) {
            final LoginResult loginResult = accountDao.areCredentialsValid(
                    passwordValidationRequest.getEmail(),
                    passwordValidationRequest.getOldPassword());

            PasswordValidator.validateAuthenticity(loginResult)
                    .ifPresent(failedOn::add);

            PasswordValidator.validateReuse(
                    passwordValidationRequest.getOldPassword(),
                    passwordValidationRequest.getNewPassword())
                    .ifPresent(failedOn::add);
        }

        PasswordValidator.validateLength(
                passwordValidationRequest.getNewPassword(),
                config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength())
                .ifPresent(failedOn::add);
        PasswordValidator.validateComplexity(
                passwordValidationRequest.getNewPassword(),
                config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex())
                .ifPresent(failedOn::add);

        return new PasswordValidationResponse.PasswordValidationResponseBuilder()
                .withFailedOn(failedOn.toArray(new PasswordValidationFailureType[0]))
                .build();
    }

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

    private String processSuccessfulLogin(final HttpServletRequest request,
                                          final Account account,
                                          final String redirectUri) {
        // Make sure the session is authenticated and ready for use
        clearSession(request);

        final String username = account.getEmail();
        LOGGER.debug("Login for {} succeeded", username);

        final boolean userNeedsToChangePassword = accountDao.needsPasswordChange(
                username,
                config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());

        URI result;

        if (userNeedsToChangePassword) {
//            final String innerRedirectUri = getPostAuthenticationCheckUrl(redirectUri);
            result = createChangePasswordUri(redirectUri);
        } else {
            result = UriBuilder.fromUri(redirectUri).build();
        }

        // Reset last access, login failures, etc...
        accountDao.recordSuccessfulLogin(account.getEmail());
        setAuthState(request.getSession(true), new AuthStateImpl(account, userNeedsToChangePassword));

        return result.toString();
    }

    @Override
    public URI createLoginUri(final String redirectUri) {
        LOGGER.debug("Sending user to login.");
        final UriBuilder uriBuilder = UriBuilder.fromUri(uriFactory.uiUri(AuthenticationService.LOGIN_URL_PATH))
                .queryParam("error", "login_required")
                .queryParam(OIDC.REDIRECT_URI, redirectUri);
        return uriBuilder.build();
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

    private static class AuthStateImpl implements AuthState {
        private final Account account;
        private final boolean requirePasswordChange;

        public AuthStateImpl(final Account account, final boolean requirePasswordChange) {
            this.account = account;
            this.requirePasswordChange = requirePasswordChange;
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
    }
}
