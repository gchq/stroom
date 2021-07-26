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

package stroom.widget.tab.client.view;

import stroom.util.shared.EqualsUtil;
import stroom.widget.menu.client.presenter.FocusBehaviour;
import stroom.widget.menu.client.presenter.FocusBehaviourImpl;
import stroom.widget.menu.client.presenter.IconMenuItem;
import stroom.widget.menu.client.presenter.Item;
import stroom.widget.menu.client.presenter.ShowMenuEvent;
import stroom.widget.menu.client.presenter.ShowMenuEvent.Handler;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.util.client.MouseUtil;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractTabBar extends Widget implements TabBar, RequiresResize {

    private final Map<TabData, AbstractTab> tabWidgetMap = new HashMap<>();
    private final List<TabData> tabPriority = new ArrayList<>();
    private final List<TabData> tabs = new ArrayList<>();
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
        if (tabs.size() == 0) {
            keyboardSelectedTab = tabData;
        }

        if (tabWidgetMap.containsKey(tabData)) {
            removeTab(tabData);
        }

        final AbstractTab tab = createTab(tabData);
        makeInvisible(tab.getElement());
        getElement().appendChild(tab.getElement());
        tabWidgetMap.put(tabData, tab);
        tabs.add(tabData);
        tabPriority.add(tabData);

        onResize();
    }

    @Override
    public void removeTab(final TabData tabData) {
        final Widget tab = tabWidgetMap.get(tabData);
        if (tab != null) {
            makeInvisible(tab.getElement());
            getElement().removeChild(tab.getElement());
            tabWidgetMap.remove(tabData);
            tabs.remove(tabData);
            tabPriority.remove(tabData);

            if (tabPriority.size() > 0) {
                keyboardSelectedTab = tabPriority.get(0);
                fireTabSelection(tabPriority.get(0));
            }

            onResize();
        }
    }

    @Override
    public void clear() {
        getElement().removeAllChildren();
        tabWidgetMap.clear();
        tabs.clear();
        tabPriority.clear();

        onResize();
    }

    private void keyboardSelectTab(final TabData tabData) {
        keyboardSelectedTab = tabData;
        onResize();
    }

    @Override
    public void selectTab(final TabData tabData) {
        if (!EqualsUtil.isEquals(tabData, selectedTab)) {
            selectedTab = tabData;
            if (selectedTab != null) {
                tabPriority.remove(tabData);

                final AbstractTab tab = getTab(selectedTab);
                if (tab != null) {
                    tabPriority.add(0, tabData);
                } else {
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
            tab.setText(tabData.getLabel());
        }
        onResize();
    }

    @Override
    public void onResize() {
//        GWT.log("onResize");

        Element tabIndexElement = null;

        // Clear all visible tabs.
        visibleTabs.clear();

        final int tabGap = getTabGap();
        final int selectorWidth = getTabSelector().getOffsetWidth();

        // Figure out how many tabs are not visible because they overflow the bar
        // width.
        int remaining = getElement().getOffsetWidth();
        if (remaining > 0) {
            overflowTabCount = 0;

            // Find the index of the last displayable tab.
            final Set<TabData> displayable = new HashSet<>();
            boolean overflow = false;
            for (int i = 0; i < tabPriority.size(); i++) {
                final TabData tabData = tabPriority.get(i);
                final AbstractTab tab = getTab(tabData);

                // Ignore tabs that have been deliberately hidden.
                if (!tab.isHidden()) {
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
                        if (i < tabPriority.size() - 1) {
                            requiredSpace = selectorWidth;
                        }

                        if (remaining < requiredSpace) {
                            overflow = true;
                            overflowTabCount++;
                        } else {
                            displayable.add(tabData);
                        }

                    } else {
                        overflowTabCount++;
                    }
                }
            }

            // Remove any separators that might be present.
            removeSeparators();

            // Loop through the tabs in display order and make them visible if
            // they are prioritised.
            int x = 0;
            for (final TabData tabData : tabs) {
                final AbstractTab tab = getTab(tabData);

                // Deal with tabs that have been deliberately hidden.
                boolean visible = false;
                if (!tab.isHidden() && displayable.contains(tabData)) {
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

            if (tabIndexElement == null) {
                tabIndexElement = getTabSelector().getElement();
            }

            switchTabIndexElement(tabIndexElement);
        }
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

    private Element addSeparator() {
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

        if (Event.ONKEYDOWN == event.getTypeInt()) {
            if (event.getKeyCode() == KeyCodes.KEY_ESCAPE) {
                if (keyboardSelectedTab != null) {
                    fireTabCloseRequest(keyboardSelectedTab);
                }
            } else if (event.getKeyCode() == KeyCodes.KEY_RIGHT) {
                TabData tabData = null;
                if (visibleTabs.size() > 0) {
                    if (keyboardSelectedTab == null) {
                        tabData = visibleTabs.get(0);
                    } else {
                        int index = visibleTabs.indexOf(keyboardSelectedTab);
                        if (index >= 0 && index < visibleTabs.size() - 1) {
                            tabData = visibleTabs.get(index + 1);
                        }
                    }
                }
                keyboardSelectTab(tabData);
            } else if (event.getKeyCode() == KeyCodes.KEY_LEFT) {
                TabData tabData = null;
                if (visibleTabs.size() > 0) {
                    if (keyboardSelectedTab == null) {
                        tabData = visibleTabs.get(visibleTabs.size() - 1);
                    } else {
                        int index = visibleTabs.indexOf(keyboardSelectedTab);
                        if (index > 0) {
                            tabData = visibleTabs.get(index - 1);
                        } else if (overflowTabCount == 0) {
                            tabData = visibleTabs.get(visibleTabs.size() - 1);
                        }
                    }
                }
                keyboardSelectTab(tabData);
            } else if (event.getKeyCode() == KeyCodes.KEY_SPACE || event.getKeyCode() == KeyCodes.KEY_ENTER) {
                if (keyboardSelectedTab != null) {
                    fireTabSelection(keyboardSelectedTab);
                } else {
                    showTabSelector(event, getTabSelector().getElement());
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
        final int left = element.getAbsoluteLeft() + 5;
        final int right = left + element.getOffsetWidth();
        final int top = element.getAbsoluteTop();
        final int bottom = top + 20;
        final PopupPosition popupPosition = new PopupPosition(left, right, top, bottom);

        final List<TabData> tabsNotShown = new ArrayList<>();
        final List<TabData> tabsShown = new ArrayList<>();

        final List<TabData> tabList = new ArrayList<>();
        for (final TabData tabData : tabPriority) {
            if (!getTab(tabData).isHidden()) {
                tabList.add(tabData);
            }
        }

        for (int i = tabList.size() - overflowTabCount; i < tabList.size(); i++) {
            tabsNotShown.add(tabList.get(i));
        }
        for (int i = 0; i < tabList.size() - overflowTabCount; i++) {
            tabsShown.add(tabList.get(i));
        }

        final TabItemComparator comparator = new TabItemComparator();
        tabsNotShown.sort(comparator);
        tabsShown.sort(comparator);

        final List<Item> menuItems = new ArrayList<>();

        for (final TabData tabData : tabsNotShown) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(0)
                    .icon(tabData.getIcon())
                    .text("<b>" + tabData.getLabel() + "</b>")
                    .command(() -> fireTabSelection(tabData))
                    .build());
        }
        for (final TabData tabData : tabsShown) {
            menuItems.add(new IconMenuItem.Builder()
                    .priority(0)
                    .icon(tabData.getIcon())
                    .text(tabData.getLabel())
                    .command(() -> fireTabSelection(tabData))
                    .build());
        }

        final FocusBehaviour focusBehaviour = new FocusBehaviourImpl(nativeEvent, currentTabIndexElement);
        ShowMenuEvent.fire(this,
                menuItems,
                focusBehaviour,
                popupPosition,
                element);
    }

    private void fireTabSelection(final TabData tabData) {
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

    public AbstractTab getTab(final TabData tabData) {
        AbstractTab tab = tabWidgetMap.get(tabData);
        if (tab == null) {
            tab = createTab(tabData);
            tabWidgetMap.put(tabData, tab);
        }
        return tab;
    }

    @Override
    public List<TabData> getTabs() {
        return tabs;
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

    private static class TabItemComparator implements Comparator<TabData> {

        @Override
        public int compare(final TabData o1, final TabData o2) {
            return o1.getLabel().compareTo(o2.getLabel());
        }
    }
}
