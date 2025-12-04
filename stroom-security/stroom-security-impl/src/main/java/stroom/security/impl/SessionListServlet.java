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
import stroom.util.date.DateUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsServlet;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;

class SessionListServlet extends HttpServlet implements IsServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SessionListServlet.class);

    public static final String PATH_PART = "/sessionList";
    private static final Set<String> PATH_SPECS = Set.of(
            PATH_PART,
            ResourcePaths.addLegacyAuthenticatedServletPrefix(PATH_PART));

    private final SecurityContext securityContext;
    private final SessionListService sessionListService;

    @Inject
    SessionListServlet(final SecurityContext securityContext,
                       final SessionListService sessionListService) {
        this.securityContext = securityContext;
        this.sessionListService = sessionListService;
    }

    /**
     * Method interceptor needs to go on public API By-pass authentication / authorisation checks.
     * <p>
     * This servlet is NOT protected by default and should be filtered by Apache access controls, see documentation for
     * details.
     */
    @Override
    public void service(final ServletRequest req, final ServletResponse res) {
        try {
            super.service(req, res);
        } catch (final ServletException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You are not authorised to view the session list");
        }
        showSessionList(request, response);
    }

    private void showSessionList(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html");

        response.getWriter().write("<html>" +
                                   "<head><link type=\"text/css\" href=\"/ui/css/SessionList.css\" rel=\"stylesheet\" /></head>" +
                                   "<body>");
        response.getWriter().write("<table>");
        response.getWriter().write("<thead>" +
                                   "<tr>" +
                                   "<th>Last Accessed</th>" +
                                   "<th>Created</th>" +
                                   "<th>User Display Name</th>" +
                                   "<th>User Full Name</th>" +
                                   "<th>User Unique Identity</th>" +
                                   "<th>Node</th>" +
                                   "<th>Agent</th>" +
                                   "</tr>" +
                                   "</thead>");

        final Writer writer = response.getWriter();
        try {
            sessionListService
                    .listSessions()
                    .stream()
                    .filter(sessionDetails -> Objects.nonNull(sessionDetails.getUserRef()))
                    .sorted(Comparator.comparing(SessionDetails::getLastAccessedMs))
                    .forEach(sessionDetails -> {
                        try {
                            writer.write("<tr>");
                            final String subjectId = NullSafe.get(
                                    sessionDetails.getUserRef(),
                                    UserRef::getSubjectId);
                            final String displayName = NullSafe.getOrElse(
                                    sessionDetails.getUserRef(),
                                    UserRef::getDisplayName,
                                    subjectId);

                            writeCell(writer, DateUtil.createNormalDateTimeString(sessionDetails.getLastAccessedMs()));
                            writeCell(writer, DateUtil.createNormalDateTimeString(sessionDetails.getCreateMs()));
                            writeCell(writer, displayName);
                            writeCell(writer, NullSafe.get(sessionDetails.getUserRef(), UserRef::getFullName));
                            writeCell(writer, subjectId);
                            writeCell(writer, sessionDetails.getNodeName());
                            writeCell(writer, "<span class=\"agent\">"
                                              + sessionDetails.getLastAccessedAgent() + "</span>");

                            writer.write("</tr>");
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (final Exception e) {
            LOGGER.error("Error displaying session list servlet: "
                         + LogUtil.exceptionMessage(e), e);
            throw new RuntimeException(e);
        }
        response.getWriter().write("</table>");
        response.getWriter().write("</body></html>");

        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void writeCell(final Writer writer, final String value) throws IOException {
        writer.write("<td>");
        writer.write(Objects.requireNonNullElse(value, "-"));
        writer.write("</td>");
    }

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    @Override
    public String getName() {
        return ResourcePaths.SESSION_LIST_SERVLET_NAME;
    }
}
