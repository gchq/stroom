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

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class TestInsecureTestCredentials {

    private static final String SECRET = "a-random-shared-secret";

    @AfterEach
    void clearProps() {
        System.clearProperty(InsecureTestCredentials.ALLOW_PROP);
        System.clearProperty(InsecureTestCredentials.SECRET_PROP);
    }

    @Test
    void disabledWhenNothingConfigured() {
        final InsecureTestCredentials credentials = new InsecureTestCredentials();
        assertThat(credentials.isEnabled()).isFalse();
        assertThat(credentials.matches(bearerRequest(SECRET))).isFalse();
    }

    @Test
    void disabledWhenSecretSetButNotAcknowledged() {
        System.setProperty(InsecureTestCredentials.SECRET_PROP, SECRET);
        final InsecureTestCredentials credentials = new InsecureTestCredentials();
        assertThat(credentials.isEnabled()).isFalse();
        assertThat(credentials.matches(bearerRequest(SECRET))).isFalse();
    }

    @Test
    void disabledWhenAcknowledgedButNoSecret() {
        System.setProperty(InsecureTestCredentials.ALLOW_PROP, "true");
        final InsecureTestCredentials credentials = new InsecureTestCredentials();
        assertThat(credentials.isEnabled()).isFalse();
    }

    @Test
    void enabledMatchesTheSecretOnly() {
        System.setProperty(InsecureTestCredentials.ALLOW_PROP, "true");
        System.setProperty(InsecureTestCredentials.SECRET_PROP, SECRET);
        final InsecureTestCredentials credentials = new InsecureTestCredentials();

        assertThat(credentials.isEnabled()).isTrue();
        assertThat(credentials.matches(bearerRequest(SECRET))).isTrue();
        assertThat(credentials.matches(bearerRequest("wrong-secret"))).isFalse();
        assertThat(credentials.matches(noAuthRequest())).isFalse();
        assertThat(credentials.matches(rawHeaderRequest(SECRET))).isFalse();
    }

    private HttpServletRequest bearerRequest(final String token) {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        return request;
    }

    private HttpServletRequest rawHeaderRequest(final String token) {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn(token);
        return request;
    }

    private HttpServletRequest noAuthRequest() {
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeader("Authorization")).thenReturn(null);
        return request;
    }
}
