package stroom.authentication.resources.authentication.v1;

import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.*;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.config.PasswordIntegrityChecksConfig;
import stroom.authentication.impl.db.TokenDao;
import stroom.authentication.impl.db.UserDao;
import stroom.authentication.resources.token.v1.Token;
import stroom.authentication.resources.user.v1.User;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.impl.ExchangeAccessCodeRequest;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javax.ws.rs.core.Response.seeOther;
import static javax.ws.rs.core.Response.status;
import static stroom.authentication.resources.authentication.v1.PasswordValidator.*;
import static stroom.authentication.resources.authentication.v1.PasswordValidator.validateComplexity;

public class AuthenticationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationService.class);

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String ACCOUNT_LOCKED_MESSAGE = "This account is locked. Please contact your administrator";
    private static final String ACCOUNT_DISABLED_MESSAGE = "This account is disabled. Please contact your administrator";
    private static final String ACCOUNT_INACTIVE_MESSAGE = "This account is marked as inactive. Please contact your administrator";
    private static final String NO_SESSION_MESSAGE = "You have no session. Please make an AuthenticationRequest to the Authentication Service.";
    private static final String SUCCESSFUL_LOGIN_MESSAGE = "User logged in successfully.";
    private static final String FAILED_LOGIN_MESSAGE = "User attempted to log in but failed.";

    private AuthenticationConfig config;
    private TokenBuilderFactory tokenBuilderFactory;
    private final TokenDao tokenDao;
    private EmailSender emailSender;
    private SessionManager sessionManager;
    private final UserDao userDao;
    private StroomEventLoggingService stroomEventLoggingService;
    private SecurityContext securityContext;

    @Inject
    public AuthenticationService(
            final @NotNull AuthenticationConfig config,
            final TokenBuilderFactory tokenBuilderFactory,
            final TokenDao tokenDao,
            final EmailSender emailSender,
            final SessionManager sessionManager,
            final UserDao userDao,
            final StroomEventLoggingService stroomEventLoggingService,
            final SecurityContext securityContext) {
        this.config = config;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.tokenDao = tokenDao;
        this.emailSender = emailSender;
        this.sessionManager = sessionManager;
        this.userDao = userDao;
        this.stroomEventLoggingService = stroomEventLoggingService;
        this.securityContext = securityContext;
    }

    public Response.ResponseBuilder handleAuthenticationRequest(
            final String sessionId,
            final String nonce,
            final String state,
            final String redirectUrl,
            final String clientId,
            final String prompt,
            final Optional<String> optionalCn) {

        boolean isAuthenticated = false;

        LOGGER.info("Received an AuthenticationRequest for session " + sessionId);

        // We need to make sure our understanding of the session is correct
        Optional<Session> optionalSession = sessionManager.get(sessionId);
        if (optionalSession.isPresent()) {
            // If we have an authenticated session then the user is logged in
            isAuthenticated = optionalSession.get().isAuthenticated();
        } else {
            // If we've not created a session then we need to create a new, unauthenticated one
            optionalSession = Optional.of(sessionManager.create(sessionId));
            optionalSession.get().setAuthenticated(false);
        }


        // We need to make sure we record this relying party against this session
        RelyingParty relyingParty = optionalSession.get().getOrCreateRelyingParty(clientId);
        relyingParty.setNonce(nonce);
        relyingParty.setState(state);
        relyingParty.setRedirectUrl(redirectUrl);


        // Now we can check if we're logged in somehow (session or certs) and build the response accordingly
        Response.ResponseBuilder responseBuilder;
//        Optional<String> optionalCn = certificateManager.getCertificate(httpServletRequest);

        // If the prompt is 'login' then we always want to prompt the user to login in with username and password.
        boolean requireLoginPrompt = prompt != null && prompt.equalsIgnoreCase("login");
        boolean loginUsingCertificate = optionalCn.isPresent() && !requireLoginPrompt;
        if (requireLoginPrompt) {
            LOGGER.info("Relying party requested a user login page by using 'prompt=login'");
        }


        // Check for an authenticated session
        if (isAuthenticated) {
            LOGGER.debug("User has a session, sending them to the RP");
            String accessCode = SessionManager.createAccessCode();
            relyingParty.setAccessCode(accessCode);
            String subject = optionalSession.get().getUserEmail();
            String idToken = createIdToken(subject, nonce, state, sessionId);
            relyingParty.setIdToken(idToken);
            responseBuilder = seeOther(buildRedirectionUrl(redirectUrl, accessCode, state));
        }
        // Check for a certificate
        else if (loginUsingCertificate) {
            String cn = optionalCn.get();
            LOGGER.debug("User has presented a certificate: {}", cn);
            Optional<String> optionalSubject = getIdFromCertificate(cn);

            if (!optionalSubject.isPresent()) {
                String errorMessage = "User is presenting a certificate but this certificate cannot be processed!";
                LOGGER.error(errorMessage);
                responseBuilder = status(Response.Status.FORBIDDEN).entity(errorMessage);
            } else {
                String subject = optionalSubject.get();
                if (!userDao.exists(subject)) {
                    LOGGER.debug("The user identified by the certificate does not exist in the auth database.");
                    // There's no user so we can't let them have access.
                    responseBuilder = seeOther(UriBuilder.fromUri(this.config.getUnauthorisedUrl()).build());
                } else {
                    User user = userDao.get(subject).get();
                    if (user.getState().equals(User.UserState.ENABLED.getStateText())) {
                        LOGGER.info("Logging user in using DN with subject {}", subject);
                        optionalSession.get().setAuthenticated(true);
                        optionalSession.get().setUserEmail(subject);
                        String accessCode = SessionManager.createAccessCode();
                        relyingParty.setAccessCode(accessCode);
                        String idToken = createIdToken(subject, nonce, state, sessionId);
                        relyingParty.setIdToken(idToken);
                        responseBuilder = seeOther(buildRedirectionUrl(redirectUrl, accessCode, state));
                        stroomEventLoggingService.createAction("Logon", "User logged in successfully");
                        // Reset last access, login failures, etc...
                        userDao.recordSuccessfulLogin(subject);
                    } else {
                        LOGGER.debug("The user identified by the certificate is not enabled!");
                        stroomEventLoggingService.createAction(
                                "Logon",
                                "User attempted to log in but failed because the account is " + User.UserState.LOCKED.getStateText() + ".");
                        String failureUrl = this.config.getUnauthorisedUrl() + "?reason=account_locked";
                        responseBuilder = seeOther(UriBuilder.fromUri(failureUrl).build());
                    }
                }

            }
        }
        // There's no session and there's no certificate so we'll send them to the login page
        else {
            LOGGER.debug("User has no session and no certificate - sending them to login.");
            final UriBuilder uriBuilder = UriBuilder.fromUri(this.config.getLoginUrl())
                    .queryParam("error", "login_required")
                    .queryParam("state", state)
                    .queryParam("clientId", clientId)
                    .queryParam("redirectUrl", redirectUrl);
            responseBuilder = seeOther(uriBuilder.build());
        }

        return responseBuilder;
    }

    public LoginResponse handleLogin(Credentials credentials, String sessionId) throws UnsupportedEncodingException {
        LoginResponse loginResponse;

        Optional<stroom.authentication.Session> optionalSession = sessionManager.get(sessionId);
        if (!optionalSession.isPresent()) {
            loginResponse = new LoginResponse(false, NO_SESSION_MESSAGE, null, 422);
        }
        else {
            stroom.authentication.Session session = optionalSession.get();

            // Check the credentials
            UserDao.LoginResult loginResult = userDao.areCredentialsValid(credentials.getEmail(), credentials.getPassword());
            switch (loginResult) {
                case BAD_CREDENTIALS:
                    LOGGER.debug("Password for {} is incorrect", credentials.getEmail());
                    boolean shouldLock = userDao.incrementLoginFailures(credentials.getEmail());
                    stroomEventLoggingService.createAction("Logon", FAILED_LOGIN_MESSAGE);

                    if (shouldLock) {
                        loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                    } else {
                        loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                    }
                    break;
                case GOOD_CREDENTIALS:
                    String redirectionUrl = processSuccessfulLogin(session, credentials, sessionId);
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
                            "User attempted to log in but failed because the account is " + User.UserState.LOCKED.getStateText() + ".");
                    loginResponse = new LoginResponse(false, ACCOUNT_LOCKED_MESSAGE, null, 200);
                    break;
                case DISABLED_BAD_CREDENTIALS:
                    // If the credentials are bad we don't want to reveal the status of the account to the user.
                    stroomEventLoggingService.createAction(
                            "Logon",
                            "User attempted to log in but failed because the account is " + User.UserState.DISABLED.getStateText() + ".");
                    loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                    break;
                case DISABLED_GOOD_CREDENTIALS:
                    stroomEventLoggingService.createAction(
                            "Logon",
                            "User attempted to log in but failed because the account is " + User.UserState.DISABLED.getStateText() + ".");
                    loginResponse = new LoginResponse(false, ACCOUNT_DISABLED_MESSAGE, null, 200);
                    break;
                case INACTIVE_BAD_CREDENTIALS:
                    // If the credentials are bad we don't want to reveal the status of the account to the user.
                    stroomEventLoggingService.createAction(
                            "Logon",
                            "User attempted to log in but failed because the account is " + User.UserState.INACTIVE.getStateText() + ".");
                    loginResponse = new LoginResponse(false, INVALID_CREDENTIALS_MESSAGE, null, 200);
                    break;
                case INACTIVE_GOOD_CREDENTIALS:
                    stroomEventLoggingService.createAction(
                            "Logon",
                            "User attempted to log in but failed because the account is " + User.UserState.INACTIVE.getStateText() + ".");
                    loginResponse = new LoginResponse(false, ACCOUNT_INACTIVE_MESSAGE, null, 200);
                    break;
                default:
                    String errorMessage = String.format("%s does not support a LoginResult of %s",
                            this.getClass().getSimpleName(), loginResult.toString());
                    throw new NotImplementedException(errorMessage);
            }
        }
        return loginResponse;
    }

    public String logout(String sessionId, String redirectUrl) {
        sessionManager.get(sessionId).ifPresent(session -> {
            stroomEventLoggingService.createAction("Logout", "The user has logged out.");
        });

        sessionManager.logout(sessionId);

        // If we have a redirect URL then we'll use that, otherwise we'll go to the advertised host.
        final String postLogoutUrl =
                redirectUrl == null || redirectUrl == "" ? this.config.getAdvertisedHost() : redirectUrl;

        return postLogoutUrl;
    }

    public boolean resetEmail(final String emailAddress) {
        stroomEventLoggingService.createAction("ResetPassword", "User reset their password");
        Optional<User> user = userDao.get(emailAddress);
        if (user.isPresent()) {
            String resetToken = tokenDao.createEmailResetToken(emailAddress, config.getStroomConfig().getClientId());
            emailSender.send(user.get(), resetToken);
            return true;
        } else {
            return false;
        }
    }

    public ChangePasswordResponse changePassword(ChangePasswordRequest changePasswordRequest) {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();
        final UserDao.LoginResult loginResult = userDao.areCredentialsValid(changePasswordRequest.getEmail(), changePasswordRequest.getOldPassword());
        validateAuthenticity(loginResult).ifPresent(failedOn::add);
        validateReuse(changePasswordRequest.getOldPassword(), changePasswordRequest.getNewPassword()).ifPresent(failedOn::add);
        validateLength(changePasswordRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength()).ifPresent(failedOn::add);
        validateComplexity(changePasswordRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex()).ifPresent(failedOn::add);

        final ChangePasswordResponse.ChangePasswordResponseBuilder responseBuilder = ChangePasswordResponse.ChangePasswordResponseBuilder.aChangePasswordResponse();
        if (failedOn.size() == 0) {
            responseBuilder.withSuccess();
            stroomEventLoggingService.createAction("ChangePassword", "User reset their password");
            userDao.changePassword(changePasswordRequest.getEmail(), changePasswordRequest.getNewPassword());
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

            final ChangePasswordResponse.ChangePasswordResponseBuilder responseBuilder = ChangePasswordResponse.ChangePasswordResponseBuilder.aChangePasswordResponse();

            if (responseBuilder.failedOn.size() == 0) {
                responseBuilder.withSuccess();
                stroomEventLoggingService.createAction("ChangePassword", "User reset their password");
                userDao.changePassword(loggedInUser, request.getNewPassword());
            }

            return responseBuilder.build();
        } else return null;
    }

    public boolean needsPasswordChange(String email) {
        boolean userNeedsToChangePassword = userDao.needsPasswordChange(
                email, config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());

        return userNeedsToChangePassword;
    }

    public PasswordValidationResponse isPasswordValid(PasswordValidationRequest passwordValidationRequest) {
        List<PasswordValidationFailureType> failedOn = new ArrayList<>();

        if (passwordValidationRequest.getOldPassword() != null) {
            final UserDao.LoginResult loginResult = userDao.areCredentialsValid(passwordValidationRequest.getEmail(), passwordValidationRequest.getOldPassword());
            validateAuthenticity(loginResult).ifPresent(failedOn::add);
            validateReuse(passwordValidationRequest.getOldPassword(), passwordValidationRequest.getNewPassword()).ifPresent(failedOn::add);
        }

        validateLength(passwordValidationRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getMinimumPasswordLength()).ifPresent(failedOn::add);
        validateComplexity(passwordValidationRequest.getNewPassword(), config.getPasswordIntegrityChecksConfig().getPasswordComplexityRegex()).ifPresent(failedOn::add);


        PasswordValidationResponse response = PasswordValidationResponse.PasswordValidationResponseBuilder
                .aPasswordValidationResponse()
                .withFailedOn(failedOn.toArray(new PasswordValidationFailureType[0]))
                .build();

        return response;
    }

    public URI postAuthenticationRedirect(final String sessionId, final String clientId) {
        stroom.authentication.Session session = this.sessionManager.get(sessionId).get();
        RelyingParty relyingParty = session.getRelyingParty(clientId);

        String username = session.getUserEmail();

        boolean userNeedsToChangePassword = userDao.needsPasswordChange(
                username, config.getPasswordIntegrityChecksConfig().getMandatoryPasswordChangeDuration().getDuration(),
                config.getPasswordIntegrityChecksConfig().isForcePasswordChangeOnFirstLogin());

        URI result;
        if (userNeedsToChangePassword) {
            final String redirectUrl = getPostAuthenticationCheckUrl(clientId);
            result = UriBuilder.fromUri(this.config.getChangePasswordUrl())
                    .queryParam("redirect_url", redirectUrl)
                    .build();
        } else {
            //TODO this method needs to take just a relying party
            session.setAuthenticated(true);
            result = buildRedirectionUrl(relyingParty.getRedirectUrl(), relyingParty.getAccessCode(), relyingParty.getState());
        }
        return result;
    }

    public Optional<String> exchangeAccessCode(ExchangeAccessCodeRequest exchangeAccessCodeRequest) {
        Optional<RelyingParty> relyingParty = this.sessionManager.getByAccessCode(exchangeAccessCodeRequest.getAccessCode());
        if (!relyingParty.isPresent()) {
            return Optional.empty();
        }

        // See the comments in StroomConfig.
        if (config.getStroomConfig().getClientId().equals(config.getStroomConfig().getClientId())
                && config.getStroomConfig().getClientSecret().equals(config.getStroomConfig().getClientSecret())) {
            String idToken = relyingParty.get().getIdToken();
            relyingParty.get().forgetIdToken();
            relyingParty.get().forgetAccessCode();
            return Optional.of(idToken);
        } else {
            return Optional.empty();
        }
    }

    private String processSuccessfulLogin(final stroom.authentication.Session session,
                                          final Credentials credentials,
                                          final String sessionId) throws UnsupportedEncodingException {
        // Make sure the session is authenticated and ready for use
        session.setAuthenticated(false);
        session.setUserEmail(credentials.getEmail());

        //The relying party is the client making this request - now that we've authenticated for them we
        // can create the access code and id token.
        String accessCode = SessionManager.createAccessCode();
        RelyingParty relyingParty = session.getRelyingParty(credentials.getRequestingClientId());
        relyingParty.setAccessCode(accessCode);
        String idToken = createIdToken(credentials.getEmail(), relyingParty.getNonce(), relyingParty.getState(), sessionId);
        relyingParty.setIdToken(idToken);

        LOGGER.debug("Login for {} succeeded", credentials.getEmail());

        // Reset last access, login failures, etc...
        userDao.recordSuccessfulLogin(credentials.getEmail());

        String redirectionUrl = getPostAuthenticationCheckUrl(credentials.getRequestingClientId());
        return redirectionUrl;
    }

    private String getPostAuthenticationCheckUrl(String clientId) {
        String postAuthenticationCheckUrl = String.format("%s/%s/v1/postAuthenticationRedirect?clientId=%s",
                this.config.getAdvertisedHost(), config.getOwnPath(), clientId);
        return postAuthenticationCheckUrl;
    }

    private Optional<String> getIdFromCertificate(final String cn) {
        Pattern idExtractionPattern = Pattern.compile(this.config.getCertificateDnPattern());
        Matcher idExtractionMatcher = idExtractionPattern.matcher(cn);
        idExtractionMatcher.find();
        int captureGroupIndex = this.config.getCertificateDnCaptureGroupIndex();
        try {
            if (idExtractionMatcher.groupCount() >= captureGroupIndex) {
                String id = idExtractionMatcher.group(captureGroupIndex);
                return Optional.of(id);
            } else {
                return Optional.empty();
            }
        } catch (IllegalStateException ex) {
            LOGGER.error("Unable to extract user ID from CN. CN was {} and pattern was {}", cn,
                    this.config.getCertificateDnPattern());
            return Optional.empty();
        }
    }

    private URI buildRedirectionUrl(String redirectUrl, String accessCode, String state) {
        return UriBuilder
                .fromUri(redirectUrl)
                .replaceQueryParam("accessCode", accessCode)
                .replaceQueryParam("state", state)
                .build();
    }

    private String createIdToken(String subject, String nonce, String state, String authSessionId) {
        TokenBuilder tokenBuilder = tokenBuilderFactory
                .newBuilder(Token.TokenType.USER)
                .clientId(config.getStroomConfig().getClientId())
                .subject(subject)
                .nonce(nonce)
                .state(state)
                .authSessionId(authSessionId);
        Instant expiresOn = tokenBuilder.getExpiryDate();
        String idToken = tokenBuilder.build();

        tokenDao.createIdToken(idToken, subject, new Timestamp(expiresOn.toEpochMilli()));
        return idToken;
    }
}
