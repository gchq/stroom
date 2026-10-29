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

import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;

import java.util.ArrayList;
import java.util.List;

/// The keyboard stories of `App/Editors/DashboardEditor` (see `DashboardEditorStories`): the order
/// in which Tab visits a dashboard's panels and splitters, and resizing panels with a splitter's
/// keys, in Stroom's real `FlexLayout`.
///
/// `FlexLayout` places its panels and splitters absolutely, so where they are on screen says
/// nothing about their order in the page, which is the order Tab visits them in. Each story walks
/// a layout with Tab from its first panel's tab, noting each stop: a panel's tab (`tab A`),
/// anything in a panel (`A panel`, named by its selected tab) or a splitter (`vertical splitter`
/// between panels side by side, `horizontal splitter` between stacked ones, as a separator's
/// `aria-orientation` names the way its line runs). The walk must visit the panels as they are laid
/// out, depth first through the layout's splits, with each splitter between the two panels (or
/// groups of panels) that it resizes, so that a keyboard user meets a splitter just after what it
/// resizes, as with `SplitPane`.
///
/// A splitter with the focus resizes the panels either side of it as releasing a drag does: the
/// arrow keys along it (left and right for panels side by side, up and down for stacked ones) move
/// it 10px, or 50px with Shift, and Home and End move it as far as it can go.
///
/// The panels hold Text Inputs, so each panel's content is one text box.
public final class DashboardKeyboardStories {

    // How many times each story presses Tab: enough to pass every stop of its layout once
    private static final int TAB_PRESSES = 30;

    // Three panels side by side: A | B | C
    private static final String SIDE_BY_SIDE = DashboardDocs.doc("dash-tab-row", "Side By Side", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.tabs(0, "a"),
                    DashboardDocs.tabs(0, "b"),
                    DashboardDocs.tabs(0, "c")),
            DashboardDocs.textInput("a", "A", "a"),
            DashboardDocs.textInput("b", "B", "b"),
            DashboardDocs.textInput("c", "C", "c"));

    // Three panels stacked: D above E above F
    private static final String STACKED = DashboardDocs.doc("dash-tab-column", "Stacked", null,
            "\"designMode\": false",
            DashboardDocs.split(1,
                    DashboardDocs.tabs(0, "d"),
                    DashboardDocs.tabs(0, "e"),
                    DashboardDocs.tabs(0, "f")),
            DashboardDocs.textInput("d", "D", "d"),
            DashboardDocs.textInput("e", "E", "e"),
            DashboardDocs.textInput("f", "F", "f"));

    // A panel beside a column of two: Left | (Top Right above Bottom Right)
    private static final String NESTED_COLUMN = DashboardDocs.doc("dash-tab-nested-column", "Nested Column", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.tabs(0, "left"),
                    DashboardDocs.split(1,
                            DashboardDocs.tabs(0, "topRight"),
                            DashboardDocs.tabs(0, "bottomRight"))),
            DashboardDocs.textInput("left", "Left", "left"),
            DashboardDocs.textInput("topRight", "Top Right", "topRight"),
            DashboardDocs.textInput("bottomRight", "Bottom Right", "bottomRight"));

    // A row of two above a panel: (Top Left | Top Right) above Bottom
    private static final String NESTED_ROW = DashboardDocs.doc("dash-tab-nested-row", "Nested Row", null,
            "\"designMode\": false",
            DashboardDocs.split(1,
                    DashboardDocs.split(0,
                            DashboardDocs.tabs(0, "topLeft"),
                            DashboardDocs.tabs(0, "topRight")),
                    DashboardDocs.tabs(0, "bottom")),
            DashboardDocs.textInput("topLeft", "Top Left", "topLeft"),
            DashboardDocs.textInput("topRight", "Top Right", "topRight"),
            DashboardDocs.textInput("bottom", "Bottom", "bottom"));

    // Three levels of splits, across, down and across again:
    // Panel 1 | ((Panel 2 | Panel 3) above Panel 4) | Panel 5
    private static final String DEEPLY_NESTED = DashboardDocs.doc("dash-tab-deep", "Deeply Nested", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.tabs(0, "p1"),
                    // Wide enough for Panel 2 and Panel 3 side by side
                    DashboardDocs.sized(DashboardDocs.split(1,
                            DashboardDocs.sized(DashboardDocs.split(0,
                                    DashboardDocs.sized(DashboardDocs.tabs(0, "p2"), 250, 100),
                                    DashboardDocs.sized(DashboardDocs.tabs(0, "p3"), 250, 100)), 500, 100),
                            DashboardDocs.sized(DashboardDocs.tabs(0, "p4"), 500, 100)), 500, 200),
                    DashboardDocs.tabs(0, "p5")),
            DashboardDocs.textInput("p1", "Panel 1", "p1"),
            DashboardDocs.textInput("p2", "Panel 2", "p2"),
            DashboardDocs.textInput("p3", "Panel 3", "p3"),
            DashboardDocs.textInput("p4", "Panel 4", "p4"),
            DashboardDocs.textInput("p5", "Panel 5", "p5"));

    // A panel of three tabs (Y selected) beside a panel of one: (X, Y, Z) | W
    private static final String TABBED = DashboardDocs.doc("dash-tab-tabbed", "Tabbed", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.tabs(1, "x", "y", "z"),
                    DashboardDocs.tabs(0, "w")),
            DashboardDocs.textInput("x", "X", "x"),
            DashboardDocs.textInput("y", "Y", "y"),
            DashboardDocs.textInput("z", "Z", "z"),
            DashboardDocs.textInput("w", "W", "w"));

    // Two panels side by side, the first with a second tab to drag out: (A, Moved) | B
    private static final String TO_REARRANGE = DashboardDocs.doc("dash-tab-rearrange", "Rearranged", null,
            "\"designMode\": false",
            DashboardDocs.split(0,
                    DashboardDocs.tabs(0, "a", "moved"),
                    DashboardDocs.tabs(0, "b")),
            DashboardDocs.textInput("a", "A", "a"),
            DashboardDocs.textInput("moved", "Moved", "moved"),
            DashboardDocs.textInput("b", "B", "b"));

    // A splitter's keys (FlexLayout's KEY_STEP and BIG_KEY_STEP)
    private static final int KEY_STEP = 10;
    private static final int BIG_KEY_STEP = 50;

    private DashboardKeyboardStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component(DashboardSupport.TITLE, DashboardKeyboardStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Panels side by side are visited left to right, a splitter between each two
                .story("TabOrderSideBySide", DashboardSupport.story(SIDE_BY_SIDE, routes -> {
                }))
                .withPlay(play -> expectTabOrder(play, "A", List.of(
                        "tab A", "A panel",
                        "vertical splitter",
                        "tab B", "B panel",
                        "vertical splitter",
                        "tab C", "C panel")))
                // Stacked panels are visited top to bottom, a splitter between each two
                .story("TabOrderStacked", DashboardSupport.story(STACKED, routes -> {
                }))
                .withPlay(play -> expectTabOrder(play, "D", List.of(
                        "tab D", "D panel",
                        "horizontal splitter",
                        "tab E", "E panel",
                        "horizontal splitter",
                        "tab F", "F panel")))
                // A panel beside a column: the panel, the splitter between it and the column, then
                // the column top to bottom, with its own splitter between its two panels
                .story("TabOrderNestedColumn", DashboardSupport.story(NESTED_COLUMN, routes -> {
                }))
                .withPlay(play -> expectTabOrder(play, "Left", List.of(
                        "tab Left", "Left panel",
                        "vertical splitter",
                        "tab Top Right", "Top Right panel",
                        "horizontal splitter",
                        "tab Bottom Right", "Bottom Right panel")))
                // A row above a panel: the row left to right, with its own splitter between its two
                // panels, then the splitter between the row and the panel, then the panel
                .story("TabOrderNestedRow", DashboardSupport.story(NESTED_ROW, routes -> {
                }))
                .withPlay(play -> expectTabOrder(play, "Top Left", List.of(
                        "tab Top Left", "Top Left panel",
                        "vertical splitter",
                        "tab Top Right", "Top Right panel",
                        "horizontal splitter",
                        "tab Bottom", "Bottom panel")))
                // Three levels of splits: depth first, each splitter just after all it resizes on its
                // left or above (the splitter right of the middle column comes after Panel 4)
                .story("TabOrderDeeplyNested", DashboardSupport.story(DEEPLY_NESTED, routes -> {
                }))
                .withPlay(play -> expectTabOrder(play, "Panel 1", List.of(
                        "tab Panel 1", "Panel 1 panel",
                        "vertical splitter",
                        "tab Panel 2", "Panel 2 panel",
                        "vertical splitter",
                        "tab Panel 3", "Panel 3 panel",
                        "horizontal splitter",
                        "tab Panel 4", "Panel 4 panel",
                        "vertical splitter",
                        "tab Panel 5", "Panel 5 panel")))
                // A panel of several tabs is one stop, its selected tab (the arrow keys move between
                // its tabs), then its selected tab's content, then the splitter and the next panel
                .story("TabOrderTabbed", DashboardSupport.story(TABBED, routes -> {
                }))
                .withPlay(play -> expectTabOrder(play, "Y", List.of(
                        "tab Y", "Y panel",
                        "vertical splitter",
                        "tab W", "W panel")))
                // A panel made by docking a tab onto the left edge of the first panel is first on
                // screen, so first in the tab order too, though it is the newest panel
                .story("TabOrderAfterDockingLeft", DashboardSupport.story(TO_REARRANGE, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    DashboardPlays.designMode(play, true);
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(2));
                    // Just inside the first panel's left edge, which docks to its left
                    DashboardPlays.drag(play, DashboardPlays.tab(play, "Moved"),
                            play.querySelectorAll(DashboardPlays.PANEL).nth(0), 0, 0.5, 12, 0);
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(3));
                    // The new panel is left of the panel it was docked onto
                    final Query moved = DashboardPlays.tab(play, "Moved").closest(DashboardPlays.PANEL);
                    final Query first = DashboardPlays.tab(play, "A").closest(DashboardPlays.PANEL);
                    play.expect("Moved's panel left of A's",
                            () -> moved.left().get() < first.left().get()).toBe(true);
                    DashboardPlays.designMode(play, false);
                    expectTabOrder(play, "Moved", List.of(
                            "tab Moved", "Moved panel",
                            "vertical splitter",
                            "tab A", "A panel",
                            "vertical splitter",
                            "tab B", "B panel"));
                })
                // A splitter between panels side by side: named by the panels either side, moved by
                // the left and right arrows (further with Shift), and by Home and End as far as it can
                // go, keeping the focus, its value following it
                .story("SplitterKeyboardResize", DashboardSupport.story(SIDE_BY_SIDE, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Query splitter = play.getByRole("separator", "Resize A and B");
                    final Query panelA = DashboardPlays.tab(play, "A").closest(DashboardPlays.PANEL);
                    play.expect(splitter).toHaveAttribute("aria-orientation", "vertical");
                    final Value<Double> start = play.capture("A's width", panelA.width());
                    final Value<String> startValue = play.capture("the splitter's value",
                            splitter.attribute("aria-valuenow"));
                    play.run("focus the splitter", () -> splitter.element().get().focus());

                    play.keyboard("{ArrowRight}");
                    expectWidthChange(play, panelA, start, KEY_STEP);
                    play.expect(splitter).toHaveFocus();
                    play.expect("the splitter's value after it moved right",
                            () -> splitter.attribute("aria-valuenow").get().equals(startValue.get())).toBe(false);

                    play.keyboard("{Shift>}{ArrowRight}{/Shift}");
                    expectWidthChange(play, panelA, start, KEY_STEP + BIG_KEY_STEP);
                    play.keyboard("{ArrowLeft}");
                    expectWidthChange(play, panelA, start, BIG_KEY_STEP);
                    // The up and down arrows don't move a splitter between panels side by side
                    play.keyboard("{ArrowDown}");
                    expectWidthChange(play, panelA, start, BIG_KEY_STEP);

                    play.keyboard("{Home}");
                    play.waitFor(() -> play.expect("A's width at the least",
                            () -> panelA.width().get() < start.get()).toBe(true));
                    play.keyboard("{End}");
                    play.waitFor(() -> play.expect("A's width at the most",
                            () -> panelA.width().get() > start.get() + BIG_KEY_STEP).toBe(true));
                    play.expect(splitter).toHaveFocus();
                    DashboardPlays.expectNoProblems(play);
                })
                // A splitter between stacked panels is moved by the up and down arrows, not by the
                // left and right ones
                .story("SplitterKeyboardResizeStacked", DashboardSupport.story(STACKED, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Query splitter = play.getByRole("separator", "Resize D and E");
                    final Query panelD = DashboardPlays.tab(play, "D").closest(DashboardPlays.PANEL);
                    play.expect(splitter).toHaveAttribute("aria-orientation", "horizontal");
                    final Value<Double> start = play.capture("D's height", panelD.height());
                    play.run("focus the splitter", () -> splitter.element().get().focus());
                    play.keyboard("{ArrowRight}");
                    play.keyboard("{ArrowDown}");
                    play.waitFor(() -> play.expect("how much D's height changed",
                            () -> (int) Math.round(panelD.height().get() - start.get())).toBe(KEY_STEP));
                    play.keyboard("{ArrowUp}");
                    play.waitFor(() -> play.expect("how much D's height changed",
                            () -> (int) Math.round(panelD.height().get() - start.get())).toBe(0));
                    DashboardPlays.expectNoProblems(play);
                })
                // Maximising a panel from its menu, and restoring it, rebuilds the layout, which removes
                // the tab the menu gives the focus back to; the focus goes to the panel's tab instead
                // of being lost
                .story("FocusKeptWhenMaximised", DashboardSupport.story(SIDE_BY_SIDE, routes -> {
                }))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    DashboardPlays.opened(play);
                    openPanelMenu(play, "B");
                    play.click(DashboardPlays.menuItem(screen, "Maximise"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(1));
                    play.waitFor(() -> play.expect(currentTab(play, "B")).toHaveFocus());

                    openPanelMenu(play, "B");
                    play.click(DashboardPlays.menuItem(screen, "Restore Pane Layout"));
                    play.waitFor(() -> play.expect(play.querySelectorAll(DashboardPlays.PANEL)).toHaveLength(3));
                    play.waitFor(() -> play.expect(currentTab(play, "B")).toHaveFocus());
                    DashboardPlays.expectNoProblems(play);
                })
                // A splitter with the keyboard focus: its line and handle in the focus colour, ringed
                .story("SplitterFocused", DashboardSupport.story(SIDE_BY_SIDE, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Query splitter = play.getByRole("separator", "Resize A and B");
                    // Back from the tab after it, so the focus is the keyboard's (:focus-visible)
                    final Query tabB = DashboardPlays.linkTab(play, "B");
                    play.run("focus the tab B", () -> tabB.element().get().focus());
                    play.tab(true);
                    play.expect(splitter).toHaveFocus();
                })
                // A splitter being dragged: the marker that follows the mouse is the line with its
                // handle, lighter, until the panels are resized as it is released (the story leaves
                // the mouse down)
                .story("SplitterDragging", DashboardSupport.story(SIDE_BY_SIDE, routes -> {
                }))
                .withPlay(play -> {
                    DashboardPlays.opened(play);
                    final Query splitter = play.getByRole("separator", "Resize A and B");
                    final Query panelA = DashboardPlays.tab(play, "A").closest(DashboardPlays.PANEL);
                    final Value<Double> start = play.capture("A's width", panelA.width());
                    DashboardPlays.press(play, splitter, 60, 0);
                    // The marker is on the page's body, above the panels (and any visualisation)
                    play.expect(play.screen().querySelector(".flex-layout__marker--splitter"))
                            .toBeInTheDocument();
                    play.expect("A's width while the splitter is dragged",
                            () -> (int) Math.round(panelA.width().get() - start.get())).toBe(0);
                });
    }

    // Opens the dashboard, focuses the tab of the panel given, and expects Tab to visit the stops
    // given in order. Consecutive stops in the same panel (e.g. its text box, and the text boxes of
    // its other tabs, which Tab passes as user-event's does) are one stop
    private static void expectTabOrder(final Play play, final String firstTab, final List<String> expected) {
        DashboardPlays.opened(play);
        for (final String stop : expected) {
            if (stop.startsWith("tab ")) {
                DashboardPlays.tab(play, stop.substring("tab ".length()));
            }
        }
        final List<String> stops = new ArrayList<>();
        final Query first = DashboardPlays.linkTab(play, firstTab);
        play.run("focus the tab " + firstTab, () -> first.element().get().focus());
        play.run("note the focus", () -> stops.add(focusStop()));
        for (int i = 0; i < TAB_PRESSES; i++) {
            play.tab();
            play.run("note the focus", () -> stops.add(focusStop()));
        }
        play.expect("the stops Tab visits, in order", () -> distinctRun(stops, expected.size()))
                .toEqual(expected);
        DashboardPlays.expectNoProblems(play);
    }

    // Opens the menu of the panel whose tab is given with the keyboard: Enter on the panel's menu
    // button (⋮)
    private static void openPanelMenu(final Play play, final String tab) {
        final Query button = play.within(DashboardPlays.tab(play, tab).closest(DashboardPlays.PANEL))
                .querySelector(".tab-layout__settings-button");
        play.run("focus the menu button of " + tab + "'s panel", () -> button.element().get().focus());
        play.keyboard("{Enter}");
        DashboardPlays.menuItem(play.screen(), "Rename");
    }

    // A component's tab, found again each time it is used (as the layout is rebuilt, its tabs are
    // replaced)
    private static Query currentTab(final Play play, final String name) {
        return play.getByText(name, DashboardPlays.COMPONENT_TAB).closest(".tab-bar__tab");
    }

    // Expects a panel's width to be what it was plus the change given
    private static void expectWidthChange(final Play play,
                                          final Query panel,
                                          final Value<Double> start,
                                          final int change) {
        play.waitFor(() -> play.expect("how much the panel's width changed",
                () -> (int) Math.round(panel.width().get() - start.get())).toBe(change));
    }

    // The first stops of a walk, with each run of the same stop counted once
    private static List<String> distinctRun(final List<String> stops, final int count) {
        final List<String> distinct = new ArrayList<>();
        for (final String stop : stops) {
            if (distinct.size() == count) {
                break;
            }
            if (distinct.isEmpty() || !distinct.get(distinct.size() - 1).equals(stop)) {
                distinct.add(stop);
            }
        }
        return distinct;
    }

    // The stop that has the focus: a splitter (by the way its line runs), a panel's tab, anything
    // else in a panel (named by the panel's selected tab), or something outside the panels
    private static native String focusStop() /*-{
        var el = $doc.activeElement;
        if (!el || el === $doc.body) {
            return 'nothing';
        }
        if (el.getAttribute('role') === 'separator') {
            return (el.getAttribute('aria-orientation') || 'unoriented') + ' splitter';
        }
        var panel = el.closest('.tab-layout');
        if (!panel) {
            return 'outside the panels';
        }
        var tab = el.closest('.tab-bar__tab');
        if (tab) {
            var label = tab.querySelector('.tab-bar__label');
            return 'tab ' + (label ? label.textContent.trim() : '?');
        }
        var selected = panel.querySelector('.tab-bar__tab[aria-selected="true"] .tab-bar__label');
        return (selected ? selected.textContent.trim() : '?') + ' panel';
    }-*/;
}
