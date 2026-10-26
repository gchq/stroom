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
import stroom.ai.client.component.AiConfigGeneralController;
import stroom.ai.client.component.AiConfigTableAnalysisController;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.alert.client.event.AlertEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.presenter.client.LayerContainer;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

public class AskStroomAiConfigPresenter
        extends MyPresenterWidget<AskStroomAiConfigView> {

    private static final TabData GENERAL = new TabDataImpl("General");
    private static final TabData TABLE_ANALYSIS = new TabDataImpl("Table Analysis");

    // ---------------------------------------------------------------------

    private final AskStroomAiClient askStroomAiClient;
    private final AiConfigGeneralController general;
    private final ControllerLayer generalLayer;
    private final AiConfigTableAnalysisController tableAnalysis;
    private final ControllerLayer tableAnalysisLayer;
    private final ClientSecurityContext clientSecurityContext;

    // ---------------------------------------------------------------------

    @Inject
    public AskStroomAiConfigPresenter(final EventBus eventBus,
                                      final AskStroomAiConfigView view,
                                      final AskStroomAiClient askStroomAiClient,
                                      final ClientSecurityContext clientSecurityContext,
                                      final AiConfigGeneralController general,
                                      final AiConfigTableAnalysisController tableAnalysis) {
        super(eventBus, view);
        own(general);
        own(tableAnalysis);
        this.askStroomAiClient = askStroomAiClient;
        this.clientSecurityContext = clientSecurityContext;
        this.general = general;
        generalLayer = new ControllerLayer(general);
        this.tableAnalysis = tableAnalysis;
        tableAnalysisLayer = new ControllerLayer(tableAnalysis);

        addTab(GENERAL);
        addTab(TABLE_ANALYSIS);
        view.getTabBar().selectTab(GENERAL);
        switchTab(GENERAL);

        own(getView().getTabBar().addSelectionHandler(e ->
                switchTab(e.getSelectedItem())));
    }

    // ---------------------------------------------------------------------

    private void addTab(final TabData tabData) {
        getView().getTabBar().addTab(tabData);
    }

    private void switchTab(final TabData tabData) {
        getView().getTabBar().selectTab(tabData);
        if (Objects.equals(GENERAL, tabData)) {
            getView().getLayerContainer().show(generalLayer);
        } else if (Objects.equals(TABLE_ANALYSIS, tabData)) {
            getView().getLayerContainer().show(tableAnalysisLayer);
        }
    }

    // ---------------------------------------------------------------------

    public void show(final AskStroomAiConfig currentConfig,
                     final Consumer<AskStroomAiConfig> configConsumer,
                     final DockBehaviour snapshotDockBehaviour,
                     final Consumer<DockBehaviour> dockBehaviourConsumer) {

        // Wire the dock behaviour callback through to the General tab.
        general.setOnDockBehaviourChange(dockBehaviourConsumer);

        final boolean isAdmin = clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION);
        getView().setRestoreFromDefaults(this::onRestoreFromDefaults);
        getView().allowSetDefaults(isAdmin);
        getView().setOnSetDefaults(isAdmin ? this::onSetDefaults
                : null);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(650, 840))
                .caption("Configure Ask Stroom AI")
                .onShow(e -> {
                    read(currentConfig, snapshotDockBehaviour);
                    general.focus();
                })
                .onHideRequest(e -> {
                    if (e.isOk()) {
                        final AskStroomAiConfig config = write();
                        dockBehaviourConsumer.accept(general.getDockBehaviour());
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

    private void onRestoreFromDefaults(final TaskMonitorFactory taskMonitorFactory) {
        askStroomAiClient.getDefaultConfig(config -> {
            read(config, new DockBehaviour(config.getDockType(), config.getDockLocation()));
        }, taskMonitorFactory);
    }

    private void onSetDefaults(final TaskMonitorFactory taskMonitorFactory) {
        final AskStroomAiConfig config = write();
        askStroomAiClient.setDefaultAskStroomAIConfig(config, success ->
                AlertEvent.fireInfo(AskStroomAiConfigPresenter.this, "Default config updated", null),
                taskMonitorFactory);
    }

    private void read(final AskStroomAiConfig config,
                      final DockBehaviour dockBehaviour) {
        general.read(config, dockBehaviour);
        final TableAnalysisConfig tableConfig = config != null
                ? config.getTableAnalysis()
                : null;
        tableAnalysis.read(
                tableConfig != null ? tableConfig : new TableAnalysisConfig(),
                config);
    }

    private AskStroomAiConfig write() {
        final AskStroomAiConfig.Builder builder = AskStroomAiConfig.builder();
        general.write(builder);
        builder.tableAnalysisConfig(tableAnalysis.write());
        builder.attachmentDownloadTimeoutMs(tableAnalysis.getAttachmentDownloadTimeoutMs());
        return builder.build();
    }

    // ---------------------------------------------------------------------


    public interface AskStroomAiConfigView extends View {

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setRestoreFromDefaults(Consumer<TaskMonitorFactory> handler);

        void allowSetDefaults(boolean allow);

        void setOnSetDefaults(Consumer<TaskMonitorFactory> handler);
    }
}
