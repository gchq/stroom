package stroom.authentication.authenticate;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.account.Account;
import stroom.authentication.account.AccountDao;
import stroom.authentication.account.AccountService;
import stroom.authentication.api.OIDC;
import stroom.authentication.authenticate.api.AuthSession;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.PasswordIntegrityChecksConfig;
import stroom.authentication.exceptions.BadRequestException;
import stroom.authentication.token.TokenDao;
import stroom.config.common.UriFactory;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;

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

import static stroom.authentication.authenticate.PasswordValidator.validateAuthenticity;
import static stroom.authentication.authenticate.PasswordValidator.validateComplexity;
import static stroom.authentication.authenticate.PasswordValidator.validateLength;
import static stroom.authentication.authenticate.PasswordValidator.validateReuse;

class AuthenticationService implements AuthSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String AUTHENTICATED_ACCOUNT = "AUTHENTICATED_ACCOUNT";
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String ACCOUNT_LOCKED_MESSAGE = "This account is locked. Please contact your administrator";
    private static final String ACCOUNT_DISABLED_MESSAGE = "This account is disabled. Please contact your administrator";
    private static final String ACCOUNT_INACTIVE_MESSAGE = "This account is marked as inactive. Please contact your administrator";
    //    private static final String NO_SESSION_MESSAGE = "You have no session. Please make an AuthenticationRequest to the Authentication Service.";
    private static final String SUCCESSFUL_LOGIN_MESSAGE = "User logged in successfully.";
    private static final String FAILED_LOGIN_MESSAGE = "User attempted to log in but failed.";

    private final UriFactory uriFactory;
    private AuthenticationConfig config;
    private final TokenDao tokenDao;
    private EmailSender emailSender;
    private final AccountDao accountDao;
    private final AccountService accountService;
    private StroomEventLoggingService stroomEventLoggingService;
    private SecurityContext securityContext;

    @Inject
    public AuthenticationService(
            final UriFactory uriFactory,
            final @NotNull AuthenticationConfig config,
            final TokenDao tokenDao,
            final EmailSender emailSender,
            final AccountDao accountDao,
            final AccountService accountService,
            final StroomEventLoggingService stroomEventLoggingService,
            final SecurityContext securityContext) {
        this.uriFactory = uriFactory;
        this.config = config;
        this.tokenDao = tokenDao;
        this.emailSender = emailSender;
        this.accountDao = accountDao;
        this.accountService = accountService;
        this.stroomEventLoggingService = stroomEventLoggingService;
        this.securityContext = securityContext;
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

    private void setSessionUser(final HttpSession session, final Account account) {
        if (session != null) {
            session.setAttribute(AUTHENTICATED_ACCOUNT, account);
        }
    }

    private Account getSessionUser(final HttpServletRequest request) {
        final HttpSession session = request.getSession(false);
        if (session != null) {
            return (Account) session.getAttribute(AUTHENTICATED_ACCOUNT);
        }
        return null;
    }

    @Override
    public Optional<String> currentSubject(final HttpServletRequest request) {
        Account account = getSessionUser(request);

        // Now we can check if we're logged in somehow (session or certs) and build the response accordingly
        if (account != null) {
            return Optional.ofNullable(account.getEmail());

        } else {
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
                        account = optionalAccount.get();
                        if (!account.isLocked() && !account.isInactive() && account.isEnabled()) {
                            LOGGER.info("Logging user in using DN with subject {}", subject);
                            setSessionUser(request.getSession(true), account);

                            stroomEventLoggingService.createAction("Logon", "User logged in successfully");
                            // Reset last access, login failures, etc...
                            accountDao.recordSuccessfulLogin(subject);

                            return Optional.ofNullable(account.getEmail());
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    public LoginResponse handleLogin(final Credentials credentials,
                                     final HttpServletRequest request,
                                     final String redirectUri) {
        LoginResponse loginResponse;

        setSessionUser(request.getSession(false), null);

        // Check the credentials
        LoginResult loginResult = accountDao.areCredentialsValid(credentials.getEmail(), credentials.getPassword());
        switch (loginResult) {
            case BAD_CREDENTIALS:
                LOGGER.debug("Password for {} is incorrect", credentials.getEmail());
                boolean shouldLock = accountDao.incrementLoginFailures(credentials.getEmail());
                stroomEventLoggingService.createAction("Logon", FAILED_LOGIN_MESSAGE);

                if (shouldLock) {
                    loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                } else {
                    loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                }
                break;
            case GOOD_CREDENTIALS:
                final Optional<Account> optionalAccount = accountService.get(credentials.getEmail());
                String redirectionUrl = processSuccessfulLogin(request, optionalAccount.get(), redirectUri);
                stroomEventLoggingService.createAction("Logon", SUCCESSFUL_LOGIN_MESSAGE);
                loginResponse = new LoginResponse(true, "", redirectionUrl, 200);
                break;
            case USER_DOES_NOT_EXIST:
                // We don't want to let the user know the account they tried to log in with doesn't exist.
                // If we did we'd be revealing the presence or absence of an account or email address and
                // we don't want to do this.
                stroomEventLoggingService.createAction("Logon", SUCCESSFUL_LOGIN_MESSAGE);
                loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                break;
            case LOCKED_BAD_CREDENTIALS:
                stroomEventLoggingService.createAction("Logon", FAILED_LOGIN_MESSAGE);
                loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                break;
            case LOCKED_GOOD_CREDENTIALS:
                // If the credentials are bad we don't want to reveal the status of the account to the user.
                stroomEventLoggingService.createAction(
                        "Logon",
                        "User attempted to log in but failed because the account is locked.");
                loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                break;
            case DISABLED_BAD_CREDENTIALS:
                // If the credentials are bad we don't want to reveal the status of the account to the user.
                stroomEventLoggingService.createAction(
                        "Logon",
                        "User attempted to log in but failed because the account is disabled.");
                loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                break;
            case DISABLED_GOOD_CREDENTIALS:
                stroomEventLoggingService.createAction(
                        "Logon",
                        "User attempted to log in but failed because the account is disabled.");
                loginResponse = new LoginResponse(false, ACCOUNT_DISABLED_MESSAGE, null, 200);
                break;
            case INACTIVE_BAD_CREDENTIALS:
                // If the credentials are bad we don't want to reveal the status of the account to the user.
                stroomEventLoggingService.createAction(
                        "Logon",
                        "User attempted to log in but failed because the account is inactive.");
                loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                break;
            case INACTIVE_GOOD_CREDENTIALS:
                stroomEventLoggingService.createAction(
                        "Logon",
                        "User attempted to log in but failed because the account is inactive.");
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
            stroomEventLoggingService.createAction("Logout", "The user has logged out.");
            setSessionUser(request.getSession(false), null);
        }
        return redirectUri;
    }

    public boolean resetEmail(final String emailAddress) {
        stroomEventLoggingService.createAction("ResetPassword", "User reset their password");
        Optional<Account> optionalAccount = accountDao.get(emailAddress);
        if (optionalAccount.isPresent()) {
            final Account account = optionalAccount.get();
            final String email = account.getEmail();
            final String firstName = account.getFirstName();
            final String lastName = account.getLastName();
            final String resetToken = tokenDao.createEmailResetToken(email, "stroom");

            Preconditions.checkNotNull(emailAddress, String.format("User %s has no email address", account));
            emailSender.send(emailAddress, firstName, lastName, resetToken);
            return true;
        } else {
            return false;
        }
    }

    public ChangePasswordResponse changePassword(ChangePasswordRequest changePasswordRequest) {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();
        final LoginResult loginResult = accountDao.areCredentialsValid(changePasswordRequest.getEmail(), changePasswordRequest.getOldPassword());
        validateAuthenticity(loginResult).ifPresent(failedOn::add);
        validateReuse(changePasswordRequest.getOldPassword(), changePasswordRequest.getNewPassword()).ifPresent(failedOn::add);
        validateLength(changePasswordRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength()).ifPresent(failedOn::add);
        validateComplexity(changePasswordRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex()).ifPresent(failedOn::add);

        final ChangePasswordResponse.ChangePasswordResponseBuilder responseBuilder = new ChangePasswordResponse.ChangePasswordResponseBuilder();
        if (failedOn.size() == 0) {
            responseBuilder.withSuccess();
            stroomEventLoggingService.createAction("ChangePassword", "User reset their password");
            accountDao.changePassword(changePasswordRequest.getEmail(), changePasswordRequest.getNewPassword());
        } else {
            responseBuilder.withFailedOn(failedOn);
        }

        return responseBuilder.build();
    }

    public ChangePasswordResponse resetPassword(ResetPasswordRequest request) {
        if (securityContext.isLoggedIn()) {
            final String loggedInUser = securityContext.getUserId();
            List<PasswordValidationFailureType> failedOn = new ArrayList<>();
            PasswordIntegrityChecksConfig conf = config.getPasswordIntegrityChecksConfig();

            validateLength(request.getNewPassword(), conf.getMinimumPasswordLength()).ifPresent(failedOn::add);
            validateComplexity(request.getNewPassword(), conf.getPasswordComplexityRegex()).ifPresent(failedOn::add);

            final ChangePasswordResponse.ChangePasswordResponseBuilder responseBuilder = new ChangePasswordResponse.ChangePasswordResponseBuilder();

            if (responseBuilder.failedOn.size() == 0) {
                responseBuilder.withSuccess();
                stroomEventLoggingService.createAction("ChangePassword", "User reset their password");
                accountDao.changePassword(loggedInUser, request.getNewPassword());
            } else {
                responseBuilder.withFailedOn(failedOn);
            }

            return responseBuilder.build();
        } else return null;
    }

    public boolean needsPasswordChange(String email) {
        return accountDao.needsPasswordChange(
                email, config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());
    }

    public PasswordValidationResponse isPasswordValid(PasswordValidationRequest passwordValidationRequest) {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();

        if (passwordValidationRequest.getOldPassword() != null) {
            final LoginResult loginResult = accountDao.areCredentialsValid(passwordValidationRequest.getEmail(), passwordValidationRequest.getOldPassword());
            validateAuthenticity(loginResult).ifPresent(failedOn::add);
            validateReuse(passwordValidationRequest.getOldPassword(), passwordValidationRequest.getNewPassword()).ifPresent(failedOn::add);
        }

        validateLength(passwordValidationRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength()).ifPresent(failedOn::add);
        validateComplexity(passwordValidationRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex()).ifPresent(failedOn::add);

        return new PasswordValidationResponse.PasswordValidationResponseBuilder()
                .withFailedOn(failedOn.toArray(new PasswordValidationFailureType[0]))
                .build();
    }

    public URI postAuthenticationRedirect(final HttpServletRequest request, final String redirectUri) {
        final Account account = getSessionUser(request);
        if (account == null) {
            throw new BadRequestException("User is not authenticated");
        }

        final String username = account.getEmail();

        boolean userNeedsToChangePassword = accountDao.needsPasswordChange(
                username, config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());

        URI result;

        if (userNeedsToChangePassword) {
            final String innerRedirectUri = getPostAuthenticationCheckUrl(redirectUri);
            result = UriBuilder.fromUri(uriFactory.publicURI(config.getChangePasswordUrl()))
                    .queryParam(OIDC.REDIRECT_URI, innerRedirectUri)
                    .build();
        } else {
            result = UriBuilder.fromUri(redirectUri).build();
        }

        return result;
    }

    private String processSuccessfulLogin(final HttpServletRequest request,
                                          final Account account,
                                          final String redirectUri) {
        // Make sure the session is authenticated and ready for use
        setSessionUser(request.getSession(false), null);

        LOGGER.debug("Login for {} succeeded", account.getEmail());

        // Reset last access, login failures, etc...
        accountDao.recordSuccessfulLogin(account.getEmail());
        setSessionUser(request.getSession(true), account);

        return getPostAuthenticationCheckUrl(redirectUri);
    }

    private String getPostAuthenticationCheckUrl(final String redirectUri) {
        final URI uri = UriBuilder
                .fromUri(uriFactory.publicURI(config.getOwnPath() + "/v1/noauth/postAuthenticationRedirect"))
                .queryParam(OIDC.REDIRECT_URI, redirectUri)
                .build();
        return uri.toString();
    }

    private Optional<String> getIdFromCertificate(final String cn) {
        final Pattern idExtractionPattern = Pattern.compile(this.config.getCertificateDnPattern());
        final Matcher idExtractionMatcher = idExtractionPattern.matcher(cn);
        if (idExtractionMatcher.find()) {
            final int captureGroupIndex = this.config.getCertificateDnCaptureGroupIndex();
            try {
                if (idExtractionMatcher.groupCount() >= captureGroupIndex) {
                    final String id = idExtractionMatcher.group(captureGroupIndex);
                    return Optional.of(id);
                }
            } catch (final IllegalStateException ex) {
                LOGGER.error("Unable to extract user ID from CN. CN was {} and pattern was {}", cn,
                        this.config.getCertificateDnPattern());
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
}
