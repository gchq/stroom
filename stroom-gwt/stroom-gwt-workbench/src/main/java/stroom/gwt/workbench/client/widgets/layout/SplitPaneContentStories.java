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

package stroom.gwt.workbench.client.widgets.layout;

import stroom.gwt.workbench.client.a11y.TabStops;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for a [SplitPane] holding what Stroom's split screens hold: Stroom's data grid and code
/// editor, forms, and the stepping screen's layout of four panes; and for what happens to a pane
/// collapsed to nothing, double clicking a splitter, and the call that says the user has resized
/// a pane.
public final class SplitPaneContentStories {

    // Spy names
    private static final String ON_RESIZED = "onResized";

    private SplitPaneContentStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Layout/SplitPaneContent", SplitPaneContentStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A paged table beside a code editor: moving the splitter resizes both, and each
                // lays itself out again (the table's columns and the editor's text area follow)
                .story("TableAndEditor", context -> {
                    final SplitPane pane = thin("40%, *", "Table, Editor");
                    pane.add(SplitPaneContent.table("grid"));
                    pane.add(SplitPaneContent.editor());
                    return frame(pane, "420px");
                })
                .withPlay(play -> {
                    play.findByText("Person 1");
                    play.waitFor(() -> play.expect(play.querySelector(".ace_content")).toBeInTheDocument());
                    final Query grid = play.getByTestId("grid");
                    final Query editorArea = play.querySelector(".ace_scroller");
                    final Value<Double> gridBefore = play.capture("table width", grid.width()::get);
                    final Value<Double> editorBefore = play.capture("editor width", editorArea.width()::get);
                    final Query splitter = play.getByRole("separator", "Table");
                    play.click(splitter);
                    play.expect(splitter).toHaveFocus();
                    play.keyboard("{Shift>}{ArrowRight}{/Shift}");
                    // Each is told of its pane's new size (by a resize observer, so not at once)
                    final Value<Double> gridWidth = grid.width();
                    final Value<Double> editorWidth = editorArea.width();
                    play.waitFor(() -> play.expect("the table's growth",
                            () -> gridWidth.get() - gridBefore.get()).toBeCloseTo(50, 0));
                    play.waitFor(() -> play.expect("the editor's growth",
                            () -> editorWidth.get() - editorBefore.get()).toBeCloseTo(-50, 0));
                })
                // The stepping screen's element layout (ElementViewImpl), which put four panes in
                // one old panel (code above, log below, input beside output in between): three
                // nested split panes, side by side with the old panel, the same sizes
                .story("SteppingLayout", context -> {
                    final FlowPanel page = new FlowPanel();
                    page.add(frame(oldStepping(), "320px"));
                    page.add(frame(newStepping(), "320px"));
                    return page;
                })
                .withPlay(play -> {
                    for (final String name : List.of("code", "input", "output", "log")) {
                        final Query oldPane = play.getByTestId("old-" + name);
                        final Query newPane = play.getByTestId("new-" + name);
                        final Value<Double> oldWidth = oldPane.width();
                        final Value<Double> newWidth = newPane.width();
                        final Value<Double> oldHeight = oldPane.height();
                        final Value<Double> newHeight = newPane.height();
                        // Within 3px: the old panel takes 2px off each share
                        play.waitFor(() -> play.expect(name + " width difference",
                                () -> Math.abs(oldWidth.get() - newWidth.get())).toBeLessThanOrEqual(3));
                        play.expect(name + " height difference",
                                () -> Math.abs(oldHeight.get() - newHeight.get())).toBeLessThanOrEqual(3);
                    }
                    // Reading order: code, input, output, then log
                    play.expect(play.getByTestId("new")).toHaveTextContent(
                            TextMatch.regex("Code.*Input.*Output.*Log"));
                })
                // A pane collapsed to nothing is inert, so its fields can't be reached unseen;
                // restored, they can again
                .story("CollapsedPaneIsInert", context -> {
                    final SplitPane pane = thin("300px, *", "Properties, Content");
                    pane.add(SplitPaneContent.form("properties"));
                    pane.add(SplitPaneContent.form("content"));
                    return frame(pane, "240px");
                })
                .withPlay(play -> {
                    final Query splitter = play.getByRole("separator", "Properties");
                    play.expect(TabStops.names(play.body())).toEqual(List.of(
                            "Name", "Description", "Properties", "Name", "Description"));
                    play.click(splitter);
                    play.keyboard("{Enter}");
                    play.expect(paneWidth(play.getByTestId("properties"))).toBe(0);
                    play.expect(TabStops.names(play.body())).toEqual(List.of(
                            "Properties", "Name", "Description"));
                    play.keyboard("{Enter}");
                    play.expect(paneWidth(play.getByTestId("properties"))).toBe(300);
                    play.expect(TabStops.names(play.body())).toEqual(List.of(
                            "Name", "Description", "Properties", "Name", "Description"));
                })
                // Double clicking a splitter collapses and restores its pane, as Enter does, so a
                // pane can be resized without dragging (WCAG 2.5.7)
                .story("DoubleClick", context -> {
                    final SplitPane pane = thin("300px, *", "Properties, Content");
                    pane.add(SplitPaneContent.block("Properties", "properties", true));
                    pane.add(SplitPaneContent.block("Content", "content", false));
                    return frame(pane, "240px");
                })
                .withPlay(play -> {
                    final Query splitter = play.getByRole("separator", "Properties");
                    play.dblClick(splitter);
                    play.expect(play.getByTestId("properties").width()).toBe(0.0);
                    play.dblClick(splitter);
                    play.expect(play.getByTestId("properties").width()).toBe(300.0);
                })
                // The split pane says when the user has resized a pane (e.g. for the main screen
                // to save the explorer's width), but not when it is resized itself or in code
                .story("OnResized", context -> {
                    final SplitPane pane = thin("300px, *", "Explorer, Content");
                    final Widget explorer = SplitPaneContent.block("Explorer", "explorer", true);
                    pane.add(explorer);
                    pane.add(SplitPaneContent.block("Content", "content", false));
                    final Spy onResized = context.fn(ON_RESIZED);
                    pane.setOnResized(() -> onResized.call(pane.getPaneSize(explorer)));
                    final FlowPanel page = new FlowPanel();
                    page.add(new Button("Set in code", (ClickHandler) event -> pane.setPaneSize(explorer, "200px")));
                    page.add(frame(pane, "200px"));
                    return page;
                })
                .withPlay(play -> {
                    final Query splitter = play.getByRole("separator", "Explorer");
                    play.click(play.getByRole("button", "Set in code"));
                    play.expect(play.getByTestId("explorer").width()).toBe(200.0);
                    play.expect(play.spy(ON_RESIZED)).not().toHaveBeenCalled();
                    play.click(splitter);
                    play.keyboard("{ArrowRight}");
                    play.expect(play.spy(ON_RESIZED)).toHaveBeenLastCalledWith("210px");
                    play.dblClick(splitter);
                    play.expect(play.spy(ON_RESIZED)).toHaveBeenLastCalledWith("0px");
                });
    }

    // --------------------------------------------------------------------------------

    // The width of the pane a widget fills (the widget itself can be no narrower than its padding)
    private static Value<Integer> paneWidth(final Query widget) {
        final Value<Element> element = widget.element();
        return Value.of(widget.describe() + "'s pane width", () -> element.get().getParentElement().getOffsetWidth());
    }

    private static SplitPane thin(final String sizes, final String names) {
        final SplitPane pane = new SplitPane();
        pane.setThin(true);
        pane.setSizes(sizes);
        pane.setNames(names);
        return pane;
    }

    private static FlowPanel frame(final Widget split, final String height) {
        final FlowPanel frame = SplitPanes.frame(split);
        frame.getElement().getStyle().setProperty("height", height);
        frame.getElement().getStyle().setProperty("margin", "8px");
        return frame;
    }

    // ElementViewImpl's layout, in one old panel: code north 50%, log south 15%, input west 50%,
    // output in the centre
    private static Widget oldStepping() {
        final ThinSplitLayoutPanel layout = new ThinSplitLayoutPanel();
        layout.setHSplits(0.5);
        layout.setVSplits(0.5, 0.15);
        layout.addNorth(SplitPaneContent.block("Code", "old-code", true), 200);
        layout.addSouth(SplitPaneContent.block("Log", "old-log", false), 30);
        layout.addWest(SplitPaneContent.block("Input", "old-input", true), 200);
        layout.add(SplitPaneContent.block("Output", "old-output", false));
        return layout;
    }

    // The same layout as split panes: code, the middle and the log stacked, and the middle input
    // beside output
    private static Widget newStepping() {
        final SplitPane middle = thin("50%, *", "Input, Output");
        middle.add(SplitPaneContent.block("Input", "new-input", true));
        middle.add(SplitPaneContent.block("Output", "new-output", false));
        final SplitPane stack = thin("50%, *, 15%", "Code, Middle, Log");
        stack.setVertical(true);
        stack.add(SplitPaneContent.block("Code", "new-code", true));
        stack.add(middle);
        stack.add(SplitPaneContent.block("Log", "new-log", false));
        stack.getElement().setAttribute("data-testid", "new");
        return stack;
    }
}
