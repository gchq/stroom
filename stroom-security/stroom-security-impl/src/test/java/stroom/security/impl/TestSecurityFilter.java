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

import stroom.config.common.UriFactory;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestSecurityFilter {

    private static final String PUBLIC_URI = "https://stroom.example.com";
    private static final String UI_URI = "https://ui.example.com";

    @Mock
    private HttpServletRequest request;
    @Mock
    private UriFactory uriFactory;

    private SecurityFilter securityFilter;

    @BeforeEach
    void setUp() {
        // Only the UriFactory is exercised by the origin check; the other collaborators are unused.
        securityFilter = new SecurityFilter(null, null, null, () -> uriFactory);
    }

    private void setUpAllowedOrigins() {
        lenient().when(uriFactory.publicUri("/")).thenReturn(URI.create(PUBLIC_URI + "/"));
        lenient().when(uriFactory.uiUri("/")).thenReturn(URI.create(UI_URI + "/"));
    }

    @Test
    void safeMethodsAreAlwaysValid() {
        // GET/HEAD/OPTIONS never carry an Origin check, even with no headers set.
        for (final String method : new String[]{"GET", "HEAD", "OPTIONS"}) {
            when(request.getMethod()).thenReturn(method);
            assertThat(securityFilter.isOriginValid(request))
                    .as("method %s", method)
                    .isTrue();
        }
    }

    @Test
    void noOriginOrRefererFallsBackToHeaderCheck() {
        // With neither Origin nor Referer present we can't verify same-origin here, so we defer to
        // the separate X-CSRF header check (i.e. isOriginValid must not reject).
        when(request.getMethod()).thenReturn("POST");
        assertThat(securityFilter.isOriginValid(request)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(value = {
            // originHeader                     | expectedValid | description
            "https://stroom.example.com         | true          | matches public uri",
            "https://stroom.example.com:443     | true          | matches public uri, explicit default port",
            "https://ui.example.com             | true          | matches ui uri (split deployment)",
            "https://STROOM.EXAMPLE.COM         | true          | host comparison is case insensitive",
            "https://evil.example.com           | false         | different host is rejected",
            "http://stroom.example.com          | false         | different scheme is rejected",
            "https://stroom.example.com:8443    | false         | different port is rejected",
            "not a uri                          | false         | unparseable origin is rejected",
    }, delimiter = '|')
    void originHeaderIsCheckedAgainstConfiguredUris(final String originHeader,
                                                    final boolean expectedValid,
                                                    final String description) {
        setUpAllowedOrigins();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Origin")).thenReturn(originHeader.trim());

        assertThat(securityFilter.isOriginValid(request))
                .as(description.trim())
                .isEqualTo(expectedValid);
    }

    @Test
    void literalNullOriginFallsBackToHeaderCheck() {
        // Browsers send "Origin: null" for opaque origins (e.g. sandboxed iframes). We treat this
        // as absent and defer to the X-CSRF header check rather than rejecting outright.
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Origin")).thenReturn("null");

        assertThat(securityFilter.isOriginValid(request)).isTrue();
    }

    @Test
    void refererIsUsedWhenOriginAbsent() {
        setUpAllowedOrigins();
        when(request.getMethod()).thenReturn("POST");
        // Referer includes a path/fragment, which must be ignored when deriving the origin.
        // Lenient because the code reads the (absent) Origin header before falling back to Referer.
        lenient().when(request.getHeader("Referer")).thenReturn(PUBLIC_URI + "/stroom/ui#/somewhere");

        assertThat(securityFilter.isOriginValid(request)).isTrue();
    }

    @Test
    void matchesTheHostTheBrowserConnectedToEvenWhenNotConfigured() {
        // publicUri/uiUri return unrelated hosts, but the request's own (proxied) host must still
        // be accepted so a missing/incorrect publicUri config can't lock users out.
        setUpAllowedOrigins();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Origin")).thenReturn("https://actual-host.example.com");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("actual-host.example.com");

        assertThat(securityFilter.isOriginValid(request)).isTrue();
    }

    @Test
    void crossOriginRequestIsRejectedEvenIfItForgesXForwardedHost() {
        // A browser CSRF attacker cannot set X-Forwarded-Host, but verify that a mismatch between
        // the (attacker) Origin and the proxied host is still rejected.
        setUpAllowedOrigins();
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("Origin")).thenReturn("https://evil.example.com");
        when(request.getHeader("X-Forwarded-Host")).thenReturn("stroom.example.com");
        when(request.getHeader("X-Forwarded-Proto")).thenReturn("https");

        assertThat(securityFilter.isOriginValid(request)).isFalse();
    }

    @Test
    void nonDefaultPortIsMatchedExactly() {
        // When the app runs on a non-standard port (e.g. 8080), the browser always includes the
        // port in the Origin header, so it must match the configured URI's explicit port.
        lenient().when(uriFactory.publicUri("/")).thenReturn(URI.create("https://stroom.example.com:8080/"));
        lenient().when(uriFactory.uiUri("/")).thenReturn(URI.create("https://stroom.example.com:8080/"));
        when(request.getMethod()).thenReturn("POST");

        when(request.getHeader("Origin")).thenReturn("https://stroom.example.com:8080");
        assertThat(securityFilter.isOriginValid(request))
                .as("matching non-default port")
                .isTrue();
    }

    @Test
    void portlessOriginDoesNotMatchNonDefaultConfiguredPort() {
        // A port-less Origin resolves to the default port (443), which must NOT match a configured
        // non-default port (8080).
        lenient().when(uriFactory.publicUri("/")).thenReturn(URI.create("https://stroom.example.com:8080/"));
        lenient().when(uriFactory.uiUri("/")).thenReturn(URI.create("https://stroom.example.com:8080/"));
        when(request.getMethod()).thenReturn("POST");

        when(request.getHeader("Origin")).thenReturn("https://stroom.example.com");
        assertThat(securityFilter.isOriginValid(request))
                .as("port-less origin vs :8080 config")
                .isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "OPTIONS"})
    void csrfSafeMethodsAreAlwaysValid(final String method) {
        // Safe methods don't require the CSRF header, even when it is absent.
        when(request.getMethod()).thenReturn(method);
        assertThat(securityFilter.isCsrfValid(request)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "DELETE", "PATCH"})
    void csrfStateChangingMethodsRequireTheHeader(final String method) {
        when(request.getMethod()).thenReturn(method);

        // Missing header -> rejected.
        assertThat(securityFilter.isCsrfValid(request))
                .as("%s with no X-CSRF header", method)
                .isFalse();

        // Correct header -> accepted.
        when(request.getHeader("X-CSRF")).thenReturn("1");
        assertThat(securityFilter.isCsrfValid(request))
                .as("%s with X-CSRF: 1", method)
                .isTrue();
    }

    @Test
    void csrfHeaderWithWrongValueIsRejected() {
        when(request.getMethod()).thenReturn("POST");
        when(request.getHeader("X-CSRF")).thenReturn("0");
        assertThat(securityFilter.isCsrfValid(request)).isFalse();
    }
}
