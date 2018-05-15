/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.script;

import stroom.script.shared.Script;
import stroom.security.Security;

import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * <p>
 * SERVLET that reports status of Stroom for scripting purposes.
 * </p>
 */
public class ScriptServlet extends HttpServlet {
    private static final long serialVersionUID = 2912973031600581055L;

    private static final Set<String> FETCH_SET = Collections.emptySet();

    private final ScriptService scriptService;
    private final Security security;

    @Inject
    ScriptServlet(final ScriptService scriptService,
                  final Security security) {
        this.scriptService = scriptService;
        this.security = security;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        // Elevate the users permissions for the duration of this task so they can read the script if they have 'use' permission.
        security.useAsRead(() -> {
            try {
                response.setContentType("text/javascript");
                response.setCharacterEncoding("UTF-8");

                final String query = request.getQueryString();
                if (query != null) {
                    final Map<String, String> queryParamMap = createQueryParamMap(query);

                    final String uuid = queryParamMap.get("uuid");
                    if (uuid != null && !uuid.isEmpty()) {
                        final Script script = getScript(uuid);
                        if (script != null && script.getResource() != null) {
                            final PrintWriter pw = response.getWriter();
                            pw.write(script.getResource());
                            pw.close();
                        }
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private Script getScript(final String uuid) {
        return scriptService.loadByUuidInsecure(uuid, FETCH_SET);
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
