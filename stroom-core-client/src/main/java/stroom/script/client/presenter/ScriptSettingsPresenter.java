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

package stroom.script.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.script.client.presenter.ScriptSettingsPresenter.ScriptSettingsView;
import stroom.script.shared.ScriptDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class ScriptSettingsPresenter extends DocumentEditPresenter<ScriptSettingsView, ScriptDoc> {

    private final ScriptDependencyListPresenter scriptDependencyListPresenter;

    @Inject
    public ScriptSettingsPresenter(final EventBus eventBus,
                                   final ScriptSettingsView view,
                                   final ScriptDependencyListPresenter scriptDependencyListPresenter) {
        super(eventBus, view);
        this.scriptDependencyListPresenter = scriptDependencyListPresenter;
        view.setDependencyList(scriptDependencyListPresenter.getView());
    }

    @Override
    protected void onBind() {
        final DirtyHandler dirtyHandler = event -> setDirty(true);
        registerHandler(scriptDependencyListPresenter.addDirtyHandler(dirtyHandler));
    }

    @Override
    protected void onRead(final DocRef docRef, final ScriptDoc script, final boolean readOnly) {
        scriptDependencyListPresenter.read(docRef, script, readOnly);
    }

    @Override
    protected ScriptDoc onWrite(ScriptDoc script) {
        script = scriptDependencyListPresenter.write(script);
        return script;
    }

    public interface ScriptSettingsView extends View {

        void setDependencyList(View view);
    }
}
