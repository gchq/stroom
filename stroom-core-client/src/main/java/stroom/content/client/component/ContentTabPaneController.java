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

package stroom.content.client.component;

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
import stroom.task.client.TaskMonitor;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.presenter.client.Layer;
import stroom.widget.tab.client.component.CurveTabLayout;
import stroom.widget.tab.client.event.MaximiseEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.AbstractTab;
import stroom.widget.util.client.ContentFocus;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.History;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/// The document tabs (a [CurveTabLayout]): opens, selects, moves, refreshes and closes the tabs the
/// content events ask for, keeps the browser's history of the tabs selected (Back and Forward go
/// between them) and the recent items, and shows each tab's tasks on the tab.
public class ContentTabPaneController
        extends Controller
        implements
        OpenContentTabHandler,
        CloseContentTabHandler,
        SelectContentTabHandler,
        RefreshContentTabHandler,
        MoveContentTabHandler {

    private final CurveTabLayout view;
    private final Map<TabData, Layer> tabContentMap = new HashMap<>();
    private final List<TabData> historyList = new ArrayList<>();
    private final RecentItems recentItems;
    private int currentHistoryId;
    private int currentIndex;
    private boolean ignoreHistory;

    /// Made by GIN, once: the main screen's document tabs.
    @Inject
    public ContentTabPaneController(final EventBus eventBus,
                                    final CurveTabLayout view,
                                    final RecentItems recentItems) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        this.recentItems = recentItems;

        own(view.getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
        own(view.getTabBar().addRequestCloseTabHandler(event ->
                RequestCloseTabEvent.fire(this, event.getTabData(), event.isForce())));
        own(view.getTabBar().addShowMenuHandler(event ->
                getEventBus().fireEvent(event)));
        view.setOnToggleSidebar(() -> MaximiseEvent.fire(this, null));

        listen(RefreshCurrentContentTabEvent.getType(),
                event -> {
                    final TabData selectedTab = getSelectedTab();
                    if (selectedTab instanceof Refreshable) {
                        //noinspection PatternVariableCanBeUsed // Not in GWT
                        final Refreshable refreshable = (Refreshable) selectedTab;
                        refreshable.refresh();
                    }
                });

        listen(GetCurrentTabsEvent.getType(),
                event -> {
                    event.getCallback().accept(getTabs());
                });

        KeyBinding.addCommand(Action.NEXT_TAB, () -> selectAdjacentTab(1));
        KeyBinding.addCommand(Action.PREVIOUS_TAB, () -> selectAdjacentTab(-1));

        // Handle the history
        own(History.addValueChangeHandler(event -> {
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

        own(view.addShowTabMenuHandler(event -> getEventBus().fireEvent(event)));
    }

    @Override
    public void onOpen(final OpenContentTabEvent event) {
        add(event.getTabData(), event.getLayer());

        event.runCallbackOnOpen();

        // Take the user to the screen they opened, rather than leave the focus where they chose it
        // (e.g. the main menu's button, which the closing menu gives it back to)
        final Object content = event.getLayer();
        Scheduler.get().scheduleDeferred(() -> ContentFocus.focus(content));

        final AbstractTab tab = view.getTabBar().getTab(event.getTabData());
        final TabTaskMonitorFactory tabTaskMonitorFactory = new TabTaskMonitorFactory(tab);
        // The content shows its tasks on the tab, and so does everything it owns, through its context
        if (event.getLayer() instanceof final HasControllerNode hasComponentNode
            && hasComponentNode.getControllerNode() != null) {
            hasComponentNode.getControllerNode().setProvided(Contexts.TASK_MONITOR, tabTaskMonitorFactory);
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
        view.getTabBar().refresh();
    }

    @Override
    public void onMove(final MoveContentTabEvent event) {
        moveTab(event.getTabData(), event.getTabPos());
    }

    private void selectTab(final TabData tabData) {
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

        final Layer layer = tabContentMap.get(tabData);
        if (layer != null) {
            fireSelectedTabChange(tabData);
            view.getTabBar().selectTab(tabData);
            view.getLayerContainer().show(layer);
        }
    }

    // Selects the tab after the selected one (step 1) or before it (-1), going round from one end
    // to the other, and takes the focus into it, as opening a tab does
    private void selectAdjacentTab(final int step) {
        final List<TabData> tabs = getTabs();
        if (tabs.isEmpty()) {
            return;
        }
        final int index = tabs.indexOf(getSelectedTab());
        final int adjacent = index < 0
                ? 0
                : (index + step + tabs.size()) % tabs.size();
        final TabData tabData = tabs.get(adjacent);
        selectTab(tabData);
        final Layer layer = getLayer(tabData);
        Scheduler.get().scheduleDeferred(() -> ContentFocus.focus(layer));
    }

    private void fireSelectedTabChange(final TabData tabData) {
        ContentTabSelectionChangeEvent.fire(this, tabData);
    }

    private void add(final TabData tabData, final Layer layer) {
        // If this item already exists then select it.
        if (!view.getTabBar().getTabs().contains(tabData)) {
            view.getTabBar().addTab(tabData);
            tabContentMap.put(tabData, layer);
        }
        selectTab(tabData);
    }

    private void remove(final TabData tabData, final boolean resizeTabBar) {
        final Layer layer = tabContentMap.remove(tabData);
        if (layer != null) {
            view.getTabBar().removeTab(tabData, resizeTabBar);
            layer.removeLayer();
            if (resizeTabBar) {
                fireSelectedTabChange(getSelectedTab());
            }
        }
    }

    private void moveTab(final TabData tabData, final int tabPos) {
        final Layer layer = tabContentMap.get(tabData);
        if (layer != null) {
            view.getTabBar().moveTab(tabData, tabPos);
            view.getTabBar().selectTab(tabData);
            view.getLayerContainer().show(layer);
        }
    }

    private List<TabData> getTabs() {
        return view.getTabBar().getTabs();
    }

    private TabData getSelectedTab() {
        return view.getTabBar().getSelectedTab();
    }

    private Layer getLayer(final TabData tabData) {
        return tabContentMap.get(tabData);
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
