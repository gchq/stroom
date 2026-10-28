/*
 * Copyright 2020 Crown Copyright
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

package stroom.receive.rules.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.component.client.Layer;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// Shows the data retention rules and their impact, and edits the rules.
public class DataRetentionController extends ContentTabController
        implements CloseContentEvent.Handler {

    public static final String TAB_TYPE = "DataRetentionRules";
    private static final TabData IMPACT_SUMMARY_TAB = new TabDataImpl("Impact Summary");

    private final DataRetention view;

    private static final TabData RULES_TAB = new TabDataImpl("Rules");
    private static final String TAB_LABEL = "Data Retention";

    private final DataRetentionPolicyController retentionPolicy;
    private final ControllerLayer retentionPolicyLayer;
    private final Provider<DataRetentionImpactController> dataRetentionImpactProvider;

    private final boolean readOnly = true;
    private Layer currentContent;
    private DataRetentionImpactController dataRetentionImpact;
    private ControllerLayer dataRetentionImpactLayer;
    private TabData selectedTab;
    private final List<TabData> tabs = new ArrayList<>();
    private boolean dirty;
    private String lastLabel;
    private Integer lastPolicyHash = null;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public DataRetentionController(final EventBus eventBus,
                                   final DataRetention view,
                                   final DataRetentionPolicyController retentionPolicy,
                                   final Provider<DataRetentionImpactController> dataRetentionImpactProvider) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(retentionPolicy);
        this.retentionPolicy = retentionPolicy;
        this.retentionPolicyLayer = new ControllerLayer(retentionPolicy);
        retentionPolicy.setOnDirtyChange(this::setDirty);
        this.dataRetentionImpactProvider = dataRetentionImpactProvider;

//        settingsPresenter.addDirtyHandler(event -> {
//            if (event.isDirty()) {
//                setDirty(true);
//            }
//        });

        addTab(RULES_TAB);
        addTab(IMPACT_SUMMARY_TAB);
        selectTab(RULES_TAB);

        own(view.getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        own(view.getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
    }

    private void getContent(final TabData tab, final Consumer<Layer> callback) {
        if (RULES_TAB.equals(tab)) {
            callback.accept(retentionPolicyLayer);
        } else if (IMPACT_SUMMARY_TAB.equals(tab)) {
            final DataRetentionImpactController impact = getOrCreateImpact();

            // Get the current state of the rules saved or dirty
            final DataRetentionRules currentRules = retentionPolicy.getPolicy();
            final int currentPolicyHash = currentRules.hashCode();
            if (lastPolicyHash == null || currentPolicyHash != lastPolicyHash) {
                lastPolicyHash = currentPolicyHash;
                // Rules have changed, so any current data is invalid
                impact.setDataRetentionRules(currentRules);
            }

            callback.accept(dataRetentionImpactLayer);
        } else {
            callback.accept(null);
        }
    }

    /// Adds a tab.
    ///
    /// @param tab The tab.
    public void addTab(final TabData tab) {
        view.getTabBar().addTab(tab);
        tabs.add(tab);
    }

    /// Shows one of the tabs.
    ///
    /// @param tab The tab.
    public void selectTab(final TabData tab) {
        final TaskMonitor taskMonitor = createTaskMonitor();
        final Task task = new SimpleTask("Selecting tab");
        taskMonitor.onStart(task);
        Scheduler.get().scheduleDeferred(() -> {
            if (tab != null) {
                getContent(tab, content -> {
                    if (content != null) {
                        currentContent = content;

                        // Set the content.
                        view.getLayerContainer().show(currentContent);

                        // Update the selected tab.
                        view.getTabBar().selectTab(tab);
                        selectedTab = tab;

                    }
                });
            }
            taskMonitor.onEnd(task);
        });
    }

    /// @return The tab shown, or null before one is.
    public TabData getSelectedTab() {
        return selectedTab;
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.HISTORY;
    }

    @Override
    public String getLabel() {
        if (isDirty()) {
            return "* " + TAB_LABEL;
        }
        return TAB_LABEL;
    }

    private void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;

            if (lastLabel == null || !lastLabel.equals(getLabel())) {
                lastLabel = getLabel();
                RefreshContentTabEvent.fire(this, this);
            }
        }
    }

    private boolean isDirty() {
//        return retentionPolicyPresenter != null && retentionPolicyPresenter.isDirty();
        return dirty;
    }

//    @Override
//    public void refresh() {
//        if (selectedTab != null) {
//            if (currentContent != null && currentContent instanceof Refreshable) {
//                ((Refreshable) currentContent).refresh();
//            }
//        }
//    }

    private DataRetentionImpactController getOrCreateImpact() {
        if (dataRetentionImpact == null) {
            dataRetentionImpact = make(dataRetentionImpactProvider);
            dataRetentionImpactLayer = new ControllerLayer(dataRetentionImpact);
        }
        return dataRetentionImpact;
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (dirty && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        "There are unsaved changes. Are you sure you want to close this tab?",
                        result -> {
                            // Closing the tab disposes this
                            event.getCallback().closeTab(result);
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
        }
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
