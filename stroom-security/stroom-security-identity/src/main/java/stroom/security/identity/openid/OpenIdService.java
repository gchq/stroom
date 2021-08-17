package stroom.security.identity.openid;

import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthState;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthStatus;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.TokenResponse;

import com.google.common.base.Objects;
import event.logging.AuthenticateOutcomeReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriBuilder;


class OpenIdService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdService.class);

    private static final String UNKNOWN_SUBJECT = "Unknown";

    private final AccessCodeCache accessCodeCache;
    private final RefreshTokenCache refreshTokenCache;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final AuthenticationService authenticationService;
    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final IdentityConfig identityConfig;

    @Inject
    OpenIdService(final AccessCodeCache accessCodeCache,
                  final RefreshTokenCache refreshTokenCache,
                  final TokenBuilderFactory tokenBuilderFactory,
                  final AuthenticationService authenticationService,
                  final OpenIdClientFactory openIdClientDetailsFactory,
                  final IdentityConfig identityConfig) {
        this.accessCodeCache = accessCodeCache;
        this.refreshTokenCache = refreshTokenCache;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.authenticationService = authenticationService;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.identityConfig = identityConfig;
    }

    public AuthResult auth(final HttpServletRequest request,
                           final String scope,
                           final String responseType,
                           final String clientId,
                           final String redirectUri,
                           @Nullable final String nonce,
                           @Nullable final String state,
                           @Nullable final String prompt) {
        URI result;
        AuthStatus authStatus = null;

        OpenIdClient oAuth2Client = openIdClientDetailsFactory.getClient(clientId);

        final Pattern pattern = Pattern.compile(oAuth2Client.getUriPattern());
        if (!pattern.matcher(redirectUri).matches()) {
            authStatus = new AuthStatus() {

                @Override
                public Optional<AuthState> getAuthState() {
                    return Optional.empty();
                }

                @Override
                public Optional<BadRequestException> getError() {
                    return Optional.of(new BadRequestException(UNKNOWN_SUBJECT,
                            AuthenticateOutcomeReason.OTHER, "Redirect URI is not allowed"));
                }

                @Override
                public boolean isNew() {
                    return true;
                }
            };

            result = authenticationService.createSignInUri(redirectUri);

        } else {
            // If the prompt is 'login' then we always want to prompt the user to login in with username and password.
            final boolean requireLoginPrompt = prompt != null && prompt.equalsIgnoreCase("login");
            if (requireLoginPrompt) {
                LOGGER.info("Relying party requested a user login page by using 'prompt=login'");
            }

            if (requireLoginPrompt) {
                LOGGER.debug("Login has been requested by the RP");
                result = authenticationService.createSignInUri(redirectUri);

            } else {
                // We need to make sure our understanding of the session is correct
                authStatus = authenticationService.currentAuthState(request);

                if (authStatus.getError().isPresent()) {
                    LOGGER.error("Error authenticating request {} for {} got {} - {}",
                            request.getRequestURI(),
                            authStatus.getError().get().getSubject(),
                            authStatus.getError().get().getReason().value(),
                            authStatus.getError().get().getMessage());
                    //Send back to log in with username/password
                    result = authenticationService.createSignInUri(redirectUri);

                } else if (authStatus.getAuthState().isPresent()) {
                    // If we have an authenticated session then the user is logged in
                    final AuthState authState = authStatus.getAuthState().get();

                    // If the users password still needs tp be changed then send them back to the login page.
                    if (authState.isRequirePasswordChange()) {
                        result = authenticationService.createSignInUri(redirectUri);

                    } else {
                        LOGGER.debug("User has a session, sending them back to the RP");

                        // We need to make sure we record this access code request.
                        final String accessCode = createAccessCode();

                        final AccessCodeRequest accessCodeRequest = new AccessCodeRequest(
                                scope,
                                responseType,
                                clientId,
                                redirectUri,
                                authState.getSubject(),
                                nonce,
                                state,
                                prompt);
                        accessCodeCache.put(accessCode, accessCodeRequest);

                        result = buildRedirectionUrl(redirectUri, accessCode, state);
                    }

                } else {
                    LOGGER.debug("User has no session and no certificate - sending them to login.");
                    result = authenticationService.createSignInUri(redirectUri);
                }

            }
        }

        return new AuthResult(result, authStatus);
    }

    private static String createAccessCode() {
        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[20];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().encodeToString(bytes);
    }

    public TokenResponse token(final MultivaluedMap<String, String> formParams) {
        final String grantType = formParams.getFirst(OpenId.GRANT_TYPE);
        if (OpenId.GRANT_TYPE__AUTHORIZATION_CODE.equals(grantType)) {
            return getIdToken(formParams);
        } else if (OpenId.REFRESH_TOKEN.equals(grantType)) {
            return refreshIdToken(formParams);
        }
        throw new BadRequestException("Unknown grant type",
                AuthenticateOutcomeReason.OTHER, "Unknown grant type " + grantType);
    }

    public TokenResponse getIdToken(final MultivaluedMap<String, String> formParams) {
        final String clientId = formParams.getFirst(OpenId.CLIENT_ID);
        final String clientSecret = formParams.getFirst(OpenId.CLIENT_SECRET);
        final String redirectUri = formParams.getFirst(OpenId.REDIRECT_URI);
        final String code = formParams.getFirst(OpenId.CODE);

        final Optional<AccessCodeRequest> optionalAccessCodeRequest = accessCodeCache.getAndRemove(code);
        if (optionalAccessCodeRequest.isEmpty()) {
            throw new BadRequestException(UNKNOWN_SUBJECT,
                    AuthenticateOutcomeReason.OTHER, "No access code request found");
        }

        final AccessCodeRequest accessCodeRequest = optionalAccessCodeRequest.get();
        if (!Objects.equal(clientId, accessCodeRequest.getClientId())) {
            throw new BadRequestException(UNKNOWN_SUBJECT, AuthenticateOutcomeReason.OTHER,
                    "Unexpected client id");
        }

        if (!Objects.equal(redirectUri, accessCodeRequest.getRedirectUri())) {
            throw new BadRequestException(UNKNOWN_SUBJECT, AuthenticateOutcomeReason.OTHER,
                    "Unexpected redirect URI");
        }

        final OpenIdClient oAuth2Client = openIdClientDetailsFactory.getClient(clientId);

        if (!Objects.equal(clientSecret, oAuth2Client.getClientSecret())) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Incorrect secret");
        }

        final Pattern pattern = Pattern.compile(oAuth2Client.getUriPattern());
        if (!pattern.matcher(redirectUri).matches()) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Redirect URI is not allowed");
        }

        final TemporalAmount expiresIn =
                identityConfig.getTokenConfig().getTokenExpiryTime();

        final TokenResponse tokenResponse = createTokenResponse(
                clientId,
                accessCodeRequest.getSubject(),
                accessCodeRequest.getNonce(),
                accessCodeRequest.getState(),
                accessCodeRequest.getScope(),
                expiresIn);

        refreshTokenCache.put(tokenResponse.getRefreshToken(),
                new TokenProperties(accessCodeRequest.getClientId(), accessCodeRequest.getSubject()));

        return tokenResponse;
    }

    public TokenResponse refreshIdToken(final MultivaluedMap<String, String> formParams) {
        final String clientId = formParams.getFirst(OpenId.CLIENT_ID);
        final String clientSecret = formParams.getFirst(OpenId.CLIENT_SECRET);
        final String refreshToken = formParams.getFirst(OpenId.REFRESH_TOKEN);

        if (refreshToken == null) {
            throw new BadRequestException("Null refresh token",
                    AuthenticateOutcomeReason.OTHER, "No refresh token has been supplied");
        }

        final Optional<TokenProperties> tokenPropertiesOptional = refreshTokenCache.getAndRemove(refreshToken);
        if (tokenPropertiesOptional.isEmpty()) {
            throw new BadRequestException("Unknown refresh token",
                    AuthenticateOutcomeReason.OTHER, "Refresh token already used or no longer remembered");
        }

        final OpenIdClient oAuth2Client = openIdClientDetailsFactory.getClient(clientId);

        if (!Objects.equal(clientSecret, oAuth2Client.getClientSecret())) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Incorrect secret");
        }

        final TemporalAmount expiresIn =
                identityConfig.getTokenConfig().getTokenExpiryTime();
        final TokenProperties tokenProperties = tokenPropertiesOptional.get();
        final TokenResponse tokenResponse = createTokenResponse(
                tokenProperties.getClientId(),
                tokenProperties.getSubject(),
                null,
                null,
                OpenId.SCOPE__OFFLINE_ACCESS,
                expiresIn);

        refreshTokenCache.put(tokenResponse.getRefreshToken(),
                new TokenProperties(tokenProperties.getClientId(), tokenProperties.getSubject()));

        return tokenResponse;
    }

    private URI buildRedirectionUrl(String redirectUri, String code, String state) {
        return UriBuilder
                .fromUri(redirectUri)
                .replaceQueryParam(OpenId.CODE, code)
                .replaceQueryParam(OpenId.STATE, state)
                .build();
    }

    private TokenResponse createTokenResponse(final String clientId,
                                              final String subject,
                                              final String nonce,
                                              final String state,
                                              final String scope,
                                              final TemporalAmount expiresIn) {
        final Instant now = Instant.now();

        final String idToken = tokenBuilderFactory.builder()
                .expirationTime(now.plus(expiresIn))
                .clientId(clientId)
                .subject(subject)
                .nonce(nonce)
                .state(state)
                .build();

        final String accessToken = tokenBuilderFactory.builder()
                .expirationTime(now.plus(expiresIn))
                .clientId(clientId)
                .subject(subject)
                .nonce(nonce)
                .state(state)
                .build();

        String refreshToken = null;
        Long refreshTokenExpiresInSeconds = null;
        if (scope.contains(OpenId.SCOPE__OFFLINE_ACCESS)) {
            final TemporalAmount refreshTokenExpiresIn = Duration.ofDays(1);
            refreshToken = tokenBuilderFactory.builder()
                    .expirationTime(now.plus(refreshTokenExpiresIn))
                    .clientId(clientId)
                    .subject(subject)
                    .nonce(nonce)
                    .state(state)
                    .build();
            refreshTokenExpiresInSeconds = Duration.from(refreshTokenExpiresIn).toMillis() / 1000;
        }

        final long expiresInSeconds = Duration.from(expiresIn).toMillis() / 1000;

        return TokenResponse.builder()
                .idToken(idToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresInSeconds)
                .refreshTokenExpiresIn(refreshTokenExpiresInSeconds)
                .build();
    }

    static class AuthResult {

        final URI uri;
        final AuthStatus status;

        AuthResult(final URI uri, @Nullable final AuthStatus status) {
            this.uri = uri;
            this.status = status;
        }

        public Optional<AuthStatus> getStatus() {
            return Optional.ofNullable(status);
        }

        public URI getUri() {
            return uri;
        }
    }
}
