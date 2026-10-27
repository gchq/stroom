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

package stroom.script.client.component;

import stroom.docref.DocRef;
import stroom.script.shared.ScriptDoc;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A script's Settings tab ([ScriptSettings]): the other scripts it depends on.
public class ScriptSettingsController extends Controller {

    private final ScriptDependencyListController scriptDependencyList;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param scriptDependencyList The scripts it depends on.
    @Inject
    public ScriptSettingsController(final EventBus eventBus,
                                    final ScriptSettings view,
                                    final ScriptDependencyListController scriptDependencyList) {
        super(eventBus);
        this.scriptDependencyList = own(scriptDependencyList);
        initWidget(view);
        view.setDependencyList(scriptDependencyList);
        scriptDependencyList.setOnChange(this::markChanged);
    }

    /// Shows a script's dependencies.
    ///
    /// @param docRef The script.
    /// @param script The script.
    public void read(final DocRef docRef, final ScriptDoc script) {
        scriptDependencyList.read(docRef, script);
    }

    /// Writes the dependencies shown into a copy of a script.
    ///
    /// @param script The script.
    /// @return A copy of it with the dependencies shown.
    public ScriptDoc write(final ScriptDoc script) {
        return scriptDependencyList.write(script);
    }
}
