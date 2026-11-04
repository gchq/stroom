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

package stroom.widget.tab.client;

import stroom.widget.menu.client.IconMenuItem;
import stroom.widget.menu.client.Item;
import stroom.widget.menu.client.ShowMenuEvent;
import stroom.widget.menu.client.ShowMenuEvent.Handler;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.popup.client.PopupPosition.PopupLocation;
import stroom.widget.tab.client.event.RequestCloseTabEvent;
import stroom.widget.tab.client.event.ShowTabMenuEvent;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.MouseUtil;
import stroom.widget.util.client.Rect;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.ComplexPanel;
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
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/// A bar of tabs (the WAI-ARIA tabs pattern): a `tablist` of `tab`s, one of them selected, with one
/// tab stop. Left and Right move between the tabs shown (and the overflow selector, when some tabs
/// don't fit), wrapping at the ends; Home and End go to the first and last; Enter or Space selects
/// the focused tab (or opens the selector), as selecting a tab may load its content; Delete closes a
/// closable tab; the context menu key or Shift+F10 opens the focused tab's menu (as a right click
/// does). When focus leaves the bar, the tab stop goes back to the selected tab, so tabbing back in
/// lands on it.
///
/// Its root and tab list come from each bar's template (`CurveTabBar.ui.xml`, `LinkTabBar.ui.xml`).
/// It places the tabs, separators and overflow selector ([TabSelector]) itself, as their positions
/// depend on their widths: `--tab-bar__left` on a tab or separator, `--tab-selector__left` on the
/// selector, and `tab-bar__tab--hidden` and `tab-selector--hidden` for those that don't fit. The
/// selector is made here, not declared in the templates, as a template whose root is an element
/// (which the bar's own widget takes as its element) can't hold widgets.
public abstract class AbstractTabBar extends ComplexPanel implements TabBar, RequiresResize {

    private static final String TAB_HIDDEN = "tab-bar__tab--hidden";
    private static final String TAB_LEFT = "--tab-bar__left";
    private static final String SELECTOR_HIDDEN = "tab-selector--hidden";
    private static final String SELECTOR_LEFT = "--tab-selector__left";
    // How far the overflow selector is placed after the last tab shown (and its gap)
    private static final int SELECTOR_OFFSET = 2;
    // The panel the tabs show, which shows its keyboard focus (tab-bar.css)
    private static final String PANEL = "tab-bar__panel";
    // How long the selected tab must have been loading before screen readers are told
    // (TabLoadAnnouncements), so that quick loads say nothing
    private static final int LOAD_DELAY_MILLIS = 1000;

    private final Map<TabData, AbstractTab> tabWidgetMap = new HashMap<>();
    private final ArrayList<TabData> tabs = new ArrayList<>();
    private final List<TabData> visibleTabs = new ArrayList<>();
    private final LinkedList<TabData> recentTabs = new LinkedList<>();
    private TabData selectedTab;
    private TabData keyboardSelectedTab;
    private Element currentTabIndexElement;
    private int overflowTabCount;
    private Object currentTargetObject;
    private final TabSelector tabSelector;
    private List<Element> separators;
    // The tabs and their separators, as a `tablist`; it covers the bar, so the tabs are placed in it
    // as in the bar. The overflow selector is outside it, as a tablist may only hold tabs.
    private final Element tabList;
    private Element tabPanel;
    // Watches the panel for whether it should be a tab stop (TabPanelTabStop)
    private JavaScriptObject tabPanelWatcher;
    private Consumer<TabData> onSelect;
    private Consumer<TabData> onClose;
    private final TabLoadAnnouncements<TabData> loadAnnouncements = new TabLoadAnnouncements<>();
    private final Timer loadDelay = new Timer() {
        @Override
        public void run() {
            onLoadDelayElapsed();
        }
    };

    /// @param root    The bar's element, from its template.
    /// @param tabList The tab list in it (`role="tablist"`), which the tabs and separators go in.
    protected AbstractTabBar(final Element root, final Element tabList) {
        setElement(root);
        this.tabList = tabList;
        // After the template's own elements, hidden until some tabs don't fit
        tabSelector = new TabSelector();
        tabSelector.addStyleName(SELECTOR_HIDDEN);
        add(tabSelector, root);
        sinkEvents(Event.ONMOUSEDOWN |
                   Event.ONMOUSEUP |
                   Event.ONKEYDOWN);
        listenForFocusLeaving(getElement());
    }

    protected abstract AbstractTab createTab(TabData tabData);

    protected Element createSeparator() {
        return null;
    }

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
        initTabElement(tab.getElement());
        watchLoading(tabData, tab);
        setOverflowed(tab.getElement(), true);
        insert(tab, tabList, insertIndex, true);
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
            // A tab closed from the keyboard has the focus, which mustn't be lost with it
            final boolean hadFocus = hasFocus(tab.getElement());

            setOverflowed(tab.getElement(), true);
            remove(tab);
            tabWidgetMap.remove(tabData);
            tabs.remove(tabData);
            recentTabs.remove(tabData);
            loadAnnouncements.removed(tabData);

            final int nextSelected = Math.max(0, tabIndex - 1);
            if (Objects.equals(keyboardSelectedTab, tabData)) {
                keyboardSelectedTab = tabs.isEmpty()
                        ? null
                        : tabs.get(nextSelected);
            }

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
                if (hadFocus && !tabs.isEmpty()) {
                    focusTabIndexElement();
                }
            } else {
                setSelectedTab(tabs.get(nextSelected));
            }
        }
    }

    @Override
    public void moveTab(final TabData tabData, final int tabPos) {
        final AbstractTab tab = tabWidgetMap.get(tabData);
        if (tab != null) {
            // The same tab is moved, not made again: what it holds reports its loading to it (its
            // spinner, e.g. ContentTabPaneController's), and its id names the panel
            remove(tab);
            tabs.remove(tabData);
            insert(tab, tabList, tabPos, true);
            tabs.add(tabPos, tabData);

            onResize();

            keyboardSelectTab(tabData);
            fireTabSelection(tabData);
        }
    }

    @Override
    public void clear() {
        // The tabs are the bar's widgets (the selector stays)
        for (final AbstractTab tab : new ArrayList<>(tabWidgetMap.values())) {
            remove(tab);
        }
        removeSeparators();
        for (final TabData tabData : tabs) {
            loadAnnouncements.removed(tabData);
        }
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
            addStyleName("tab-bar--multiple");
        } else {
            removeStyleName("tab-bar--multiple");
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

            if (visibleTabs.contains(tabData)) {
                // Tab is already visible — just update styling, don't recalculate layout.
                // Recalculating would change the visible set because the recentTabs order
                // has changed, causing tabs to shift around despite no resize.
                updateTabStyles();
            } else {
                onResize();
            }
        }
    }

    /**
     * Updates the selected state on all tab widgets, and moves the tab stop to the selected tab,
     * without recalculating the layout.
     */
    private void updateTabStyles() {
        for (final TabData tabData : tabs) {
            final AbstractTab tab = getTab(tabData);
            if (tab != null) {
                final boolean visible = visibleTabs.contains(tabData);
                setSelectedState(tab, visible && tabData.equals(selectedTab));
                if (visible && tabData.equals(keyboardSelectedTab)) {
                    switchTabIndexElement(tab.getElement());
                }
            }
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
        updateTabPanelLabel();
        updateTabPanelBusy();
        // A tab selected while it is loading is waited on as one that has just started
        if (tabData != null && loadAnnouncements.startWaiting(tabData, isLoading(tabData), true)) {
            loadDelay.schedule(LOAD_DELAY_MILLIS);
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
    protected void onLoad() {
        super.onLoad();
        // Laid out again once the page's fonts have loaded, as the tabs' widths, which the tabs
        // shown depend on, change then but the bar's size doesn't
        whenFontsLoaded(() -> {
            if (isAttached()) {
                onResize();
            }
        });
    }

    private static native void whenFontsLoaded(Runnable runnable) /*-{
        if ($doc.fonts && $doc.fonts.ready) {
            $doc.fonts.ready.then($entry(function () {
                runnable.@java.lang.Runnable::run()();
            }));
        }
    }-*/;

    @Override
    public void onResize() {
        overflowTabCount = 0;

        // Figure out how many tabs are not visible because they overflow the bar width.
        if (getElement().getOffsetWidth() > 0) {
            // Shrunk to fit first, if the bar shrinks its tabs, so that as many show as can
            final List<AbstractTab> shown = tabs.stream()
                    .map(this::getTab)
                    .filter(tab -> tab != null && !tab.isHidden())
                    .collect(Collectors.toList());
            fitTabs(shown, getTabSpaceWidth() - getTabSpacing() * Math.max(0, shown.size() - 1));

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
                        CssProperties.set(separator, TAB_LEFT, x + "px");
                        x += separator.getOffsetWidth();
                    }
                }

                visible = true;
                CssProperties.set(tab.getElement(), TAB_LEFT, x + "px");
                setOverflowed(tab.getElement(), false);
                visibleTabs.add(tabData);
                x += tab.getOffsetWidth() + tabGap;

                if (keyboardSelectedTab == null && overflowTabCount == 0) {
                    keyboardSelectedTab = tabData;
                }
            }

            if (!visible) {
                setOverflowed(tab.getElement(), true);
            }

            if (visible && tabData.equals(keyboardSelectedTab)) {
                tabIndexElement = tab.getElement();
            }

            setSelectedState(tab, visible && tabData.equals(selectedTab));
        }

        if (!visibleTabs.contains(keyboardSelectedTab)) {
            if (overflowTabCount > 0) {
                keyboardSelectedTab = null;
            }
        }

        x += SELECTOR_OFFSET;
        setOverflowTabCount(x, overflowTabCount);

        return tabIndexElement;
    }

    /**
     * Determines which tabs to display by walking outward from the selected tab,
     * preferring the direction containing the more recently selected adjacent tab.
     * Hidden tabs are pre-filtered so the walk logic operates on a clean list.
     */
    private Set<TabData> getDisplayableTabs() {
        final int selectorWidth = getSelectorRoom();
        final int totalWidth = getTabSpaceWidth();
        // Between two tabs: their gap and any separator (as buildTabBar places them)
        final int tabGap = getTabSpacing();

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

    /// Focuses the bar's tab stop (the selected tab, or the list of tabs that don't fit), laying the
    /// bar out first if it hasn't been yet, as the tab stop is chosen only then.
    ///
    /// @return Whether it was focused: not if the bar isn't shown.
    public boolean focusTabStop() {
        if (currentTabIndexElement == null) {
            onResize();
        }
        if (currentTabIndexElement == null) {
            return false;
        }
        focusTabIndexElement();
        return true;
    }

    private void focusTabIndexElement() {

        if (currentTabIndexElement != null) {
            currentTabIndexElement.focus();
        }
    }

    public native Element getActiveElement(Document doc) /*-{
        return doc.activeElement;
    }-*/;

    // Calls onFocusLeft when focus moves from the bar to somewhere outside it (focusout bubbles, so
    // it hears focus leaving any of the bar's tabs or its selector)
    private native void listenForFocusLeaving(Element bar) /*-{
        var self = this;
        bar.addEventListener('focusout', $entry(function (event) {
            if (!event.relatedTarget || !bar.contains(event.relatedTarget)) {
                self.@stroom.widget.tab.client.AbstractTabBar::onFocusLeft()();
            }
        }));
    }-*/;

    // The arrow keys move the tab stop with the focus without selecting; once focus leaves the bar,
    // the tab stop returns to the selected tab (the WAI-ARIA tabs pattern)
    private void onFocusLeft() {
        if (selectedTab != null
            && visibleTabs.contains(selectedTab)
            && !Objects.equals(keyboardSelectedTab, selectedTab)) {
            keyboardSelectedTab = selectedTab;
            updateTabStyles();
        }
    }

    protected Element addSeparator() {
        final Element separator = createSeparator();
        if (separator != null) {
            if (separators == null) {
                separators = new ArrayList<>();
            }
            // A separator ("|") is drawn, not read
            separator.setAttribute("aria-hidden", "true");
            separators.add(separator);
            tabList.appendChild(separator);
        }

        return separator;
    }

    private void removeSeparators() {
        if (separators != null) {
            for (final Element separator : separators) {
                tabList.removeChild(separator);
            }
            separators.clear();
        }
    }

    private void setOverflowTabCount(final int x, final int count) {
        if (count > 0) {
            getTabSelector().setHiddenCount(count);
            CssProperties.set(getTabSelector().getElement(), SELECTOR_LEFT, x + "px");
            getTabSelector().removeStyleName(SELECTOR_HIDDEN);
        } else {
            getTabSelector().addStyleName(SELECTOR_HIDDEN);
        }
    }

    // A tab that doesn't fit (or isn't placed yet) is out of sight (tab-bar.css)
    private static void setOverflowed(final Element tab, final boolean hidden) {
        if (hidden) {
            tab.addClassName(TAB_HIDDEN);
        } else {
            tab.removeClassName(TAB_HIDDEN);
        }
    }

    /// Names the tab list for screen readers, e.g. "Open documents".
    ///
    /// @param name The name, or null for none.
    public void setAccessibleName(final String name) {
        if (name == null) {
            tabList.removeAttribute("aria-label");
        } else {
            tabList.setAttribute("aria-label", name);
        }
    }

    @Override
    public void setTabPanel(final Element panel) {
        if (tabPanel != null) {
            TabPanelTabStop.unwatch(tabPanel, tabPanelWatcher);
            tabPanel.removeClassName(PANEL);
            tabPanelWatcher = null;
        }
        tabPanel = panel;
        if (panel != null) {
            panel.setAttribute("role", "tabpanel");
            panel.addClassName(PANEL);
            if (panel.getId() == null || panel.getId().isEmpty()) {
                panel.setId(DOM.createUniqueId());
            }
            // A tab stop while it holds none of its own
            tabPanelWatcher = TabPanelTabStop.watch(panel);
        }
        for (final AbstractTab tab : tabWidgetMap.values()) {
            setControls(tab.getElement());
        }
        updateTabPanelLabel();
    }

    // A tab's id (which names the panel) and the panel it controls; its role, tab index and
    // selected state start in its template
    private void initTabElement(final Element element) {
        element.setId(DOM.createUniqueId());
        setControls(element);
    }

    private void setControls(final Element element) {
        if (tabPanel == null) {
            element.removeAttribute("aria-controls");
        } else {
            element.setAttribute("aria-controls", tabPanel.getId());
        }
    }

    // The selected state, which a tab's look is styled by (tab-bar.css)
    private void setSelectedState(final AbstractTab tab, final boolean selected) {
        tab.getElement().setAttribute("aria-selected", String.valueOf(selected));
    }

    /// @return Where screen readers are told that the selected tab is loading and has loaded (a
    /// polite `status` live region, there from the start, as one added with its text already in
    /// it isn't reliably read), or null for a bar that doesn't tell them.
    protected Element getLoadStatus() {
        return null;
    }

    /// Shrinks the tabs to fit the bar, if the bar does, before the bar works out which ones show:
    /// those that still don't fit overflow into its selector. By default tabs keep their width.
    ///
    /// @param tabs      The tabs that may show (not hidden), in order.
    /// @param available The room they have: the bar's, less the gaps between them.
    protected void fitTabs(final List<AbstractTab> tabs, final int available) {
        // Tabs keep their width
    }

    /// @return What a loading tab is described by for screen readers ("Loading", heard after its
    /// name; `aria-describedby`), hidden and outside the tabs, as a tab's text is its name; or
    /// null for a bar that doesn't describe them.
    protected Element getLoadDescription() {
        return null;
    }

    private void watchLoading(final TabData tabData, final AbstractTab tab) {
        tab.setOnBusyChange(busy -> onTabLoading(tabData, busy));
    }

    private boolean isLoading(final TabData tabData) {
        final AbstractTab tab = tabData == null
                ? null
                : getTab(tabData);
        return tab != null && tab.isBusy();
    }

    // A tab has started or stopped loading
    private void onTabLoading(final TabData tabData, final boolean busy) {
        describeLoading(tabData, busy);
        if (Objects.equals(tabData, selectedTab)) {
            updateTabPanelBusy();
        }
        if (busy) {
            if (loadAnnouncements.startWaiting(tabData, true, Objects.equals(tabData, selectedTab))) {
                loadDelay.schedule(LOAD_DELAY_MILLIS);
            }
        } else if (loadAnnouncements.finished(tabData)) {
            announceLoading(tabData.getLabel() + " loaded");
        }
    }

    private void describeLoading(final TabData tabData, final boolean busy) {
        final Element description = getLoadDescription();
        final AbstractTab tab = getTab(tabData);
        if (description != null && tab != null) {
            if (busy) {
                tab.getElement().setAttribute("aria-describedby", description.getId());
            } else {
                tab.getElement().removeAttribute("aria-describedby");
            }
        }
    }

    private void onLoadDelayElapsed() {
        final TabData tabData = loadAnnouncements.getWaiting();
        if (loadAnnouncements.delayElapsed(tabData,
                isLoading(tabData),
                Objects.equals(tabData, selectedTab))) {
            announceLoading("Loading " + tabData.getLabel());
        }
    }

    private void announceLoading(final String text) {
        final Element status = getLoadStatus();
        if (status == null) {
            return;
        }
        if (text.equals(status.getInnerText())) {
            // The same text again is only read if it changes, so it is cleared first
            status.setInnerText("");
            Scheduler.get().scheduleDeferred(() -> status.setInnerText(text));
        } else {
            status.setInnerText(text);
        }
    }

    // The panel is busy while the selected tab is loading
    private void updateTabPanelBusy() {
        if (tabPanel != null) {
            if (isLoading(selectedTab)) {
                tabPanel.setAttribute("aria-busy", "true");
            } else {
                tabPanel.removeAttribute("aria-busy");
            }
        }
    }

    // The panel is named by the selected tab
    private void updateTabPanelLabel() {
        if (tabPanel != null) {
            final AbstractTab tab = selectedTab == null
                    ? null
                    : getTab(selectedTab);
            if (tab == null) {
                tabPanel.removeAttribute("aria-labelledby");
            } else {
                tabPanel.setAttribute("aria-labelledby", tab.getElement().getId());
            }
        }
    }

    private void onKeyDown(final Event event) {
        // Shortcuts (e.g. Alt+W to close a tab) are left to others
        if (event.getAltKey() || event.getCtrlKey() || event.getMetaKey()) {
            return;
        }
        switch (event.getKeyCode()) {
            case KeyCodes.KEY_LEFT:
                moveFocus(-1);
                break;
            case KeyCodes.KEY_RIGHT:
                moveFocus(1);
                break;
            case KeyCodes.KEY_HOME:
                focusTab(visibleTabs.isEmpty()
                        ? null
                        : visibleTabs.get(0));
                break;
            case KeyCodes.KEY_END:
                focusTab(visibleTabs.isEmpty()
                        ? null
                        : visibleTabs.get(visibleTabs.size() - 1));
                break;
            case KeyCodes.KEY_ENTER:
            case KeyCodes.KEY_SPACE:
                if (keyboardSelectedTab != null) {
                    fireTabSelection(keyboardSelectedTab);
                } else if (overflowTabCount > 0) {
                    showTabSelector(event, getTabSelector().getElement());
                }
                break;
            case KeyCodes.KEY_DELETE:
                if (keyboardSelectedTab != null && keyboardSelectedTab.isCloseable()) {
                    fireTabCloseRequest(keyboardSelectedTab);
                }
                break;
            case KeyCodes.KEY_CONTEXT_MENU:
                showFocusedTabMenu();
                break;
            case KeyCodes.KEY_F10:
                if (!event.getShiftKey()) {
                    return;
                }
                showFocusedTabMenu();
                break;
            default:
                return;
        }
        event.preventDefault();
    }

    // Shows the focused tab's menu below it, as a right click on it does
    private void showFocusedTabMenu() {
        final AbstractTab tab = keyboardSelectedTab == null
                ? null
                : getTab(keyboardSelectedTab);
        if (tab != null) {
            ShowTabMenuEvent.fire(this, keyboardSelectedTab, tabs,
                    new PopupPosition(new Rect(tab.getElement()).grow(3), PopupLocation.BELOW));
        }
    }

    // Moves the focus to the next (or previous) of the tabs shown, then the overflow selector when
    // some tabs don't fit, wrapping at the ends
    private void moveFocus(final int step) {
        final List<TabData> stops = new ArrayList<>(visibleTabs);
        if (overflowTabCount > 0) {
            // The selector
            stops.add(null);
        }
        if (!stops.isEmpty()) {
            final int index = Math.max(0, stops.indexOf(keyboardSelectedTab));
            focusTab(stops.get(Math.floorMod(index + step, stops.size())));
        }
    }

    // Moves the focus to a tab (or the overflow selector, for null)
    private void focusTab(final TabData tabData) {
        if (tabData != null || overflowTabCount > 0) {
            keyboardSelectTab(tabData);
        }
    }

    @Override
    public void onBrowserEvent(final Event event) {
        if (Event.ONKEYDOWN == event.getTypeInt()) {
            onKeyDown(event);

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
                .name("All Tabs")
                .opener(element)
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
            if (onSelect != null) {
                onSelect.accept(tabData);
            }
        }
    }

    private void fireTabCloseRequest(final TabData tabData) {
        if (tabData != null) {
            RequestCloseTabEvent.fire(this, tabData);
            if (onClose != null) {
                onClose.accept(tabData);
            }
        }
    }

    @Override
    public void setOnSelect(final Consumer<TabData> onSelect) {
        this.onSelect = onSelect;
    }

    @Override
    public void setOnClose(final Consumer<TabData> onClose) {
        this.onClose = onClose;
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

    private TabSelector getTabSelector() {
        return tabSelector;
    }

    protected int getTabGap() {
        return 0;
    }

    // The room between two tabs: their gap and the separator between them, if the bar has them
    private int getTabSpacing() {
        return getTabGap() + getSeparatorWidth();
    }

    // A separator's width, measured on one put in the tab list for the purpose, or 0 if the bar
    // has none
    private int getSeparatorWidth() {
        final Element separator = createSeparator();
        if (separator == null) {
            return 0;
        }
        tabList.appendChild(separator);
        final int width = separator.getOffsetWidth();
        tabList.removeChild(separator);
        return width;
    }

    // The room the overflow selector takes after the last tab shown: the last tab's gap, the
    // selector's offset, and the selector with its margin before it (tab-selector.css)
    private int getSelectorRoom() {
        final Element selector = getTabSelector().getElement();
        return getTabGap() + SELECTOR_OFFSET + marginLeft(selector) + selector.getOffsetWidth();
    }

    private static native int marginLeft(Element element) /*-{
        return Math.ceil(parseFloat($wnd.getComputedStyle(element).marginLeft) || 0);
    }-*/;

    /// The width the bar's tabs, and its overflow selector, must fit in.
    ///
    /// @return By default the bar's width.
    protected int getTabSpaceWidth() {
        return getElement().getOffsetWidth();
    }

    private enum Direction {
        LEFT,
        RIGHT
    }
}
