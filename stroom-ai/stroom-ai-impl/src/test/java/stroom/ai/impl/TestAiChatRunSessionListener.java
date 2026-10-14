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

package stroom.ai.impl;

import stroom.ai.shared.AiChatRunStopReason;

import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpSessionEvent;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TestAiChatRunSessionListener {

    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final AiChatRunSessionListener listener = new AiChatRunSessionListener(() -> runManager);

    @Test
    void testSignOut() {
        final HttpSession session = session("s1", true);

        listener.sessionDestroyed(new HttpSessionEvent(session));

        Mockito.verify(runManager).stopForSession("s1", AiChatRunStopReason.SIGN_OUT);
    }

    @Test
    void testExpiry() {
        final HttpSession session = session("s2", false);

        listener.sessionDestroyed(new HttpSessionEvent(session));

        Mockito.verify(runManager).stopForSession("s2", AiChatRunStopReason.SESSION_EXPIRED);
    }

    private static HttpSession session(final String id, final boolean explicitLogout) {
        final HttpSession session = Mockito.mock(HttpSession.class);
        Mockito.when(session.getId()).thenReturn(id);
        Mockito.when(session.getAttribute("stroom.explicitLogout")).thenReturn(explicitLogout
                ? Boolean.TRUE
                : null);
        return session;
    }
}
