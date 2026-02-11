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
import stroom.ai.shared.AskStroomAIConfig;
import stroom.ai.shared.ChatMemoryConfig;
import stroom.ai.shared.TableSummaryConfig;
import stroom.alert.client.event.AlertEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.time.SimpleDuration;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class AskStroomAiConfigPresenter
        extends MyPresenterWidget<AskStroomAiConfigView>
        implements AskStroomAiConfigUiHandlers {

    private final AskStroomAiClient askStroomAiClient;

    @Inject
    public AskStroomAiConfigPresenter(final EventBus eventBus,
                                      final AskStroomAiConfigView view,
                                      final AskStroomAiClient askStroomAiClient,
                                      final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.askStroomAiClient = askStroomAiClient;
        view.setUiHandlers(this);

        // Only allow administrators to set the default model.
        view.allowSetDefault(clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION));
    }

    public void show() {
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(700, 500))
                .caption("Configure Ask Stroom AI")
                .onShow(e -> {
                    // Read the local config or default.
                    askStroomAiClient.getConfig(config -> {
                        readChatMemoryConfig(NullSafe.getOrElse(config,
                                AskStroomAIConfig::getChatMemory, new ChatMemoryConfig()));
                        readTableSummaryConfig(NullSafe.getOrElse(config,
                                AskStroomAIConfig::getTableSummary, new TableSummaryConfig()));
                    }, this);
                    getView().focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        // Update the local config.
                        askStroomAiClient.getConfig(config -> {
                            final ChatMemoryConfig chatMemoryConfig = writeChatMemoryConfig();
                            final TableSummaryConfig tableSummaryConfig = writeTableSummaryConfig();
                            final AskStroomAIConfig askStroomAIConfig = config
                                    .copy()
                                    .tableSummaryConfig(tableSummaryConfig)
                                    .chatMemoryConfig(chatMemoryConfig)
                                    .build();
                            askStroomAiClient.setConfig(askStroomAIConfig);
                        }, this);
                    }
                    e.hide();
                })
                .fire();
    }

    @Override
    public void onSetDefault(final TaskMonitorFactory taskMonitorFactory) {
        askStroomAiClient.setDefaultChatMemoryConfigConfig(writeChatMemoryConfig(), success -> {
            askStroomAiClient.setDefaultTableSummaryConfig(writeTableSummaryConfig(), success2 -> {
                AlertEvent.fireInfo(AskStroomAiConfigPresenter.this, "Default table config updated", null);
            }, taskMonitorFactory);
        }, taskMonitorFactory);
    }

    public void readTableSummaryConfig(final TableSummaryConfig config) {
        getView().setMaximumBatchSize(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getMaximumBatchSize,
                TableSummaryConfig.DEFAULT_MAXIMUM_BATCH_SIZE));
        getView().setMaximumTableInputRows(NullSafe.getOrElse(
                config,
                TableSummaryConfig::getMaximumTableInputRows,
                TableSummaryConfig.DEFAULT_MAXIMUM_TABLE_INPUT_ROWS));
    }

    public TableSummaryConfig writeTableSummaryConfig() {
        return new TableSummaryConfig(
                getView().getMaximumBatchSize(),
                getView().getMaximumTableInputRows());
    }

    public void readChatMemoryConfig(final ChatMemoryConfig config) {
        getView().setMemoryTokenLimit(NullSafe.getOrElse(
                config,
                ChatMemoryConfig::getTokenLimit,
                ChatMemoryConfig.DEFAULT_CHAT_MEMORY_TOKEN_LIMIT));
        getView().setMemoryTimeToLive(NullSafe.getOrElse(
                config,
                ChatMemoryConfig::getTimeToLive,
                ChatMemoryConfig.DEFAULT_CHAT_MEMORY_TTL));
    }

    public ChatMemoryConfig writeChatMemoryConfig() {
        return new ChatMemoryConfig(
                getView().getMemoryTokenLimit(),
                getView().getMemoryTimeToLive());
    }

    public interface AskStroomAiConfigView extends View, Focus, HasUiHandlers<AskStroomAiConfigUiHandlers> {

        void allowSetDefault(boolean allow);

        void setMaximumBatchSize(int maximumBatchSize);

        int getMaximumBatchSize();

        void setMaximumTableInputRows(int maximumTableInputRows);

        int getMaximumTableInputRows();

        void setMemoryTokenLimit(int memoryTokenLimit);

        int getMemoryTokenLimit();

        SimpleDuration getMemoryTimeToLive();

        void setMemoryTimeToLive(SimpleDuration memoryTimeToLive);
    }

}
