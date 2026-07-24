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

package stroom.security.identity.authenticate;

import stroom.config.common.UriFactory;
import stroom.task.api.ExecutorProvider;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestAuthenticationLogout {

    private static final String PUBLIC_ROOT = "https://stroom.example.com";

    @Mock
    private UriFactory uriFactory;
    @Mock
    private ExecutorProvider executorProvider;
    @Mock
    private HttpServletRequest request;

    private AuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(executorProvider.get(any())).thenReturn(Runnable::run);
        lenient().when(uriFactory.publicUri("/")).thenReturn(URI.create(PUBLIC_ROOT));
        service = new AuthenticationServiceImpl(
                uriFactory, null, null, null, null, null, null, null, null, executorProvider, null);
    }

    // --- CSRF origin check (isSameOrigin), fetch metadata ---

    @Test
    void sameOriginFetchIsAllowed() {
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("same-origin");
        assertThat(service.isSameOrigin(request)).isTrue();
    }

    @Test
    void userInitiatedRequestIsAllowed() {
        // "none" is a user-initiated request: a typed URL or a bookmark.
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("none");
        assertThat(service.isSameOrigin(request)).isTrue();
    }

    @Test
    void crossSiteIsRejectedEvenForTopLevelNavigation() {
        // A cross-site request is refused outright, even a top-level document navigation: under a
        // SameSite=Lax session cookie the cookie IS sent on a cross-site top-level navigation, so allowing it
        // would let an attacker page's window.location force a logout. (Sec-Fetch-Dest is not even consulted.)
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("cross-site");
        assertThat(service.isSameOrigin(request)).isFalse();
    }

    @Test
    void sameSiteSubresourceLoadIsRejected() {
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("same-site");
        when(request.getHeader("Sec-Fetch-Dest")).thenReturn("image");
        assertThat(service.isSameOrigin(request)).isFalse();
    }

    @Test
    void sameSiteIframeNavigationIsRejected() {
        // A nested <iframe src=".../logout"> is a "navigate" request but carries Sec-Fetch-Dest: iframe, so
        // it must NOT be treated as a top-level navigation - otherwise a sibling subdomain could force a
        // silent logout while still sending the SameSite cookie.
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("same-site");
        when(request.getHeader("Sec-Fetch-Dest")).thenReturn("iframe");
        assertThat(service.isSameOrigin(request)).isFalse();
    }

    @Test
    void sameSiteTopLevelNavigationIsAllowed() {
        // A genuine same-site top-level document navigation (e.g. a split UI/API deployment where the UI is on
        // a sibling subdomain) is allowed; it is a real navigation, not a silent embedded load.
        when(request.getHeader("Sec-Fetch-Site")).thenReturn("same-site");
        when(request.getHeader("Sec-Fetch-Dest")).thenReturn("document");
        assertThat(service.isSameOrigin(request)).isTrue();
    }

    // --- CSRF origin check (isSameOrigin), Origin/Referer fallback when fetch metadata is absent ---

    @Test
    void withoutFetchMetadataFallsBackToOriginAndAllowsSameOrigin() {
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn(PUBLIC_ROOT);
        assertThat(service.isSameOrigin(request)).isTrue();
    }

    @Test
    void withoutFetchMetadataRejectsForeignOrigin() {
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn("https://evil.example.com");
        assertThat(service.isSameOrigin(request)).isFalse();
    }

    @Test
    void refererIsUsedWhenOriginIsAbsent() {
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Referer")).thenReturn(PUBLIC_ROOT + "/some/page");
        assertThat(service.isSameOrigin(request)).isTrue();
    }

    @Test
    void crossOriginRefererIsRejected() {
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Referer")).thenReturn("https://evil.example.com/page");
        assertThat(service.isSameOrigin(request)).isFalse();
    }

    @Test
    void withoutFetchMetadataAndNoOriginOrRefererIsAllowed() {
        // Known residual: a browser that sends no fetch metadata (pre-2023 Safari/iOS) and no Origin/Referer
        // is allowed, so it must not break a genuine navigation that carries neither. This is the pre-existing
        // fail-open, left in place so logout keeps working for those clients; fetch metadata closes it on
        // every browser that sends it.
        when(request.getHeader("Sec-Fetch-Site")).thenReturn(null);
        when(request.getHeader("Origin")).thenReturn(null);
        when(request.getHeader("Referer")).thenReturn(null);
        assertThat(service.isSameOrigin(request)).isTrue();
    }

    // --- post_logout_redirect_uri validation ---

    @Test
    void sameOriginPostLogoutRedirectIsHonoured() {
        assertThat(service.getValidatedPostLogoutRedirectUri(PUBLIC_ROOT + "/?state=abc"))
                .isEqualTo(URI.create(PUBLIC_ROOT + "/?state=abc"));
    }

    @Test
    void offOriginPostLogoutRedirectFallsBackToRoot() {
        assertThat(service.getValidatedPostLogoutRedirectUri("https://evil.example.com/"))
                .isEqualTo(URI.create(PUBLIC_ROOT));
    }

    @Test
    void nullPostLogoutRedirectFallsBackToRoot() {
        assertThat(service.getValidatedPostLogoutRedirectUri(null))
                .isEqualTo(URI.create(PUBLIC_ROOT));
    }
}
