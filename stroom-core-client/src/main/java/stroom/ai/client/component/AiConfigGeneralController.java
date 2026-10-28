/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client.component;

import stroom.ai.client.component.AskStroomAiController.DockBehaviour;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.explorer.client.component.DocSelectionController;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The General tab of the Ask Stroom AI settings ([AiConfigGeneral]), offering the AI models the
/// user may use.
public class AiConfigGeneralController extends Controller {

    private final AiConfigGeneral view;
    private final DocSelectionController model;

    /// Made by GIN, with its tab.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The tab.
    /// @param docSelectionProvider Makes the model's picker.
    @Inject
    public AiConfigGeneralController(final EventBus eventBus,
                                     final AiConfigGeneral view,
                                     final Provider<DocSelectionController> docSelectionProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        model = make(docSelectionProvider);
        model.attach(view.getModelBox());
        model.setIncludedTypes(OpenAIModelDoc.TYPE);
        model.setRequiredPermissions(DocumentPermission.USE);
    }

    /// Shows the settings, using the defaults for any the settings don't have.
    ///
    /// @param config        The settings, or null for the defaults.
    /// @param dockBehaviour Where the panel is shown.
    public void read(final AskStroomAiConfig config, final DockBehaviour dockBehaviour) {
        model.setSelectedEntityReference(NullSafe.get(config, AskStroomAiConfig::getModelRef), true);
        view.read(config, dockBehaviour);
    }

    /// Writes the settings shown into the settings' builder.
    ///
    /// @param builder The builder.
    public void write(final AskStroomAiConfig.Builder builder) {
        builder.modelRef(model.getSelectedEntityReference());
        view.write(builder);
    }

    /// @return Where the panel is shown, as chosen.
    public DockBehaviour getDockBehaviour() {
        return view.getDockBehaviour();
    }

    /// @param onDockBehaviourChange What to run, with where the panel is now to be shown, when the
    ///                              user changes the dock type or location.
    public void setOnDockBehaviourChange(final Consumer<DockBehaviour> onDockBehaviourChange) {
        view.setOnDockBehaviourChange(onDockBehaviourChange);
    }

    /// Puts the focus on the tab's first setting.
    public void focus() {
        view.focus();
    }
}
