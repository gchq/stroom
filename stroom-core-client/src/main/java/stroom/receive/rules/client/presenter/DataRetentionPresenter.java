/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.receive.rules.client.presenter;

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.presenter.ContentTabPresenter;
import stroom.core.client.ContentManager.CloseCallback;
import stroom.core.client.ContentManager.CloseHandler;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.entity.client.presenter.ContentCallback;
import stroom.security.client.api.ClientSecurityContext;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPresets;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.PresenterWidget;

import javax.inject.Provider;
import java.util.ArrayList;
import java.util.List;

public class DataRetentionPresenter extends ContentTabPresenter<DataRetentionPresenter.DataRetentionView>
        implements HasDirtyHandlers, CloseHandler {

    private static final TabData RULES_TAB = new TabDataImpl("Rules");
    private static final TabData IMPACT_SUMMARY_TAB = new TabDataImpl("Impact Summary");
    private static final String TAB_LABEL = "Data Retention";

    private final DataRetentionPolicyPresenter retentionPolicyPresenter;
    private final Provider<DataRetentionImpactPresenter> dataRetentionImpactPresenterProvider;

    private EditorPresenter codePresenter;
    private boolean readOnly = true;
    private PresenterWidget<?> currentContent;
    private DataRetentionImpactPresenter dataRetentionImpactPresenter;
    private TabData selectedTab;
    private final List<TabData> tabs = new ArrayList<>();
    private boolean dirty;
    private String lastLabel;

    @Inject
    public DataRetentionPresenter(final EventBus eventBus,
                                  final DataRetentionView view,
                                  final DataRetentionPolicyPresenter retentionPolicyPresenter,
                                  final Provider<DataRetentionImpactPresenter> dataRetentionImpactPresenterProvider,
                                  final ClientSecurityContext securityContext) {
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
    }


    protected void getContent(final TabData tab, final ContentCallback callback) {
        if (RULES_TAB.equals(tab)) {
            callback.onReady(retentionPolicyPresenter);
        } else if (IMPACT_SUMMARY_TAB.equals(tab)) {
            final DataRetentionImpactPresenter impactPresenter = getOrCreateImpactPresenter();
            impactPresenter.setDataRetentionRules(retentionPolicyPresenter.getPolicy());

            // TODO only call refresh if the rules have changed since last refresh.  Need to hash the rules.
            impactPresenter.refresh();
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
        TaskStartEvent.fire(DataRetentionPresenter.this);
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

            TaskEndEvent.fire(DataRetentionPresenter.this);
        });
    }

    public TabData getSelectedTab() {
        return selectedTab;
    }

    protected void afterSelectTab(final PresenterWidget<?> content) {
    }

    @Override
    public Icon getIcon() {
        return SvgPresets.HISTORY;
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
    public void onCloseRequest(final CloseCallback callback) {
        if (dirty) {
            ConfirmEvent.fire(this,
                            "' has unsaved changes. Are you sure you want to close this item?",
                    result -> {
                        callback.closeTab(result);
                        if (result) {
                            unbind();
                        }
                    });
        } else {
            callback.closeTab(true);
            unbind();
        }
    }

    public static interface DataRetentionView extends LinkTabsLayoutView {

    }

}
