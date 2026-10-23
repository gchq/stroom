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
import stroom.explorer.client.event.GetCurrentTabsEvent;
import stroom.explorer.client.presenter.RecentItems;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.HasComponentNode;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.tab.client.presenter.CurveTabLayoutPresenter;
import stroom.widget.tab.client.presenter.CurveTabLayoutView;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.AbstractTab;
import stroom.widget.tab.client.view.CurveTabLayoutUiHandlers;
import stroom.widget.util.client.ContentFocus;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.History;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

public class ContentTabPanePresenter
        extends CurveTabLayoutPresenter
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
                                   final RecentItems recentItems) {
        super(eventBus, view);
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

        registerHandler(eventBus.addHandler(GetCurrentTabsEvent.getType(),
                event -> {
                    event.getCallback().accept(getTabs());
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
    public void onOpen(final OpenContentTabEvent event) {
        add(event.getTabData(), event.getLayer());

        event.runCallbackOnOpen();

        // Take the user to the screen they opened, rather than leave the focus where they chose it
        // (e.g. the main menu's button, which the closing menu gives it back to)
        final Object content = event.getLayer();
        Scheduler.get().scheduleDeferred(() -> ContentFocus.focus(content));

        final AbstractTab tab = getView().getTabBar().getTab(event.getTabData());
        final TabTaskMonitorFactory tabTaskMonitorFactory = new TabTaskMonitorFactory(tab);
        // The content shows its tasks on the tab, and so does everything it owns, through its context
        if (event.getLayer() instanceof final HasComponentNode hasComponentNode
            && hasComponentNode.getComponentNode() != null) {
            hasComponentNode.getComponentNode().setProvided(Contexts.TASK_MONITOR, tabTaskMonitorFactory);
        }
    }

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
    }

    @Override
    public void onSelect(final SelectContentTabEvent event) {
        selectTab(event.getTabData());
    }

    @Override
    public void onRefresh(final RefreshContentTabEvent event) {
        refresh(event.getTabData());
    }

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

    /// Hears the app-wide events the screen handles, and makes the screen when it is first needed.
    public static final class ContentTabPaneListener {

        @Inject
        public ContentTabPaneListener(final EventBus eventBus, final Provider<ContentTabPanePresenter> presenter) {
            eventBus.addHandler(OpenContentTabEvent.getType(), event -> presenter.get().onOpen(event));
            eventBus.addHandler(CloseContentTabEvent.getType(), event -> presenter.get().onClose(event));
            eventBus.addHandler(SelectContentTabEvent.getType(), event -> presenter.get().onSelect(event));
            eventBus.addHandler(RefreshContentTabEvent.getType(), event -> presenter.get().onRefresh(event));
            eventBus.addHandler(MoveContentTabEvent.getType(), event -> presenter.get().onMove(event));
        }
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
