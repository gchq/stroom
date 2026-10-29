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

package stroom.visualisation.client.component;

import stroom.docref.DocRef;
import stroom.editor.client.component.CodeEditorController;
import stroom.explorer.client.component.DocSelectionController;
import stroom.script.shared.ScriptDoc;
import stroom.security.shared.DocumentPermission;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// A visualisation's Settings tab ([VisualisationSettings]): its function's name, its script and
/// the JSON of its options, which can't be changed in a document the user can't change.
public class VisualisationSettingsController extends Controller {

    private final VisualisationSettings view;
    private final DocSelectionController script;
    private final CodeEditorController settings;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The tab.
    /// @param docSelectionProvider         Makes the script's picker's controller.
    /// @param codeEditorControllerProvider Makes the options' editor's controller.
    @Inject
    public VisualisationSettingsController(final EventBus eventBus,
                                           final VisualisationSettings view,
                                           final Provider<DocSelectionController> docSelectionProvider,
                                           final Provider<CodeEditorController> codeEditorControllerProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        script = make(docSelectionProvider);
        script.attach(view.getScriptBox());
        script.setIncludedTypes(ScriptDoc.TYPE);
        script.setRequiredPermissions(DocumentPermission.USE);
        settings = make(codeEditorControllerProvider).attach(view.getSettingsEditor());
        settings.setMode(AceEditorMode.JSON);

        // Each change makes the document dirty
        view.setOnChange(this::markChanged);
        own(settings.addValueChangeHandler(event -> markChanged()));
        own(script.addSelectionListener(event -> markChanged()));
        own(settings.addFormatListener(this::markChanged));
        watch(Contexts.READ_ONLY, this::setReadOnly);
    }

    /// Shows a visualisation's settings.
    ///
    /// @param docRef        The visualisation.
    /// @param visualisation Its settings.
    public void read(final DocRef docRef, final VisualisationDoc visualisation) {
        view.setFunctionName(visualisation.getFunctionName());
        script.setSelectedEntityReference(visualisation.getScriptRef(), true);
        settings.setText(visualisation.getSettings());
    }

    /// Writes the settings shown into a copy of a visualisation.
    ///
    /// @param visualisation The visualisation.
    /// @return A copy of it with the settings shown.
    public VisualisationDoc write(final VisualisationDoc visualisation) {
        return visualisation
                .copy()
                .functionName(view.getFunctionName().trim())
                .scriptRef(script.getSelectedEntityReference())
                .settings(settings.getText().trim())
                .build();
    }

    private void setReadOnly(final boolean readOnly) {
        view.setReadOnly(readOnly);
        script.setReadOnly(readOnly);
        settings.setReadOnly(readOnly);
        settings.getFormatAction().setAvailable(!readOnly);
    }
}
