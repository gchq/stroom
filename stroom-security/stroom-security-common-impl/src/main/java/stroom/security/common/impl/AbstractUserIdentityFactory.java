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

package stroom.security.common.impl;

import stroom.security.api.HasJwt;
import stroom.security.api.HasSession;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.openid.api.TokenResponse;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.authentication.HasRefreshable;
import stroom.util.authentication.Refreshable;
import stroom.util.authentication.Refreshable.RefreshMode;
import stroom.util.cert.CertificateExtractor;
import stroom.util.concurrent.CachedValue;
import stroom.util.exception.ThrowingFunction;
import stroom.util.io.SimplePathCreator;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.string.TemplateUtil;
import stroom.util.string.TemplateUtil.Templator;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.jwt.consumer.JwtContext;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Singleton
public abstract class AbstractUserIdentityFactory implements UserIdentityFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AbstractUserIdentityFactory.class);

    private final JwtContextFactory jwtContextFactory;
    private final Provider<OpenIdConfiguration> openIdConfigProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final CertificateExtractor certificateExtractor;
    private final ServiceUserFactory serviceUserFactory;
    private final JerseyClientFactory jerseyClientFactory;
    private final SimplePathCreator simplePathCreator;
    private final CachedValue<Templator, String> cachedFullNameTemplate;

    // A service account/user for communicating with other apps in the same OIDC realm,
    // e.g. proxy => stroom. Created lazily.
    // This is tied to stroom/proxy's clientId, and we have only one of them
    private volatile UserIdentity serviceUserIdentity;

    private final RefreshManager refreshManager;
    // Don't change the configuration of this mapper after it is created, else not thread safe
    private final ObjectMapper objectMapper;
    private final IdpType idpType;

    public AbstractUserIdentityFactory(final JwtContextFactory jwtContextFactory,
                                       final Provider<OpenIdConfiguration> openIdConfigProvider,
                                       final DefaultOpenIdCredentials defaultOpenIdCredentials,
                                       final CertificateExtractor certificateExtractor,
                                       final ServiceUserFactory serviceUserFactory,
                                       final JerseyClientFactory jerseyClientFactory,
                                       final SimplePathCreator simplePathCreator,
                                       final RefreshManager refreshManager) {
        this.jwtContextFactory = jwtContextFactory;
        this.openIdConfigProvider = openIdConfigProvider;
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.certificateExtractor = certificateExtractor;
        this.serviceUserFactory = serviceUserFactory;
        this.jerseyClientFactory = jerseyClientFactory;
        this.simplePathCreator = simplePathCreator;
        this.refreshManager = refreshManager;
        this.objectMapper = createObjectMapper();
        // Bake this in as a restart is required for this prop
        this.idpType = openIdConfigProvider.get().getIdentityProviderType();
        this.cachedFullNameTemplate = CachedValue.builder()
                .withMaxCheckIntervalMinutes(1)
                .withStateSupplier(() ->
                        NullSafe.nonBlankStringElse(openIdConfigProvider.get().getFullNameClaimTemplate(),
                                AbstractOpenIdConfig.DEFAULT_FULL_NAME_CLAIM_TEMPLATE))
                .withValueFunction(template -> TemplateUtil.parseTemplate(template))
                .build();
    }

    /**
     * Map the IDP identity provided by the {@link JwtContext} to a local user.
     *
     * @param jwtContext The identity on the IDP to map to a local user.
     * @param request    The HTTP request
     * @return A local {@link UserIdentity} if the identity can be mapped.
     */
    protected abstract Optional<UserIdentity> mapApiIdentity(final JwtContext jwtContext,
                                                             final HttpServletRequest request);

    /**
     * Map the IDP identity provided by the {@link JwtContext} and the
     * {@link TokenResponse}to a local user. This is for use in a UI based
     * authentication flow.
     *
     * @param jwtContext    The identity on the IDP to map to a local user.
     * @param request       The HTTP request
     * @param tokenResponse The token received from the IDP.
     * @return A local {@link UserIdentity} if the identity can be mapped.
     */
    protected abstract Optional<UserIdentity> mapAuthFlowIdentity(final JwtContext jwtContext,
                                                                  final HttpServletRequest request,
                                                                  final TokenResponse tokenResponse);

    @Override
    public Optional<UserIdentity> getApiUserIdentity(final HttpServletRequest request) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();

        if (IdpType.NO_IDP.equals(idpType)) {
            throw new IllegalStateException(
                    "Attempting to get user identity from tokens in request when " +
                    "identityProviderType set to NONE.");
        } else {
            // See if we can log in with a token if one is supplied. It is valid for it to not be present.
            // e.g. the front end calling API methods, as the user is held in session.
            try {
                final Optional<JwtContext> optJwtContext = jwtContextFactory.getJwtContext(request);

                optUserIdentity = optJwtContext.flatMap(jwtContext ->
                                mapApiIdentity(jwtContext, request))
                        .or(() -> {
                            LOGGER.trace(() ->
                                    "No JWS found in headers in request to " + request.getRequestURI());
                            return Optional.empty();
                        });
            } catch (final RuntimeException e) {
                throw new AuthenticationException("Error authenticating request to "
                                                  + request.getRequestURI() + " - " + e.getMessage(), e);
            }

            if (optUserIdentity.isEmpty()) {
                LOGGER.trace(() -> "Cannot get a valid JWS for API request to " + request.getRequestURI());
            } else {
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("Got API user identity "
                                 + optUserIdentity.map(Objects::toString).orElse("EMPTY"));
                }
            }
        }
        return optUserIdentity;
    }

    @Override
    public boolean hasAuthenticationToken(final HttpServletRequest request) {
        return jwtContextFactory.hasToken(request);
    }

    @Override
    public boolean hasAuthenticationCertificate(final HttpServletRequest request) {
        return certificateExtractor.extractCertificate(request).isPresent();
    }

    @Override
    public void removeAuthEntries(final Map<String, String> headers) {
        jwtContextFactory.removeAuthorisationEntries(headers);
    }

    @Override
    public Map<String, String> getServiceUserAuthHeaders() {
        if (IdpType.NO_IDP.equals(idpType)) {
            LOGGER.debug("IdpType is {}", idpType);
            return Collections.emptyMap();
        } else {
            final UserIdentity serviceUserIdentity = getServiceUserIdentity();
            return getAuthHeaders(serviceUserIdentity);
        }
    }

    @Override
    public Map<String, String> getAuthHeaders(final UserIdentity userIdentity) {

        if (userIdentity == null) {
            LOGGER.debug("Null user supplied");
            return Collections.emptyMap();

        } else {
            LOGGER.trace(() -> LogUtil.message("IdpType: {}, userIdentity type: {}",
                    idpType, userIdentity.getClass().getSimpleName()));

            if (IdpType.NO_IDP.equals(idpType)) {
                return Collections.emptyMap();

            } else if (IdpType.TEST_CREDENTIALS.equals(idpType)
                       && !serviceUserFactory.isServiceUser(userIdentity, getServiceUserIdentity())) {
                // The processing user is a bit special so even when using hard-coded default open id
                // creds the proc user uses tokens created by the internal IDP.
                LOGGER.debug("Using default token");
                return jwtContextFactory.createAuthorisationEntries(defaultOpenIdCredentials.getApiKey());

            } else if (userIdentity instanceof final HasJwt hasJwt) {
                LOGGER.debug(() -> LogUtil.message("Getting auth headers as {}, {}",
                        HasJwt.class.getSimpleName(),
                        userIdentity.getClass().getSimpleName()));

                // The JWT may be of the type that requires refreshing so check that first
                if (userIdentity instanceof final HasRefreshable hasRefreshable) {
                    final Refreshable refreshable = hasRefreshable.getRefreshable();
                    refreshable.refreshIfRequired(RefreshMode.JUST_IN_TIME, refreshManager::addOrUpdate);
                }

                // This is for stroom's internal IDP processing user identity (which we don't need to refresh as
                // ProcessingUserIdentityProviderImpl handles that) or for users that have come from
                // an AWS ALB with an access token that we don't update.
                final String accessToken = Objects.requireNonNull(hasJwt.getJwt(),
                        () -> "Null access token for userIdentity " + userIdentity);
                return jwtContextFactory.createAuthorisationEntries(accessToken);

            } else {
                LOGGER.debug(() -> "Wrong type of userIdentity " + userIdentity.getClass());
                return Collections.emptyMap();
            }
        }
    }

    @Override
    public Map<String, String> getAuthHeaders(final String token) {
        return jwtContextFactory.createAuthorisationEntries(token);
    }

    /**
     * Extracts the authenticated user's identity from http request when that
     * request is part of a UI based authentication flow with the IDP
     */
    public Optional<UserIdentity> getAuthFlowUserIdentity(final HttpServletRequest request,
                                                          final String code,
                                                          final AuthenticationState state) {
        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();

        if (IdpType.NO_IDP.equals(idpType)) {
            throw new IllegalStateException(
                    "Attempting to do OIDC auth flow with identityProviderType set to NONE.");
        }

        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();

        final TokenResponse tokenResponse = new OpenIdTokenRequestHelper(
                tokenEndpoint, openIdConfiguration, objectMapper, jerseyClientFactory)
                .withCode(code)
                .withGrantType(OpenId.GRANT_TYPE__AUTHORIZATION_CODE)
                .withRedirectUri(state.getRedirectUri())
                .sendRequest(true);

        final Optional<UserIdentity> optUserIdentity = jwtContextFactory.getJwtContext(tokenResponse.getIdToken())
                .flatMap(jwtContext ->
                        createUserIdentity(request, state, tokenResponse, jwtContext))
                .or(() -> {
                    throw new AuthenticationException("Unable to authenticate ID token");
                });

        LOGGER.debug(() -> "Got auth flow user identity "
                           + optUserIdentity.map(Objects::toString).orElse("EMPTY"));

        return optUserIdentity;
    }

    @Override
    public UserIdentity getServiceUserIdentity() {

        try {
            // Ideally the token will get recreated by the refresh queue just before
            // it expires so callers to this will find a token that is good to use and
            // thus won't be contended.
            final boolean didCreate;
            if (serviceUserIdentity == null) {
                synchronized (this) {
                    if (serviceUserIdentity == null) {
                        serviceUserIdentity = createServiceUserIdentity();
                        didCreate = true;
                    } else {
                        didCreate = false;
                    }
                }
            } else {
                didCreate = false;
            }

            // Make sure it is up-to-date before giving it out
            if (!didCreate && serviceUserIdentity instanceof final HasRefreshable hasRefreshable) {
                NullSafe.consume(hasRefreshable.getRefreshable(), refreshable ->
                        refreshable.refreshIfRequired(RefreshMode.JUST_IN_TIME, refreshManager::addOrUpdate));
            }

            return serviceUserIdentity;
        } catch (final Exception e) {
            throw new RuntimeException("Error getting service user identity - " + LogUtil.exceptionMessage(e), e);
        }
    }

    @Override
    public boolean isServiceUser(final UserIdentity userIdentity) {
        return serviceUserFactory.isServiceUser(userIdentity, getServiceUserIdentity());
    }

    /**
     * Refresh the user identity including any tokens associated with that user.
     *
     * @param userIdentity
     */
    public void refresh(final UserIdentity userIdentity) {
        Objects.requireNonNull(userIdentity, "Null userIdentity");
        if (userIdentity instanceof final HasRefreshable hasRefreshable) {

            // This will try and refresh/recreate the token just before it expires
            // so that there is no delay for users of the token. They can explicitly call
            // refresh after checking AbstractTokenUserIdentity.hasTokenExpired() if they don't trust
            // the refresh queue, but it should always return false.
            final Refreshable refreshable = hasRefreshable.getRefreshable();
            final boolean didRefresh = refreshable.refreshIfRequired(
                    RefreshMode.JUST_IN_TIME, refreshManager::addOrUpdate);

            if (LOGGER.isTraceEnabled()) {
                if (!didRefresh) {
                    LOGGER.trace("Refresh not done for userIdentity: {}, updatableToken: {}",
                            userIdentity, refreshable);
                }
            }
        }
    }

    // Maybe ought to bake the refresh token claims into the UpdatableToken
    private boolean hasRefreshTokenExpired(final TokenResponse tokenResponse) {
        // At some point the refresh token itself will expire, so then we need to remove
        // the identity from the session if there is one to force the user to re-authenticate
        if (NullSafe.isBlankString(tokenResponse, TokenResponse::getRefreshToken)) {
            return false;
        } else {
            return jwtContextFactory.getJwtContext(tokenResponse.getRefreshToken(), false)
                    .map(JwtContext::getJwtClaims)
                    .map(ThrowingFunction.unchecked(JwtClaims::getExpirationTime))
                    .map(expireTime -> NumericDate.now().isAfter(expireTime))
                    .orElse(false);
        }
    }

    protected FetchTokenResult refreshUsingRefreshToken(final UpdatableToken updatableToken) {
        Objects.requireNonNull(updatableToken);
        final UserIdentity identity = Objects.requireNonNull(updatableToken.getUserIdentity());
        final TokenResponse currentTokenResponse = updatableToken.getTokenResponse();

        FetchTokenResult fetchTokenResult;

        if (hasRefreshTokenExpired(currentTokenResponse)) {
            if (identity instanceof final HasSession userWithSession) {
                LOGGER.info("Refresh token has expired, removing user identity from " +
                            "session to force re-authentication. userIdentity: {}", userWithSession);
                userWithSession.removeUserFromSession();
            } else {
                LOGGER.warn("Refresh token has expired, can't refresh token or create new.");
            }
            fetchTokenResult = null;
        } else {
            TokenResponse newTokenResponse = null;
            JwtClaims jwtClaims = null;
            try {
                LOGGER.debug("Refreshing token " + identity);

                LOGGER.debug(LogUtil.message(
                        "Current token expiry max age: {}, refresh token expiry max age: {}",
                        NullSafe.toString(updatableToken.getTokenResponse(),
                                TokenResponse::getExpiresIn,
                                Duration::ofSeconds),
                        NullSafe.toString(updatableToken.getTokenResponse(),
                                TokenResponse::getEffectiveRefreshExpiresIn,
                                Duration::ofSeconds)));

                fetchTokenResult = refreshTokens(currentTokenResponse);
                newTokenResponse = fetchTokenResult.tokenResponse();
                jwtClaims = fetchTokenResult.jwtClaims();
            } catch (final RuntimeException e) {
                LOGGER.error("Error refreshing token for {} {} ({}) - {}",
                        identity.subjectId(),
                        identity.getDisplayName(),
                        identity.getFullName().orElse("-"),
                        LogUtil.exceptionMessage(e), e);
                if (identity instanceof final HasSession userWithSession) {
                    userWithSession.invalidateSession();
                }
                throw e;
            } finally {
                // Some IDPs don't seem to send updated refresh tokens so keep the existing refresh token.
                if (newTokenResponse != null
                    && newTokenResponse.getRefreshToken() == null
                    && currentTokenResponse.getRefreshToken() != null) {
                    newTokenResponse = newTokenResponse
                            .copy()
                            .refreshToken(currentTokenResponse.getRefreshToken())
                            .refreshTokenExpiresIn(Objects.requireNonNullElseGet(
                                    newTokenResponse.getEffectiveRefreshExpiresIn(),
                                    currentTokenResponse::getEffectiveRefreshExpiresIn))
                            .build();
                }
                fetchTokenResult = new FetchTokenResult(newTokenResponse, jwtClaims);

                LOGGER.debug(LogUtil.message(
                        "New token expiry max age: {}, refresh token expiry max age: {}",
                        NullSafe.toString(newTokenResponse, TokenResponse::getExpiresIn, Duration::ofSeconds),
                        NullSafe.toString(newTokenResponse,
                                TokenResponse::getEffectiveRefreshExpiresIn,
                                Duration::ofSeconds)));
            }
        }
        return fetchTokenResult;
    }

    protected void addTokenToRefreshManager(final Refreshable refreshable) {
        NullSafe.consume(refreshable, refreshManager::addOrUpdate);
    }

    protected void removeTokenFromRefreshManager(final Refreshable refreshable) {
        NullSafe.consume(refreshable, refreshManager::remove);
    }

    protected Optional<String> getUserFullName(final OpenIdConfiguration openIdConfiguration,
                                               final JwtClaims jwtClaims) {
        Objects.requireNonNull(openIdConfiguration);
        Objects.requireNonNull(jwtClaims);
        // e.g. "${firstName} ${lastName}" => "john Doe"
        final Templator fullNameTemplator = cachedFullNameTemplate.getValue();
        if (!fullNameTemplator.isBlank()) {
            // If the claim in the template is not in the claims then just replace with empty string
            final String fullName = NullSafe.trim(fullNameTemplator.buildGenerator()
                    .addCommonReplacementFunction(aClaim -> JwtUtil.getClaimValue(jwtClaims, aClaim)
                            .map(NullSafe::trim)
                            .orElse(""))
                    .generate());
            return fullName.isEmpty()
                    ? Optional.empty()
                    : Optional.of(fullName);
        } else {
            return Optional.empty();
        }
    }

    private FetchTokenResult refreshTokens(final TokenResponse existingTokenResponse) {

        final String refreshToken = NullSafe.requireNonNull(
                existingTokenResponse,
                TokenResponse::getRefreshToken,
                () -> "Unable to refresh token as no existing refresh token is available");

        final OpenIdConfiguration openIdConfiguration = openIdConfigProvider.get();
        final String tokenEndpoint = openIdConfiguration.getTokenEndpoint();

        final TokenResponse newTokenResponse = new OpenIdTokenRequestHelper(
                tokenEndpoint, openIdConfiguration, objectMapper, jerseyClientFactory)
                .withGrantType(OpenId.GRANT_TYPE__REFRESH_TOKEN)
                .withRefreshToken(refreshToken)
                .sendRequest(true);

        final JwtClaims jwtClaims = jwtContextFactory.getJwtContext(newTokenResponse.getIdToken())
                .map(JwtContext::getJwtClaims)
                .orElseThrow(() -> new RuntimeException("Unable to extract JWT claims"));

        return new FetchTokenResult(newTokenResponse, jwtClaims);
    }

    private ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    private Optional<UserIdentity> createUserIdentity(final HttpServletRequest request,
                                                      final AuthenticationState state,
                                                      final TokenResponse tokenResponse,
                                                      final JwtContext jwtContext) {
        Optional<UserIdentity> optUserIdentity = Optional.empty();
        final JwtClaims jwtClaims = jwtContext.getJwtClaims();

        final String nonce = (String) jwtClaims.getClaimsMap()
                .get(OpenId.NONCE);
        final boolean match = nonce != null && nonce.equals(state.getNonce());
        if (match) {
            optUserIdentity = mapAuthFlowIdentity(jwtContext, request, tokenResponse);
            optUserIdentity.ifPresent(userIdentity -> {
                if (userIdentity instanceof final HasRefreshable hasRefreshable) {
                    NullSafe.consume(hasRefreshable.getRefreshable(),
                            refreshable ->
                                    refreshable.refreshIfRequired(
                                            RefreshMode.JUST_IN_TIME, refreshManager::addOrUpdate));
                }
            });
        } else {
            // If the nonces don't match we need to redirect to log in again.
            // Maybe the request uses an out-of-date stroomSessionId?
            LOGGER.info(() -> "Received a bad nonce!");
        }

        return optUserIdentity;
    }

    private UserIdentity createServiceUserIdentity() {
        // Delegate creation to an idpType appropriate class
        final UserIdentity userIdentity = serviceUserFactory.createServiceUserIdentity();

        if (userIdentity instanceof final HasRefreshable hasRefreshable) {
            NullSafe.consume(hasRefreshable.getRefreshable(),
                    refreshManager::addOrUpdate);
        }
        return userIdentity;
    }
}
