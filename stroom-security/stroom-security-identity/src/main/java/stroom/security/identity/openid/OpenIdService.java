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

package stroom.security.identity.openid;

import stroom.config.common.UriFactory;
import stroom.security.identity.authenticate.api.AuthenticationService;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthState;
import stroom.security.identity.authenticate.api.AuthenticationService.AuthStatus;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.config.TokenConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.token.TokenBuilderFactory;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.openid.api.OpenIdClientFactory;
import stroom.security.openid.api.Pkce;
import stroom.security.openid.api.TokenResponse;
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
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;


class OpenIdService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenIdService.class);

    private static final String UNKNOWN_SUBJECT = "Unknown";

    private final AccessCodeCache accessCodeCache;
    private final RefreshTokenCache refreshTokenCache;
    private final TokenBuilderFactory tokenBuilderFactory;
    private final AuthenticationService authenticationService;
    private final OpenIdClientFactory openIdClientDetailsFactory;
    private final IdentityConfig identityConfig;
    private final UriFactory uriFactory;

    @Inject
    OpenIdService(final AccessCodeCache accessCodeCache,
                  final RefreshTokenCache refreshTokenCache,
                  final TokenBuilderFactory tokenBuilderFactory,
                  final AuthenticationService authenticationService,
                  final OpenIdClientFactory openIdClientDetailsFactory,
                  final IdentityConfig identityConfig,
                  final UriFactory uriFactory) {
        this.accessCodeCache = accessCodeCache;
        this.refreshTokenCache = refreshTokenCache;
        this.tokenBuilderFactory = tokenBuilderFactory;
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

        final RefreshTokenRecord record = refreshTokenCache.consume(refreshToken)
                .orElseThrow(() -> new BadRequestException("Unknown refresh token",
                        AuthenticateOutcomeReason.OTHER,
                        "Refresh token already used, revoked or no longer remembered"));

        // A refreshed id token reports the original login time, not now, and the successor stays in the
        // same rotation family. There is no fresh nonce as this is not a new authentication.
        return createTokenResponse(
                record.clientId(),
                record.subject(),
                null,
                record.scope(),
                record.authTimeEpochSecond(),
                record.familyId());
    }

    private URI buildRedirectionUrl(final String redirectUri, final String code, final String state) {
        return UriBuilder
                .fromUri(redirectUri)
                .replaceQueryParam(OpenId.CODE, code)
                .replaceQueryParam(OpenId.STATE, state)
                .build();
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

    private TokenResponse createTokenResponse(final String clientId,
                                              final String subject,
                                              final String nonce,
                                              final String scope,
                                              final long authTimeEpochSecond,
                                              final String familyId) {
        final TokenConfig tokenConfig = identityConfig.getTokenConfig();
        final Instant now = Instant.now();

        // The nonce and auth_time belong to the id token; nonce binds it to this authentication request.
        final String idToken = tokenBuilderFactory.builder()
                .expirationTime(now.plus(tokenConfig.getIdTokenExpiration()))
                .clientId(clientId)
                .subject(subject)
                .nonce(nonce)
                .authTime(authTimeEpochSecond)
                .build();

        final String accessToken = tokenBuilderFactory.builder()
                .expirationTime(now.plus(tokenConfig.getAccessTokenExpiration()))
                .clientId(clientId)
                .subject(subject)
                .scope(scope)
                // Mark this as an access token so it, and not the id or refresh token, may be presented
                // as a bearer credential.
                .type(OpenId.TOKEN_TYPE__ACCESS)
                .build();

        // The refresh token is an opaque string backed by server-side state, never a JWT.
        final TemporalAmount refreshTokenExpiresIn = tokenConfig.getRefreshTokenExpiration();
        final String refreshToken = refreshTokenCache.issue(new RefreshTokenRecord(
                clientId,
                subject,
                scope,
                authTimeEpochSecond,
                familyId,
                now.plus(refreshTokenExpiresIn).toEpochMilli()));
        final Long refreshTokenExpiresInSeconds = Duration.from(refreshTokenExpiresIn).toMillis() / 1000;

        final long expiresInSeconds = tokenConfig.getIdTokenExpiration().toMillis() / 1000;

        return TokenResponse.builder()
                .idToken(idToken)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(expiresInSeconds)
                .refreshTokenExpiresIn(refreshTokenExpiresInSeconds)
                .build();
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
