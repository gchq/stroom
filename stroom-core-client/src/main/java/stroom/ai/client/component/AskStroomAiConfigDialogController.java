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

package stroom.ai.client.component;

import stroom.ai.client.AskStroomAiClient;
import stroom.ai.client.component.AskStroomAiController.DockBehaviour;
import stroom.ai.shared.AskStroomAiConfig;
import stroom.ai.shared.TableAnalysisConfig;
import stroom.alert.client.event.AlertEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.popup.client.PopupSize;
import stroom.widget.popup.client.PopupType;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// Ask Stroom AI's settings ([AskStroomAiConfigDialog]) in General and Table Analysis tabs, made
/// for one use. A user who may change the properties can make the settings everyone's defaults.
public class AskStroomAiConfigDialogController extends Controller {

    private static final TabData GENERAL = new TabDataImpl("General");
    private static final TabData TABLE_ANALYSIS = new TabDataImpl("Table Analysis");

    private final AskStroomAiConfigDialog view;
    private final AskStroomAiClient askStroomAiClient;
    private final AiConfigGeneralController general;
    private final ControllerLayer generalLayer;
    private final AiConfigTableAnalysisController tableAnalysis;
    private final ControllerLayer tableAnalysisLayer;

    /// Made by GIN, with its content and its tabs.
    ///
    /// @param eventBus              The application's event bus.
    /// @param view                  The content.
    /// @param askStroomAiClient     Fetches and saves the default settings.
    /// @param clientSecurityContext Says whether the user may change the defaults.
    /// @param general               The General tab.
    /// @param tableAnalysis         The Table Analysis tab.
    @Inject
    public AskStroomAiConfigDialogController(final EventBus eventBus,
                                             final AskStroomAiConfigDialog view,
                                             final AskStroomAiClient askStroomAiClient,
                                             final ClientSecurityContext clientSecurityContext,
                                             final AiConfigGeneralController general,
                                             final AiConfigTableAnalysisController tableAnalysis) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.askStroomAiClient = askStroomAiClient;
        this.general = own(general);
        generalLayer = new ControllerLayer(general);
        this.tableAnalysis = own(tableAnalysis);
        tableAnalysisLayer = new ControllerLayer(tableAnalysis);

        view.addTab(GENERAL);
        view.addTab(TABLE_ANALYSIS);
        switchTab(GENERAL);
        view.setOnTabSelected(this::switchTab);

        view.setOnRestoreFromDefaults(this::onRestoreFromDefaults);
        if (clientSecurityContext.hasAppPermission(AppPermission.MANAGE_PROPERTIES_PERMISSION)) {
            view.setSetDefaultsVisible(true);
            view.setOnSetDefaults(this::onSetDefaults);
        }
    }

    /// Shows the settings. The opener owns the dialog until it is hidden.
    ///
    /// @param opener                What shows it.
    /// @param currentConfig         The settings to show.
    /// @param configConsumer        Given the settings when OK is pressed.
    /// @param snapshotDockBehaviour Where the AI pane is docked now, gone back to on Cancel.
    /// @param dockBehaviourConsumer Given where to dock the AI pane, as it is changed.
    public void show(final HasControllerNode opener,
                     final AskStroomAiConfig currentConfig,
                     final Consumer<AskStroomAiConfig> configConsumer,
                     final DockBehaviour snapshotDockBehaviour,
                     final Consumer<DockBehaviour> dockBehaviourConsumer) {
        // Wire the dock behaviour callback through to the General tab.
        general.setOnDockBehaviourChange(dockBehaviourConsumer);

        ControllerDialog.showOnce(opener, this)
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
                    } else if (dockBehaviourConsumer != null && snapshotDockBehaviour != null) {
                        dockBehaviourConsumer.accept(snapshotDockBehaviour);
                    }
                    e.hide();
                })
                .fire();
    }

    private void switchTab(final TabData tabData) {
        if (Objects.equals(GENERAL, tabData)) {
            view.showTab(tabData, generalLayer);
        } else if (Objects.equals(TABLE_ANALYSIS, tabData)) {
            view.showTab(tabData, tableAnalysisLayer);
        }
    }

    private void onRestoreFromDefaults() {
        askStroomAiClient.getDefaultConfig(guard(config ->
                        read(config, new DockBehaviour(config.getDockType(), config.getDockLocation()))),
                view.getRestoreFromDefaultsTaskMonitor());
    }

    private void onSetDefaults() {
        final AskStroomAiConfig config = write();
        askStroomAiClient.setDefaultAskStroomAIConfig(config,
                guard(success -> AlertEvent.fireInfo(this, "Default config updated", null)),
                view.getSetDefaultsTaskMonitor());
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
}
