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

package stroom.content.client.presenter;

import stroom.content.client.event.CloseContentTabEvent;
import stroom.content.client.event.CloseContentTabEvent.CloseContentTabHandler;
import stroom.content.client.event.ContentTabSelectionChangeEvent;
import stroom.content.client.event.MoveContentTabEvent;
import stroom.content.client.event.MoveContentTabEvent.MoveContentTabHandler;
import stroom.content.client.event.OpenContentTabEvent;
import stroom.content.client.event.OpenContentTabEvent.OpenContentTabHandler;
import stroom.content.client.event.RefreshContentTabEvent;
import stroom.content.client.event.RefreshContentTabEvent.RefreshContentTabHandler;
import stroom.content.client.event.RefreshCurrentContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent;
import stroom.content.client.event.SelectContentTabEvent.SelectContentTabHandler;
import stroom.data.table.client.Refreshable;
import stroom.explorer.client.presenter.RecentItems;
import stroom.main.client.presenter.MainPresenter;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.tab.client.presenter.CurveTabLayoutPresenter;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.AbstractTab;
import stroom.widget.tab.client.view.CurveTabLayoutUiHandlers;

import com.google.gwt.user.client.History;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.annotations.ProxyCodeSplit;
import com.gwtplatform.mvp.client.annotations.ProxyEvent;
import com.gwtplatform.mvp.client.proxy.Proxy;
import com.gwtplatform.mvp.client.proxy.RevealContentEvent;

import java.util.ArrayList;
import java.util.List;

public class ContentTabPanePresenter
        extends CurveTabLayoutPresenter<ContentTabPanePresenter.ContentTabPaneProxy>
        implements
        OpenContentTabHandler,
        CloseContentTabHandler,
        SelectContentTabHandler,
        RefreshContentTabHandler,
        CurveTabLayoutUiHandlers,
        MoveContentTabHandler {

    private final List<TabData> historyList = new ArrayList<>();
    private final RecentItems recentItems;
    private int currentHistoryId;
    private int currentIndex;
    private boolean ignoreHistory;

    @Inject
    public ContentTabPanePresenter(final EventBus eventBus,
                                   final CurveTabLayoutView view,
                                   final ContentTabPaneProxy proxy,
                                   final RecentItems recentItems) {
        super(eventBus, view, proxy);
        this.recentItems = recentItems;
        view.setUiHandlers(this);

        registerHandler(eventBus.addHandler(RefreshCurrentContentTabEvent.getType(),
                event -> {
                    final TabData selectedTab = getSelectedTab();
                    if (selectedTab instanceof Refreshable) {
                        //noinspection PatternVariableCanBeUsed // Not in GWT
                        final Refreshable refreshable = (Refreshable) selectedTab;
                        refreshable.refresh();
                    }
                }));

        getView().setRightIndent(32);

        // Handle the history
        registerHandler(History.addValueChangeHandler(event -> {
            if (!ignoreHistory) {
                ignoreHistory = true;
                try {
                    // Try and stop the user leaving Stroom.
                    if ("".equals(event.getValue())) {
                        History.forward();
                    } else {
                        final String key = event.getValue();

                        final int id = Integer.parseInt(key);
                        final int diff = id - currentHistoryId;
                        currentHistoryId = id;

                        currentIndex = currentIndex + diff;

                        if (historyList.isEmpty()) {
                            currentIndex = 0;
                        } else {
                            if (currentIndex >= historyList.size()) {
                                currentIndex = historyList.size() - 1;
                            }
                            if (currentIndex < 0) {
                                currentIndex = 0;
                            }

                            final TabData tabData = historyList.get(currentIndex);
                            selectTab(tabData);
                        }
                    }

                } catch (final RuntimeException e) {
                    // Ignore.
                }
                ignoreHistory = false;
            }
        }));
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(getView().addShowTabMenuHandler(event -> getEventBus().fireEvent(event)));
    }

    @Override
    public void maximise() {
        MaximiseEvent.fire(this, null);
    }

    @Override
    protected void revealInParent() {
        // Make sure there are no tabs displayed.
        clear();

        // Now display this presenter.
        RevealContentEvent.fire(this, MainPresenter.CONTENT, this);
    }

    @ProxyEvent
    @Override
    public void onOpen(final OpenContentTabEvent event) {
        // Make sure this tab pane is revealed before we try and reveal child
        // tabs.
        forceReveal();
        add(event.getTabData(), event.getLayer());

        event.runCallbackOnOpen();

        if (event.getLayer() instanceof HasTaskMonitorFactory) {
            final AbstractTab tab = getView().getTabBar().getTab(event.getTabData());
            ((HasTaskMonitorFactory) event.getLayer())
                    .setTaskMonitorFactory(new TabTaskMonitorFactory(tab));
        }
    }

    @ProxyEvent
    @Override
    public void onClose(final CloseContentTabEvent event) {
        remove(event.getTabData(), event.resizeTabBar());

        for (int i = historyList.size() - 1; i >= 0; i--) {
            final TabData tabData = historyList.get(i);
            if (tabData == event.getTabData()) {
                historyList.remove(i);
                if (currentIndex >= i) {
                    currentIndex--;
                }
            }
        }

        if (event.getTabData() instanceof HasTaskMonitorFactory) {
            ((HasTaskMonitorFactory) event.getTabData())
                    .setTaskMonitorFactory(new DefaultTaskMonitorFactory(this));
        }
    }

    @ProxyEvent
    @Override
    public void onSelect(final SelectContentTabEvent event) {
        selectTab(event.getTabData());
    }

    @ProxyEvent
    @Override
    public void onRefresh(final RefreshContentTabEvent event) {
        refresh(event.getTabData());
    }

    @ProxyEvent
    @Override
    public void onMove(final MoveContentTabEvent event) {
        moveTab(event.getTabData(), event.getTabPos());
    }

    @Override
    public void selectTab(final TabData tabData) {
        if (!ignoreHistory) {
            ignoreHistory = true;
            try {
                for (int i = historyList.size() - 1; i > currentIndex; i--) {
                    historyList.remove(i);
                }

                historyList.add(tabData);
                currentIndex = historyList.size() - 1;
                recentItems.add(tabData);

                currentHistoryId++;
                final String key = String.valueOf(currentHistoryId);
                History.newItem(key);

            } catch (final RuntimeException e) {
                // Ignore.
            }
            ignoreHistory = false;
        }

        super.selectTab(tabData);
    }

    @Override
    protected void fireSelectedTabChange(final TabData tabData) {
        ContentTabSelectionChangeEvent.fire(this, tabData);
    }

    @ProxyCodeSplit
    public interface ContentTabPaneProxy extends Proxy<ContentTabPanePresenter> {

    }


    // --------------------------------------------------------------------------------


    private static class TabTaskMonitorFactory implements TaskMonitorFactory {

        private final AbstractTab tab;

        public TabTaskMonitorFactory(final AbstractTab tab) {
            this.tab = tab;
        }

        @Override
        public TaskMonitor createTaskMonitor() {
            return tab.createTaskMonitor();
        }
    }
}
