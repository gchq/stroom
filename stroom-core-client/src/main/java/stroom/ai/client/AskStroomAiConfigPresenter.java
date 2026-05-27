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

import stroom.ai.client.AskStroomAiConfigPresenter.AskStroomAiConfigView;
import stroom.ai.client.AskStroomAiPresenter.DockBehaviour;
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.TableSummaryConfig;
import stroom.alert.client.event.AlertEvent;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.function.Consumer;

public class AskStroomAiConfigPresenter
        extends MyPresenterWidget<AskStroomAiConfigView>
        implements AskStroomAiConfigUiHandlers {

    private final AskStroomAiClient askStroomAiClient;
    private final DocSelectionBoxPresenter docSelectionBoxPresenter;

    private Consumer<DockBehaviour> dockBehaviourChangeHandler;

    @Inject
    public AskStroomAiConfigPresenter(final EventBus eventBus,
                                      final AskStroomAiConfigView view,
                                      final AskStroomAiClient askStroomAiClient,
                                      final ClientSecurityContext clientSecurityContext,
                                      final DocSelectionBoxPresenter docSelectionBoxPresenter) {
        super(eventBus, view);
        this.askStroomAiClient = askStroomAiClient;
        this.docSelectionBoxPresenter = docSelectionBoxPresenter;

        view.setUiHandlers(this);

        getView().setModelRefSelection(docSelectionBoxPresenter.getView());
        docSelectionBoxPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        docSelectionBoxPresenter.setRequiredPermissions(DocumentPermission.USE);

        // Only allow administrators to set the default model.
        view.allowSetDefault(clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION));
    }

    public void show(final AskStroomAIConfig currentConfig,
                     final Consumer<AskStroomAIConfig> configConsumer,
                     final DockBehaviour snapshotDockBehaviour,
                     final Consumer<DockBehaviour> dockBehaviourConsumer) {
        this.dockBehaviourChangeHandler = dockBehaviourConsumer;

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(600, 750))
                .caption("Configure Ask Stroom AI")
                .onShow(e -> {
                    read(currentConfig);
                    getView().setDockBehaviour(snapshotDockBehaviour);
                    getView().focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        // Update the local config.
                        final AskStroomAIConfig config = write();
                        askStroomAiClient.setConfig(config);
                        dockBehaviourConsumer.accept(getView().getDockBehaviour());
                        configConsumer.accept(config);
                    } else {
                        if (dockBehaviourConsumer != null && snapshotDockBehaviour != null) {
                            dockBehaviourConsumer.accept(snapshotDockBehaviour);
                        }
                    }
                    e.hide();
                })
                .fire();
    }

    @Override
    public void onSetDefault(final TaskMonitorFactory taskMonitorFactory) {
        final AskStroomAIConfig config = write();
        askStroomAiClient.setDefaultAskStroomAIConfig(config, success -> {
            AlertEvent.fireInfo(AskStroomAiConfigPresenter.this, "Default config updated", null);
        }, taskMonitorFactory);
    }

    @Override
    public void onDockBehaviourChange(final DockBehaviour dockBehaviour) {
        // Forward to the caller for immediate (live) application.
        if (dockBehaviourChangeHandler != null) {
            dockBehaviourChangeHandler.accept(dockBehaviour);
        }
    }

    private void read(final AskStroomAIConfig config) {
        readTableSummaryConfig(NullSafe.getOrElse(config,
                AskStroomAIConfig::getTableSummary, new TableSummaryConfig()));
        if (config != null && config.getModelRef() != null) {
            docSelectionBoxPresenter.setSelectedEntityReference(config.getModelRef(), true);
        }
    }

    public void readTableSummaryConfig(final TableSummaryConfig config) {
        getView().setMaximumBatchSize(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getMaximumBatchSize,
                TableSummaryConfig.DEFAULT_MAXIMUM_TABLE_BATCH_SIZE));
        getView().setMaximumTableInputRows(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getMaximumTableInputRows,
                TableSummaryConfig.DEFAULT_MAXIMUM_TABLE_INPUT_ROWS));
        getView().setTableQuerySystemPrompt(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getTableQuerySystemPrompt,
                TableSummaryConfig.DEFAULT_TABLE_QUERY_SYSTEM_PROMPT));
        getView().setTableQueryUserPrompt(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getTableQueryUserPrompt,
                TableSummaryConfig.DEFAULT_TABLE_QUERY_USER_PROMPT));
        getView().setSummaryMergePrompt(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getSummaryMergePrompt,
                TableSummaryConfig.DEFAULT_SUMMARY_MERGE_PROMPT));
    }

    public AskStroomAIConfig write() {
        final TableSummaryConfig tableSummaryConfig = writeTableSummaryConfig();
        return AskStroomAIConfig
                .builder()
                .tableSummaryConfig(tableSummaryConfig)
                .modelRef(docSelectionBoxPresenter.getSelectedEntityReference())
                .build();
    }

    public TableSummaryConfig writeTableSummaryConfig() {
        return new TableSummaryConfig(
                getView().getMaximumBatchSize(),
                getView().getMaximumTableInputRows(),
                TableSummaryConfig.DEFAULT_MAX_PARALLEL_BATCHES,
                getView().getTableQuerySystemPrompt(),
                getView().getTableQueryUserPrompt(),
                getView().getSummaryMergePrompt(),
                TableSummaryConfig.DEFAULT_MULTI_SUMMARY_MERGE_PROMPT);
    }

    public interface AskStroomAiConfigView extends View, Focus, HasUiHandlers<AskStroomAiConfigUiHandlers> {

        void allowSetDefault(boolean allow);

        void setMaximumBatchSize(int maximumBatchSize);

        int getMaximumBatchSize();

        void setMaximumTableInputRows(int maximumTableInputRows);

        int getMaximumTableInputRows();

        void setTableQuerySystemPrompt(String prompt);

        String getTableQuerySystemPrompt();

        void setTableQueryUserPrompt(String prompt);

        String getTableQueryUserPrompt();

        void setSummaryMergePrompt(String prompt);

        String getSummaryMergePrompt();

        void setDockBehaviour(DockBehaviour dockBehaviour);

        DockBehaviour getDockBehaviour();

        void setModelRefSelection(View view);
    }

}
