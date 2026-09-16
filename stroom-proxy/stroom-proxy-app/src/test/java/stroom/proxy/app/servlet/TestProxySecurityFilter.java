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

package stroom.proxy.app.servlet;

import stroom.security.api.CommonSecurityContext;
import stroom.security.api.UserIdentityFactory;
import stroom.util.shared.AuthenticationBypassChecker;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The filter used to end with a bare {@code else { chain.doFilter(..) }} for every non-API
 * request — so a request that had already been judged <em>not</em> to bypass authentication was
 * served anyway. {@code @Unauthenticated}, the only declaration of the trust boundary this
 * application has, was therefore advisory outside {@code /api}: its answer was computed and ignored.
 * <p>
 * Under contracts.md §1.1 the proxy runs at various trust positions in different modes, so a control
 * that looks vacuous in the deployment nearest to hand is load-bearing in another.
 * </p>
 */
class TestProxySecurityFilter {

    private static final String NON_API_PATH = "/some-servlet";

    @Test
    void testANonApiRequestThatDoesNotBypassIsRejected() throws Exception {
        final Fixture fixture = new Fixture(false, Optional.empty());

        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);

        Mockito.verify(fixture.response).setStatus(Status.UNAUTHORIZED.getStatusCode());
        Mockito.verify(fixture.chain, Mockito.never())
                .doFilter(Mockito.any(), Mockito.any());
    }

    /**
     * The behaviour that must NOT change: {@code ReceiveDataServlet} (/datafeed) and the other
     * unauthenticated servlets declare {@code @Unauthenticated}, so they take the bypass branch and
     * are served exactly as before. Data receipt does not depend on the fail-open above.
     */
    @Test
    void testANonApiRequestThatBypassesIsStillServed() throws Exception {
        final Fixture fixture = new Fixture(true, Optional.empty());

        fixture.filter.doFilter(fixture.request, fixture.response, fixture.chain);

        Mockito.verify(fixture.chain).doFilter(fixture.request, fixture.response);
        Mockito.verify(fixture.response, Mockito.never()).setStatus(Mockito.anyInt());
    }

    // --------------------------------------------------------------------------------

    private static class Fixture {

        private final ProxySecurityFilter filter;
        private final HttpServletRequest request;
        private final HttpServletResponse response;
        private final FilterChain chain;

        private Fixture(final boolean shouldBypass, final Optional<?> identity) {
            request = Mockito.mock(HttpServletRequest.class);
            response = Mockito.mock(HttpServletResponse.class);
            chain = Mockito.mock(FilterChain.class);

            Mockito.when(request.getMethod()).thenReturn("POST");
            Mockito.when(request.getServletPath()).thenReturn(NON_API_PATH);
            Mockito.when(request.getRequestURI()).thenReturn(NON_API_PATH);

            final AuthenticationBypassChecker bypassChecker =
                    Mockito.mock(AuthenticationBypassChecker.class);
            Mockito.when(bypassChecker.isUnauthenticated(
                            Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(shouldBypass);

            final UserIdentityFactory userIdentityFactory = Mockito.mock(UserIdentityFactory.class);
            Mockito.when(userIdentityFactory.getApiUserIdentity(Mockito.any()))
                    .thenReturn(Optional.empty());

            filter = new ProxySecurityFilter(
                    () -> Mockito.mock(CommonSecurityContext.class),
                    userIdentityFactory,
                    bypassChecker);
        }
    }
}
