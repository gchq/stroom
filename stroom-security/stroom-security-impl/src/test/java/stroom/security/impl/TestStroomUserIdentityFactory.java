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

package stroom.security.impl;

import stroom.cache.impl.CacheManagerImpl;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserIdentity;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.UserService;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.InsecureTestCredentials;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.impl.apikey.ApiKeyService;
import stroom.security.mock.MockSecurityContext;
import stroom.security.openid.api.ClusterToken;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenId;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.User;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.io.SimplePathCreator;
import stroom.util.logging.LogUtil;

import jakarta.servlet.http.HttpServletRequest;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.MalformedClaimException;
import org.jose4j.jwt.consumer.JwtContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class TestStroomUserIdentityFactory {

    public static final String USER_123_SUBJECT = "user123";
    @Mock
    private UserService mockUserService;
    @Mock
    private OpenIdConfiguration mockOpenIdConfiguration;
    @Mock
    private EntityEventBus mockEntityEventBus;
    @Mock
    private JwtClaims mockJwtClaims;
    @Mock
    private JwtContext mockJwtContext;
    @Mock
    private HttpServletRequest mockHttpServletRequest;

    @Test
    void mapApiIdentity() throws MalformedClaimException {
        givenClaimsForUser();
        givenResolvedUser(userBuilder().build());

        createFactory().getApiUserIdentity(mockJwtContext, mockHttpServletRequest);

        Mockito.verify(mockUserService, Mockito.never())
                .update(Mockito.any());
    }

    @Test
    void disabledUserIsRejectedOnTheBearerPath() throws MalformedClaimException {
        // A valid token must not authenticate a disabled user, matching the run-as, interactive and API
        // key paths - otherwise a user disabled in the UI keeps API access until their token expires.
        givenClaimsForUser();
        givenResolvedUser(userBuilder().enabled(false).build());

        final StroomUserIdentityFactory factory = createFactory();

        assertThatThrownBy(() -> factory.getApiUserIdentity(mockJwtContext, mockHttpServletRequest))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void clusterVerifierIsConsultedInAllModes() {
        // The internally-signed inter-node cluster token is verified by the dedicated ClusterTokenVerifier
        // in every IdP mode (the internal signing key is the sole trust anchor), so it is always consulted.
        assertClusterVerifierConsulted(IdpType.EXTERNAL_IDP);
        assertClusterVerifierConsulted(IdpType.INTERNAL_IDP);
    }

    private void assertClusterVerifierConsulted(final IdpType idpType) {
        final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);
        Mockito.lenient().when(openIdConfiguration.getIdentityProviderType()).thenReturn(idpType);

        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.lenient().when(request.getRequestURI()).thenReturn("/api/example");

        final ApiKeyService apiKeyService = Mockito.mock(ApiKeyService.class);
        Mockito.lenient().when(apiKeyService.fetchVerifiedIdentity(request)).thenReturn(Optional.empty());

        // The cluster-token verifier - the thing under test. Returns empty so the call resolves to empty.
        final ClusterTokenVerifier clusterTokenVerifier = Mockito.mock(ClusterTokenVerifier.class);
        Mockito.lenient().when(clusterTokenVerifier.verify(request)).thenReturn(Optional.empty());

        // The normal (delegating) inbound path - returns nothing so the whole call resolves to empty.
        final JwtContextFactory jwtContextFactory = Mockito.mock(JwtContextFactory.class);
        Mockito.lenient().when(jwtContextFactory.getJwtContext(request)).thenReturn(Optional.empty());

        final StroomUserIdentityFactory factory = new StroomUserIdentityFactory(
                jwtContextFactory,
                () -> openIdConfiguration,
                null,
                null,
                null,
                () -> mockUserService,
                new MockSecurityContext(),
                null,
                mockEntityEventBus,
                null,
                apiKeyService,
                AuthorisationConfig::new,
                new CacheManagerImpl(),
                new SimplePathCreator(() -> null, () -> null),
                clusterTokenVerifier,
                new InsecureTestCredentials());

        factory.getApiUserIdentity(request);

        Mockito.verify(clusterTokenVerifier).verify(request);
    }

    @Test
    void clusterTokenWithRunAsDownscopesToThatHuman() {
        // A verified cluster token carrying a run-as header must resolve to that human (downscoped), so the
        // receiver enforces THEIR permissions - not the processing user's.
        final String runAsUuid = "run-as-uuid";
        final User alice = User.builder()
                .uuid(runAsUuid)
                .subjectId("alice")
                .displayName("alice")
                .fullName(null)
                .enabled(true)
                .build();

        final UserCache userCache = Mockito.mock(UserCache.class);
        Mockito.when(userCache.getByUuid(runAsUuid)).thenReturn(Optional.of(alice));

        final HttpServletRequest request = clusterRequest(runAsUuid);
        final ClusterTokenVerifier clusterTokenVerifier = validClusterTokenVerifier(request);

        final UserIdentity identity = factoryForCluster(userCache, clusterTokenVerifier, null)
                .getApiUserIdentity(request)
                .orElseThrow();

        assertThat(identity.subjectId()).isEqualTo("alice");
    }

    @Test
    void clusterTokenRunAsUnknownUserIsRejected() {
        final String runAsUuid = "missing-uuid";
        final UserCache userCache = Mockito.mock(UserCache.class);
        Mockito.when(userCache.getByUuid(runAsUuid)).thenReturn(Optional.empty());

        final HttpServletRequest request = clusterRequest(runAsUuid);
        final ClusterTokenVerifier clusterTokenVerifier = validClusterTokenVerifier(request);
        final StroomUserIdentityFactory factory = factoryForCluster(userCache, clusterTokenVerifier, null);

        assertThatThrownBy(() -> factory.getApiUserIdentity(request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void clusterTokenWithNoRunAsIsRejected() {
        // Fail closed: a verified cluster token with no run-as header must NOT default to the processing
        // user - an accidental omission is rejected rather than silently escalating.
        final HttpServletRequest request = clusterRequest(null);
        final ClusterTokenVerifier clusterTokenVerifier = validClusterTokenVerifier(request);
        final StroomUserIdentityFactory factory = factoryForCluster(
                Mockito.mock(UserCache.class), clusterTokenVerifier, null);

        assertThatThrownBy(() -> factory.getApiUserIdentity(request))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    void clusterTokenWithProcessingUserSentinelIsTheProcessingUser() {
        // A genuine system/background inter-node call carries the processing-user sentinel and runs as the
        // proc user (explicit god-mode, rather than an implicit default).
        final UserIdentity serviceUser = Mockito.mock(UserIdentity.class);
        final ServiceUserFactory serviceUserFactory = Mockito.mock(ServiceUserFactory.class);
        Mockito.when(serviceUserFactory.createServiceUserIdentity()).thenReturn(serviceUser);

        final HttpServletRequest request = clusterRequest(ClusterToken.PROCESSING_USER_SUBJECT);
        final ClusterTokenVerifier clusterTokenVerifier = validClusterTokenVerifier(request);
        final StroomUserIdentityFactory factory = factoryForCluster(
                Mockito.mock(UserCache.class), clusterTokenVerifier, serviceUserFactory);

        assertThat(factory.getApiUserIdentity(request)).containsSame(serviceUser);
    }

    private HttpServletRequest clusterRequest(final String runAsUuid) {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.lenient().when(request.getRequestURI()).thenReturn("/api/example");
        Mockito.when(request.getHeader(UserIdentityFactory.RUN_AS_USER_HEADER)).thenReturn(runAsUuid);
        return request;
    }

    private ClusterTokenVerifier validClusterTokenVerifier(final HttpServletRequest request) {
        final ClusterTokenVerifier clusterTokenVerifier = Mockito.mock(ClusterTokenVerifier.class);
        Mockito.when(clusterTokenVerifier.verify(request))
                .thenReturn(Optional.of(Mockito.mock(JwtContext.class)));
        return clusterTokenVerifier;
    }

    private StroomUserIdentityFactory factoryForCluster(final UserCache userCache,
                                                        final ClusterTokenVerifier clusterTokenVerifier,
                                                        final ServiceUserFactory serviceUserFactory) {
        final OpenIdConfiguration openIdConfiguration = Mockito.mock(OpenIdConfiguration.class);
        Mockito.lenient().when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);
        final ApiKeyService apiKeyService = Mockito.mock(ApiKeyService.class);
        Mockito.lenient().when(apiKeyService.fetchVerifiedIdentity(Mockito.any())).thenReturn(Optional.empty());

        return new StroomUserIdentityFactory(
                Mockito.mock(JwtContextFactory.class),
                () -> openIdConfiguration,
                null,
                userCache,
                serviceUserFactory,
                () -> mockUserService,
                new MockSecurityContext(),
                null,
                mockEntityEventBus,
                null,
                apiKeyService,
                AuthorisationConfig::new,
                new CacheManagerImpl(),
                new SimplePathCreator(() -> null, () -> null),
                clusterTokenVerifier,
                new InsecureTestCredentials());
    }

    private static User.Builder userBuilder() {
        return User.builder()
                .subjectId(USER_123_SUBJECT)
                .displayName(USER_123_SUBJECT)
                .fullName(null);
    }

    private void givenClaimsForUser() throws MalformedClaimException {
        final String displayNameClaim = "disp";
        Mockito.when(mockOpenIdConfiguration.getIdentityProviderType())
                .thenReturn(IdpType.EXTERNAL_IDP);
        Mockito.when(mockOpenIdConfiguration.getUniqueIdentityClaim())
                .thenReturn(OpenId.CLAIM__SUBJECT);
        Mockito.when(mockOpenIdConfiguration.getUserDisplayNameClaim())
                .thenReturn(displayNameClaim);
        Mockito.when(mockJwtClaims.getClaimValue(Mockito.anyString(), Mockito.any()))
                .thenAnswer(invocation -> {
                    final String claim = invocation.getArgument(0, String.class);
                    return switch (claim) {
                        case OpenId.CLAIM__SUBJECT -> USER_123_SUBJECT;
                        case OpenId.CLAIM__NAME -> null;
                        case displayNameClaim -> null;
                        default -> throw new IllegalArgumentException(LogUtil.message("'{}' not expected", claim));
                    };
                });
        Mockito.when(mockJwtContext.getJwtClaims())
                .thenReturn(mockJwtClaims);
    }

    private void givenResolvedUser(final User user) {
        Mockito.when(mockUserService.getOrCreateUser(Mockito.eq(USER_123_SUBJECT)))
                .thenReturn(user);
    }

    private StroomUserIdentityFactory createFactory() {
        return new StroomUserIdentityFactory(
                null,
                () -> mockOpenIdConfiguration,
                null,
                null,
                null,
                () -> mockUserService,
                new MockSecurityContext(),
                null,
                mockEntityEventBus,
                null,
                null,
                AuthorisationConfig::new,
                new CacheManagerImpl(),
                new SimplePathCreator(() -> null, () -> null),
                null,
                new InsecureTestCredentials());
    }
}
