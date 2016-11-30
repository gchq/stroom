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

package stroom.script.server;

import org.springframework.aop.framework.Advised;
import org.springframework.stereotype.Component;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.shared.Res;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;
import stroom.security.SecurityContext;
import stroom.util.task.TaskScopeContextHolder;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * SERVLET that reports status of Stroom for scripting purposes.
 * </p>
 */
@Component(ScriptServlet.BEAN_NAME)
public class ScriptServlet extends HttpServlet {
    public static final String BEAN_NAME = "scriptServlet";

    private static final long serialVersionUID = 2912973031600581055L;

    private static final Set<String> FETCH_SET = Collections.singleton(Script.FETCH_RESOURCE);

    private final ScriptService scriptService;
    private final SecurityContext securityContext;

    @Inject
    ScriptServlet(final ScriptService scriptService, final SecurityContext securityContext) {
        this.scriptService = scriptService;
        this.securityContext = securityContext;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        TaskScopeContextHolder.addContext();
        try {
            // Elevate the users permissions for the duration of this task so they can read the script if they have 'use' permission.
            securityContext.elevatePermissions();

            response.setContentType("text/javascript");
            response.setCharacterEncoding("UTF-8");

            final String query = request.getQueryString();
            if (query != null) {
                final Map<String, String> queryParamMap = createQueryParamMap(query);

                final String uuid = queryParamMap.get("uuid");
                if (uuid != null && uuid.length() > 0) {
                    final Script script = getScript(uuid);
                    final Res res = script.getResource();
                    if (res != null && res.getData() != null) {
                        final PrintWriter pw = response.getWriter();
                        pw.write(res.getData());
                        pw.close();
                    }

                } else {
                    final String idString = queryParamMap.get("id");
                    if (idString != null && idString.length() > 0) {
                        final long id = Long.parseLong(idString);
                        final Script script = getScript(id);
                        final Res res = script.getResource();
                        if (res != null && res.getData() != null) {
                            final PrintWriter pw = response.getWriter();
                            pw.write(res.getData());
                            pw.close();
                        }
                    }
                }
            }

        } finally {
            securityContext.restorePermissions();
            TaskScopeContextHolder.removeContext();
        }
    }

    private Script getScript(final long id) {
        // TODO : Remove this when the explorer service is broken out as a separate micro service.
        final DocumentEntityServiceImpl documentEntityService = getDocEntityService();
        if (documentEntityService != null) {
            return (Script) documentEntityService.loadByIdInsecure(id, FETCH_SET);
        }

        return scriptService.loadById(id, FETCH_SET);
    }

    private Script getScript(final String uuid) {
        // TODO : Remove this when the explorer service is broken out as a separate micro service.
        final DocumentEntityServiceImpl documentEntityService = getDocEntityService();
        if (documentEntityService != null) {
            return (Script) documentEntityService.loadByUuidInsecure(uuid, FETCH_SET);
        }

        return scriptService.loadByUuid(uuid, FETCH_SET);
    }

    private DocumentEntityServiceImpl getDocEntityService() {
        final Object target = getTarget(scriptService);
        if (target instanceof DocumentEntityServiceImpl) {
            return (DocumentEntityServiceImpl) target;
        }

        return null;
    }

    private Object getTarget(Object obj) {
        if (obj instanceof Advised) {
            try {
                final Advised advised = (Advised) obj;
                return advised.getTargetSource().getTarget();
            } catch (final Exception e) {
                // Ignore
            }
        }

        return obj;
    }

    private Map<String, String> createQueryParamMap(final String query) {
        if (query == null) {
            return Collections.emptyMap();
        }

        final Map<String, String> map = new HashMap<>();
        final String[] parts = query.split("&");
        for (final String part : parts) {
            final String[] kv = part.split("=");
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }

        return map;
    }
}
