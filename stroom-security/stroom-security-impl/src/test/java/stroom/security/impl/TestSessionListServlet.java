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

import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.SessionListResponse;
import stroom.util.shared.UserRef;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestSessionListServlet {

    private static final String AGENT_PAYLOAD = "<script>alert('xss')</script>";
    private static final String DISPLAY_NAME_PAYLOAD = "<img src=x onerror=alert(1)>";

    @Test
    void sessionListEscapesUserControlledValues() throws Exception {
        final String html = render(sessionWith(AGENT_PAYLOAD, DISPLAY_NAME_PAYLOAD));

        // The attacker-controlled User-Agent must be escaped, not emitted as live markup.
        assertThat(html).contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;");
        assertThat(html).doesNotContain(AGENT_PAYLOAD);
        assertThat(html).doesNotContain("<script>alert");

        // The (potentially IdP-supplied) display name must be escaped too.
        assertThat(html).contains("&lt;img src=x onerror=alert(1)&gt;");
        assertThat(html).doesNotContain(DISPLAY_NAME_PAYLOAD);

        // The intentional styling markup around the agent is preserved.
        assertThat(html).contains("<span class=\"agent\">");
    }

    @Test
    void sessionListSetsStrictContentSecurityPolicy() throws Exception {
        final HttpServletResponse response = drive(sessionWith("Mozilla/5.0", "alice"));
        // A strict CSP that forbids all script is set as a backstop, overriding the app-wide policy.
        verify(response).setHeader("Content-Security-Policy", "default-src 'none'; style-src 'self'");
        verify(response).setContentType("text/html; charset=UTF-8");
    }

    private String render(final SessionDetails sessionDetails) throws Exception {
        final StringWriter stringWriter = new StringWriter();
        driveInto(sessionDetails, stringWriter);
        return stringWriter.toString();
    }

    private HttpServletResponse drive(final SessionDetails sessionDetails) throws Exception {
        return driveInto(sessionDetails, new StringWriter());
    }

    private HttpServletResponse driveInto(final SessionDetails sessionDetails,
                                          final StringWriter sink) throws Exception {
        final SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)).thenReturn(true);

        final SessionListService sessionListService = mock(SessionListService.class);
        when(sessionListService.listSessions())
                .thenReturn(new SessionListResponse(List.of(sessionDetails)));

        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final PrintWriter printWriter = new PrintWriter(sink);
        when(response.getWriter()).thenReturn(printWriter);

        new SessionListServlet(securityContext, sessionListService).doGet(request, response);
        printWriter.flush();
        return response;
    }

    private SessionDetails sessionWith(final String agent, final String displayName) {
        final UserRef userRef = UserRef.builder()
                .uuid("user-uuid")
                .subjectId("subject-id")
                .displayName(displayName)
                .fullName("Full Name")
                .build();
        return new SessionDetails(userRef, 0L, 0L, agent, "node1");
    }
}
