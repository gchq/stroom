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

import stroom.content.client.component.CurveTabLayout;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.tabs.TabWidgets.StoryTab;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Button;
import stroom.widget.tab.client.CurveTabBar;
import stroom.widget.tab.client.TabBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.task.client.SimpleTask;
import stroom.widget.task.client.TaskMonitor;

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

/// Stories for Stroom's [CurveTabBar], in the tab bar row of `CurveTabLayout`
/// (`curve-tab-layout__bar` > optional sidebar toggle toolbar > `curve-tab-layout__tab-bar-outer` >
/// `tab-bar--curved`).
/// The stories without the sidebar toggle build that row as the view does; the one with it uses
/// the real [CurveTabLayout]. The overflow selector shows Stroom's real menu.
public final class CurveTabBarStories {

    private static final String ON_SELECT = "onSelect";
    private static final String ON_CLOSE = "onClose";
    private static final String ON_TOGGLE_SIDEBAR = "onToggleSidebar";

    // Stroom hides an overflowed tab with its --hidden modifier (out of sight, off the bar)
    private static final String OVERFLOWED = ".tab-bar__tab.tab-bar__tab--hidden";
    // The selector is only made visible when some tabs overflow
    private static final String SELECTOR_SHOWN = ".tab-selector:not(.tab-selector--hidden)";
    // Each menu item's content is a `menu__item` (the item, with `role="menuitem"`, is its cell)
    private static final String MENU_ITEM = ".menu__item";
    private static final int MEASURE_TIMEOUT_MILLIS = 5000;
    // A document name of 50 characters, the most a tab once showed before cutting it
    private static final String FIFTY_CHARACTERS = "Firewall Events Translation to Common Schema v2.10";
    private static final String LONG_NAME =
            "Corporate Proxy Server Access Logs Translation Pipeline for the Event Logging Schema";

    private CurveTabBarStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        // Dragging a tab within the bar reorders it.
        registry.component("Widgets/Tabs/CurveTabBar", CurveTabBarStories.class)
                .layout(StoryLayout.PADDED)
                // Several tabs with icons and labels - one selected, a dirty tab (`* ` prefix), a
                // loading tab, and a non-closable Welcome tab. Drag tabs to reorder.
                .story("Basic", context -> demo(context, sampleTabs(), "feed", null, false))
                .withPlay(CurveTabBarStories::playKeyboard)
                // With the leading hide/show-sidebar navigation button, named for screen readers
                .story("WithSidebarToggle", context -> demo(context, sampleTabs(), "pipe", null, true))
                .withPlay(play -> {
                    play.click(play.getByRole("button", "Hide Sidebar"));
                    play.expect(play.spy(ON_TOGGLE_SIDEBAR)).toHaveBeenCalled();
                    play.expect(play.getByRole("button", "Show Sidebar")).toBeInTheDocument();
                })
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
                // A single, non-closable tab (no `tab-bar--multiple`, no overflow chevron)
                .story("SingleTab", context -> demo(context,
                        List.of(new StoryTab("welcome", "Welcome", "Welcome", null, false)),
                        "welcome", null, false))
                // A long name is cut short with an ellipsis where it is drawn, but not in its text,
                // so a screen reader reads all of it; the one before it shows where 50 characters
                // were cut before
                .story("LongName", context -> demo(context, List.of(
                                new StoryTab("fifty", FIFTY_CHARACTERS, "Feed", SvgImage.DOCUMENT_FEED, true),
                                new StoryTab("long", LONG_NAME, "Pipeline", SvgImage.DOCUMENT_PIPELINE, true)),
                        "long", null, false))
                .withPlay(CurveTabBarStories::playLongName)
                // Long names that don't all fit are cut, the widest first, then alike, so that the
                // tabs fit rather than overflow; a short name keeps its width
                .story("ShrinkToFit", context -> demo(context, longTabs(), "proxy", "1000px", false))
                .withPlay(CurveTabBarStories::playShrinkToFit)
                // Too many to fit even at about 10 characters each: the rest overflow, and a name
                // of fewer than 10 characters isn't cut. The chevron looks like a tab
                .story("ShrinkThenOverflow", context -> demo(context, longTabs(), "proxy", "560px", false))
                .withPlay(play -> {
                    play.expect(play.querySelector(SELECTOR_SHOWN)).toBeInTheDocument();
                    play.expect("Welcome is whole", () -> isCut(play, "Welcome")).toEqual(false);
                    TabWidgets.expectSelectorLikeTabs(play, ".tab-bar--curved");
                })
                // A tab that is loading says so: its description, its panel busy, and once it has
                // been the selected tab for a second, "Loading <name>", then "<name> loaded" when
                // it is (the Finish Loading button ends its load); moved, as by dragging it (the
                // Move to Start button), it still shows its load
                .story("Loading", CurveTabBarStories::loading)
                .withPlay(CurveTabBarStories::playLoading);
    }

    private static List<StoryTab> longTabs() {
        return List.of(
                new StoryTab("welcome", "Welcome", "Welcome", null, false),
                new StoryTab("proxy", LONG_NAME, "Pipeline", SvgImage.DOCUMENT_PIPELINE, true),
                new StoryTab("fifty", FIFTY_CHARACTERS, "XSLT", SvgImage.DOCUMENT_XSLT, true),
                new StoryTab("feed", "Firewall Events from the Perimeter Gateways", "Feed",
                        SvgImage.DOCUMENT_FEED, true),
                new StoryTab("dash", "Security Operations Overview Dashboard", "Dashboard",
                        SvgImage.DOCUMENT_DASHBOARD, true));
    }

    // Every tab shows, none in the selector; the long names are cut alike, and Welcome isn't
    private static void playShrinkToFit(final Play play) {
        play.expect(play.getAllByRole("tab")).toHaveLength(5);
        play.expect(play.querySelector(OVERFLOWED)).not().toBeInTheDocument();
        play.expect(play.querySelector(SELECTOR_SHOWN)).not().toBeInTheDocument();
        play.expect("the long names are cut", () -> isCut(play, LONG_NAME) && isCut(play, FIFTY_CHARACTERS))
                .toEqual(true);
        play.expect("Welcome is whole", () -> isCut(play, "Welcome")).toEqual(false);
        play.expect("the long names are cut alike", () -> labelWidth(play, LONG_NAME)
                == labelWidth(play, FIFTY_CHARACTERS)).toEqual(true);
    }

    private static Element label(final Play play, final String tabName) {
        return play.within(play.getByRole("tab", tabName)).querySelector(".tab-bar__label").element().get();
    }

    private static boolean isCut(final Play play, final String tabName) {
        final Element label = label(play, tabName);
        return label.getScrollWidth() > label.getClientWidth();
    }

    private static int labelWidth(final Play play, final String tabName) {
        return label(play, tabName).getClientWidth();
    }

    // The bar with its panel, an unselected tab loading, and a button that ends its load
    private static Widget loading(final StoryContext context) {
        final ScreenHarness harness = TabWidgets.menuHarness(context);
        final CurveTabBar tabBar = new CurveTabBar();
        final StoryTab feed = new StoryTab("feed", "EVENTS_FEED", "Feed", SvgImage.DOCUMENT_FEED, true);
        final StoryTab pipe = new StoryTab("pipe", "Event Processing", "Pipeline", SvgImage.DOCUMENT_PIPELINE, true);
        harness.addRegistration(tabBar.addSelectionHandler(event -> tabBar.selectTab(event.getSelectedItem())));
        tabBar.addTab(feed);
        tabBar.addTab(pipe);
        tabBar.selectTab(feed);

        final SimplePanel panel = new SimplePanel(TabWidgets.pane("A document's content.", "16px", "0.85rem"));
        panel.getElement().getStyle().setProperty("height", "120px");
        tabBar.setTabPanel(panel.getElement());

        final TaskMonitor monitor = tabBar.getTab(pipe).createTaskMonitor();
        final SimpleTask task = new SimpleTask("Loading");
        monitor.onStart(task);
        final Button finish = new Button();
        finish.setText("Finish Loading");
        harness.addRegistration(finish.addClickHandler(event -> monitor.onEnd(task)));
        // As dragging it there does (DraggableTabBar)
        final Button move = new Button();
        move.setText("Move to Start");
        harness.addRegistration(move.addClickHandler(event -> tabBar.moveTab(pipe, 0)));

        // CurveTabLayout's tab bar row, as demo makes it
        final SimplePanel tabBarOuter = new SimplePanel(tabBar);
        tabBarOuter.setStyleName("curve-tab-layout__tab-bar-outer");
        final FlowPanel tabBarContainer = new FlowPanel();
        tabBarContainer.setStyleName("curve-tab-layout__bar");
        tabBarContainer.add(tabBarOuter);
        final FlowPanel frame = new FlowPanel();
        frame.add(tabBarContainer);
        frame.add(panel);
        frame.add(finish);
        frame.add(move);
        harness.add(frame);
        return harness.asWidget();
    }

    private static void playLoading(final Play play) {
        final Query pipe = play.getByRole("tab", "Event Processing");
        final Query status = play.getByRole("status");
        final Query panel = play.getByRole("tabpanel");
        // Loading out of sight: said only on the tab
        play.expect(pipe).toHaveAccessibleDescription("Loading");
        play.expect(play.getByRole("tab", "EVENTS_FEED")).not().toHaveAccessibleDescription();
        play.expect(panel).not().toHaveAttribute("aria-busy");
        play.expect(status).toHaveTextContent("");

        // Selected, it is waited on: nothing is said until it has gone on for a second
        play.click(pipe);
        play.expect(panel).toHaveAttribute("aria-busy", "true");
        play.expect(status).toHaveTextContent("");
        play.waitFor(3000, () -> play.expect(status).toHaveTextContent("Loading Event Processing"));

        // Moved (as by dragging), it is still the tab its load reports to: still loading, its
        // spinner showing (a move once made the tab again, and the new one never heard of the load)
        play.click(play.getByRole("button", "Move to Start"));
        play.expect(play.getAllByRole("tab").first()).toHaveTextContent("Event Processing");
        play.expect(pipe).toHaveAccessibleDescription("Loading");
        play.expect(play.within(pipe).querySelector(".busy-spinner--active")).toBeInTheDocument();

        play.click(play.getByRole("button", "Finish Loading"));
        play.expect(status).toHaveTextContent("Event Processing loaded");
        play.expect(pipe).not().toHaveAccessibleDescription();
        play.expect(panel).not().toHaveAttribute("aria-busy");
    }

    // The long tab is named by all of its name, though its label is cut short
    private static void playLongName(final Play play) {
        final Query tab = play.getByRole("tab", LONG_NAME);
        play.expect(tab).toBeInTheDocument();
        final Query label = play.within(tab).querySelector(".tab-bar__label");
        play.expect("the label is cut short", () -> {
            final Element element = label.element().get();
            return element.getScrollWidth() > element.getClientWidth();
        }).toEqual(true);
    }

    // The tabs are a tab list with one tab stop. The arrow keys move the focus between them (not
    // the selection, as a document may load when selected), wrapping at the ends; Home and End go
    // to the first and last; Enter selects; Delete closes a closable tab, and the focus moves to
    // the tab before it. Letters and Escape don't move or close.
    private static void playKeyboard(final Play play) {
        final Query tabList = play.getByRole("tablist", "Open documents");
        play.expect(play.within(tabList).getAllByRole("tab")).toHaveLength(5);
        final Query feed = play.getByRole("tab", "EVENTS_FEED");
        final Query pipe = play.getByRole("tab", "* Event Processing");
        final Query welcome = play.getByRole("tab", "Welcome");
        final Query dashboard = play.getByRole("tab", "Ops Dashboard");
        play.expect(feed).toHaveAttribute("aria-selected", "true");
        play.expect(pipe).toHaveAttribute("aria-selected", "false");

        play.tab();
        play.expect(feed).toHaveFocus();
        play.keyboard("{ArrowRight}");
        play.expect(pipe).toHaveFocus();
        play.expect(pipe).toHaveAttribute("aria-selected", "false");
        play.expect(play.spy(ON_SELECT)).not().toHaveBeenCalled();
        play.keyboard("dh");
        play.expect(pipe).toHaveFocus();

        play.keyboard("{End}");
        play.expect(dashboard).toHaveFocus();
        play.keyboard("{Home}");
        play.expect(welcome).toHaveFocus();
        play.keyboard("{ArrowLeft}");
        play.expect(dashboard).toHaveFocus();
        play.keyboard("{ArrowRight}");
        play.expect(welcome).toHaveFocus();

        play.keyboard("{ArrowRight}{ArrowRight}{Enter}");
        play.expect(play.spy(ON_SELECT)).toHaveBeenLastCalledWith("pipe");
        play.expect(pipe).toHaveAttribute("aria-selected", "true");

        play.keyboard("{Escape}");
        play.expect(play.getAllByRole("tab")).toHaveLength(5);
        play.keyboard("{Delete}");
        play.expect(play.spy(ON_CLOSE)).toHaveBeenLastCalledWith("pipe");
        play.expect(play.getAllByRole("tab")).toHaveLength(4);
        play.expect(feed).toHaveFocus();
        // Welcome can't be closed
        play.keyboard("{Home}{Delete}");
        play.expect(play.getAllByRole("tab")).toHaveLength(4);
        play.expect(welcome).toHaveFocus();
    }

    private static void playOverflow(final Play play) {
        // Wait for the width measurement to settle: tabs that don't fit are hidden and the
        // selector chevron shows a count
        play.waitFor(MEASURE_TIMEOUT_MILLIS, () -> play.expect(play.querySelector(SELECTOR_SHOWN)).not().toBeNull());
        play.expect(play.querySelector(OVERFLOWED)).not().toBeNull();
        // The selector is a button named with the number of tabs it holds that don't fit
        final Query selector = play.getByRole("button", TextMatch.regex("^Show all tabs \\(\\d+ hidden\\)$"));
        final Query count = play.within(selector).querySelector(".tab-selector__count");
        play.expect("the overflow count", () -> Double.valueOf(count.textContent().get())).toBeGreaterThan(0);

        // The selected tab is the walk centre, so it is never overflowed
        final Query selectedTab = play.querySelector(".tab-bar__tab[aria-selected='true']");
        play.expect(selectedTab).not().toBeNull();
        play.expect(selectedTab).toBeVisible();

        // From the keyboard, it follows the last tab shown, so Left from the first tab reaches it
        play.tab();
        play.keyboard("{Home}{ArrowLeft}");
        play.expect(selector).toHaveFocus();

        // Open the selector menu: it lists every tab, with the overflowed ones in bold.
        // The menu is a popup on the page's body, not in the canvas.
        play.click(selector);
        play.waitFor(() -> play.expect(play.screen().querySelectorAll(MENU_ITEM)).toHaveLength(manyTabs().size()));
        final Query boldLabel = play.screen().querySelector(".menu__text b");
        play.expect(boldLabel).not().toBeNull();

        // Clicking an overflowed (bold) tab selects it and brings it into view
        play.click(boldLabel.closest(MENU_ITEM));
        play.waitFor(() -> {
            final Query selected = play.querySelector(".tab-bar__tab[aria-selected='true']");
            play.expect(selected).not().toBeNull();
            play.expect(selected).toBeVisible();
        });
        play.expect(play.spy(ON_SELECT)).toHaveBeenCalled();
    }

    private static void playHiddenTabs(final Play play) {
        final int shownCount = manyTabs().size();
        play.waitFor(MEASURE_TIMEOUT_MILLIS, () -> play.expect(play.querySelector(SELECTOR_SHOWN)).not().toBeNull());

        // Stroom keeps a hidden tab's element in the bar, hidden in the same way as an overflowed
        // tab, so its text is in the document but not visible
        play.expect(play.getByText("SECRET_FEED")).not().toBeVisible();
        play.expect(play.querySelectorAll(".tab-bar--curved .tab-bar__tab")).toHaveLength(shownCount + 1);
        // The overflow count excludes the hidden tab
        final Query count = play.querySelector(".tab-selector__count");
        play.expect("the overflowed tab count", () -> Integer.valueOf(count.textContent().get())
                + countVisibleTabs()).toBe(shownCount);

        // The selector menu lists only the non-hidden tabs
        play.click(play.querySelector(".tab-selector"));
        play.waitFor(() -> play.expect(play.screen().querySelectorAll(MENU_ITEM)).toHaveLength(shownCount));
        play.expect(play.screen().queryByText("SECRET_FEED", ".menu__text")).toBeNull();
    }

    private static void playOverflowAlgorithm(final Play play) {
        // GWT's overflow algorithm (AbstractTabBar.getDisplayableTabs) is private and measures the
        // tabs, so this lays out real tab bars with tabs fixed at 100px (6px apart) and the selector
        // at 40px, and reads which tabs the bar shows. The chevron takes 48px: the gap after the
        // last tab, the selector's 2px offset, and the selector.

        // From centre 'a', only 'a','b' fit in 260px once 48px is reserved for the chevron
        play.expect("overflow from 'a'", () -> overflow(4, 260, "a", "a")).toEqual(List.of("c", "d"));
        // Centre 'c': 'd' was selected more recently than 'b', so the walk keeps 'd'
        play.expect("overflow from 'c' after 'd'", () -> overflow(5, 260, "d", "c"))
                .toEqual(List.of("a", "b", "e"));
        // Fit-from-start fallback (no selection, no recent tab): fills from the left
        play.expect("overflow with no selection", () -> overflow(3, 260)).toEqual(List.of("c"));
        // Everything fits: no chevron reserved, nothing overflows
        play.expect("overflow when all fit", () -> overflow(2, 260, "a", "a")).toEqual(List.of());
        // The chevron isn't drawn past the bar: in 250px, 'a','b' and the chevron (to 254px) don't fit
        play.expect("the chevron fits the bar", () -> overflow(4, 250, "a", "a")).toEqual(List.of("b", "c", "d"));
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
                if (selectors.getItem(i).hasClassName("tab-selector")) {
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
            if (tab.hasClassName("tab-bar__tab") && !tab.hasClassName("tab-bar__tab--hidden")) {
                count++;
            }
        }
        return count;
    }

    /// A bordered box holding the tab bar, which selects, closes and reorders (by dragging) its
    /// tabs.
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
            final CurveTabLayout view = new CurveTabLayout();
            view.setOnToggleSidebar(onToggleSidebar::call);
            tabBar = view.getTabBar();
            row = view.asWidget();
        } else {
            // CurveTabLayout's tab bar row, without the sidebar toggle
            final CurveTabBar curveTabBar = new CurveTabBar();
            final SimplePanel tabBarOuter = new SimplePanel(curveTabBar);
            tabBarOuter.setStyleName("curve-tab-layout__tab-bar-outer");
            final FlowPanel tabBarContainer = new FlowPanel();
            tabBarContainer.setStyleName("curve-tab-layout__bar");
            tabBarContainer.add(tabBarOuter);
            tabBar = curveTabBar;
            row = tabBarContainer;
        }

        // As ContentTabPaneController handles the bar's events
        harness.addRegistration(tabBar.addSelectionHandler(event -> {
            tabBar.selectTab(event.getSelectedItem());
            onSelect.call(((StoryTab) event.getSelectedItem()).getId());
        }));
        harness.addRegistration(tabBar.addRequestCloseTabHandler(event -> {
            onClose.call(((StoryTab) event.getTabData()).getId());
            // Stroom selects the tab to the left of a closed selected tab
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
}
