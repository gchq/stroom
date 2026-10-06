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


package stroom.gwt.workbench.client.app.dashboard;

import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.tree.ExplorerFixture;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.List;

/// The layout stories of `App/Editors/DashboardEditor` (see `DashboardEditorStories`): a
/// dashboard's tabs, panels, splitters, design mode and tab menu, in Stroom's real `FlexLayout`
/// and `TabManager`.
///
/// Stroom's layout is driven with the mouse (`FlexLayout` reads mouse events, not pointer events):
/// a click on a selected tab opens its menu, and tabs and splitters are dragged with
/// `DashboardPlays.drag`.
public final class DashboardLayoutStories {

    // React's DASHBOARD_DOC with no design mode flag (a dashboard saved before it existed)
    private static final String DESIGN_MODE_ABSENT = DashboardDocs.DASHBOARD_DOC.replace(
            "\"designMode\": false, ", "");

    // React's root(row) = [groupA[a1, a2], inner(col)[groupB[b1], groupC[c1]]].
    // Differs from React: the root is a column (inner below groupA): GWT finds a drop target on the
    // outer splits first, and with a row root the inner split's top edge would be the root's top
    // edge too, so the drop would dock onto the root
    private static final String INNER_SPLIT_DASHBOARD = DashboardDocs.doc("dash-inner", "Inner Split", null,
            "\"designMode\": false",
            DashboardDocs.split(1,
                    DashboardDocs.tabs(0, "a1", "a2"),
                    DashboardDocs.split(1, DashboardDocs.tabs(0, "b1"), DashboardDocs.tabs(0, "c1"))),
            DashboardDocs.textInput("a1", "A1", "a1"),
            DashboardDocs.textInput("a2", "A2", "a2"),
            DashboardDocs.textInput("b1", "B1", "b1"),
            DashboardDocs.textInput("c1", "C1", "c1"));

    // The explorer tree of the 'Choose Dashboard' dialog
    private static final ExplorerFixture DASHBOARDS = new ExplorerFixture(ExplorerFixture.folder("System",
            ExplorerFixture.doc("My Dashboard", "Dashboard"),
            ExplorerFixture.doc("Other Dashboard", "Dashboard")));

    private DashboardLayoutStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(DashboardSupport.TITLE, DashboardLayoutStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A legacy dashboard gets a 'Params' input holding its old parameters
                .story("MigrateLegacyDashboard", DashboardSupport.story(DashboardDocs.LEGACY_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "Params")).toBeInTheDocument());
                    play.expect(DashboardPlays.tab(play, "The Query")).toBeInTheDocument();
                    // Differs from React: the input has no id; it is the Key/Value Input's text box
                    final Query input = play.querySelector(".KeyValueInputView input");
                    play.expect(input).toBeInTheDocument();
                    play.expect(input).toHaveValue("user=jbloggs feed=MY_FEED");
                    DashboardPlays.expectNoProblems(play);
                })
                // The editor's tabs, the component's tab and the dashboard's time range
                .story("Default", DashboardSupport.story(DashboardDocs.DASHBOARD_DOC, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    for (final String tab : List.of("Dashboard", "Documentation", "Permissions")) {
                        // Differs from React: Stroom's link tabs have no role="tab"
                        play.expect(play.getByText(tab, ".linkTabPanelViewImpl .linkTab-label")).toBeInTheDocument();
                    }
                    play.expect(DashboardPlays.tab(play, "My Panel")).toBeInTheDocument();
                    // Differs from React: GWT's Embedded Query shows only its results, with no
                    // Execute button of its own; the dashboard's toolbar runs it
                    play.expect(DashboardPlays.toolbarButton(play, "Execute Query")).toBeInTheDocument();
                    play.expect(play.getAllByText("All time").count()).toBeGreaterThanOrEqual(1);
                    DashboardPlays.expectNoProblems(play);
                })
                // A split layout shows both panels, and the reference panel loads its query
                .story("SplitLayout", DashboardSupport.story(DashboardDocs.SPLIT_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.expect(DashboardPlays.tab(play, "Left Panel")).toBeInTheDocument();
                    play.expect(DashboardPlays.tab(play, "Right Panel")).toBeInTheDocument();
                    // Differs from React: GWT's Embedded Queries have no Execute buttons, so the play
                    // checks that both panels show their (results) views, and that the reference
                    // panel loaded its query
                    play.waitFor(() -> play.expect(play.querySelectorAll(".tabLayout-content .TableViewImpl"))
                            .toHaveLength(2));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get("/query/v1/q-ref").toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // A dashboard with no design mode flag opens out of design mode
                .story("DesignModeAbsentOpensNormal", DashboardSupport.story(DESIGN_MODE_ABSENT, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.expect(play.getByRole("button", "Enter Design Mode")).toBeInTheDocument();
                    play.expect(play.queryByRole("button", "Exit Design Mode")).toBeNull();
                    play.expect(play.queryByRole("button", "Add Component")).toBeNull();
                    DashboardPlays.expectNoProblems(play);
                })
                // A dashboard saved out of design mode opens out of it
                .story("DesignModeFalseOpensNormal", DashboardSupport.story(DashboardDocs.DASHBOARD_DOC, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.expect(play.getByRole("button", "Enter Design Mode")).toBeInTheDocument();
                    play.expect(play.queryByRole("button", "Add Component")).toBeNull();
                    DashboardPlays.expectNoProblems(play);
                })
                // Design mode is a change to the document, which a save keeps
                .story("DesignModeIsRememberedOnSave", DashboardSupport.story(DashboardDocs.DASHBOARD_DOC,
                        routes -> {
                        }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Query save = play.getByRole("button", "Save");
                    play.expect(save).toBeDisabled();
                    DashboardPlays.designMode(play, true);
                    play.expect(play.getByRole("button", "Exit Design Mode")).toBeInTheDocument();
                    // Differs from React: React checks the working document; GWT's toggle makes the
                    // document dirty (Save is enabled), and saving it sends the flag
                    play.waitFor(() -> play.expect(save).toBeEnabled());
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/dashboard/v1/dash-1")
                                    .withJsonBodyContaining("{\"dashboardConfig\": {\"designMode\": true}}")
                                    .toSpyMatcher()));
                    play.waitFor(() -> play.expect(save).toBeDisabled());
                    // Differs from React: GWT has no compare with the saved document (any change
                    // makes it dirty until it is saved), so toggling back is saved as well
                    DashboardPlays.designMode(play, false);
                    play.waitFor(() -> play.expect(save).toBeEnabled());
                    play.click(save);
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/dashboard/v1/dash-1")
                                    .withJsonBodyContaining("{\"dashboardConfig\": {\"designMode\": false}}")
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // An Embedded Query's tab menu has Edit Query and Run Query, after Maximise
                .story("EmbeddedQueryTabMenu", DashboardSupport.story(DashboardDocs.SPLIT_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    DashboardPlays.openTabMenu(play, "Left Panel");
                    final Value<List<String>> labels = play.capture("labels",
                            screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT).textContents());
                    play.expect(labels).toContain("Edit Query");
                    play.expect(labels).toContain("Run Query");
                    play.expect("index of Edit Query after Maximise",
                                    () -> labels.get().indexOf("Edit Query") > labels.get().indexOf("Maximise"))
                            .toBe(true);
                    play.expect(labels).not().toContain("Show Visualisation");
                    play.expect(labels).not().toContain("Show Table");
                    play.keyboard("{Escape}");
                    DashboardPlays.expectNoProblems(play);
                })
                // Design mode: add a component, rename it and remove it with its tab's menu
                .story("DesignModeEditing", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    play.click(play.findByRole("button", "Add Component"));
                    play.click(DashboardPlays.simpleMenuItem(screen, "Text"));
                    // Differs from React: GWT shows no 'place the new component' banner; the
                    // component follows the mouse until it is dropped onto a panel
                    play.click(play.querySelectorAll(DashboardPlays.PANEL).nth(0));
                    // Differs from React: the dialog's caption is 'Settings'
                    final Play settings = DashboardPlays.dialog(screen, "Settings");
                    play.click(settings.getByRole("button", StroomDom.button("Cancel")));
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "Text")).toBeInTheDocument());
                    DashboardPlays.openTabMenu(play, "Text");
                    play.click(DashboardPlays.menuItem(screen, "Rename"));
                    final Play rename = DashboardPlays.dialog(screen, "Rename Tab");
                    // Differs from React: the name box has no id
                    play.clear(rename.querySelector("input"));
                    play.type(rename.querySelector("input"), "My Text");
                    play.click(rename.getByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "My Text")).toBeInTheDocument());
                    DashboardPlays.openTabMenu(play, "My Text");
                    play.click(DashboardPlays.menuItem(screen, "Remove"));
                    // Differs from React: Stroom's confirmation has no 'Confirm' caption
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("My Text", DashboardPlays.COMPONENT_TAB))
                            .toBeNull());
                    DashboardPlays.expectNoProblems(play);
                })
                // Dragging a tab to a panel's right edge docks it beside the panel
                .story("DragTabToDock", DashboardSupport.story(DashboardDocs.SINGLE_GROUP_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    // Differs from React: a panel is a tab layout (React's split children)
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(1));
                    DashboardPlays.drag(play, DashboardPlays.tab(play, "The Table"),
                            play.querySelectorAll(DashboardPlays.PANEL).nth(0), 1, 0.5, -12, 0);
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(2));
                    play.expect(DashboardPlays.tab(play, "The Query")).toBeInTheDocument();
                    play.expect(DashboardPlays.tab(play, "The Table")).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                })
                // Docking onto a panel that isn't the root's keeps its width
                .story("DockOntoMiddlePanel", DashboardSupport.story(DashboardDocs.THREE_PANEL_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    DashboardPlays.drag(play, DashboardPlays.tab(play, "Drag"),
                            play.querySelectorAll(DashboardPlays.PANEL).nth(1), 0.5, 1, 0, -8);
                    final Query dragPanel = DashboardPlays.tab(play, "Drag").closest(DashboardPlays.PANEL);
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(4));
                    play.waitFor(() -> play.expect(dragPanel.width()).toBeGreaterThan(40));
                    DashboardPlays.expectNoProblems(play);
                })
                // Resizing one splitter of three panels keeps the third panel's width
                .story("SplitterKeepsThirdPanel", DashboardSupport.story(DashboardDocs.SIZELESS_THREE_PANEL,
                        routes -> {
                        }))
                .withPlay(play -> {
                    // Differs from React: GWT can't lay out panels with no preferred sizes (a
                    // NullPointerException in FlexLayout.recalculateDimension), so the three panels
                    // have Stroom's equal default sizes (see DashboardDocs)
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    final Query splitter = play.querySelectorAll(DashboardPlays.SPLITTER).nth(0);
                    play.waitFor(() -> play.expect(splitter).toBeInTheDocument());
                    DashboardPlays.drag(play, splitter, splitter, 0.5, 0.5, 80, 0);
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL).nth(2).width())
                            .toBeGreaterThan(40));
                    DashboardPlays.expectNoProblems(play);
                })
                // Dragging the splitter between two panels resizes them
                .story("SplitterResize", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    final Query splitter = play.querySelectorAll(DashboardPlays.SPLITTER).nth(0);
                    play.waitFor(() -> play.expect(splitter).toBeInTheDocument());
                    final Value<Double> leftBefore = play.capture("leftBefore",
                            play.querySelectorAll(DashboardPlays.PANEL).nth(0).width());
                    DashboardPlays.drag(play, splitter, splitter, 0.5, 0.5, 120, 0);
                    final Value<Double> left = play.querySelectorAll(DashboardPlays.PANEL).nth(0).width();
                    play.waitFor(() -> play.expect("the left panel's growth", () -> left.get() - leftBefore.get())
                            .toBeGreaterThan(20));
                    DashboardPlays.expectNoProblems(play);
                })
                // A dashboard that doesn't fit its width is a fixed width, in a scroll panel
                .story("LayoutConstraintsFixedWidth", DashboardSupport.story(DashboardDocs.CONSTRAINED_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    // Differs from React: GWT's canvas is the design surface, sized in pixels both
                    // ways (the height fits the panel), inside a horizontally scrolling panel
                    final Query canvas = play.querySelector(".dashboard-designSurface");
                    play.waitFor(() -> play.expect(canvas.attribute("style")).toMatch("width: 1200px"));
                    play.expect(play.querySelector(".dashboard-scrollPanel"))
                            .toHaveClass("dashboard-scrollPanel--horizontal-scroll");
                    play.expect(play.querySelector(".dashboard-scrollPanel"))
                            .not().toHaveClass("dashboard-scrollPanel--vertical-scroll");
                    DashboardPlays.expectNoProblems(play);
                })
                // A panel's tab menu removes all its tabs, once confirmed
                .story("TabPanelRemoveAll", DashboardSupport.story(DashboardDocs.SINGLE_GROUP_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "The Query")).toBeInTheDocument());
                    DashboardPlays.openTabMenu(play, "The Query");
                    play.click(DashboardPlays.menuItem(screen, "Remove All"));
                    play.click(screen.findByRole("button", StroomDom.button("OK")));
                    play.waitFor(() -> play.expect(play.queryByText("The Query", DashboardPlays.COMPONENT_TAB))
                            .toBeNull());
                    play.expect(play.queryByText("The Table", DashboardPlays.COMPONENT_TAB)).toBeNull();
                    DashboardPlays.expectNoProblems(play);
                })
                // Clicking a tab that isn't selected selects it
                .story("TabClickSelects", DashboardSupport.story(DashboardDocs.SINGLE_GROUP_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    // Differs from React: a selected link tab has the 'linkTab-selected' class (no
                    // aria-selected)
                    play.waitFor(() -> play.expect(DashboardPlays.linkTab(play, "The Query"))
                            .toHaveClass(DashboardPlays.SELECTED_TAB));
                    play.expect(DashboardPlays.linkTab(play, "The Table"))
                            .not().toHaveClass(DashboardPlays.SELECTED_TAB);
                    play.click(DashboardPlays.tab(play, "The Table"));
                    play.waitFor(() -> play.expect(DashboardPlays.linkTab(play, "The Table"))
                            .toHaveClass(DashboardPlays.SELECTED_TAB));
                    play.expect(DashboardPlays.linkTab(play, "The Query"))
                            .not().toHaveClass(DashboardPlays.SELECTED_TAB);
                    DashboardPlays.expectNoProblems(play);
                })
                // Dragging a tab onto another tab of its strip moves it there
                .story("DragTabReorder", DashboardSupport.story(DashboardDocs.SINGLE_GROUP_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    play.expect(play.querySelectorAll(DashboardPlays.COMPONENT_TAB).nth(0))
                            .toHaveTextContent("The Query");
                    DashboardPlays.drag(play, DashboardPlays.tab(play, "The Table"),
                            DashboardPlays.linkTab(play, "The Query"), 0, 0.5, 3, 0);
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.COMPONENT_TAB).nth(0))
                            .toHaveTextContent("The Table"));
                    DashboardPlays.expectNoProblems(play);
                })
                // A tab's menu duplicates, maximises (then restores) and hides components
                .story("TabActions", DashboardSupport.story(DashboardDocs.SINGLE_GROUP_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    DashboardPlays.openTabMenu(play, "The Table");
                    play.click(DashboardPlays.menuItem(screen, "Duplicate"));
                    // Differs from React: GWT's copy follows the mouse until it is dropped onto a
                    // panel, as a new component does
                    play.click(play.querySelectorAll(DashboardPlays.PANEL).nth(0));
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "The Table 2")).toBeInTheDocument());
                    DashboardPlays.openTabMenu(play, "The Query");
                    final Value<List<String>> items = play.capture("items",
                            screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT).textContents());
                    for (final String item : List.of("Rename", "Settings", "Hide", "Duplicate", "Duplicate To...",
                            "Remove", "Maximise")) {
                        play.expect(items).toContain(item);
                    }
                    play.expect("index of Maximise after Remove",
                                    () -> items.get().indexOf("Maximise") > items.get().indexOf("Remove"))
                            .toBe(true);
                    play.click(DashboardPlays.menuItem(screen, "Maximise"));
                    // The toolbar's Restore (shown only while maximised) restores the tabs
                    play.click(play.findByRole("button", "Restore"));
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "The Table")).toBeInTheDocument());
                    DashboardPlays.openTabMenu(play, "The Table 2");
                    play.click(DashboardPlays.menuItem(screen, "Hide"));
                    play.waitFor(() -> play.expect(play.queryByText("The Table 2", DashboardPlays.COMPONENT_TAB))
                            .toBeNull());
                    DashboardPlays.expectNoProblems(play);
                })
                // A hidden tab isn't shown, and the selected index counts it
                .story("HiddenTabSelection", DashboardSupport.story(DashboardDocs.HIDDEN_TAB_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    play.waitFor(() -> play.expect(DashboardPlays.tab(play, "Bravo")).toBeInTheDocument());
                    play.expect(play.queryByText("Alpha", DashboardPlays.COMPONENT_TAB)).toBeNull();
                    // Differs from React: GWT's 'selected' is the index of a visible tab
                    // (FlexLayout.layout), so 2 is out of range and the first visible tab, Bravo, is
                    // selected
                    play.expect(DashboardPlays.linkTab(play, "Bravo")).toHaveClass(DashboardPlays.SELECTED_TAB);
                    play.expect(DashboardPlays.linkTab(play, "Charlie")).not().toHaveClass(DashboardPlays.SELECTED_TAB);
                    DashboardPlays.expectNoProblems(play);
                })
                // Dropping a tab on a nested split's outer edge docks it in that split
                .story("InnerSplitDock", DashboardSupport.story(INNER_SPLIT_DASHBOARD, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    // Differs from React: React calls the port's layout function; GWT's is FlexLayout's
                    // drop, so the play drags A1 to the top edge of the nested (column) split and checks
                    // the layout that is saved
                    DashboardPlays.drag(play, DashboardPlays.tab(play, "A1"),
                            DashboardPlays.tab(play, "B1").closest(DashboardPlays.PANEL), 0.5, 0, 0, 2);
                    play.waitFor(() -> play.expect(play.getByRole("button", "Save")).toBeEnabled());
                    play.click(play.getByRole("button", "Save"));
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.put("/dashboard/v1/dash-inner")
                                    .withJsonBodyContaining("{\"dashboardConfig\": {\"layout\": {\"children\": ["
                                                            + "{\"tabs\": [{\"id\": \"a2\"}]}, "
                                                            + "{\"dimension\": 1, \"children\": ["
                                                            + "{\"tabs\": [{\"id\": \"a1\"}]}, {}, {}]}]}}}")
                                    .toSpyMatcher()));
                    DashboardPlays.expectNoProblems(play);
                })
                // A new component can't be placed by pressing Escape
                .story("AddComponentPlacementCancel", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> {
                        }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    play.click(play.findByRole("button", "Add Component"));
                    play.click(DashboardPlays.simpleMenuItem(screen, "Text"));
                    // Differs from React: GWT shows no placement banner and has no Escape to cancel
                    // the placement (the component keeps following the mouse until it is dropped); the
                    // play checks that Escape places nothing
                    play.keyboard("{Escape}");
                    play.sleep(300);
                    play.expect(play.queryByText("Text", DashboardPlays.COMPONENT_TAB)).toBeNull();
                    play.expect(screen.queryByText("Settings", StroomDom.DIALOG_TITLE)).toBeNull();
                    DashboardPlays.expectNoProblems(play);
                })
                // A tab's menu duplicates a component to another dashboard, chosen in a dialog
                .story("DuplicateToDashboard", DashboardSupport.story(DashboardDocs.QUERY_TABLE_DASHBOARD,
                        routes -> routes.post("/explorer/v2/fetchExplorerNodes",
                                request -> RestReply.json(DASHBOARDS.fetch(request.getBody())))))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    DashboardPlays.openTabMenu(play, "The Table");
                    play.click(DashboardPlays.menuItem(screen, "Duplicate To..."));
                    play.expect(screen.findByText("Choose Dashboard", StroomDom.DIALOG_TITLE)).toBeInTheDocument();
                    DashboardPlays.expectNoProblems(play);
                });
    }
}
