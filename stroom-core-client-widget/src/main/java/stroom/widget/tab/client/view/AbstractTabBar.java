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
import stroom.widget.menu.client.presenter.Separator;
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
import java.util.HashMap;
import java.util.HashSet;
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
            final int minVisibleTab = visibleTabs.isEmpty() ? -1 : indexOf(visibleTabs.get(0));
            final int maxVisibleTab = visibleTabs.isEmpty() ? -1 : indexOf(visibleTabs.get(visibleTabs.size() - 1));
            final int tabsSize = tabs.size() - 1;
            final int tabIndex = tabs.indexOf(tabData);

            makeInvisible(tab.getElement());
            remove(tab);
            tabWidgetMap.remove(tabData);
            tabs.remove(tabData);

            final int nextSelected = Math.max(0, tabIndex - 1);

            if (resize) {
                if (!tabs.isEmpty()) {
                    if (tabData == selectedTab) {
                        // select tab on the left of the removed tab
                        keyboardSelectedTab = tabs.get(nextSelected);
                        fireTabSelection(tabs.get(nextSelected));
                    }

                    visibleTabs.clear();

                    if (maxVisibleTab < tabsSize) {
                        // keep current lhs tab if there are non-visible tabs to the right
                        onResize(minVisibleTab);
                    } else if (minVisibleTab > 0) {
                        // or move left one tab if there are non-visible tabs to the left
                        onResize(minVisibleTab - 1);
                    } else if (maxVisibleTab == tabsSize) {
                        // or keep lhs tab if there are no non-visible tabs to show
                        onResize(minVisibleTab);
                    } else {
                        // otherwise calculate out the new visible tabs
                        onResize();
                    }
                } else {
                    selectTab(null);
                }
                updateTabCount();
            } else {
                selectedTab = tabs.get(nextSelected);
            }
        }
    }

    @Override
    public void moveTab(final TabData tabData, final int tabPos) {
        final Widget tabWidget = tabWidgetMap.get(tabData);
        if (tabWidget != null) {
            final int minVisibleTab = indexOf(visibleTabs.get(0));
            final int maxVisibleTab = indexOf(visibleTabs.get(visibleTabs.size() - 1));

            final boolean tabPosVisible = tabPos >= minVisibleTab && tabPos <= maxVisibleTab;
            // clear out visible tabs so we display the moved tab in the correct place
            if (!tabPosVisible) {
                visibleTabs.clear();
            }

            final int startIndex = tabPosVisible
                    ? minVisibleTab
                    : -1;

            remove(tabWidget);
            tabWidgetMap.remove(tabData);
            tabs.remove(tabData);

            final AbstractTab tab = createTab(tabData);
            insert(tab, tabPos);
            tabWidgetMap.put(tabData, tab);
            tabs.add(tabPos, tabData);

            onResize(startIndex);

            keyboardSelectTab(tabData);
            fireTabSelection(tabData);
        }
    }

    @Override
    public void clear() {
        getElement().removeAllChildren();
        tabWidgetMap.clear();
        tabs.clear();

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
            selectedTab = tabData;
            keyboardSelectedTab = tabData;
            if (selectedTab != null) {
                final AbstractTab tab = getTab(selectedTab);
                if (tab == null) {
                    selectedTab = null;
                }
            }
            onResize();
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
        onResize(-1);
    }

    private void onResize(final int startIndex) {
//        GWT.log("onResize");
        overflowTabCount = 0;

        // Figure out how many tabs are not visible because they overflow the bar width.
        if (getElement().getOffsetWidth() > 0) {

            Set<TabData> displayableTabs = new HashSet<>();

            if (startIndex != -1 || visibleTabs.contains(selectedTab)) {
                // use the given start index or the current one if we are already showing the selected tab
                final int newStartIndex = startIndex == -1
                        ? indexOf(visibleTabs.get(0))
                        : startIndex;
                displayableTabs = getDisplayableTabs(newStartIndex);

            } else {
                // loop through from the beginning of the tabs until we show the selected tab
                for (int i = 0; i < tabs.size(); i++) {
                    displayableTabs = getDisplayableTabs(i);
                    if (selectedTab == null || displayableTabs.contains(selectedTab)) {
                        break;
                    }
                }
            }

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

    private Set<TabData> getDisplayableTabs(final int startIndex) {
        final int selectorWidth = getTabSelector().getOffsetWidth();
        int remaining = getElement().getOffsetWidth();
        final int tabGap = getTabGap();

        final Set<TabData> displayableTabs = new HashSet<>();
        boolean overflow = false;
        for (int i = startIndex; i < tabs.size(); i++) {
            final TabData tabData = tabs.get(i);
            final AbstractTab tab = getTab(tabData);

            // Ignore tabs that have been deliberately hidden.
            if (tab != null && !tab.isHidden()) {
                if (!overflow) {
                    remaining -= tab.getOffsetWidth();
                    if (i > 0) {
                        final Element separator = addSeparator();
                        if (separator != null) {
                            remaining -= separator.getOffsetWidth();
                        }
                        remaining -= tabGap;
                    }

                    // We will require more space to the right of the tab if
                    // we need to show the tab selector.
                    int requiredSpace = 0;
                    if (i < tabs.size() - 1) {
                        requiredSpace = selectorWidth;
                    }

                    if (remaining < requiredSpace) {
                        overflow = true;
                    } else {
                        displayableTabs.add(tabData);
                    }
                }
            }
        }

//        GWT.log(startIndex + " " + displayableTabs.stream().map(TabData::getLabel).collect(Collectors.toList()));
        return displayableTabs;
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

        final int indexOfSelectedTab = indexOf(selectedTab);

        final List<TabData> tabsNotShownToLeft = getTabsNotShown(t -> indexOf(t) < indexOfSelectedTab);
        final List<TabData> tabsNotShownToRight = getTabsNotShown(t -> indexOf(t) > indexOfSelectedTab);

        final List<Item> menuItems = new ArrayList<>();

        tabsNotShownToLeft.stream().map(this::toIconMenuItem).forEach(menuItems::add);

        if (!tabsNotShownToLeft.isEmpty() && !tabsNotShownToRight.isEmpty()) {
            menuItems.add(new Separator(0));
        }

        tabsNotShownToRight.stream().map(this::toIconMenuItem).forEach(menuItems::add);

        ShowMenuEvent
                .builder()
                .items(menuItems)
                .popupPosition(popupPosition)
                .addAutoHidePartner(element)
                .fire(this);
    }

    private List<TabData> getTabsNotShown(final Predicate<TabData> predicate) {
        return tabs.stream()
                .filter(Predicate.not(visibleTabs::contains))
                .filter(t -> !getTab(t).isHidden())
                .filter(predicate == null
                        ? t -> true
                        : predicate)
                .collect(Collectors.toList());
    }

    private Item toIconMenuItem(final TabData tabData) {
        return new IconMenuItem.Builder()
                .priority(0)
                .icon(tabData.getIcon())
                .text(new SafeHtmlBuilder()
                        .appendHtmlConstant("<b>")
                        .appendEscaped(tabData.getLabel())
                        .appendHtmlConstant("</b>")
                        .toSafeHtml())
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
}
