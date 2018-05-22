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

package stroom.script.client.presenter;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextArea;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.core.client.event.DirtyKeyDownHander;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.DocumentSettingsPresenter;
import stroom.query.api.v2.DocRef;
import stroom.script.client.presenter.ScriptSettingsPresenter.ScriptSettingsView;
import stroom.script.shared.ScriptDoc;
import stroom.security.client.ClientSecurityContext;

public class ScriptSettingsPresenter
        extends DocumentSettingsPresenter<ScriptSettingsView, ScriptDoc> {
    private final ScriptDependencyListPresenter scriptDependencyListPresenter;

    @Inject
    public ScriptSettingsPresenter(final EventBus eventBus, final ScriptSettingsView view,
                                   final ScriptDependencyListPresenter scriptDependencyListPresenter,
                                   final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);
        this.scriptDependencyListPresenter = scriptDependencyListPresenter;

        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };

        registerHandler(view.getDescription().addKeyDownHandler(keyDownHander));
        view.setDependencyList(scriptDependencyListPresenter.getView());
    }

    @Override
    public String getType() {
        return ScriptDoc.DOCUMENT_TYPE;
    }

    @Override
    protected void onBind() {
        final DirtyHandler dirtyHandler = event -> setDirty(true);
        registerHandler(scriptDependencyListPresenter.addDirtyHandler(dirtyHandler));
    }

    @Override
    protected void onRead(final DocRef docRef, final ScriptDoc script) {
        getView().getDescription().setText(script.getDescription());

        scriptDependencyListPresenter.read(docRef, script);
    }

    @Override
    protected void onWrite(final ScriptDoc script) {
        script.setDescription(getView().getDescription().getText().trim());

        scriptDependencyListPresenter.write(script);
    }

    public interface ScriptSettingsView extends View {
        TextArea getDescription();

        void setDependencyList(View view);
    }
}
