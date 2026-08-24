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
import stroom.ai.shared.TableAnalysisConfig;
import stroom.alert.client.event.AlertEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;

public class AskStroomAiConfigPresenter
        extends MyPresenterWidget<AskStroomAiConfigView> {

    private static final TabData GENERAL = new TabDataImpl("General");
    private static final TabData TABLE_ANALYSIS = new TabDataImpl("Table Analysis");

    // ---------------------------------------------------------------------

    private final AskStroomAiClient askStroomAiClient;
    private final AiConfigGeneralPresenter generalPresenter;
    private final AiConfigTableAnalysisPresenter tableAnalysisPresenter;
    private final ClientSecurityContext clientSecurityContext;

    // ---------------------------------------------------------------------

    @Inject
    public AskStroomAiConfigPresenter(final EventBus eventBus,
                                      final AskStroomAiConfigView view,
                                      final AskStroomAiClient askStroomAiClient,
                                      final ClientSecurityContext clientSecurityContext,
                                      final AiConfigGeneralPresenter generalPresenter,
                                      final AiConfigTableAnalysisPresenter tableAnalysisPresenter) {
        super(eventBus, view);
        this.askStroomAiClient = askStroomAiClient;
        this.clientSecurityContext = clientSecurityContext;
        this.generalPresenter = generalPresenter;
        this.tableAnalysisPresenter = tableAnalysisPresenter;

        addTab(GENERAL, generalPresenter);
        addTab(TABLE_ANALYSIS, tableAnalysisPresenter);
        view.getTabBar().selectTab(GENERAL);
        switchTab(GENERAL);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().getTabBar().addSelectionHandler(e ->
                switchTab(e.getSelectedItem())));
    }

    // ---------------------------------------------------------------------

    private void addTab(final TabData tabData, final MyPresenterWidget<?> presenterWidget) {
        getView().getTabBar().addTab(tabData);
    }

    private void switchTab(final TabData tabData) {
        getView().getTabBar().selectTab(tabData);
        if (Objects.equals(GENERAL, tabData)) {
            getView().getLayerContainer().show(generalPresenter);
        } else if (Objects.equals(TABLE_ANALYSIS, tabData)) {
            getView().getLayerContainer().show(tableAnalysisPresenter);
        }
    }

    // ---------------------------------------------------------------------

    public void show(final AskStroomAIConfig currentConfig,
                     final Consumer<AskStroomAIConfig> configConsumer,
                     final DockBehaviour snapshotDockBehaviour,
                     final Consumer<DockBehaviour> dockBehaviourConsumer) {

        // Wire the dock behaviour callback through to the General sub-presenter.
        generalPresenter.setDockBehaviourChangeHandler(dockBehaviourConsumer);

        final boolean isAdmin = clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION);
        getView().allowSetDefault(isAdmin);
        getView().setOnSetDefault(isAdmin ? this::onSetDefault : null);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(650, 840))
                .caption("Configure Ask Stroom AI")
                .onShow(e -> {
                    read(currentConfig, snapshotDockBehaviour);
                    generalPresenter.getView().focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AskStroomAIConfig config = write();
                        dockBehaviourConsumer.accept(generalPresenter.getDockBehaviour());
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

    // ---------------------------------------------------------------------

    private void onSetDefault(final TaskMonitorFactory taskMonitorFactory) {
        final AskStroomAIConfig config = write();
        askStroomAiClient.setDefaultAskStroomAIConfig(config, success ->
                AlertEvent.fireInfo(AskStroomAiConfigPresenter.this, "Default config updated", null),
                taskMonitorFactory);
    }

    private void read(final AskStroomAIConfig config,
                      final DockBehaviour dockBehaviour) {
        generalPresenter.read(config, dockBehaviour);
        final TableAnalysisConfig tableConfig = config != null
                ? config.getTableAnalysis()
                : null;
        tableAnalysisPresenter.read(
                tableConfig != null ? tableConfig : new TableAnalysisConfig(),
                config);
    }

    private AskStroomAIConfig write() {
        final AskStroomAIConfig.Builder builder = AskStroomAIConfig.builder();
        generalPresenter.write(builder);
        builder.tableAnalysisConfig(tableAnalysisPresenter.write());
        builder.attachmentDownloadTimeoutMs(tableAnalysisPresenter.getAttachmentDownloadTimeoutMs());
        return builder.build();
    }

    // ---------------------------------------------------------------------


    public interface AskStroomAiConfigView extends View {

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void allowSetDefault(boolean allow);

        void setOnSetDefault(Consumer<TaskMonitorFactory> handler);
    }
}
