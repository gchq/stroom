/*
 * Copyright 2020 Crown Copyright
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

package stroom.security.identity.openid;

import stroom.config.common.UriFactory;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthState;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthStatus;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.TokenConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.token.MintedToken;
import stroom.security.identity.token.OAuthToken;
import stroom.security.identity.token.OAuthTokenDao;
import stroom.security.identity.token.OAuthTokenType;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.Pkce;
import stroom.security.openid.api.TokenResponse;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;

import event.logging.AuthenticateOutcomeReason;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.TemporalAmount;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


class OpenIdService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdService.class);

    private static final String UNKNOWN_SUBJECT = "Unknown";

    private final AccessCodeCache accessCodeCache;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final OAuthTokenDao oAuthTokenDao;
    private final AuthenticationService authenticationService;
    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final IdentityConfig identityConfig;
    private final UriFactory uriFactory;

    @Inject
    OpenIdService(final AccessCodeCache accessCodeCache,
                  final RefreshTokenStore refreshTokenStore,
                  final TokenBuilderFactory tokenBuilderFactory,
                  final OAuthTokenDao oAuthTokenDao,
                  final AuthenticationService authenticationService,
                  final OpenIdClientFactory openIdClientDetailsFactory,
                  final IdentityConfig identityConfig,
                  final UriFactory uriFactory) {
        this.accessCodeCache = accessCodeCache;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenBuilderFactory = tokenBuilderFactory;
        this.oAuthTokenDao = oAuthTokenDao;
        this.authenticationService = authenticationService;
        this.openIdClientDetailsFactory = openIdClientDetailsFactory;
        this.identityConfig = identityConfig;
        this.uriFactory = uriFactory;
    }

    public AuthResult auth(final HttpServletRequest request,
                           final String scope,
                           final String responseType,
                           final String clientId,
                           final String redirectUri,
                           @Nullable final String nonce,
                           @Nullable final String state,
                           @Nullable final String prompt,
                           @Nullable final String codeChallenge,
                           @Nullable final String codeChallengeMethod) {
        final URI result;
        AuthStatus authStatus = null;

        // Reject the request up front if the client id is not one we recognise, before sending the user
        // through sign in. Throws if it is unknown.
        openIdClientDetailsFactory.getClient(clientId);

        // After sign in attempts we want to come back here.
        final String postSignInRedirectUri = getPostSignInRedirectUri(request);

        if (!isRedirectUriAllowed(redirectUri)) {
            authStatus = badAuthRequest("Redirect URI is not allowed");
            result = authenticationService.createSignInUri(postSignInRedirectUri);

        } else if (!OpenId.RESPONSE_TYPE__CODE.equals(responseType)) {
            // The only flow this provider implements, and the only one its discovery document advertises.
            // Taking the parameter and never reading it left an authorize request able to ask for anything.
            authStatus = badAuthRequest("Only the 'code' response type is supported");
            result = authenticationService.createSignInUri(postSignInRedirectUri);

        } else if (!hasOpenIdScope(scope)) {
            // Without it this is not an OpenID Connect request at all, whatever else it asks for.
            authStatus = badAuthRequest("The 'openid' scope is required");
            result = authenticationService.createSignInUri(postSignInRedirectUri);

        } else if (NullSafe.isBlankString(nonce)) {
            // The nonce is what ties the id token this flow produces back to this request, so accepting a
            // request without one gives away the replay protection the token's nonce claim exists for.
            authStatus = badAuthRequest("A nonce is required");
            result = authenticationService.createSignInUri(postSignInRedirectUri);

        } else if (!isValidS256Pkce(codeChallenge, codeChallengeMethod)) {
            // PKCE (RFC 7636) is required. Only S256 is accepted; 'plain' is not.
            authStatus = badAuthRequest("A valid S256 PKCE code challenge is required");
            result = authenticationService.createSignInUri(postSignInRedirectUri);

        } else {
            // If the prompt is 'login' then we always want to prompt the user to login in with username and password.
            final boolean requireLoginPrompt = prompt != null && prompt.equalsIgnoreCase(OpenId.LOGIN_PROMPT);
            if (requireLoginPrompt) {
                LOGGER.info("Relying party requested a user login page by using 'prompt=login'");
            }

            if (requireLoginPrompt) {
                LOGGER.debug("Login has been requested by the RP");
                result = authenticationService.createSignInUri(postSignInRedirectUri);

            } else {
                // We need to make sure our understanding of the session is correct
                authStatus = authenticationService.currentAuthState(request);

                if (authStatus.getError().isPresent()) {
                    final BadRequestException badRequestException = authStatus.getError().get();
                    LOGGER.error("Error authenticating request {} for {} got {} - {}",
                            request.getRequestURI(),
                            badRequestException.getSubject(),
                            badRequestException.getReason().value(),
                            badRequestException.getMessage());
                    //Send back to log in with username/password
//                    result = authenticationService.createSignInUri(postSignInRedirectUri);
                    result = authenticationService.createErrorUri(authStatus.getError().get());

                } else if (authStatus.getAuthState().isPresent()) {
                    // If we have an authenticated session then the user is logged in
                    final AuthState authState = authStatus.getAuthState().get();

                    // If the users password still needs to be changed then send them back to the login page.
                    if (authState.isRequirePasswordChange()) {
                        result = authenticationService.createSignInUri(postSignInRedirectUri);

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
                                prompt,
                                codeChallenge);
                        accessCodeCache.put(accessCode, accessCodeRequest);

                        result = buildRedirectionUrl(redirectUri, accessCode, state);
                    }

                } else {
                    LOGGER.debug("User has no session and no certificate - sending them to login.");
                    result = authenticationService.createSignInUri(postSignInRedirectUri);
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
        if (!Objects.equals(clientId, accessCodeRequest.getClientId())) {
            throw new BadRequestException(UNKNOWN_SUBJECT, AuthenticateOutcomeReason.OTHER,
                    "Unexpected client id");
        }

        if (!Objects.equals(redirectUri, accessCodeRequest.getRedirectUri())) {
            throw new BadRequestException(UNKNOWN_SUBJECT, AuthenticateOutcomeReason.OTHER,
                    "Unexpected redirect URI");
        }

        final OpenIdClient oAuth2Client = openIdClientDetailsFactory.getClient(clientId);

        if (!secretsMatch(clientSecret, oAuth2Client.getClientSecret())) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Incorrect secret");
        }

        if (!isRedirectUriAllowed(redirectUri)) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Redirect URI is not allowed");
        }

        // PKCE: the caller must prove it began the flow by presenting the verifier for the stored
        // challenge. The challenge is always present because the authorization endpoint requires it.
        final String codeVerifier = formParams.getFirst(OpenId.CODE_VERIFIER);
        if (!isCodeVerifierValid(codeVerifier, accessCodeRequest.getCodeChallenge())) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Invalid PKCE code verifier");
        }

        // This is a fresh login, so the authentication time is now and this token starts a new family.
        return createTokenResponse(
                clientId,
                accessCodeRequest.getSubject(),
                accessCodeRequest.getNonce(),
                accessCodeRequest.getScope(),
                Instant.now().getEpochSecond(),
                UUID.randomUUID().toString());
    }

    public TokenResponse refreshIdToken(final MultivaluedMap<String, String> formParams) {
        final String clientId = formParams.getFirst(OpenId.CLIENT_ID);
        final String clientSecret = formParams.getFirst(OpenId.CLIENT_SECRET);
        final String refreshToken = formParams.getFirst(OpenId.REFRESH_TOKEN);

        if (refreshToken == null) {
            throw new BadRequestException("Null refresh token",
                    AuthenticateOutcomeReason.OTHER, "No refresh token has been supplied");
        }

        // Authenticate the client before touching the token store, so only a caller that holds the client
        // secret can redeem or, by replaying a spent token, trigger revocation of a token family.
        final OpenIdClient oAuth2Client = openIdClientDetailsFactory.getClient(clientId);
        if (!secretsMatch(clientSecret, oAuth2Client.getClientSecret())) {
            throw new BadRequestException(oAuth2Client.getName(), AuthenticateOutcomeReason.OTHER,
                    "Incorrect secret");
        }

        // Consuming the presented token and issuing its successor happen in a single transaction inside the
        // store, so there is no window in which the client's token is spent but it has no replacement.
        final TokenConfig tokenConfig = identityConfig.getTokenConfig();
        final TemporalAmount refreshTokenExpiresIn = tokenConfig.getRefreshTokenExpiration();
        final RefreshTokenStore.Rotation rotation = refreshTokenStore
                .rotate(refreshToken, Instant.now().plus(refreshTokenExpiresIn).toEpochMilli())
                .orElseThrow(() -> new BadRequestException("Unknown refresh token",
                        AuthenticateOutcomeReason.OTHER,
                        "Refresh token already used, revoked or expired"));
        final RefreshTokenRecord record = rotation.record();

        // A refreshed id token reports the original login time, not now, and the successor stays in the
        // same rotation family. There is no fresh nonce as this is not a new authentication.
        return buildTokenResponse(
                record.clientId(),
                record.subject(),
                null,
                record.scope(),
                record.authTimeEpochSecond(),
                record.familyId(),
                rotation.successorToken(),
                refreshTokenExpiresIn);
    }

    private URI buildRedirectionUrl(final String redirectUri, final String code, final String state) {
        return UriBuilder
                .fromUri(redirectUri)
                .replaceQueryParam(OpenId.CODE, code)
                .replaceQueryParam(OpenId.STATE, state)
                .build();
    }

    // Package-private for testing.
    // An OpenID Connect authorization request has to ask for the 'openid' scope; without it this is a plain
    // OAuth request and the id token this flow issues was never asked for.
    static boolean hasOpenIdScope(final String scope) {
        return scope != null
               && Arrays.asList(scope.split(" ")).contains(OpenId.SCOPE__OPENID);
    }

    // Package-private for testing.
    // PKCE (RFC 7636) is mandatory and only the S256 method is accepted, so an authorization request must
    // carry a code challenge and name the S256 method.
    boolean isValidS256Pkce(final String codeChallenge, final String codeChallengeMethod) {
        return codeChallenge != null
               && !codeChallenge.isBlank()
               && OpenId.CODE_CHALLENGE_METHOD__S256.equals(codeChallengeMethod);
    }

    // Package-private for testing.
    // Verify a presented code_verifier against the code_challenge stored with the code: the S256 hash of
    // the verifier must equal the challenge.
    boolean isCodeVerifierValid(final String codeVerifier, final String codeChallenge) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }
        return codeChallenge.equals(Pkce.createS256Challenge(codeVerifier));
    }

    private AuthStatus badAuthRequest(final String message) {
        return new AuthStatus() {

            @Override
            public Optional<AuthState> getAuthState() {
                return Optional.empty();
            }

            @Override
            public Optional<BadRequestException> getError() {
                return Optional.of(new BadRequestException(UNKNOWN_SUBJECT,
                        AuthenticateOutcomeReason.OTHER, message));
            }

            @Override
            public boolean isNew() {
                return true;
            }
        };
    }

    // Package-private for testing.
    // The internal identity provider accepts a single redirect_uri, matched exactly: the application's own
    // OIDC sign-in callback (the BFF endpoint the IdP redirects back to). The relying party (this same
    // application) always sends exactly this value, so any other is rejected. This mirrors how a client's
    // redirect_uri is pre-registered and exact-matched at Keycloak, Duende and other providers.
    boolean isRedirectUriAllowed(final String redirectUri) {
        final String allowedRedirectUri = uriFactory.publicUri(
                ResourcePaths.buildSignInOidcCallbackPath()).toString();
        final boolean allowed = Objects.equals(redirectUri, allowedRedirectUri);
        if (!allowed) {
            LOGGER.warn("Rejecting redirect_uri '{}'; the only permitted value is '{}'",
                    redirectUri, allowedRedirectUri);
        }
        return allowed;
    }

    private String getPostSignInRedirectUri(final HttpServletRequest request) {
        // We have a new request so we're going to redirect with an AuthenticationRequest.
        // Get the redirect URL for the auth service from the current request.
        final String originalPath = request.getRequestURI() + Optional.ofNullable(request.getQueryString())
                .map(queryStr -> "?" + queryStr)
                .orElse("");

        // Dropwiz is likely sat behind Nginx with requests reverse proxied to it
        // so we need to append just the path/query part to the public URI defined in config
        // rather than using the full url of the request
        final String uri = uriFactory.publicUri(originalPath).toString();

        final UriBuilder uriBuilder = UriBuilder.fromUri(uri);
        // Ensure we have no prompt so we don't go round in circles.
        uriBuilder.replaceQueryParam(OpenId.PROMPT, "");
        return uriBuilder.build().toString();
    }

    /**
     * Mint a fresh set of tokens at initial authentication, including a brand new refresh token starting a
     * new rotation family. A refresh takes {@link #buildTokenResponse} directly, because its successor
     * refresh token has already been issued as part of consuming the predecessor.
     */
    private TokenResponse createTokenResponse(final String clientId,
                                              final String subject,
                                              final String nonce,
                                              final String scope,
                                              final long authTimeEpochSecond,
                                              final String familyId) {
        final TokenConfig tokenConfig = identityConfig.getTokenConfig();
        final TemporalAmount refreshTokenExpiresIn = tokenConfig.getRefreshTokenExpiration();

        // The refresh token is an opaque string backed by a durable row, never a JWT.
        final String refreshToken = refreshTokenStore.issue(new RefreshTokenRecord(
                clientId,
                subject,
                scope,
                authTimeEpochSecond,
                familyId,
                Instant.now().plus(refreshTokenExpiresIn).toEpochMilli()));

        return buildTokenResponse(clientId, subject, nonce, scope, authTimeEpochSecond, familyId,
                refreshToken, refreshTokenExpiresIn);
    }

    private TokenResponse buildTokenResponse(final String clientId,
                                             final String subject,
                                             final String nonce,
                                             final String scope,
                                             final long authTimeEpochSecond,
                                             final String familyId,
                                             final String refreshToken,
                                             final TemporalAmount refreshTokenExpiresIn) {
        final TokenConfig tokenConfig = identityConfig.getTokenConfig();
        final Instant now = Instant.now();

        // The nonce and auth_time belong to the id token; nonce binds it to this authentication request.
        final MintedToken idToken = tokenBuilderFactory.builder()
                .expirationTime(now.plus(tokenConfig.getIdTokenExpiration()))
                .clientId(clientId)
                .subject(subject)
                .nonce(nonce)
                .authTime(authTimeEpochSecond)
                .build();

        final MintedToken accessToken = tokenBuilderFactory.builder()
                .expirationTime(now.plus(tokenConfig.getAccessTokenExpiration()))
                .clientId(clientId)
                .subject(subject)
                .scope(scope)
                // Mark this as an access token so it, and not the id or refresh token, may be presented
                // as a bearer credential.
                .type(OpenId.TOKEN_TYPE__ACCESS)
                .build();

        recordInInventory(OAuthTokenType.ID, idToken, clientId, subject, scope, authTimeEpochSecond, familyId);
        recordInInventory(
                OAuthTokenType.ACCESS, accessToken, clientId, subject, scope, authTimeEpochSecond, familyId);

        final Long refreshTokenExpiresInSeconds = Duration.from(refreshTokenExpiresIn).toMillis() / 1000;

        final long expiresInSeconds = tokenConfig.getIdTokenExpiration().toMillis() / 1000;

        return TokenResponse.builder()
                .idToken(idToken.token())
                .accessToken(accessToken.token())
                .refreshToken(refreshToken)
                .expiresIn(expiresInSeconds)
                .refreshTokenExpiresIn(refreshTokenExpiresInSeconds)
                .build();
    }

    /**
     * Record a minted JWT in the token inventory so that it can later be listed and revoked.
     * <p>
     * Only a reference is stored - the {@code jti}, never the token itself. Nothing reads these rows yet;
     * they exist so that the revoked-jti denylist has something to be built from.
     * </p>
     */
    private void recordInInventory(final OAuthTokenType tokenType,
                                   final MintedToken mintedToken,
                                   final String clientId,
                                   final String subject,
                                   final String scope,
                                   final long authTimeEpochSecond,
                                   final String familyId) {
        oAuthTokenDao.create(OAuthToken.newJwt(
                tokenType,
                mintedToken.jti(),
                subject,
                clientId,
                // Same family as the refresh token from the same grant, so revoking a family kills the
                // access and id tokens it produced as well as its refresh lineage.
                familyId,
                scope,
                authTimeEpochSecond * 1000L,
                System.currentTimeMillis(),
                // One second beyond the exp claim - see MintedToken.inventoryExpiresMs().
                mintedToken.inventoryExpiresMs()));
    }

    /**
     * Compares a supplied client secret against the stored one in constant time, so the time taken does
     * not reveal how many leading characters matched - which would let the secret be recovered a character
     * at a time. A null on either side fails closed: the stored secret is never null ({@link OpenIdClient}
     * requires it), and a request that omits the secret must not authenticate.
     */
    private static boolean secretsMatch(final String supplied, final String expected) {
        if (supplied == null || expected == null) {
            return false;
        }
        return MessageDigest.isEqual(
                supplied.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
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
