/*
 * Copyright 2016-2026 Crown Copyright
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
import stroom.security.common.impl.AuthenticationState;
import stroom.security.openid.api.OpenIdConfiguration;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestAuthFlowResourceImpl {

    private static final String STATE_COOKIE = "STROOM_OIDC_STATE";

    // --- the post-login destination must stay same-origin ---

    @Test
    void offOriginRedirectUriIsReplacedWithTheRoot() {
        // An off-origin redirect_uri would be an open redirect after login, so it must not reach the state.
        assertStoredInitiatingUri("https://evil.example.com/steal", "/");
    }

    @Test
    void sameOriginRedirectUriIsPreserved() {
        assertStoredInitiatingUri("https://stroom.example.com/some/page", "https://stroom.example.com/some/page");
    }

    @Test
    void rootRelativeRedirectUriIsPreserved() {
        assertStoredInitiatingUri("/some/page", "/some/page");
    }

    private void assertStoredInitiatingUri(final String requestedRedirectUri, final String expectedStored) {
        final HttpServletRequest request = mock(HttpServletRequest.class);   // getSession(false) -> null
        final HttpServletResponse response = mock(HttpServletResponse.class);

        final UriFactory uriFactory = mock(UriFactory.class);
        when(uriFactory.publicUri(anyString())).thenReturn(URI.create("https://stroom.example.com/"));

        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        final AuthenticationState state = mock(AuthenticationState.class);
        when(state.getId()).thenReturn("state-id");
        when(stateCache.create(anyString(), anyString(), anyBoolean())).thenReturn(state);

        final OpenIdManager openIdManager = mock(OpenIdManager.class);
        when(openIdManager.createAuthUri(any(), any(), any())).thenReturn("https://idp.example.com/auth");

        final AuthFlowResourceImpl resource = new AuthFlowResourceImpl(
                () -> openIdManager,
                () -> mock(OpenIdConfiguration.class),
                () -> stateCache,
                () -> uriFactory,
                null);

        resource.status(requestedRedirectUri, request, response);

        verify(stateCache).create(eq(expectedStored), anyString(), eq(false));
    }

    // --- the state must be bound to the initiating browser ---

    @Test
    void statusSetsAStateBindingCookie() {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);

        final UriFactory uriFactory = mock(UriFactory.class);
        when(uriFactory.publicUri(anyString())).thenReturn(URI.create("https://stroom.example.com/"));

        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        final AuthenticationState state = mock(AuthenticationState.class);
        when(state.getId()).thenReturn("the-state-id");
        when(stateCache.create(anyString(), anyString(), anyBoolean())).thenReturn(state);

        final OpenIdManager openIdManager = mock(OpenIdManager.class);
        when(openIdManager.createAuthUri(any(), any(), any())).thenReturn("https://idp/auth");

        final AuthFlowResourceImpl resource = new AuthFlowResourceImpl(
                () -> openIdManager,
                () -> mock(OpenIdConfiguration.class),
                () -> stateCache,
                () -> uriFactory,
                null);

        resource.status("/", request, response);

        final ArgumentCaptor<String> header = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(eq("Set-Cookie"), header.capture());
        assertThat(header.getValue())
                .contains(STATE_COOKIE + "=the-state-id")
                .contains("SameSite=Lax")
                .contains("HttpOnly");
    }

    @Test
    void callbackRejectsAStateNotBoundToThisBrowser() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(STATE_COOKIE, "someone-elses-state")});
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);

        final AuthFlowResourceImpl resource = new AuthFlowResourceImpl(
                null, null, () -> stateCache, null, null);

        resource.callback("the-code", "the-state", request, response);

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_REQUEST), anyString());
        // Rejected before the state is even consumed.
        verify(stateCache, never()).getAndRemove(any());
    }

    @Test
    void callbackAcceptsAStateBoundToThisBrowser() throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getCookies()).thenReturn(new Cookie[]{new Cookie(STATE_COOKIE, "the-state")});
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final AuthenticationStateCache stateCache = mock(AuthenticationStateCache.class);
        when(stateCache.getAndRemove("the-state")).thenReturn(Optional.empty());

        final AuthFlowResourceImpl resource = new AuthFlowResourceImpl(
                null, null, () -> stateCache, null, null);

        resource.callback("the-code", "the-state", request, response);

        // The binding matched, so the flow proceeded to consume the state (it then fails as unknown, which
        // is a later stage) - proving the browser-binding check let it through.
        verify(stateCache).getAndRemove("the-state");
    }
}
