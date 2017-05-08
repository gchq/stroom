/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.script.server;

import org.springframework.context.annotation.Scope;
import stroom.query.api.DocRef;
import stroom.script.shared.FetchScriptAction;
import stroom.script.shared.Script;
import stroom.script.shared.ScriptService;
import stroom.security.SecurityContext;
import stroom.task.server.AbstractTaskHandler;
import stroom.task.server.TaskHandlerBean;
import stroom.util.shared.SharedList;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@TaskHandlerBean(task = FetchScriptAction.class)
@Scope(value = StroomScope.TASK)
/**
 * For now we deliberately require visualisation view permissions to access
 * scripts.
 */
class FetchScriptHandler extends AbstractTaskHandler<FetchScriptAction, SharedList<Script>> {
    private final ScriptService scriptService;
    private final SecurityContext securityContext;

    @Inject
    FetchScriptHandler(final ScriptService scriptService, final SecurityContext securityContext) {
        this.scriptService = scriptService;
        this.securityContext = securityContext;
    }

    @Override
    public SharedList<Script> exec(final FetchScriptAction action) {
        try {
            // Elevate the users permissions for the duration of this task so they can read the script if they have 'use' permission.
            securityContext.elevatePermissions();

            final List<Script> scripts = new ArrayList<>();

            Set<DocRef> uiLoadedScripts = action.getLoadedScripts();
            if (uiLoadedScripts == null) {
                uiLoadedScripts = new HashSet<>();
            }

            // Load the script and it's dependencies.
            loadScripts(action.getScript(), uiLoadedScripts, new HashSet<>(), scripts, action.getFetchSet());

            return new SharedList<>(scripts);
        } finally {
            securityContext.restorePermissions();
        }
    }

    private void loadScripts(final DocRef docRef, final Set<DocRef> uiLoadedScripts, final Set<DocRef> loadedScripts,
                             final List<Script> scripts, final Set<String> actionFetchSet) {
        // Prevent circular reference loading with this set.
        if (!loadedScripts.contains(docRef)) {
            loadedScripts.add(docRef);

            // Load the script.
            final Set<String> fetchSet = new HashSet<>();
            // Don't bother to fetch the script resource if the UI will not need
            // it.
            if (actionFetchSet != null && actionFetchSet.contains(Script.FETCH_RESOURCE)
                    && !uiLoadedScripts.contains(docRef)) {
                fetchSet.add(Script.FETCH_RESOURCE);
            }

            final Script loadedScript = scriptService.loadByUuid(docRef.getUuid(), fetchSet);
            if (loadedScript != null) {
                // Add required dependencies first.
                if (loadedScript.getDependencies() != null) {
                    for (final DocRef dep : loadedScript.getDependencies()) {
                        loadScripts(dep, uiLoadedScripts, loadedScripts, scripts, actionFetchSet);
                    }
                }

                // Add this script.
                if (!uiLoadedScripts.contains(docRef)) {
                    uiLoadedScripts.add(docRef);
                    scripts.add(loadedScript);
                }
            }
        }
    }
}
