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
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
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

    // True once every user's settings have been reset, so that closing the dialog saves nothing.
    private boolean resetAllDone;
    // Only administrators see and change the table analysis settings, which apply to everyone.
    private boolean isAdmin;
    // The table analysis settings as loaded, to tell whether the administrator changed them.
    private String loadedTableAnalysis;

    public void show(final AskStroomAiConfig currentConfig,
                     final Consumer<AskStroomAiConfig> configConsumer,
                     final DockBehaviour snapshotDockBehaviour,
                     final Consumer<DockBehaviour> dockBehaviourConsumer) {

        // Wire the dock behaviour callback through to the General sub-presenter.
        generalPresenter.setDockBehaviourChangeHandler(dockBehaviourConsumer);

        isAdmin = clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION);
        getView().getTabBar().setTabHidden(TABLE_ANALYSIS, !isAdmin);
        switchTab(GENERAL);
        getView().setRestoreFromDefaults(this::onRestoreFromDefaults);
        getView().allowSetDefaults(isAdmin);
        getView().setOnSetDefaults(isAdmin ? this::onSetDefaults
                : null);
        getView().setOnResetAllUsers(isAdmin ? this::onResetAllUsers
                : null);

        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(PopupSize.resizable(650, 840))
                .caption("Configure Ask Stroom AI")
                .onShow(e -> {
                    read(currentConfig, snapshotDockBehaviour);
                    generalPresenter.getView().focus();
                })
                .onHideRequest(e -> {
                    if (resetAllDone) {
                        // Everyone's settings were just reset, so save nothing back, not even the dock.
                        resetAllDone = false;
                        e.hide();
                        return;
                    }
                    if (e.isOk()) {
                        final AskStroomAiConfig config = write();
                        dockBehaviourConsumer.accept(generalPresenter.getDockBehaviour());
                        configConsumer.accept(config);
                        saveTableAnalysisIfChanged();
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
        final AskStroomAiConfig config = write().copy()
                .tableAnalysisConfig(tableAnalysisPresenter.write())
                .build();
        askStroomAiClient.setDefaultAskStroomAIConfig(config, success ->
                AlertEvent.fireInfo(AskStroomAiConfigPresenter.this, "Default config updated", null),
                taskMonitorFactory);
    }

    private void onResetAllUsers(final TaskMonitorFactory taskMonitorFactory) {
        ConfirmEvent.fire(this,
                "Reset every user's Ask Stroom AI settings (model, prompts, dock position and so on) to the "
                + "defaults? Their own choices are lost. Users with Stroom open get the defaults when they "
                + "next load it.",
                ok -> {
                    if (ok) {
                        askStroomAiClient.resetAllUsersConfig(count -> {
                            // Close without saving, as saving would store this user's settings again.
                            resetAllDone = true;
                            HidePopupRequestEvent.builder(AskStroomAiConfigPresenter.this).fire();
                            AlertEvent.fireInfo(AskStroomAiConfigPresenter.this,
                                    "Reset the Ask Stroom AI settings in " + count + " preference "
                                    + (count == 1
                                            ? "record"
                                            : "records")
                                    + ". Everyone now uses the defaults. Reload Stroom to see them yourself.",
                                    null);
                        }, taskMonitorFactory);
                    }
                });
    }

    private void read(final AskStroomAiConfig config,
                      final DockBehaviour dockBehaviour) {
        generalPresenter.read(config, dockBehaviour);
        if (isAdmin) {
            // The table analysis settings are always the ones everyone uses, not this user's own.
            askStroomAiClient.getDefaultConfig(defaults -> {
                final TableAnalysisConfig tableConfig = defaults.getTableAnalysis() != null
                        ? defaults.getTableAnalysis()
                        : new TableAnalysisConfig();
                tableAnalysisPresenter.read(tableConfig, config);
                loadedTableAnalysis = tableAnalysisPresenter.write().toString();
            }, this);
        }
    }

    private void saveTableAnalysisIfChanged() {
        if (!isAdmin || loadedTableAnalysis == null) {
            return;
        }
        final TableAnalysisConfig tableConfig = tableAnalysisPresenter.write();
        if (!tableConfig.toString().equals(loadedTableAnalysis)) {
            askStroomAiClient.setTableAnalysisConfig(tableConfig, done -> {
            }, this);
        }
    }

    private AskStroomAiConfig write() {
        final AskStroomAiConfig.Builder builder = AskStroomAiConfig.builder();
        generalPresenter.write(builder);
        // Table analysis settings apply to everyone, so are not kept in a user's own settings.
        builder.tableAnalysisConfig(null);
        builder.attachmentDownloadTimeoutMs(tableAnalysisPresenter.getAttachmentDownloadTimeoutMs());
        return builder.build();
    }

    // ---------------------------------------------------------------------


    public interface AskStroomAiConfigView extends View {

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setRestoreFromDefaults(Consumer<TaskMonitorFactory> handler);

        void allowSetDefaults(boolean allow);

        /// @param handler Resets every user's settings to the defaults, or null if this user cannot.
        void setOnResetAllUsers(Consumer<TaskMonitorFactory> handler);

        void setOnSetDefaults(Consumer<TaskMonitorFactory> handler);
    }
}
