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

import stroom.entity.client.component.LinkTabPanel;
import stroom.entity.client.component.TabbedController;
import stroom.entity.client.presenter.HasToolbar;
import stroom.gwt.workbench.client.a11y.ButtonRuns;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.widgets.tabs.TabWidgets.StoryTab;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.svg.shared.SvgImage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// Stories for Stroom's link tab panel ([LinkTabPanel]): a link tab bar, an optional
/// toolbar and the selected tab's content.
///
/// The panel is driven by a [TabbedController] (Stroom's document editors and a folder's tabs extend
/// it), so the stories extend it with tabs showing fixed content. The toolbars are its own: its
/// toolbar, then any toolbars of a tab's content that [HasToolbar].
public final class LinkTabPanelStories {

    private static final String PANE_PADDING = "16px";
    private static final String PANE_FONT_SIZE = "0.85rem";

    private LinkTabPanelStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Tabs/LinkTabPanel", LinkTabPanelStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Basic tab panel with internal selection state
                .story("Basic", context -> panel(context, sampleTabs(), null, null, false))
                // With a global toolbar (Save / Save As) between the tab bar and content
                .story("WithToolbar", context -> panel(context, sampleTabs(), null,
                        documentActions(true), false))
                // The toolbar's icon buttons, and the tab stops they cost
                .withPlay(play -> play.expect(ButtonRuns.of(play.body()))
                        .toEqual(List.of("Save, Save As: 1 tab stop")))
                // Many tabs in a narrow bar - the chevron overflow selector (») appears
                .story("Overflow", context -> {
                    final List<String> many = new ArrayList<>();
                    for (int i = 1; i <= 12; i++) {
                        many.add("Tab " + i);
                    }
                    return panel(context, many, null, null, true);
                })
                // A tab with per-tab extra toolbar widgets, shown only when that tab is active
                .story("PerTabToolbar", context -> panel(context,
                        List.of("Data", "Query"),
                        "Query",
                        documentActions(false), false));
    }

    private static List<String> sampleTabs() {
        return List.of("Data", "Info", "Related", "Meta");
    }

    private static String text(final String label, final boolean perTabToolbar) {
        if (perTabToolbar) {
            return "Data".equals(label)
                    ? "Data pane — no extra toolbar."
                    : "Query pane — has its own extra toolbar buttons.";
        }
        switch (label) {
            case "Data":
                return "Data content here — metadata rows, source events, etc.";
            case "Info":
                return "Information about this stream — feed, pipeline, dates.";
            case "Related":
                return "Related streams linked to this meta record.";
            case "Meta":
                return "Raw meta attributes and error markers.";
            default:
                return "Content for " + label.toLowerCase() + ".";
        }
    }

    /// Equivalent of `<div style={{height: 260}}><LinkTabPanel .../></div>`.
    ///
    /// @param labels The tab labels.
    /// @param extraToolbarTab The label of the tab with its own toolbar (a Save query button), or
    ///                        null for none.
    /// @param toolbar The panel's toolbar's buttons, or null for none.
    /// @param narrow Whether the panel is in a narrow (320px) bordered box.
    private static Widget panel(final StoryContext context,
                                final List<String> labels,
                                final String extraToolbarTab,
                                final List<SvgImage> toolbar,
                                final boolean narrow) {
        final ScreenHarness harness = TabWidgets.menuHarness(context);
        final EventBus eventBus = harness.getEventBus();
        final Map<TabData, Supplier<Controller>> contents = new HashMap<>();
        final List<TabData> tabs = new ArrayList<>();
        for (final String label : labels) {
            final TabData tab = new StoryTab(label, label, null, null, false);
            tabs.add(tab);
            final Widget pane = TabWidgets.pane(text(label, extraToolbarTab != null), PANE_PADDING, PANE_FONT_SIZE);
            if (label.equals(extraToolbarTab)) {
                contents.put(tab, () -> new ToolbarContent(eventBus, pane));
            } else {
                contents.put(tab, () -> TabWidgets.content(eventBus, pane));
            }
        }

        final LinkTabPanel view = new LinkTabPanel();
        final StoryTabs panel = new StoryTabs(eventBus, view, tabs, contents, toolbar);
        harness.own(panel);
        harness.addRegistration(view.getTabBar().addShowMenuHandler(event -> eventBus.fireEvent(event)));

        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("height", "260px");
        if (narrow) {
            style.setProperty("width", "320px");
            style.setProperty("border", "1px solid var(--border-color)");
        }
        frame.add(panel.asWidget());
        harness.add(frame);
        return harness.asWidget();
    }

    // A document's toolbar's buttons, Save and optionally Save As; pressing them does nothing
    private static List<SvgImage> documentActions(final boolean withSaveAs) {
        return withSaveAs
                ? List.of(SvgImage.SAVE, SvgImage.SAVEAS)
                : List.of(SvgImage.SAVE);
    }


    // --------------------------------------------------------------------------------


    /// A link tab panel with fixed content for each tab.
    private static final class StoryTabs extends TabbedController<String> {

        private StoryTabs(final EventBus eventBus,
                          final LinkTabPanel view,
                          final List<TabData> tabs,
                          final Map<TabData, Supplier<Controller>> contents,
                          final List<SvgImage> toolbar) {
            super(eventBus, view);
            if (toolbar != null) {
                for (final SvgImage icon : toolbar) {
                    final String name = SvgImage.SAVE.equals(icon)
                            ? "Save"
                            : "Save As";
                    getToolbar().button(icon, name, () -> {
                    });
                }
            }
            for (final TabData tab : tabs) {
                addTab(tab, contents.get(tab), (content, docRef, text) -> {
                });
            }
            selectTab(tabs.get(0));
        }
    }


    // --------------------------------------------------------------------------------


    /// Tab content with its own toolbar.
    private static final class ToolbarContent extends Controller implements HasToolbar {

        private final Toolbar extraToolbar;

        private ToolbarContent(final EventBus eventBus, final Widget pane) {
            super(eventBus);
            initWidget(pane);
            extraToolbar = new Toolbar("Query actions");
            extraToolbar.button(SvgImage.SAVE, "Save query", () -> {
            });
        }

        @Override
        public List<Widget> getToolbars() {
            return List.of(extraToolbar);
        }
    }
}
