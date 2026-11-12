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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class TestMockHttpSessionFactory {

    @Test
    void returnsTheGivenId() {
        assertThat(MockHttpSessionFactory.create("session-1").getId())
                .isEqualTo("session-1");
    }

    @Test
    void attributeIsReadBack() {
        final HttpSession session = MockHttpSessionFactory.create("session-1");

        session.setAttribute("name", "value");

        assertThat(session.getAttribute("name"))
                .isEqualTo("value");
    }

    @Test
    void unsetAttributeIsNull() {
        assertThat(MockHttpSessionFactory.create("session-1").getAttribute("name"))
                .isNull();
    }

    @Test
    void attributeIsReplaced() {
        final HttpSession session = MockHttpSessionFactory.create("session-1");

        session.setAttribute("name", "first");
        session.setAttribute("name", "second");

        assertThat(session.getAttribute("name"))
                .isEqualTo("second");
    }

    @Test
    void removedAttributeIsNull() {
        final HttpSession session = MockHttpSessionFactory.create("session-1");
        session.setAttribute("name", "value");

        session.removeAttribute("name");

        assertThat(session.getAttribute("name"))
                .isNull();
    }

    @Test
    void settingANullValueRemovesTheAttribute() {
        // As on a real session.
        final HttpSession session = MockHttpSessionFactory.create("session-1");
        session.setAttribute("name", "value");

        session.setAttribute("name", null);

        assertThat(session.getAttribute("name"))
                .isNull();
    }

    @Test
    void sessionsDoNotShareAttributes() {
        final HttpSession session1 = MockHttpSessionFactory.create("session-1");
        final HttpSession session2 = MockHttpSessionFactory.create("session-2");

        session1.setAttribute("name", "value");

        assertThat(session2.getAttribute("name"))
                .isNull();
    }

    @Test
    void callsCanStillBeVerified() {
        final HttpSession session = MockHttpSessionFactory.create("session-1");

        session.setAttribute("name", "value");

        verify(session).setAttribute("name", "value");
    }
}
