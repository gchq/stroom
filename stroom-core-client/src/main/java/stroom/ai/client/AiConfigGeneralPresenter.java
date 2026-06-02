/*
 * Copyright 2025 Crown Copyright
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

package stroom.ai.client;

import stroom.ai.client.AiConfigGeneralPresenter.AiConfigGeneralView;
import stroom.ai.client.AskStroomAiPresenter.DockBehaviour;
import stroom.ai.client.AskStroomAiPresenter.DockLocation;
import stroom.ai.client.AskStroomAiPresenter.DockType;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AiConfigGeneralPresenter
        extends MyPresenterWidget<AiConfigGeneralView>
        implements AiConfigGeneralUiHandlers {

    private final DocSelectionBoxPresenter docSelectionBoxPresenter;
    private Consumer<DockBehaviour> dockBehaviourChangeHandler;

    @Inject
    public AiConfigGeneralPresenter(final EventBus eventBus,
                                    final AiConfigGeneralView view,
                                    final ClientSecurityContext clientSecurityContext,
                                    final DocSelectionBoxPresenter docSelectionBoxPresenter) {
        super(eventBus, view);
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;

        view.setUiHandlers(this);

        getView().setModelRefSelection(docSelectionBoxPresenter.getView());
        docSelectionBoxPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        docSelectionBoxPresenter.setRequiredPermissions(DocumentPermission.USE);
    }

    // ---------------------------------------------------------------------

    public void setDockBehaviourChangeHandler(final Consumer<DockBehaviour> handler) {
        this.dockBehaviourChangeHandler = handler;
    }

    public void read(final AskStroomAIConfig config,
                     final DockBehaviour dockBehaviour) {
        if (config != null && config.getModelRef() != null) {
            docSelectionBoxPresenter.setSelectedEntityReference(config.getModelRef(), true);
        }
        getView().setChatSystemPrompt(NullSafe.getOrElse(
                config,
                AskStroomAIConfig::getChatSystemPrompt,
                AskStroomAIConfig.DEFAULT_CHAT_SYSTEM_PROMPT));
        getView().setMaxConversationHistoryMessages(NullSafe.getOrElse(
                config,
                AskStroomAIConfig::getMaxConversationHistoryMessages,
                AskStroomAIConfig.DEFAULT_MAX_CONVERSATION_HISTORY_MESSAGES));
        getView().setDockBehaviour(dockBehaviour);
    }

    public void write(final AskStroomAIConfig.Builder builder) {
        builder
                .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                .chatSystemPrompt(getView().getChatSystemPrompt())
                .maxConversationHistoryMessages(getView().getMaxConversationHistoryMessages());
    }

    public DockBehaviour getDockBehaviour() {
        return getView().getDockBehaviour();
    }

    // ---------------------------------------------------------------------

    @Override
    public void onDockBehaviourChange(final DockBehaviour dockBehaviour) {
        if (dockBehaviourChangeHandler != null) {
            dockBehaviourChangeHandler.accept(dockBehaviour);
        }
    }

    // ---------------------------------------------------------------------


    public interface AiConfigGeneralView
            extends View, Focus, HasUiHandlers<AiConfigGeneralUiHandlers> {

        void setModelRefSelection(View view);

        void setDockBehaviour(DockBehaviour dockBehaviour);

        DockBehaviour getDockBehaviour();

        void setChatSystemPrompt(String prompt);

        String getChatSystemPrompt();

        void setMaxConversationHistoryMessages(int max);

        int getMaxConversationHistoryMessages();
    }
}
