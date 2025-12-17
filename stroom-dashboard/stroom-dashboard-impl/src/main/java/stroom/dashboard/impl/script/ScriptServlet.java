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

package stroom.dashboard.impl.script;

import stroom.docref.DocRef;
import stroom.script.shared.ScriptDoc;
import stroom.security.api.SecurityContext;
import stroom.util.shared.IsServlet;
import stroom.util.shared.ResourcePaths;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provides access to {@link ScriptDoc}s for the visualisations
 */
class ScriptServlet extends HttpServlet implements IsServlet {

    private static final long serialVersionUID = 2912973031600581055L;
    private static final Set<String> PATH_SPECS = Set.of(ResourcePaths.SCRIPT_PATH);

    private final ScriptStore scriptStore;
    private final SecurityContext securityContext;

    @Inject
    ScriptServlet(final ScriptStore scriptStore,
                  final SecurityContext securityContext) {
        this.scriptStore = scriptStore;
        this.securityContext = securityContext;
    }

    @Override
    protected void doGet(final HttpServletRequest request, final HttpServletResponse response) {
        // Elevate the users permissions for the duration of this task, so they can read the script if
        // they have 'use' permission.
        securityContext.useAsRead(() -> {
            try {
                response.setContentType("text/javascript");
                response.setCharacterEncoding("UTF-8");

                final String query = request.getQueryString();
                if (query != null) {
                    final Map<String, String> queryParamMap = createQueryParamMap(query);

                    final String uuid = queryParamMap.get("uuid");
                    if (uuid != null && !uuid.isEmpty()) {
                        final ScriptDoc script = getScript(uuid);
                        if (script.getData() != null) {
                            final PrintWriter pw = response.getWriter();
                            pw.write(script.getData());
                            pw.close();
                        }
                    }
                }
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private ScriptDoc getScript(final String uuid) {
        return scriptStore.readDocument(new DocRef(ScriptDoc.TYPE, uuid));
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

    /**
     * @return The part of the path that will be in addition to any base path,
     * e.g. "/datafeed".
     */
    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }
}
