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

package stroom.entity.client.presenter;

import stroom.data.table.client.Refreshable;
import stroom.document.client.event.OpenDocumentEvent;
import stroom.document.client.event.OpenDocumentEvent.CommonDocLinkTab;
import stroom.task.client.SimpleTask;
import stroom.task.client.Task;
import stroom.task.client.TaskMonitor;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.core.client.Scheduler;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.Layer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.PresenterWidget;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;

public abstract class LinkTabPanelPresenter extends MyPresenterWidget<LinkTabPanelView> implements Refreshable {

    private final Map<CommonDocLinkTab, TabData> commonTabsMap;

    private TabData selectedTab;
    private PresenterWidget<?> currentContent;

    public LinkTabPanelPresenter(final EventBus eventBus, final LinkTabPanelView view) {
        super(eventBus, view);

        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));

        commonTabsMap = new EnumMap<>(CommonDocLinkTab.class);
        addEntry(commonTabsMap, CommonDocLinkTab.PERMISSIONS, this::getPermissionsTab);
    }

    private void addEntry(final Map<CommonDocLinkTab, TabData> commonTabsMap,
                          final CommonDocLinkTab commonDocLinkTab,
                          final Supplier<TabData> tabDataSupplier) {
        final TabData tabData = tabDataSupplier.get();
        if (tabData != null) {
            commonTabsMap.put(commonDocLinkTab, tabData);
        }
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
    }

    protected abstract void getContent(TabData tab, ContentCallback callback);

    /**
     * @return The {@link TabData} instance for the Permissions tab.
     */
    protected abstract TabData getPermissionsTab();

    public void selectCommonTab(final OpenDocumentEvent.CommonDocLinkTab commonDocLinkTab) {
        if (commonDocLinkTab != null) {
            final TabData tabData = commonTabsMap.get(commonDocLinkTab);
            if (tabData != null) {
                selectTab(tabData);
            }
        }
    }

    public void selectTab(final TabData tab) {
        final TaskMonitor taskMonitor = createTaskMonitor();
        final Task task = new SimpleTask("Select tab");
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

    protected void afterSelectTab(final PresenterWidget<?> content) {
    }

    public TabData getSelectedTab() {
        return selectedTab;
    }

    @Override
    public void refresh() {
        if (selectedTab != null) {
            if (currentContent != null && currentContent instanceof Refreshable) {
                ((Refreshable) currentContent).refresh();
            }
        }
    }
}
