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

package stroom.widget.tab.client.view;

import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.menu.client.presenter.ShowMenuEvent.Handler;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupPosition.PopupLocation;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.event.ShowTabMenuEvent;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.KeyBinding;
import stroom.widget.util.client.KeyBinding.Action;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public abstract class AbstractTabBar extends FlowPanel implements TabBar, RequiresResize {

    private final Map<TabData, AbstractTab> tabWidgetMap = new HashMap<>();
    private final ArrayList<TabData> tabs = new ArrayList<>();
    private final List<TabData> visibleTabs = new ArrayList<>();
    private final LinkedList<TabData> recentTabs = new LinkedList<>();
    private TabData selectedTab;
    private TabData keyboardSelectedTab;
    private Element currentTabIndexElement;
    private int overflowTabCount;
    private Object currentTargetObject;
    private AbstractTabSelector tabSelector;
    private List<Element> separators;

    public AbstractTabBar() {
        sinkEvents(Event.ONMOUSEDOWN |
                   Event.ONMOUSEUP |
                   Event.ONMOUSEOVER |
                   Event.ONMOUSEOUT |
                   Event.ONKEYDOWN);
    }

    protected abstract AbstractTab createTab(TabData tabData);

    protected Element createSeparator() {
        return null;
    }

    protected abstract AbstractTabSelector createTabSelector();

    @Override
    public void addTab(final TabData tabData) {
        final int insertIndex;
        if (selectedTab == null || tabs.isEmpty()) {
            insertIndex = tabs.size();
        } else {
            insertIndex = indexOf(selectedTab) + 1;
        }

        if (tabs.isEmpty()) {
            keyboardSelectedTab = tabData;
        }

        if (tabWidgetMap.containsKey(tabData)) {
            removeTab(tabData);
        }

        final AbstractTab tab = createTab(tabData);
        makeInvisible(tab.getElement());
        insert(tab, insertIndex);
        tabWidgetMap.put(tabData, tab);
        tabs.add(insertIndex, tabData);

        onResize();
        updateTabCount();
    }

    @Override
    public void removeTab(final TabData tabData) {
        removeTab(tabData, true);
    }

    @Override
    public void removeTab(final TabData tabData, final boolean resize) {
        final Widget tab = tabWidgetMap.get(tabData);
        if (tab != null) {
            final int tabIndex = tabs.indexOf(tabData);

            makeInvisible(tab.getElement());
            remove(tab);
            tabWidgetMap.remove(tabData);
            tabs.remove(tabData);
            recentTabs.remove(tabData);

            final int nextSelected = Math.max(0, tabIndex - 1);

            if (resize) {
                if (!tabs.isEmpty()) {
                    if (tabData == selectedTab) {
                        // select tab on the left of the removed tab
                        keyboardSelectedTab = tabs.get(nextSelected);
                        fireTabSelection(tabs.get(nextSelected));
                    }

                    visibleTabs.clear();
                    onResize();
                } else {
                    selectTab(null);
                }
                updateTabCount();
            } else {
                setSelectedTab(tabs.get(nextSelected));
            }
        }
    }

    @Override
    public void moveTab(final TabData tabData, final int tabPos) {
        final Widget tabWidget = tabWidgetMap.get(tabData);
        if (tabWidget != null) {
            remove(tabWidget);
            tabWidgetMap.remove(tabData);
            tabs.remove(tabData);

            final AbstractTab tab = createTab(tabData);
            insert(tab, tabPos);
            tabWidgetMap.put(tabData, tab);
            tabs.add(tabPos, tabData);

            onResize();

            keyboardSelectTab(tabData);
            fireTabSelection(tabData);
        }
    }

    @Override
    public void clear() {
        getElement().removeAllChildren();
        tabWidgetMap.clear();
        tabs.clear();
        recentTabs.clear();

        onResize();
        updateTabCount();
    }

    protected void keyboardSelectTab(final TabData tabData) {
        keyboardSelectedTab = tabData;
        onResize();
    }

    private void updateTabCount() {
        if (tabs.size() > 1) {
            addStyleName("multiple-tabs");
        } else {
            removeStyleName("multiple-tabs");
        }
    }

    @Override
    public void selectTab(final TabData tabData) {
        if (!Objects.equals(tabData, selectedTab)) {
            keyboardSelectedTab = tabData;
            if (tabData != null) {
                final AbstractTab tab = getTab(tabData);
                if (tab == null) {
                    setSelectedTab(null);
                } else {
                    setSelectedTab(tabData);
                }
            } else {
                setSelectedTab(null);
            }
            onResize();
        }
    }

    /**
     * Single place where selectedTab and recentTabs are updated together.
     */
    private void setSelectedTab(final TabData tabData) {
        selectedTab = tabData;
        if (tabData != null) {
            recentTabs.remove(tabData);
            recentTabs.addFirst(tabData);
        }
    }

    @Override
    public TabData getSelectedTab() {
        return selectedTab;
    }

    @Override
    public void refresh() {
        for (final TabData tabData : tabs) {
            final AbstractTab tab = getTab(tabData);
            if (tab != null) {
                tab.setText(tabData.getLabel());
            }
        }
        onResize();
    }

    private int indexOf(final TabData tabData) {
        if (tabData == null) {
            return 0;
        }

        return Math.max(tabs.indexOf(tabData), 0);
    }

    @Override
    public void onResize() {
        overflowTabCount = 0;

        // Figure out how many tabs are not visible because they overflow the bar width.
        if (getElement().getOffsetWidth() > 0) {
            final Set<TabData> displayableTabs = getDisplayableTabs();

            final int tabCount = tabs.stream().map(this::getTab).filter(Predicate.not(AbstractTab::isHidden))
                    .collect(Collectors.toSet()).size();
            overflowTabCount = tabCount - displayableTabs.size();

            // Remove any separators that might be present.
            removeSeparators();

            Element tabIndexElement = buildTabBar(displayableTabs);

            if (tabIndexElement == null) {
                tabIndexElement = getTabSelector().getElement();
            }

            switchTabIndexElement(tabIndexElement);
        }
    }

    private Element buildTabBar(final Set<TabData> displayableTabs) {
        Element tabIndexElement = null;

        final int tabGap = getTabGap();

        // Clear all visible tabs.
        visibleTabs.clear();

        // Loop through the tabs in display order and make them visible
        int x = 0;
        for (final TabData tabData : tabs) {
            final AbstractTab tab = getTab(tabData);

            // Deal with tabs that have been deliberately hidden.
            boolean visible = false;
            if (tab != null && !tab.isHidden() && displayableTabs.contains(tabData)) {
                if (x > 0) {
                    final Element separator = addSeparator();
                    if (separator != null) {
                        separator.getStyle().setLeft(x, Unit.PX);
                        x += separator.getOffsetWidth();
                    }
                }

                visible = true;
                makeVisible(tab.getElement(), x);
                visibleTabs.add(tabData);
                x += tab.getOffsetWidth() + tabGap;

                if (keyboardSelectedTab == null && overflowTabCount == 0) {
                    keyboardSelectedTab = tabData;
                }
            }

            if (!visible) {
                makeInvisible(tab.getElement());
            }

            if (visible && tabData.equals(keyboardSelectedTab)) {
                tabIndexElement = tab.getElement();
            }

            tab.setKeyboardSelected(visible && tabData.equals(keyboardSelectedTab));
            tab.setSelected(visible && tabData.equals(selectedTab));
        }

        if (!visibleTabs.contains(keyboardSelectedTab)) {
            if (overflowTabCount > 0) {
                keyboardSelectedTab = null;
            }
        }

        x += 2;
        setOverflowTabCount(x, overflowTabCount);

        return tabIndexElement;
    }

    /**
     * Determines which tabs to display by walking outward from the selected tab,
     * preferring the direction containing the more recently selected adjacent tab.
     * Hidden tabs are pre-filtered so the walk logic operates on a clean list.
     */
    private Set<TabData> getDisplayableTabs() {
        final int selectorWidth = getTabSelector().getOffsetWidth();
        final int totalWidth = getElement().getOffsetWidth();
        final int tabGap = getTabGap();

        // Pre-filter: only consider non-hidden tabs
        final List<TabData> candidateTabs = tabs.stream()
                .filter(td -> {
                    final AbstractTab t = getTab(td);
                    return t != null && !t.isHidden();
                })
                .collect(Collectors.toList());

        // If there are no visible tabs then none are displayable.
        if (candidateTabs.isEmpty()) {
            return Collections.emptySet();
        }

        // Determine the centre tab to walk outward from.
        // Prefer the selected tab, fall back to the most recent tab in recentTabs,
        // and finally fall back to fitting from the start.
        final TabData centreTab = findCentreTab(candidateTabs);
        if (centreTab == null) {
            return getDisplayableTabsFromStart(candidateTabs, totalWidth, selectorWidth, tabGap);
        }

        // Start with the centre tab
        final int centreIndex = candidateTabs.indexOf(centreTab);
        int usedWidth = getTab(centreTab).getOffsetWidth();
        final Set<TabData> fitted = new HashSet<>();
        fitted.add(centreTab);

        // Walk outward from the centre tab's position in candidateTabs
        int left = centreIndex - 1;
        int right = centreIndex + 1;

        while (left >= 0 || right < candidateTabs.size()) {
            // Choose direction: pick whichever adjacent tab was more recently selected.
            // If neither is in recentTabs (both MAX_VALUE), default to LEFT.
            final Direction direction;
            if (left < 0) {
                direction = Direction.RIGHT;
            } else if (right >= candidateTabs.size()) {
                direction = Direction.LEFT;
            } else {
                direction = getRecentTabsRank(candidateTabs.get(left))
                            <= getRecentTabsRank(candidateTabs.get(right))
                        ? Direction.LEFT
                        : Direction.RIGHT;
            }

            final TabData candidate = direction == Direction.LEFT
                    ? candidateTabs.get(left)
                    : candidateTabs.get(right);
            final AbstractTab candidateWidget = getTab(candidate);

            // Calculate width needed (tab + gap/separator)
            final int candidateWidth = candidateWidget.getOffsetWidth() + tabGap;

            // Reserve space for the selector if not all tabs will fit
            final int candidateFittedCount = fitted.size() + 1;
            final int requiredExtra = candidateFittedCount < candidateTabs.size()
                    ? selectorWidth
                    : 0;

            if (usedWidth + candidateWidth + requiredExtra <= totalWidth) {
                fitted.add(candidate);
                usedWidth += candidateWidth;
                if (direction == Direction.LEFT) {
                    left--;
                } else {
                    right++;
                }
            } else {
                // Tab doesn't fit in this direction — exhaust it so we still try the other side.
                // A narrower tab on the opposite side may still fit.
                if (direction == Direction.LEFT) {
                    left = -1;
                } else {
                    right = candidateTabs.size();
                }
            }
        }

        return fitted;
    }

    /**
     * Returns the rank of a tab in recentTabs (lower = more recent).
     * Tabs not in recentTabs get Integer.MAX_VALUE.
     */
    private int getRecentTabsRank(final TabData tabData) {
        final int index = recentTabs.indexOf(tabData);
        return index == -1
                ? Integer.MAX_VALUE
                : index;
    }

    /**
     * Finds the best tab to centre the walk on.
     * Prefers the selected tab if it's in the candidate list,
     * otherwise falls back to the most recent tab from recentTabs.
     * Returns null if no suitable centre tab can be found.
     */
    private TabData findCentreTab(final List<TabData> candidateTabs) {
        if (selectedTab != null && candidateTabs.contains(selectedTab)) {
            return selectedTab;
        }
        for (final TabData td : recentTabs) {
            if (candidateTabs.contains(td)) {
                return td;
            }
        }
        return null;
    }

    /**
     * Fallback when there's no selected tab — fit as many as possible from the start.
     */
    private Set<TabData> getDisplayableTabsFromStart(
            final List<TabData> candidateTabs,
            final int totalWidth,
            final int selectorWidth,
            final int tabGap) {
        final Set<TabData> fitted = new HashSet<>();
        int usedWidth = 0;
        for (final TabData td : candidateTabs) {
            final AbstractTab tab = getTab(td);

            int tabWidth = tab.getOffsetWidth();
            if (!fitted.isEmpty()) {
                tabWidth += tabGap;
            }

            final int requiredExtra = (fitted.size() + 1) < candidateTabs.size()
                    ? selectorWidth
                    : 0;

            if (usedWidth + tabWidth + requiredExtra <= totalWidth) {
                fitted.add(td);
                usedWidth += tabWidth;
            } else {
                break;
            }
        }
        return fitted;
    }

    @Override
    public void focus() {
        focusTabIndexElement();
    }

    private boolean hasFocus(final Element element) {
        if (element != null) {
            final Element activeElement = getActiveElement(Document.get());
//            GWT.log("Active Element = " + (activeElement == null
//                    ? "null"
//                    : activeElement.getTagName()));
//            GWT.log("Current tab index = " + (element == null
//                    ? "null"
//                    : element.getTagName()));
            return activeElement != null && activeElement.equals(element);
        }
        return false;
    }

    private void switchTabIndexElement(final Element tabIndexElement) {
        if (tabIndexElement != null && !tabIndexElement.equals(currentTabIndexElement)) {
            final boolean currentHasFocus = hasFocus(currentTabIndexElement);

            if (currentTabIndexElement != null) {
                currentTabIndexElement.setTabIndex(-1);
            }
            tabIndexElement.setTabIndex(0);
            currentTabIndexElement = tabIndexElement;

            if (currentHasFocus) {
                focusTabIndexElement();
            }
        }
    }

    private void focusTabIndexElement() {
//        GWT.log("Focus: " + currentTabIndexElement);

        if (currentTabIndexElement != null) {
            currentTabIndexElement.focus();
        }
    }

    public native Element getActiveElement(Document doc) /*-{
        return doc.activeElement;
    }-*/;

    protected Element addSeparator() {
        final Element separator = createSeparator();
        if (separator != null) {
            if (separators == null) {
                separators = new ArrayList<>();
            }
            separators.add(separator);
            getElement().appendChild(separator);
        }

        return separator;
    }

    private void removeSeparators() {
        if (separators != null) {
            for (final Element separator : separators) {
                getElement().removeChild(separator);
            }
            separators.clear();
        }
    }

    private void setOverflowTabCount(final int x, final int count) {
        if (count > 0) {
            getTabSelector().setText(String.valueOf(count));
            makeVisible(getTabSelector().getElement(), x);
            getTabSelector().setKeyboardSelected(keyboardSelectedTab == null);
        } else {
            makeInvisible(getTabSelector().getElement());
        }
    }

    private void makeInvisible(final Element element) {
        element.getStyle().setVisibility(Visibility.HIDDEN);
        element.getStyle().setLeft(-1000, Unit.PX);
    }

    private void makeVisible(final Element element, final int x) {
        element.getStyle().setLeft(x, Unit.PX);
        element.getStyle().setVisibility(Visibility.VISIBLE);
    }

    @Override
    public void onBrowserEvent(final Event event) {
//        GWT.log("onBrowserEvent " + event.getType());
        final Action action = KeyBinding.test(event);
        if (Event.ONKEYDOWN == event.getTypeInt()) {
            TabData tabData = null;
            if (action != null) {
                switch (action) {
                    case CLOSE:
                        if (keyboardSelectedTab != null) {
                            fireTabCloseRequest(keyboardSelectedTab);
                        }
                        break;

                    case MOVE_RIGHT:
                        if (!visibleTabs.isEmpty()) {
                            if (keyboardSelectedTab == null) {
                                tabData = visibleTabs.get(0);
                            } else {
                                final int index = visibleTabs.indexOf(keyboardSelectedTab);
                                if (index >= 0 && index < visibleTabs.size() - 1) {
                                    tabData = visibleTabs.get(index + 1);
                                }
                            }
                        }
                        keyboardSelectTab(tabData);
                        break;

                    case MOVE_LEFT:
                        if (!visibleTabs.isEmpty()) {
                            if (keyboardSelectedTab == null) {
                                tabData = visibleTabs.get(visibleTabs.size() - 1);
                            } else {
                                final int index = visibleTabs.indexOf(keyboardSelectedTab);
                                if (index > 0) {
                                    tabData = visibleTabs.get(index - 1);
                                } else if (overflowTabCount == 0) {
                                    tabData = visibleTabs.get(visibleTabs.size() - 1);
                                }
                            }
                        }
                        keyboardSelectTab(tabData);
                        break;

                    case SELECT:
                    case EXECUTE:
                        if (keyboardSelectedTab != null) {
                            fireTabSelection(keyboardSelectedTab);
                        } else {
                            showTabSelector(event, getTabSelector().getElement());
                        }
                        break;
                }
            }

        } else if (Event.ONMOUSEDOWN == event.getTypeInt()) {
            if (MouseUtil.isPrimary(event)) {
                currentTargetObject = getTargetObject(event);
            }

        } else if (Event.ONMOUSEUP == event.getTypeInt()) {
            if (MouseUtil.isPrimary(event)) {
                final Element target = event.getEventTarget().cast();
                final Element targetObject = getTargetObject(event);

                if (targetObject == currentTargetObject) {
                    if (getTabSelector().getElement().isOrHasChild(target)) {
                        switchTabIndexElement(getTabSelector().getElement());
                        focusTabIndexElement();
                        showTabSelector(event, getTabSelector().getElement());

                    } else {
                        select(target);
                    }
                }
            } else {
                final Element target = event.getEventTarget().cast();
                final TabData targetTabData = getTargetTabData(target);
                if (targetTabData != null) {
                    ShowTabMenuEvent.fire(this,
                            targetTabData, tabs,
                            new PopupPosition(event.getClientX(), event.getClientY()));
                }
            }
        } else if (Event.ONMOUSEOVER == event.getTypeInt()) {
            final Element target = event.getEventTarget().cast();

            if (getTabSelector().getElement().isOrHasChild(target)) {
                getTabSelector().setHover(true);
            } else {
                final AbstractTab tab = getTargetTab(target);
                if (tab != null) {
                    if (tab.getCloseElement() != null && tab.getCloseElement().isOrHasChild(target)) {
                        tab.setCloseActive(true);
                    }
                    tab.setHover(true);
                }
            }
        } else if (Event.ONMOUSEOUT == event.getTypeInt()) {
            final Element target = event.getEventTarget().cast();

            if (getTabSelector().getElement().isOrHasChild(target)) {
                getTabSelector().setHover(false);
            } else {
                final AbstractTab tab = getTargetTab(target);
                if (tab != null) {
                    if (tab.getCloseElement() != null && tab.getCloseElement().isOrHasChild(target)) {
                        tab.setCloseActive(false);
                    } else {
                        tab.setHover(false);
                    }
                }
            }
        }

        super.onBrowserEvent(event);
    }

    private void select(final Element target) {
        final TabData targetTabData = getTargetTabData(target);
        if (targetTabData != null) {
            final AbstractTab tab = getTab(targetTabData);
            if (tab != null) {
                // See if close has been clicked on this tab.
                if (tab.getCloseElement() != null && tab.getCloseElement().isOrHasChild(target)) {
                    fireTabCloseRequest(targetTabData);

                } else if (targetTabData != selectedTab) {
                    switchTabIndexElement(tab.getElement());
                    focusTabIndexElement();

                    // If this tab isn't currently selected then
                    // request it is selected.
                    keyboardSelectTab(targetTabData);
                    fireTabSelection(targetTabData);
                }
            }
        }
    }

    private TabData getTargetTabData(final Element target) {
        for (final TabData tabData : tabs) {
            final AbstractTab tab = getTab(tabData);

            // Test if this tab has been clicked at all.
            if (tab != null && tab.getElement().isOrHasChild(target)) {
                return tabData;
            }
        }
        return null;
    }

    private Element getTargetObject(final Event event) {
        final Element target = event.getEventTarget().cast();
        if (getTabSelector().getElement().isOrHasChild(target)) {
            return getTabSelector().getElement();
        } else {
            final AbstractTab tab = getTargetTab(target);
            if (tab != null) {
                if (tab.getCloseElement() != null && tab.getCloseElement().isOrHasChild(target)) {
                    return tab.getCloseElement();
                }
                return tab.getElement();
            } else {
                return getElement();
            }
        }
    }

    private AbstractTab getTargetTab(final Element target) {
        for (final AbstractTab tab : tabWidgetMap.values()) {
            if (tab.getElement().isOrHasChild(target)) {
                return tab;
            }
        }
        return null;
    }

    private void showTabSelector(final NativeEvent nativeEvent, final Element element) {
        Rect relativeRect = new Rect(element);
        relativeRect = relativeRect.grow(3);
        final PopupPosition popupPosition = new PopupPosition(relativeRect, PopupLocation.BELOW);

        final List<Item> menuItems = tabs.stream()
                .filter(t -> !getTab(t).isHidden())
                .map(t -> toIconMenuItem(t, visibleTabs.contains(t)))
                .collect(Collectors.toList());

        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .fire(this);
    }

    private Item toIconMenuItem(final TabData tabData, final boolean visible) {
        final SafeHtmlBuilder sb = new SafeHtmlBuilder();
        if (!visible) {
            sb.appendHtmlConstant("<b>");
        }
        sb.appendEscaped(tabData.getLabel());
        if (!visible) {
            sb.appendHtmlConstant("</b>");
        }

        return new IconMenuItem.Builder()
                .priority(0)
                .icon(tabData.getIcon())
                .text(sb.toSafeHtml())
                .tooltip(tabData.getLabel())
                .command(() -> fireTabSelection(tabData))
                .build();
    }

    protected void fireTabSelection(final TabData tabData) {
        if (tabData != null && tabData != selectedTab) {
            SelectionEvent.fire(this, tabData);
        }
    }

    private void fireTabCloseRequest(final TabData tabData) {
        if (tabData != null) {
            RequestCloseTabEvent.fire(this, tabData);
        }
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<TabData> handler) {
        return addHandler(handler, SelectionEvent.getType());
    }

    @Override
    public HandlerRegistration addRequestCloseTabHandler(
            final RequestCloseTabEvent.Handler handler) {
        return addHandler(handler, RequestCloseTabEvent.getType());
    }

    @Override
    public HandlerRegistration addShowMenuHandler(final Handler handler) {
        return addHandler(handler, ShowMenuEvent.getType());
    }

    @Override
    public void setTabHidden(final TabData tabData, final boolean hidden) {
        final AbstractTab tab = getTab(tabData);
        if (tab != null) {
            tab.setHidden(hidden);
        }
        onResize();
    }

    @Override
    public boolean isTabHidden(final TabData tabData) {
        final AbstractTab tab = getTab(tabData);
        if (tab != null) {
            return tab.isHidden();
        }
        return false;
    }

    @Override
    public AbstractTab getTab(final TabData tabData) {
        return tabWidgetMap.get(tabData);
    }

    @Override
    public List<TabData> getTabs() {
        return tabs;
    }

    protected Optional<TabData> getTabData(final AbstractTab tab) {
        return tabWidgetMap.entrySet()
                .stream()
                .filter(entry -> tab.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public List<TabData> getVisibleTabs() {
        return visibleTabs;
    }

    private AbstractTabSelector getTabSelector() {
        if (tabSelector == null) {
            tabSelector = createTabSelector();
            makeInvisible(tabSelector.getElement());
            getElement().appendChild(tabSelector.getElement());
        }
        return tabSelector;
    }

    protected int getTabGap() {
        return 0;
    }

    private enum Direction {
        LEFT,
        RIGHT
    }
}
