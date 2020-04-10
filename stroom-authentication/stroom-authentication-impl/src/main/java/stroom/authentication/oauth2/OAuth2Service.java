package stroom.authentication.oauth2;

import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.api.OIDC;
import stroom.authentication.authenticate.api.AuthSession;
import stroom.authentication.config.AuthenticationConfig;
import stroom.authentication.exceptions.BadRequestException;
import stroom.authentication.token.Token;
import stroom.authentication.token.TokenBuilder;
import stroom.authentication.token.TokenBuilderFactory;
import stroom.config.common.UriFactory;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

class OAuth2Service {
    private static final Logger LOGGER = LoggerFactory.getLogger(OAuth2Service.class);

    private final UriFactory uriFactory;
    private final AuthenticationConfig authenticationConfig;
    private final AccessCodeCache accessCodeCache;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final AuthSession authSession;
    private final OAuth2ClientDao dao;

    @Inject
    OAuth2Service(final UriFactory uriFactory,
                  final AuthenticationConfig authenticationConfig,
                  final AccessCodeCache accessCodeCache,
                  final TokenBuilderFactory tokenBuilderFactory,
                  final AuthSession authSession,
                  final OAuth2ClientDao dao) {
        this.uriFactory = uriFactory;
        this.authenticationConfig = authenticationConfig;
        this.accessCodeCache = accessCodeCache;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.authSession = authSession;
        this.dao = dao;
    }

    public URI auth(final HttpServletRequest request,
                    final String scope,
                    final String responseType,
                    final String clientId,
                    final String redirectUri,
                    @Nullable final String nonce,
                    @Nullable final String state,
                    @Nullable final String prompt) {
        URI result;
        try {
            final Optional<OAuth2Client> optionalClient = dao.getClientForClientId(clientId);
            if (optionalClient.isEmpty()) {
                throw new BadRequestException("Unknown client with id=" + clientId);
            }

            final Pattern pattern = Pattern.compile(optionalClient.get().getUriPattern());
            if (!pattern.matcher(redirectUri).matches()) {
                throw new BadRequestException("Redirect URI is not allowed");
            }

            // If the prompt is 'login' then we always want to prompt the user to login in with username and password.
            final boolean requireLoginPrompt = prompt != null && prompt.equalsIgnoreCase("login");
            if (requireLoginPrompt) {
                LOGGER.info("Relying party requested a user login page by using 'prompt=login'");
            }

            // We need to make sure we record this access code request.
            final String accessCode = createAccessCode();
            final AccessCodeRequest accessCodeRequest = new AccessCodeRequest(
                    scope,
                    responseType,
                    clientId,
                    redirectUri,
                    nonce,
                    state,
                    prompt);
            accessCodeCache.put(accessCode, accessCodeRequest);

            if (requireLoginPrompt) {
                LOGGER.debug("Login has been requested by the RP");
                result = redirectToLoginPage(redirectUri);

            } else {
                // We need to make sure our understanding of the session is correct
                final Optional<String> optionalSubject = authSession.currentSubject(request);

                // If we have an authenticated session then the user is logged in
                if (optionalSubject.isPresent()) {
                    // Check for an authenticated session
                    LOGGER.debug("User has a session, sending them back to the RP");
                    result = buildRedirectionUrl(redirectUri, accessCode, state);

                } else {
                    LOGGER.debug("User has no session and no certificate - sending them to login.");
                    result = redirectToLoginPage(redirectUri);
                }
            }

        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage());
            result = UriBuilder.fromUri(uriFactory.publicURI(authenticationConfig.getUnauthorisedUrl())).build();
        }

        return result;
    }

    private URI redirectToLoginPage(final String redirectUri) {
        LOGGER.debug("Sending user to login.");
        final UriBuilder uriBuilder = UriBuilder.fromUri(uriFactory.publicURI(authenticationConfig.getLoginUrl()))
                .queryParam("error", "login_required")
                .queryParam(OIDC.REDIRECT_URI, redirectUri);
        return uriBuilder.build();
    }

    private static String createAccessCode() {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public TokenResponse token(final HttpServletRequest request, final TokenRequest tokenRequest) {
        final String grantType = tokenRequest.getGrantType();
        final String clientId = tokenRequest.getClientId();
        final String clientSecret = tokenRequest.getClientSecret();
        final String redirectUri = tokenRequest.getRedirectUri();
        final String code = tokenRequest.getCode();

        final HttpSession httpSession = request.getSession(false);
        if (httpSession == null) {
            throw new BadRequestException("No HTTP session");
        }

        final Optional<String> optionalSubject = authSession.currentSubject(request);
        if (optionalSubject.isEmpty()) {
            throw new BadRequestException("No authenticated subject");
        }

        final Optional<AccessCodeRequest> optionalAccessCodeRequest = accessCodeCache.getAndRemove(code);
        if (optionalAccessCodeRequest.isEmpty()) {
            throw new BadRequestException("No access code request found");
        }

        final AccessCodeRequest accessCodeRequest = optionalAccessCodeRequest.get();
        if (!Objects.equal(clientId, accessCodeRequest.getClientId())) {
            throw new BadRequestException("Unexpected client id");
        }

        if (!Objects.equal(redirectUri, accessCodeRequest.getRedirectUri())) {
            throw new BadRequestException("Unexpected redirect URI");
        }

        final Optional<OAuth2Client> optionalClient = dao.getClientForClientId(clientId);
        if (optionalClient.isEmpty()) {
            throw new BadRequestException("Unknown client with id=" + clientId);
        }

        if (!Objects.equal(clientSecret, optionalClient.get().getClientSecret())) {
            throw new BadRequestException("Incorrect secret");
        }

        final Pattern pattern = Pattern.compile(optionalClient.get().getUriPattern());
        if (!pattern.matcher(redirectUri).matches()) {
            throw new BadRequestException("Redirect URI is not allowed");
        }

        final String subject = optionalSubject.get();
        final String token = createIdToken(clientId, subject, accessCodeRequest.getNonce(), accessCodeRequest.getState());
        return new TokenResponse(token);
    }

    private URI buildRedirectionUrl(String redirectUri, String code, String state) {
        return UriBuilder
                .fromUri(redirectUri)
                .replaceQueryParam(OIDC.CODE, code)
                .replaceQueryParam(OIDC.STATE, state)
                .build();
    }

    private String createIdToken(final String clientId,
                                 final String subject,
                                 final String nonce,
                                 final String state) {
        final TokenBuilder tokenBuilder = tokenBuilderFactory
                .newBuilder(Token.TokenType.USER)
                .clientId(clientId)
                .subject(subject)
                .nonce(nonce)
                .state(state);
//                .authSessionId(authSessionId);
//        Instant expiresOn = tokenBuilder.getExpiryDate();
        return tokenBuilder.build();
    }
}
