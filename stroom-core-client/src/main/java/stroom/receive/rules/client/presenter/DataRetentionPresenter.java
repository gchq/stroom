/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.event.CloseContentEvent;
import stroom.core.client.event.CloseContentEvent.DirtyMode;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.entity.client.presenter.ContentCallback;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Provider;

public class DataRetentionPresenter
        extends ContentTabPresenter<DataRetentionPresenter.DataRetentionView>
        implements HasDirtyHandlers, CloseContentEvent.Handler {

    public static final String TAB_TYPE = "DataRetentionRules";
    private static final TabData RULES_TAB = new TabDataImpl("Rules");
    private static final TabData IMPACT_SUMMARY_TAB = new TabDataImpl("Impact Summary");
    private static final String TAB_LABEL = "Data Retention";

    private final DataRetentionPolicyPresenter retentionPolicyPresenter;
    private final Provider<DataRetentionImpactPresenter> dataRetentionImpactPresenterProvider;

    private final boolean readOnly = true;
    private PresenterWidget<?> currentContent;
    private DataRetentionImpactPresenter dataRetentionImpactPresenter;
    private TabData selectedTab;
    private final List<TabData> tabs = new ArrayList<>();
    private boolean dirty;
    private String lastLabel;
    private Integer lastPolicyHash = null;

    @Inject
    public DataRetentionPresenter(final EventBus eventBus,
                                  final DataRetentionView view,
                                  final DataRetentionPolicyPresenter retentionPolicyPresenter,
                                  final Provider<DataRetentionImpactPresenter> dataRetentionImpactPresenterProvider) {
        super(eventBus, view);
        this.retentionPolicyPresenter = retentionPolicyPresenter;
        this.retentionPolicyPresenter.setParentPresenter(this);
        this.dataRetentionImpactPresenterProvider = dataRetentionImpactPresenterProvider;

//        settingsPresenter.addDirtyHandler(event -> {
//            if (event.isDirty()) {
//                setDirty(true);
//            }
//        });

        addTab(RULES_TAB);
        addTab(IMPACT_SUMMARY_TAB);
        selectTab(RULES_TAB);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        registerHandler(getView().getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
    }

    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (RULES_TAB.equals(tab)) {
            callback.onReady(retentionPolicyPresenter);
        } else if (IMPACT_SUMMARY_TAB.equals(tab)) {
            final DataRetentionImpactPresenter impactPresenter = getOrCreateImpactPresenter();

            // Get the current state of the rules saved or dirty
            final DataRetentionRules currentRules = retentionPolicyPresenter.getPolicy();
            final int currentPolicyHash = currentRules.hashCode();
            if (lastPolicyHash == null || currentPolicyHash != lastPolicyHash) {
                lastPolicyHash = currentPolicyHash;
                // Rules have changed, so any current data is invalid
                impactPresenter.setDataRetentionRules(currentRules);
            }

            callback.onReady(impactPresenter);
        } else {
            callback.onReady(null);
        }
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
        tabs.add(tab);
    }

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
                        getView().getLayerContainer().show((Layer) currentContent);

                        // Update the selected tab.
                        getView().getTabBar().selectTab(tab);
                        selectedTab = tab;

                        afterSelectTab(content);
                    }
                });
            }
            taskMonitor.onEnd(task);
        });
    }

    public TabData getSelectedTab() {
        return selectedTab;
    }

    protected void afterSelectTab(final PresenterWidget<?> content) {
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

    void setDirty(final boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            DirtyEvent.fire(this, dirty);

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

    private DataRetentionImpactPresenter getOrCreateImpactPresenter() {
        if (dataRetentionImpactPresenter == null) {
            dataRetentionImpactPresenter = dataRetentionImpactPresenterProvider.get();
        }
        return dataRetentionImpactPresenter;
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    @Override
    public void onCloseRequest(final CloseContentEvent event) {
        final DirtyMode dirtyMode = event.getDirtyMode();
        if (dirty && DirtyMode.FORCE != dirtyMode) {
            if (DirtyMode.CONFIRM_DIRTY == dirtyMode) {
                ConfirmEvent.fire(this,
                        "There are unsaved changes. Are you sure you want to close this tab?",
                        result -> {
                            event.getCallback().closeTab(result);
                            if (result) {
                                unbind();
                            }
                        });
            } else if (DirtyMode.SKIP_DIRTY == dirtyMode) {
                // Do nothing
            } else {
                throw new RuntimeException("Unexpected DirtyMode: " + dirtyMode);
            }
        } else {
            event.getCallback().closeTab(true);
            unbind();
        }
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    public interface DataRetentionView extends LinkTabsLayoutView {

    }
}
