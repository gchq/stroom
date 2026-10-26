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

package stroom.script.client.presenter;

import stroom.docref.DocRef;
import stroom.entity.client.presenter.DocPresenter;
import stroom.script.client.component.ScriptDependencyListController;
import stroom.script.client.presenter.ScriptSettingsPresenter.ScriptSettingsView;
import stroom.script.shared.ScriptDoc;
import stroom.widget.presenter.client.View;

import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class ScriptSettingsPresenter extends DocPresenter<ScriptSettingsView, ScriptDoc> {

    private final ScriptDependencyListController scriptDependencyListPresenter;

    @Inject
    public ScriptSettingsPresenter(final EventBus eventBus,
                                   final ScriptSettingsView view,
                                   final ScriptDependencyListController scriptDependencyListPresenter) {
        super(eventBus, view);
        own(scriptDependencyListPresenter);
        this.scriptDependencyListPresenter = scriptDependencyListPresenter;
        view.setDependencyList(scriptDependencyListPresenter);

        scriptDependencyListPresenter.setOnChange(this::onChange);
    }

    @Override
    protected void onRead(final DocRef docRef, final ScriptDoc script) {
        scriptDependencyListPresenter.read(docRef, script);
    }

    @Override
    protected ScriptDoc onWrite(ScriptDoc script) {
        script = scriptDependencyListPresenter.write(script);
        return script;
    }

    public interface ScriptSettingsView extends View {

        void setDependencyList(IsWidget view);
    }
}
