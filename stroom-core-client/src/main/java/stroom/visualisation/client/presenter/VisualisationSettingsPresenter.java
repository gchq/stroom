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

package stroom.visualisation.client.presenter;

import stroom.core.client.event.DirtyKeyDownHander;
import stroom.docref.DocRef;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.DocumentEditPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.script.shared.ScriptDoc;
import stroom.security.shared.DocumentPermission;
import stroom.visualisation.client.presenter.VisualisationSettingsPresenter.VisualisationSettingsView;
import stroom.visualisation.shared.VisualisationDoc;

import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.event.dom.client.KeyDownHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public class VisualisationSettingsPresenter extends DocumentEditPresenter<VisualisationSettingsView, VisualisationDoc> {

    private final DocSelectionBoxPresenter scriptPresenter;
    private final EditorPresenter editorPresenter;

    @Inject
    public VisualisationSettingsPresenter(final EventBus eventBus,
                                          final VisualisationSettingsView view,
                                          final DocSelectionBoxPresenter scriptPresenter,
                                          final EditorPresenter editorPresenter) {
        super(eventBus, view);
        this.scriptPresenter = scriptPresenter;
        this.editorPresenter = editorPresenter;

        scriptPresenter.setIncludedTypes(ScriptDoc.TYPE);
        scriptPresenter.setRequiredPermissions(DocumentPermission.USE);

        view.setScriptView(scriptPresenter.getView());
        view.setSettingsView(editorPresenter.getView());

        editorPresenter.setMode(AceEditorMode.JSON);
    }

    @Override
    protected void onBind() {
        // Add listeners for dirty events.
        final KeyDownHandler keyDownHander = new DirtyKeyDownHander() {
            @Override
            public void onDirty(final KeyDownEvent event) {
                setDirty(true);
            }
        };
        registerHandler(getView().getFunctionName().addKeyDownHandler(keyDownHander));
        registerHandler(editorPresenter.addValueChangeHandler(event -> setDirty(true)));
        registerHandler(scriptPresenter.addDataSelectionHandler(event -> setDirty(true)));
        registerHandler(editorPresenter.addFormatHandler(event -> setDirty(true)));
    }

    @Override
    protected void onRead(final DocRef docRef, final VisualisationDoc visualisation, final boolean readOnly) {
        scriptPresenter.setEnabled(!readOnly);
        editorPresenter.setReadOnly(readOnly);
        editorPresenter.getFormatAction().setAvailable(!readOnly);

        getView().getFunctionName().setText(visualisation.getFunctionName());
        scriptPresenter.setSelectedEntityReference(visualisation.getScriptRef(), true);
        editorPresenter.setText(visualisation.getSettings());
    }

    @Override
    protected VisualisationDoc onWrite(final VisualisationDoc visualisation) {
        visualisation.setFunctionName(getView().getFunctionName().getText().trim());
        visualisation.setScriptRef(scriptPresenter.getSelectedEntityReference());
        visualisation.setSettings(editorPresenter.getText().trim());
        return visualisation;
    }


    // --------------------------------------------------------------------------------


    public interface VisualisationSettingsView extends View {

        TextBox getFunctionName();

        void setScriptView(View view);

        void setSettingsView(View view);
    }
}
