/*
 * Copyright 2016 Crown Copyright
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

package stroom.servlet;

import stroom.security.Security;
import stroom.task.api.TaskIdFactory;
import stroom.task.TaskManager;
import stroom.util.date.DateUtil;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

public class SessionListServlet extends HttpServlet {
    private static final long serialVersionUID = 8723931558071593017L;

    private final TaskManager taskManager;
    private final Security security;

    @Inject
    SessionListServlet(final TaskManager taskManager,
                       final Security security) {
        this.taskManager = taskManager;
        this.security = security;
    }

    /**
     * Method interceptor needs to go on public API By-pass authentication / authorisation checks.
     * <p>
     * This servlet is NOT protected by default and should be filtered by Apache access controls, see documentation for
     * details.
     */
    @Override
    public void service(final ServletRequest req, final ServletResponse res) {
        security.insecure(() -> {
            try {
                super.service(req, res);
            } catch (ServletException e) {
                throw new RuntimeException(e.getMessage(), e);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws IOException {
        response.setContentType("text/html");

        final List<List<String>> table = new ArrayList<>();

        final SessionListAction sessionListAction = new SessionListAction();
        sessionListAction.setId(TaskIdFactory.create());

        final List<SessionDetails> sessionDetailsList = taskManager.exec(sessionListAction);

        final HttpSession httpSession = request.getSession(false);

        for (final SessionDetails sessionDetails : sessionDetailsList) {
            String prefix = "";
            String suffix = "";
            if (httpSession != null) {
                if (httpSession.getId().equals(sessionDetails.getId())) {
                    prefix = "<b>";
                    suffix = "</b>";
                }
            }
            final ArrayList<String> row = new ArrayList<>();
            row.add(DateUtil.createNormalDateTimeString(sessionDetails.getLastAccessedMs()));
            row.add(DateUtil.createNormalDateTimeString(sessionDetails.getCreateMs()));
            row.add(sessionDetails.getUserName());
            row.add(sessionDetails.getNodeName());
            row.add(prefix + sessionDetails.getId() + suffix);
            row.add("<span class=\"agent\">" + sessionDetails.getLastAccessedAgent() + "</span>");
            table.add(row);
        }

        table.sort((l1, l2) -> l2.get(0).compareTo(l1.get(0)));

        response.getWriter().write(
                "<html><head><link type=\"text/css\" href=\"css/SessionList.css\" rel=\"stylesheet\" /></head><body>");
        response.getWriter().write("<table>");
        response.getWriter().write(
                "<thead><tr><th>Last Accessed</th><th>Created</th><th>User Id</th><th>Node</th><th>Session Id</th><th>Agent</th></tr></thead>");

        for (final List<String> row : table) {
            response.getWriter().write("<tr>");

            for (final String cell : row) {
                response.getWriter().write("<td>");
                if (cell == null) {
                    response.getWriter().write("-");
                } else {
                    response.getWriter().write(cell);
                }
                response.getWriter().write("</td>");
            }
            response.getWriter().write("</tr>");
        }
        response.getWriter().write("</table>");
        response.getWriter().write("</body></html>");

        response.setStatus(HttpServletResponse.SC_OK);
    }
}
