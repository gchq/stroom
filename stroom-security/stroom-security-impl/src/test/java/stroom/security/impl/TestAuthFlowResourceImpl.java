/*
 * Copyright 2026 Crown Copyright
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

import stroom.config.common.UriFactory;
import stroom.security.api.UserIdentity;
import stroom.security.api.exception.AuthenticationException;
import stroom.security.common.impl.AuthenticationState;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.AuthFlowResponse;
import stroom.util.authentication.HasExpiry;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

class TestAuthFlowResourceImpl {

    private static final String STATE_COOKIE = "STROOM_OIDC_STATE";
    private static final String TARGET_COOKIE = "STROOM_OIDC_TARGET";
    private static final String RETRY_COOKIE = "STROOM_OIDC_RETRY";
    private static final String ORIGIN = "https://stroom.example.com";
    private static final URI PUBLIC_ROOT = URI.create(ORIGIN + "/");

    // --- the post-login destination must stay same-origin ---

    @Test
    void offOriginRedirectUriIsReplacedWithTheRoot() {
        // An off-origin redirect_uri would be an open redirect after login, so it must not reach the state.
        assertStoredInitiatingUri("https://evil.example.com/steal", "/");
    }

    @Test
    void sameOriginRedirectUriIsPreserved() {
        assertStoredInitiatingUri(ORIGIN + "/some/page", ORIGIN + "/some/page");
    }

    @Test
    void rootRelativeRedirectUriIsPreserved() {
        assertStoredInitiatingUri("/some/page", "/some/page");
    }

    private void assertStoredInitiatingUri(final String requestedRedirectUri, final String expectedStored) {
        final HttpServletRequest request = mock(HttpServletRequest.class);   // getSession(false) -> null
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = stateCache("state-id");

        newResource(stateCache).status(requestedRedirectUri, request, response);

        verify(stateCache).create(eq(expectedStored), anyString(), eq(false));
    }

    // --- the state must be bound to the initiating browser ---

    @Test
    void statusSetsAStateBindingCookie() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);

        newResource(stateCache("the-state-id")).status("/some/page", request, response);

        assertThat(setCookies(response))
                .anySatisfy(header -> assertThat(header)
                        .contains(STATE_COOKIE + "=the-state-id")
                        .contains("SameSite=Lax")
                        .contains("HttpOnly"));
    }

    @Test
    void statusRemembersTheDestinationSeparatelyFromTheBinding() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);

        newResource(stateCache("the-state-id")).status("/some/page", request, response);

        // The destination is not a security decision, so it deliberately outlives the 10 minute binding.
        assertThat(setCookies(response))
                .anySatisfy(header -> assertThat(header)
                        .contains(TARGET_COOKIE + "=" + URLEncoder.encode("/some/page", StandardCharsets.UTF_8))
                        .contains("Max-Age=3600"));
        assertThat(setCookies(response))
                .anySatisfy(header -> assertThat(header)
                        .contains(STATE_COOKIE + "=")
                        .contains("Max-Age=600"));
    }

    @Test
    void statusKeepsEarlierFlowsBound() {
        // A second tab starting a flow must not invalidate the first tab's.
        final HttpServletRequest request = requestWithCookies(new Cookie(STATE_COOKIE, "older-state"));
        final HttpServletResponse response = mock(HttpServletResponse.class);

        newResource(stateCache("newer-state")).status("/", request, response);

        assertThat(setCookies(response))
                .anySatisfy(header -> assertThat(header)
                        .contains(STATE_COOKIE + "=newer-state~older-state"));
    }

    @Test
    void callbackAcceptsAStateBoundToThisBrowser() throws Exception {
        final HttpServletRequest request = requestWithCookies(new Cookie(STATE_COOKIE, "the-state"));
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        when(stateCache.getAndRemove("the-state")).thenReturn(Optional.empty());

        newResource(stateCache).callback("the-code", "the-state", request, response);

        // The binding matched, so the flow proceeded to consume the state (it then fails as unknown, which
        // is a later stage) - proving the browser-binding check let it through.
        verify(stateCache).getAndRemove("the-state");
    }

    @Test
    void callbackConsumesOnlyTheMatchedBinding() throws Exception {
        // One tab completing must leave another tab's in-flight flow bound.
        final HttpServletRequest request = requestWithCookies(new Cookie(STATE_COOKIE, "state-a~state-b"));
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        when(stateCache.getAndRemove("state-b")).thenReturn(Optional.empty());

        newResource(stateCache).callback("the-code", "state-b", request, response);

        assertThat(setCookies(response))
                .anySatisfy(header -> assertThat(header).contains(STATE_COOKIE + "=state-a"));
        assertThat(setCookies(response))
                .noneSatisfy(header -> assertThat(header).contains("state-b"));
    }

    // --- an unrecognised state restarts the flow rather than dead-ending ---

    @Test
    void callbackRestartsWhenTheStateIsNotBoundToThisBrowser() throws Exception {
        final HttpServletRequest request = requestWithCookies(
                new Cookie(STATE_COOKIE, "someone-elses-state"));
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);

        newResource(stateCache).callback("the-code", "the-state", request, response);

        verify(response).sendRedirect(PUBLIC_ROOT.toString());
        // The planted code is discarded, never redeemed - the login-CSRF defence still holds.
        verify(stateCache, never()).getAndRemove(any());
        verify(response, never()).sendError(anyInt(), anyString());
    }

    @Test
    void callbackRestartsWhenTheStateHasExpired() throws Exception {
        final HttpServletRequest request = requestWithCookies(new Cookie(STATE_COOKIE, "the-state"));
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        when(stateCache.getAndRemove("the-state")).thenReturn(Optional.empty());

        newResource(stateCache).callback("the-code", "the-state", request, response);

        verify(response).sendRedirect(PUBLIC_ROOT.toString());
    }

    @Test
    void restartReturnsTheUserToWhereTheyWereHeading() throws Exception {
        final HttpServletRequest request = requestWithCookies(
                new Cookie(TARGET_COOKIE, URLEncoder.encode("/some/page", StandardCharsets.UTF_8)));
        final HttpServletResponse response = mock(HttpServletResponse.class);

        newResource(mock(AuthenticationStateCache.class))
                .callback("the-code", "the-state", request, response);

        verify(response).sendRedirect("/some/page");
    }

    @Test
    void restartIgnoresAnOffOriginRememberedDestination() throws Exception {
        // The cookie is request-derived, so it is re-validated rather than trusted.
        final HttpServletRequest request = requestWithCookies(new Cookie(
                TARGET_COOKIE,
                URLEncoder.encode("https://evil.example.com/steal", StandardCharsets.UTF_8)));
        final HttpServletResponse response = mock(HttpServletResponse.class);

        newResource(mock(AuthenticationStateCache.class))
                .callback("the-code", "the-state", request, response);

        verify(response).sendRedirect(PUBLIC_ROOT.toString());
    }

    @Test
    void restartHappensOnlyOncePerBrowser() throws Exception {
        // Having just restarted and come straight back, restarting again would only loop.
        final HttpServletRequest request = requestWithCookies(new Cookie(RETRY_COOKIE, "1"));
        final HttpServletResponse response = mock(HttpServletResponse.class);

        newResource(mock(AuthenticationStateCache.class))
                .callback("the-code", "the-state", request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
        verify(response, never()).sendRedirect(anyString());
    }

    // --- the request itself may carry a credential (edge proxy / relayed bearer token) ---

    @Test
    void requestTokenIdentityIsReportedAuthenticatedWithNoFlowStarted() {
        // Behind an authenticating edge proxy (ALB x-amzn-oidc-data, relayed bearer token) the
        // status request arrives carrying a verifiable credential; the app must load with no
        // second, stroom-owned flow stacked on the proxy's - so no state, no cookies.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        final OpenIdManager openIdManager = defaultOpenIdManager();
        final UserIdentity tokenUser = identity("token-user", "Token User");
        when(openIdManager.loginWithRequestToken(request)).thenReturn(Optional.of(tokenUser));

        final AuthFlowResponse authFlowResponse =
                newResource(stateCache, openIdManager, false, null)
                        .status("/some/page", request, response);

        assertThat(authFlowResponse.isAuthenticated()).isTrue();
        assertThat(authFlowResponse.getSubjectId()).isEqualTo("token-user");
        verify(stateCache, never()).create(anyString(), anyString(), anyBoolean());
        verify(response, never()).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void expiredRequestTokenIdentityFallsThroughToTheFlow() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final UserIdentity expired = mock(UserIdentity.class,
                withSettings().extraInterfaces(HasExpiry.class));
        lenient().doReturn("expired-user").when(expired).subjectId();
        lenient().doReturn(Instant.now().minusSeconds(60)).when((HasExpiry) expired).getExpireTime();
        final OpenIdManager openIdManager = defaultOpenIdManager();
        when(openIdManager.loginWithRequestToken(request)).thenReturn(Optional.of(expired));

        final AuthFlowResponse authFlowResponse =
                newResource(stateCache("state-id"), openIdManager, false, null)
                        .status("/", request, response);

        assertThat(authFlowResponse.isAuthenticated()).isFalse();
        assertThat(authFlowResponse.getRedirectUrl()).isNotNull();
    }

    @Test
    void requestTokenAuthenticationFailureIsTerminalInEdgeMode() {
        // A token that fails authentication (unknown/disabled user, or an unverifiable token) must
        // NOT fall through to a flow start in edge mode: the IdP re-authenticates silently and
        // returns straight back - an endless bounce. It must fail terminally instead.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        final OpenIdManager openIdManager = defaultOpenIdManager();
        when(openIdManager.loginWithRequestToken(request))
                .thenThrow(new AuthenticationException("User 'x' failed authentication"));

        assertThatThrownBy(() ->
                newResource(stateCache, openIdManager, true, null)
                        .status("/", request, response))
                .isInstanceOf(ForbiddenException.class);
        verify(stateCache, never()).create(anyString(), anyString(), anyBoolean());
    }

    @Test
    void requestTokenAuthenticationFailureFallsThroughWithoutAnEdge() {
        // With no edge there is no bounce to defend against, so a bad token must not dead-end the
        // browser - stroom's own flow starts, and a genuinely unknown/disabled user fails at the
        // callback exactly as before request tokens were consulted here.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final OpenIdManager openIdManager = defaultOpenIdManager();
        when(openIdManager.loginWithRequestToken(request))
                .thenThrow(new AuthenticationException("Error authenticating request"));

        final AuthFlowResponse authFlowResponse =
                newResource(stateCache("state-id"), openIdManager, false, null)
                        .status("/", request, response);

        assertThat(authFlowResponse.isAuthenticated()).isFalse();
        assertThat(authFlowResponse.getRedirectUrl()).isNotNull();
    }

    @Test
    void noIdpModeNeverConsultsTheRequestToken() {
        // DelegatingJwtContextFactory throws for NO_IDP, so the token source must be skipped.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final OpenIdManager openIdManager = defaultOpenIdManager();

        newResource(stateCache("state-id"), openIdManager, false, IdpType.NO_IDP)
                .status("/", request, response);

        verify(openIdManager, never()).loginWithRequestToken(any());
    }

    // --- edge mode: stroom owns no flow at either end ---

    @Test
    void edgeModeReturnsUnauthenticatedWithNoRedirectUrlAndNoCookies() {
        // No redirectUrl tells the bootstrap to reload and let the proxy redirect as a top-level
        // navigation. No state/target cookies: there is no flow to bind them to.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);

        final AuthFlowResponse authFlowResponse =
                newResource(stateCache, defaultOpenIdManager(), true, null)
                        .status("/some/page", request, response);

        assertThat(authFlowResponse.isAuthenticated()).isFalse();
        assertThat(authFlowResponse.getRedirectUrl()).isNull();
        verify(stateCache, never()).create(anyString(), anyString(), anyBoolean());
        verify(response, never()).addHeader(eq("Set-Cookie"), anyString());
    }

    @Test
    void edgeModeDisablesTheCallback() {
        // With the edge as relying party no code can legitimately arrive here; 'exactly one RP'
        // is enforced in code.
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);

        assertThatThrownBy(() ->
                newResource(stateCache, defaultOpenIdManager(), true, null)
                        .callback("the-code", "the-state", request, response))
                .isInstanceOf(ForbiddenException.class);
        verify(stateCache, never()).getAndRemove(any());
    }

    // --------------------------------------------------------------------------------

    private AuthFlowResourceImpl newResource(final AuthenticationStateCache stateCache) {
        return newResource(stateCache, defaultOpenIdManager(), false, null);
    }

    private AuthFlowResourceImpl newResource(final AuthenticationStateCache stateCache,
                                             final OpenIdManager openIdManager,
                                             final boolean edgeEnabled,
                                             final IdpType idpType) {
        final UriFactory uriFactory = mock(UriFactory.class);
        lenient().when(uriFactory.publicUri(anyString())).thenReturn(PUBLIC_ROOT);

        final OpenIdConfiguration openIdConfiguration = mock(OpenIdConfiguration.class);
        lenient().when(openIdConfiguration.getIdentityProviderType()).thenReturn(idpType);

        final AuthenticationConfig authenticationConfig = mock(AuthenticationConfig.class);
        lenient().when(authenticationConfig.getEdgeAuthenticationConfig())
                .thenReturn(new EdgeAuthenticationConfig(edgeEnabled, null));

        return new AuthFlowResourceImpl(
                () -> openIdManager,
                () -> openIdConfiguration,
                () -> stateCache,
                () -> uriFactory,
                null,
                () -> authenticationConfig);
    }

    private OpenIdManager defaultOpenIdManager() {
        final OpenIdManager openIdManager = mock(OpenIdManager.class);
        lenient().when(openIdManager.createAuthUri(any(), any(), any()))
                .thenReturn("https://idp.example.com/auth");
        return openIdManager;
    }

    private UserIdentity identity(final String subjectId, final String displayName) {
        final UserIdentity identity = mock(UserIdentity.class);
        // doReturn, as getDisplayName() is a default method that delegates to subjectId().
        lenient().doReturn(subjectId).when(identity).subjectId();
        lenient().doReturn(displayName).when(identity).getDisplayName();
        return identity;
    }

    private AuthenticationStateCache stateCache(final String stateId) {
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        final AuthenticationState state = mock(AuthenticationState.class);
        when(state.getId()).thenReturn(stateId);
        when(stateCache.create(anyString(), anyString(), anyBoolean())).thenReturn(state);
        return stateCache;
    }

    private HttpServletRequest requestWithCookies(final Cookie... cookies) {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(cookies);
        return request;
    }

    private List<String> setCookies(final HttpServletResponse response) {
        final ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        verify(response, atLeastOnce()).addHeader(eq("Set-Cookie"), header.capture());
        return header.getAllValues();
    }
}
