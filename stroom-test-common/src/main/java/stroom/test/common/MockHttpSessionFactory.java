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

package stroom.test.common;

import jakarta.servlet.http.HttpSession;
import org.mockito.Mockito;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/// Creates Mockito mock [HttpSession]s whose attributes behave like a real session's, so code
/// under test can set and read attributes through its normal API rather than tests stubbing
/// individual attribute names.
public final class MockHttpSessionFactory {

    private MockHttpSessionFactory() {
    }

    /// Create a mock session with the given id whose `setAttribute`, `getAttribute` and
    /// `removeAttribute` are backed by a map. Setting an attribute to null removes it, as it
    /// does on a real session.
    ///
    /// All stubbing is lenient, so it is safe to use with strict stubs. Other methods can be
    /// stubbed or verified on the returned mock as usual.
    ///
    /// @param sessionId The id the session returns from `getId()`.
    /// @return The mock session.
    public static HttpSession create(final String sessionId) {
        final Map<String, Object> attributes = new ConcurrentHashMap<>();
        final HttpSession session = Mockito.mock(HttpSession.class);
        lenient().when(session.getId()).thenReturn(sessionId);
        lenient().doAnswer(invocation -> {
            final String name = invocation.getArgument(0);
            final Object value = invocation.getArgument(1);
            if (value == null) {
                attributes.remove(name);
            } else {
                attributes.put(name, value);
            }
            return null;
        }).when(session).setAttribute(anyString(), any());
        lenient().doAnswer(invocation -> attributes.remove((String) invocation.getArgument(0)))
                .when(session).removeAttribute(anyString());
        lenient().when(session.getAttribute(anyString()))
                .thenAnswer(invocation -> attributes.get((String) invocation.getArgument(0)));
        return session;
    }
}
