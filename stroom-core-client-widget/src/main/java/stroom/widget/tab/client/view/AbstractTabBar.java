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

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.Widget;
import stroom.util.shared.EqualsUtil;
import stroom.widget.popup.client.presenter.DefaultPopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupPosition;
import stroom.widget.popup.client.presenter.PopupSupport;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;
import stroom.widget.popup.client.view.PopupSupportImpl;
import stroom.widget.tab.client.event.MaximiseRequestEvent;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabListPresenter;
import stroom.widget.util.client.DoubleClickTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractTabBar extends Widget implements TabBar, RequiresResize {
    private final Map<TabData, AbstractTab> tabWidgetMap = new HashMap<TabData, AbstractTab>();
    private final List<TabData> tabPriority = new ArrayList<TabData>();
    private final List<TabData> tabs = new ArrayList<TabData>();
    private final DoubleClickTest doubleClickTest = new DoubleClickTest();
    private TabData selectedTab;
    private int overflowTabCount;
    private TabListPresenter tabItemListPresenter;
    private PopupSupport popupSupport;
    private Object currentTargetObject;
    private AbstractTabSelector tabSelector;
    private List<Element> separators;

    public AbstractTabBar() {
        sinkEvents(Event.ONMOUSEDOWN | Event.ONMOUSEUP | Event.ONMOUSEOVER | Event.ONMOUSEOUT);
    }

    protected abstract AbstractTab createTab(TabData tabData);

    protected Element createSeparator() {
        return null;
    }

    protected abstract AbstractTabSelector createTabSelector();

    @Override
    public void addTab(final TabData tabData) {
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

    @Override
    public void selectTab(final int index) {
        if (index < 0 || index >= tabs.size()) {
            selectTab(null);
        } else {
            selectTab(tabs.get(index));
        }
    }

    @Override
    public void selectTab(final TabData tabData) {
        if (!EqualsUtil.isEquals(tabData, selectedTab)) {
            selectedTab = tabData;

            if (selectedTab != null) {
                tabPriority.remove(tabData);

                final AbstractTab tab = getTab(selectedTab);
                if (tab != null) {
                    tab.setSelected(true);
                    tabPriority.add(0, tabData);
                } else {
                    selectedTab = null;
                }
            }

            for (int i = 0; i < tabs.size(); i++) {
                final TabData t = tabs.get(i);
                getTab(t).setSelected(t.equals(selectedTab));
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
        for (int i = 0; i < tabs.size(); i++) {
            final TabData tabData = tabs.get(i);
            final AbstractTab tab = getTab(tabData);
            tab.setText(tabData.getLabel());
        }
        onResize();
    }

    @Override
    public void onResize() {
        final int tabGap = getTabGap();
        final int selectorWidth = getTabSelector().getOffsetWidth();

        // igure out how many tabs are not visible because they overflow the bar
        // width.
        int remaining = getElement().getOffsetWidth();
        if (remaining > 0) {
            overflowTabCount = 0;

            // Find the index of the last displayable tab.
            final Set<TabData> displayable = new HashSet<TabData>();
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
            for (int i = 0; i < tabs.size(); i++) {
                final TabData tabData = tabs.get(i);
                final AbstractTab tab = getTab(tabData);

                // Deal with tabs that have been deliberately hidden.
                if (tab.isHidden()) {
                    makeInvisible(tab.getElement());

                } else {
                    if (displayable.contains(tabData)) {
                        if (x > 0) {
                            final Element separator = addSeparator();
                            if (separator != null) {
                                separator.getStyle().setLeft(x, Unit.PX);
                                x += separator.getOffsetWidth();
                            }
                        }

                        makeVisible(tab.getElement(), x);
                        x += tab.getOffsetWidth() + tabGap;
                    } else {
                        makeInvisible(tab.getElement());
                    }
                }
            }

            x += 2;
            setOverflowTabCount(x, overflowTabCount);
        }
    }

    private Element addSeparator() {
        final Element separator = createSeparator();
        if (separator != null) {
            if (separators == null) {
                separators = new ArrayList<Element>();
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
        } else {
            makeInvisible(getTabSelector().getElement());
        }
    }

    private void makeInvisible(final Element element) {
        element.getStyle().setOpacity(0);
        element.getStyle().setLeft(-1000, Unit.PX);
    }

    private void makeVisible(final Element element, final int x) {
        element.getStyle().setLeft(x, Unit.PX);
        element.getStyle().setOpacity(1);
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (Event.ONMOUSEDOWN == event.getTypeInt()) {
            if ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0) {
                currentTargetObject = getTargetObject(event);
            }

        } else if (Event.ONMOUSEUP == event.getTypeInt()) {
            if ((event.getButton() & NativeEvent.BUTTON_LEFT) != 0) {
                final Element target = event.getEventTarget().cast();
                final Element targetObject = getTargetObject(event);

                Element doubleClickTarget = null;

                if (targetObject == currentTargetObject) {
                    if (getTabSelector().getElement().isOrHasChild(target)) {
                        showTabSelector(getTabSelector().getElement());

                    } else {
                        TabData targetTabData = null;
                        for (final TabData tabData : tabs) {
                            final AbstractTab tab = getTab(tabData);

                            // Test if this tab has been clicked at all.
                            if (tab != null && tab.getElement().isOrHasChild(target)) {
                                targetTabData = tabData;
                                break;
                            }
                        }

                        if (targetTabData == null) {
                            // If we didn't click a tab then test for maximise.
                            doubleClickTarget = targetObject;

                        } else {
                            final AbstractTab tab = getTab(targetTabData);
                            // See if close has been clicked on this tab.
                            if (tab.getCloseElement() != null && tab.getCloseElement().isOrHasChild(target)) {
                                fireTabCloseRequest(targetTabData);

                            } else if (targetTabData == selectedTab) {
                                // If this tab has been clicked and is the same
                                // as the currently selected tab then try a
                                // double click test.
                                doubleClickTarget = targetObject;

                            } else {
                                // If this tab isn't currently selected then
                                // request it is selected.
                                fireTabSelection(targetTabData);
                            }
                        }
                    }
                }

                if (doubleClickTest.isDoubleClick(doubleClickTarget)) {
                    fireMaximiseRequest();
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

    private void showTabSelector(final Element element) {
        final int left = element.getAbsoluteLeft() + 5;
        final int right = left + element.getOffsetWidth();
        final int top = element.getAbsoluteTop();
        final int bottom = top + 20;

        if (tabItemListPresenter == null) {
            tabItemListPresenter = new TabListPresenter();
            popupSupport = new PopupSupportImpl(tabItemListPresenter.getView(), null, null, element);

            tabItemListPresenter.addSelectionHandler(event -> {
                popupSupport.hide();

                final TabData tabData = event.getSelectedItem();
                fireTabSelection(tabData);
            });
        }

        final List<TabData> nonHiddenTabs = new ArrayList<>();
        for (final TabData tabData : tabPriority) {
            if (!getTab(tabData).isHidden()) {
                nonHiddenTabs.add(tabData);
            }
        }

        tabItemListPresenter.setData(nonHiddenTabs, overflowTabCount);

        final PopupUiHandlers popupUiHandlers = new DefaultPopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                popupSupport.hide();
            }
        };

        final PopupPosition popupPosition = new PopupPosition(left, right, top, bottom);
        popupSupport.show(PopupType.POPUP, popupPosition, null, popupUiHandlers);

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

    private void fireMaximiseRequest() {
        MaximiseRequestEvent.fire(this);
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<TabData> handler) {
        return addHandler(handler, SelectionEvent.getType());
    }

    @Override
    public com.google.web.bindery.event.shared.HandlerRegistration addRequestCloseTabHandler(
            final RequestCloseTabEvent.Handler handler) {
        return addHandler(handler, RequestCloseTabEvent.getType());
    }

    @Override
    public HandlerRegistration addMaximiseRequestHandler(final MaximiseRequestEvent.Handler handler) {
        return addHandler(handler, MaximiseRequestEvent.getType());
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
}
