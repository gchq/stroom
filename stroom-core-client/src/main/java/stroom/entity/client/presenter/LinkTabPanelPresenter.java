/*
 * Copyright 2016 Crown Copyright
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

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.PresenterWidget;
import stroom.data.table.client.Refreshable;
import stroom.task.client.TaskEndEvent;
import stroom.task.client.TaskStartEvent;
import stroom.widget.tab.client.presenter.Layer;
import stroom.widget.tab.client.presenter.TabData;

import java.util.ArrayList;
import java.util.List;

public abstract class LinkTabPanelPresenter extends MyPresenterWidget<LinkTabPanelView> implements Refreshable {
    private final List<TabData> tabs = new ArrayList<TabData>();
    private TabData selectedTab;
    private PresenterWidget<?> currentContent;

    public LinkTabPanelPresenter(final EventBus eventBus, final LinkTabPanelView view) {
        super(eventBus, view);

        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }

    public void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
        tabs.add(tab);
    }

    protected abstract void getContent(TabData tab, ContentCallback callback);

    public void addWidgetLeft(final Widget widget) {
        getView().addWidgetLeft(widget);
    }

    public void addWidgetRight(final Widget widget) {
        getView().addWidgetRight(widget);
    }

    public void selectTab(final TabData tab) {
        TaskStartEvent.fire(LinkTabPanelPresenter.this);
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

            TaskEndEvent.fire(LinkTabPanelPresenter.this);
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
