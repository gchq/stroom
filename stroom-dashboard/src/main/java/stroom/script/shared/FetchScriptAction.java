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

package stroom.script.shared;

import stroom.dispatch.shared.Action;
import stroom.entity.shared.DocRef;
import stroom.util.shared.SharedList;

import java.util.Set;

public class FetchScriptAction extends Action<SharedList<Script>> {
    private static final long serialVersionUID = -1773544031158236156L;

    private DocRef script;
    private Set<DocRef> loadedScripts;
    private Set<String> fetchSet;

    public FetchScriptAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchScriptAction(final DocRef script, final Set<DocRef> loadedScripts, final Set<String> fetchSet) {
        this.script = script;
        this.loadedScripts = loadedScripts;
        this.fetchSet = fetchSet;
    }

    public DocRef getScript() {
        return script;
    }

    public Set<DocRef> getLoadedScripts() {
        return loadedScripts;
    }

    public Set<String> getFetchSet() {
        return fetchSet;
    }

    @Override
    public String getTaskName() {
        return "Dashboard - fetchScript()";
    }
}
