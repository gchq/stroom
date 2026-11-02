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

package stroom.util.servlet;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TestSessionUtil {

    @Test
    void testExplicitLogout() {
        final HttpSession session = sessionWithAttributes();

        assertThat(SessionUtil.isExplicitLogout(session)).isFalse();
        SessionUtil.markExplicitLogout(session);
        assertThat(SessionUtil.isExplicitLogout(session)).isTrue();
    }

    @Test
    void testExplicitLogout_nullOrInvalidSession() {
        final HttpSession invalid = Mockito.mock(HttpSession.class);
        Mockito.when(invalid.getAttribute(Mockito.anyString())).thenThrow(new IllegalStateException("invalid"));
        Mockito.doThrow(new IllegalStateException("invalid"))
                .when(invalid).setAttribute(Mockito.anyString(), Mockito.any());

        assertThat(SessionUtil.isExplicitLogout(null)).isFalse();
        assertThat(SessionUtil.isExplicitLogout(invalid)).isFalse();
        assertThatCode(() -> SessionUtil.markExplicitLogout(null)).doesNotThrowAnyException();
        assertThatCode(() -> SessionUtil.markExplicitLogout(invalid)).doesNotThrowAnyException();
    }

    private static HttpSession sessionWithAttributes() {
        final Map<String, Object> attributes = new HashMap<>();
        final HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.doAnswer(invocation -> attributes.put(invocation.getArgument(0), invocation.getArgument(1)))
                .when(session).setAttribute(Mockito.anyString(), Mockito.any());
        Mockito.when(session.getAttribute(Mockito.anyString()))
                .thenAnswer(invocation -> attributes.get(invocation.<String>getArgument(0)));
        return session;
    }
}
