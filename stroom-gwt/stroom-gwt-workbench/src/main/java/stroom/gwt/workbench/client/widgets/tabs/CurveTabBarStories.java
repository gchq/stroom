/*
 * Copyright 2026 Crown Copyright
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


package stroom.gwt.workbench.client.widgets.tabs;

import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.tabs.TabWidgets.StoryTab;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.task.client.SimpleTask;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.view.CurveTabBar;
import stroom.widget.tab.client.view.CurveTabLayoutUiHandlers;
import stroom.widget.tab.client.view.CurveTabLayoutViewImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/// Stories for Stroom's [CurveTabBar], matching `Widgets/Tabs/CurveTabBar` in the React Storybook.
///
/// The React `CurveTabBar` renders the tab bar row of `CurveTabLayoutViewImpl` (`tabBarContainer`
/// > optional `navigation-menu-button` > `curveTabLayoutViewImpl-tabBarOuter` > `curveTabBar`).
/// The stories without the sidebar toggle build that row as the view does; the one with it uses
/// the real [CurveTabLayoutViewImpl]. The overflow selector shows Stroom's real menu.
public final class CurveTabBarStories {

    private static final String ON_SELECT = "onSelect";
    private static final String ON_CLOSE = "onClose";
    private static final String ON_TOGGLE_SIDEBAR = "onToggleSidebar";

    // Differs from React: Stroom hides an overflowed tab with an inline `visibility: hidden` (and
    // moves it off the bar), rather than a `curveTab--overflowed` class
    private static final String OVERFLOWED = ".curveTab[style*='visibility: hidden']";
    // The selector is only made visible when some tabs overflow
    private static final String SELECTOR_SHOWN = ".curveTabSelector[style*='visibility: visible']";
    // Each menu item's content is a `menuItem-outer` (the item, with `role="menuitem"`, is its cell)
    private static final String MENU_ITEM = ".menuItem-outer";
    private static final int MEASURE_TIMEOUT_MILLIS = 5000;

    private CurveTabBarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // React's onDragOut (a hook for a cross-pane tab engine) and onTabContextMenu have no
        // equivalent on the GWT tab bar; dragging a tab within the bar reorders it.
        registry.component("Widgets/Tabs/CurveTabBar", CurveTabBarStories.class)
                .layout(StoryLayout.PADDED)
                // Several tabs with icons and labels - one selected, a dirty tab (`* ` prefix), a
                // loading tab, and a non-closable Welcome tab. Drag tabs to reorder.
                .story("Basic", context -> demo(context, sampleTabs(), "feed", null, false))
                // With the leading hide/show-sidebar navigation button
                .story("WithSidebarToggle", context -> demo(context, sampleTabs(), "pipe", null, true))
                // Many tabs in a narrow bar - the ones that do not fit collapse into the "»"
                // overflow chevron, whose menu lists every tab with the overflowed ones in bold
                .story("Overflow", context -> demo(context, manyTabs(), "index", "420px", false))
                .withPlay(CurveTabBarStories::playOverflow)
                // Hidden tabs are left out of the bar, the overflow count and the selector menu
                .story("HiddenTabs", context -> {
                    final List<StoryTab> tabs = new ArrayList<>();
                    tabs.add(new StoryTab("secret", "SECRET_FEED", "Feed", SvgImage.DOCUMENT_FEED, true));
                    tabs.addAll(manyTabs());
                    return demo(context, tabs, "index", "420px", false, "secret");
                })
                .withPlay(CurveTabBarStories::playHiddenTabs)
                // The overflow algorithm (AbstractTabBar.getDisplayableTabs) with fixed widths
                .story("OverflowAlgorithm", context -> demo(context, manyTabs(), "index", "420px", false))
                .withPlay(CurveTabBarStories::playOverflowAlgorithm)
                // A single, non-closable tab (no `multiple-tabs`, no overflow chevron)
                .story("SingleTab", context -> demo(context,
                        List.of(new StoryTab("welcome", "Welcome", "Welcome", null, false)),
                        "welcome", null, false));
    }

    private static void playOverflow(final Play play) {
        // Wait for the width measurement to settle: tabs that don't fit are hidden and the
        // selector chevron shows a count
        play.waitFor(MEASURE_TIMEOUT_MILLIS, () -> play.expect(play.querySelector(SELECTOR_SHOWN)).not().toBeNull());
        play.expect(play.querySelector(OVERFLOWED)).not().toBeNull();
        // Differs from React: the selector has no title ('Show hidden tabs'); its aria-label is
        // 'Tab Selector'
        final Query selector = play.getByLabelText("Tab Selector");
        final Query count = play.within(selector).querySelector(".curveTabSelector-text");
        play.expect("the overflow count", () -> Double.valueOf(count.textContent().get())).toBeGreaterThan(0);

        // The selected tab is the walk centre, so it is never overflowed
        final Query selectedTab = play.querySelector(".curveTab-selected");
        play.expect(selectedTab).not().toBeNull();
        play.expect(selectedTab).toBeVisible();

        // Open the selector menu: it lists every tab, with the overflowed ones in bold.
        // Differs from React: the menu is a popup on the page's body, not in the canvas.
        play.click(selector);
        play.waitFor(() -> play.expect(play.screen().querySelectorAll(MENU_ITEM)).toHaveLength(manyTabs().size()));
        final Query boldLabel = play.screen().querySelector(".menuItem-text b");
        play.expect(boldLabel).not().toBeNull();

        // Clicking an overflowed (bold) tab selects it and brings it into view
        play.click(boldLabel.closest(MENU_ITEM));
        play.waitFor(() -> {
            final Query selected = play.querySelector(".curveTab-selected");
            play.expect(selected).not().toBeNull();
            play.expect(selected).toBeVisible();
        });
        play.expect(play.spy(ON_SELECT)).toHaveBeenCalled();
    }

    private static void playHiddenTabs(final Play play) {
        final int shownCount = manyTabs().size();
        play.waitFor(MEASURE_TIMEOUT_MILLIS, () -> play.expect(play.querySelector(SELECTOR_SHOWN)).not().toBeNull());

        // Differs from React: Stroom keeps a hidden tab's element in the bar, hidden in the same
        // way as an overflowed tab, so its text is in the document but not visible
        play.expect(play.getByText("SECRET_FEED")).not().toBeVisible();
        play.expect(play.querySelectorAll(".curveTabBar .curveTab")).toHaveLength(shownCount + 1);
        // The overflow count excludes the hidden tab
        final Query count = play.querySelector(".curveTabSelector-text");
        play.expect("the overflowed tab count", () -> Integer.valueOf(count.textContent().get())
                + countVisibleTabs()).toBe(shownCount);

        // The selector menu lists only the non-hidden tabs
        play.click(play.getByLabelText("Tab Selector"));
        play.waitFor(() -> play.expect(play.screen().querySelectorAll(MENU_ITEM)).toHaveLength(shownCount));
        play.expect(play.screen().queryByText("SECRET_FEED", ".menuItem-text")).toBeNull();
    }

    private static void playOverflowAlgorithm(final Play play) {
        // Differs from React: the React play calls the port's pure computeOverflowIds. GWT's
        // algorithm (AbstractTabBar.getDisplayableTabs) is private and measures the tabs, so this
        // lays out real tab bars with tabs fixed at 100px and the selector at 40px, and reads
        // which tabs the bar shows.

        // From centre 'a', only 'a','b' fit in 250px once 40px is reserved for the chevron
        play.expect("overflow from 'a'", () -> overflow(4, 250, "a", "a")).toEqual(List.of("c", "d"));
        // Centre 'c': 'd' was selected more recently than 'b', so the walk keeps 'd'
        play.expect("overflow from 'c' after 'd'", () -> overflow(5, 250, "d", "c"))
                .toEqual(List.of("a", "b", "e"));
        // Fit-from-start fallback (no selection, no recent tab): fills from the left
        play.expect("overflow with no selection", () -> overflow(3, 250)).toEqual(List.of("c"));
        // Everything fits: no chevron reserved, nothing overflows
        play.expect("overflow when all fit", () -> overflow(2, 250, "a", "a")).toEqual(List.of());
    }

    /// Lays out a real tab bar of the given width with `tabCount` tabs (`a`, `b`, ...) 100px
    /// wide, selects the given tabs in order, and returns the ids of the tabs that overflow.
    private static List<String> overflow(final int tabCount, final int width, final String... selections) {
        final CurveTabBar bar = new CurveTabBar();
        final Style barStyle = bar.getElement().getStyle();
        barStyle.setProperty("width", width + "px");
        barStyle.setProperty("boxSizing", "border-box");
        barStyle.setProperty("position", "absolute");
        barStyle.setProperty("left", "-10000px");
        final List<StoryTab> tabs = new ArrayList<>();
        RootPanel.get().add(bar);
        try {
            for (int i = 0; i < tabCount; i++) {
                final String id = String.valueOf((char) ('a' + i));
                final StoryTab tab = new StoryTab(id, id, "Feed", null, true);
                tabs.add(tab);
                bar.addTab(tab);
                fixWidth(bar.getTab(tab).getElement(), 100);
            }
            final NodeList<Element> selectors = bar.getElement().getElementsByTagName("div");
            for (int i = 0; i < selectors.getLength(); i++) {
                if (selectors.getItem(i).hasClassName("curveTabSelector")) {
                    fixWidth(selectors.getItem(i), 40);
                }
            }
            for (final String selection : selections) {
                bar.selectTab(tabs.get(selection.charAt(0) - 'a'));
            }
            bar.onResize();
            final List<String> overflowed = new ArrayList<>();
            for (final StoryTab tab : tabs) {
                if (!bar.getVisibleTabs().contains(tab)) {
                    overflowed.add(tab.getId());
                }
            }
            return overflowed;
        } finally {
            RootPanel.get().remove(bar);
        }
    }

    private static void fixWidth(final Element element, final int width) {
        final Style style = element.getStyle();
        style.setProperty("boxSizing", "border-box");
        style.setProperty("width", width + "px");
        style.setProperty("minWidth", width + "px");
        style.setProperty("maxWidth", width + "px");
        style.setProperty("padding", "0");
        style.setProperty("margin", "0");
    }

    private static int countVisibleTabs() {
        final NodeList<Element> tabs = RootPanel.get().getElement().getElementsByTagName("div");
        int count = 0;
        for (int i = 0; i < tabs.getLength(); i++) {
            final Element tab = tabs.getItem(i);
            if (tab.hasClassName("curveTab") && "visible".equals(tab.getStyle().getVisibility())) {
                count++;
            }
        }
        return count;
    }

    /// The React `Demo` harness: a bordered box holding the tab bar, which selects, closes and
    /// reorders (by dragging) its tabs.
    private static Widget demo(final StoryContext context,
                               final List<StoryTab> tabs,
                               final String selectedId,
                               final String width,
                               final boolean sidebar,
                               final String... hiddenIds) {
        final ScreenHarness harness = TabWidgets.menuHarness(context);
        final Spy onSelect = context.fn(ON_SELECT);
        final Spy onClose = context.fn(ON_CLOSE);
        final Spy onToggleSidebar = context.fn(ON_TOGGLE_SIDEBAR);

        final TabBar tabBar;
        final Widget row;
        if (sidebar) {
            final CurveTabLayoutViewImpl view = new CurveTabLayoutViewImpl(
                    GWT.create(CurveTabLayoutViewImpl.Binder.class));
            view.setUiHandlers(new SidebarToggle(onToggleSidebar));
            tabBar = view.getTabBar();
            row = view.asWidget();
        } else {
            // CurveTabLayoutViewImpl's tab bar row, without the sidebar toggle
            final CurveTabBar curveTabBar = new CurveTabBar();
            final SimplePanel tabBarOuter = new SimplePanel(curveTabBar);
            tabBarOuter.setStyleName("curveTabLayoutViewImpl-tabBarOuter");
            final FlowPanel tabBarContainer = new FlowPanel();
            tabBarContainer.setStyleName("tabBarContainer");
            tabBarContainer.add(tabBarOuter);
            tabBar = curveTabBar;
            row = tabBarContainer;
        }

        // As CurveTabLayoutPresenter handles the bar's events
        harness.addRegistration(tabBar.addSelectionHandler(event -> {
            tabBar.selectTab(event.getSelectedItem());
            onSelect.call(((StoryTab) event.getSelectedItem()).getId());
        }));
        harness.addRegistration(tabBar.addRequestCloseTabHandler(event -> {
            onClose.call(((StoryTab) event.getTabData()).getId());
            // Differs from React: Stroom selects the tab to the left of a closed selected tab,
            // React the one that takes its place
            tabBar.removeTab(event.getTabData());
        }));
        harness.addRegistration(tabBar.addShowMenuHandler(event -> harness.getEventBus().fireEvent(event)));

        // Add the tabs before selecting, as Stroom adds a tab after the selected one
        TabData selected = null;
        for (final StoryTab tab : tabs) {
            tabBar.addTab(tab);
            if (tab.getId().equals(selectedId)) {
                selected = tab;
            }
        }
        final List<String> hidden = Arrays.asList(hiddenIds);
        for (final StoryTab tab : tabs) {
            if (hidden.contains(tab.getId())) {
                tabBar.setTabHidden(tab, true);
            }
            if ("dash".equals(tab.getId()) && tabs.size() == sampleTabs().size()) {
                // The loading tab: Stroom shows a spinner while a tab's task runs
                tabBar.getTab(tab).createTaskMonitor().onStart(new SimpleTask("Loading"));
            }
        }
        tabBar.selectTab(selected);

        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("width", width == null
                ? "100%"
                : width);
        style.setProperty("overflow", "hidden");
        style.setProperty("border", "1px solid var(--splitter__background-color)");
        frame.add(row);
        harness.add(frame);
        return harness.asWidget();
    }

    private static List<StoryTab> sampleTabs() {
        return List.of(
                new StoryTab("welcome", "Welcome", "Welcome", null, false),
                new StoryTab("feed", "EVENTS_FEED", "Feed", SvgImage.DOCUMENT_FEED, true),
                new StoryTab("pipe", "Event Processing", "Pipeline", SvgImage.DOCUMENT_PIPELINE, true).dirty(),
                new StoryTab("xslt", "Format XSLT", "XSLT", SvgImage.DOCUMENT_XSLT, true),
                new StoryTab("dash", "Ops Dashboard", "Dashboard", SvgImage.DOCUMENT_DASHBOARD, true));
    }

    private static List<StoryTab> manyTabs() {
        return List.of(
                new StoryTab("welcome", "Welcome", "Welcome", null, false),
                new StoryTab("feed1", "FIREWALL_EVENTS", "Feed", SvgImage.DOCUMENT_FEED, true),
                new StoryTab("feed2", "PROXY_EVENTS", "Feed", SvgImage.DOCUMENT_FEED, true),
                new StoryTab("pipe", "Event Processing Pipeline", "Pipeline", SvgImage.DOCUMENT_PIPELINE, true),
                new StoryTab("xslt1", "Translation XSLT", "XSLT", SvgImage.DOCUMENT_XSLT, true),
                new StoryTab("xslt2", "Decoration XSLT", "XSLT", SvgImage.DOCUMENT_XSLT, true),
                new StoryTab("dict", "Reference Dictionary", "Dictionary", SvgImage.DOCUMENT_DICTIONARY, true),
                new StoryTab("index", "Example Index", "Index", SvgImage.DOCUMENT_INDEX, true),
                new StoryTab("dash", "Operations Dashboard", "Dashboard", SvgImage.DOCUMENT_DASHBOARD, true),
                new StoryTab("folder", "System Folder", "Folder", SvgImage.DOCUMENT_FOLDER, true));
    }


    // --------------------------------------------------------------------------------


    /// The sidebar toggle's handler: Stroom's `CurveTabLayoutPresenter` maximises the content.
    private static final class SidebarToggle implements CurveTabLayoutUiHandlers {

        private final Spy onToggleSidebar;

        private SidebarToggle(final Spy onToggleSidebar) {
            this.onToggleSidebar = onToggleSidebar;
        }

        @Override
        public void maximise() {
            onToggleSidebar.call();
        }
    }
}
