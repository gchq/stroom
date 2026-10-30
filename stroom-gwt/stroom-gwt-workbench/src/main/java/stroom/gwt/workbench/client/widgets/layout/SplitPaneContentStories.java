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
import stroom.gwt.workbench.client.a11y.TargetSpacing;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.button.client.Button;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for a [SplitPane] holding what Stroom's split screens hold: Stroom's data grid and code
/// editor, forms, and the stepping screen's layout of four panes; and for what happens to a pane
/// collapsed to nothing, double clicking a splitter, and the call that says the user has resized
/// a pane.
public final class SplitPaneContentStories {

    private static final String MENU_ITEM_TEXT = ".menu__text";

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
                    // A splitter is narrower than 24px, so nothing else clickable may be within 12px
                    // of its centre (WCAG 2.5.8): the pager's buttons and the editor's gutter aren't.
                    // The grid's rows are, which is accepted (full-width targets; see ISSUES.md)
                    play.expect(TargetSpacing.crowding(splitter, grid)).toEqual(List.of());
                })
                // A regular splitter (a 7px gap) beside Stroom's table and code editor (the real
                // input check presses it with the mouse)
                .story("RegularTableAndEditor", context -> {
                    final SplitPane pane = thin("420px, *", "Table, Editor");
                    pane.setThin(false);
                    pane.add(SplitPaneContent.table("grid"));
                    pane.add(SplitPaneContent.editor());
                    return frame(pane, "250px");
                })
                .withPlay(play -> {
                    play.findByText("Person 1");
                    play.expect(play.getByTestId("grid").width()).toBe(420.0);
                })
                // The stepping screen's element layout (SteppingElement): code above, log below,
                // and input beside output in between, as three nested split panes
                .story("SteppingLayout", context -> frame(stepping(), "320px"))
                .withPlay(play -> {
                    final Value<Double> height = play.getByTestId("stepping").height();
                    final Value<Double> width = play.getByTestId("stepping").width();
                    final Value<Double> code = play.getByTestId("code").height();
                    final Value<Double> log = play.getByTestId("log").height();
                    final Value<Double> input = play.getByTestId("input").width();
                    play.expect("code's share less 50%",
                            () -> Math.abs(code.get() - height.get() * 0.5)).toBeLessThanOrEqual(1);
                    play.expect("the log's share less 15%",
                            () -> Math.abs(log.get() - height.get() * 0.15)).toBeLessThanOrEqual(1);
                    play.expect("input's share less 50%",
                            () -> Math.abs(input.get() - width.get() * 0.5)).toBeLessThanOrEqual(1);
                    // Reading order: code, input, output, then log
                    play.expect(play.getByTestId("stepping")).toHaveTextContent(
                            TextMatch.regex("Code.*Input.*Output.*Log"));
                    // Where the Input splitter meets the Code splitter in a T, at the middle of
                    // the Code splitter (where its handle is), the Code splitter is on top
                    final Value<Element> codeSplitter = play.getByRole("separator", "Code").element();
                    play.expect("the splitter at the middle of the Code splitter",
                                    () -> labelAtCentre(codeSplitter.get()))
                            .toBe("Code");
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
                // Alt+M maximises the pane holding the focus (the others shrink to nothing and are
                // inert) and restores it, whichever pane it is
                .story("MaximiseKey", context -> {
                    final SplitPane pane = thin("300px, *", "Properties, Content");
                    pane.add(SplitPaneContent.form("properties"));
                    pane.add(SplitPaneContent.form("content"));
                    return frame(pane, "240px");
                })
                .withPlay(play -> {
                    final Query properties = play.getByTestId("properties");
                    final Query content = play.getByTestId("content");
                    final Value<Integer> fullWidth = paneWidth(content);
                    final Value<Integer> both = play.capture("both panes' width",
                            () -> paneWidth(properties).get() + 1 + fullWidth.get());
                    // In the second pane, the one taking the rest
                    play.click(play.getAllByLabelText("Name").nth(1));
                    play.keyboard("{Alt>}m{/Alt}");
                    play.expect(paneWidth(properties)).toBe(0);
                    play.expect("the content pane's width, less the splitter",
                            () -> fullWidth.get() + 1 - both.get()).toBe(0);
                    play.expect(play.getAllByLabelText("Name").nth(1)).toHaveFocus();
                    play.expect(TabStops.names(play.body())).toEqual(List.of("Properties", "Name", "Description"));
                    play.keyboard("{Alt>}m{/Alt}");
                    play.expect(paneWidth(properties)).toBe(300);
                    // In the first pane
                    play.click(play.getAllByLabelText("Name").nth(0));
                    play.keyboard("{Alt>}m{/Alt}");
                    play.expect(paneWidth(content)).toBe(0);
                    play.keyboard("{Alt>}m{/Alt}");
                    play.expect(paneWidth(properties)).toBe(300);
                })
                // In split panes nested in one another, Alt+M maximises the pane in the split pane
                // nearest the focus, leaving the outer one as it is
                .story("MaximiseNested", context -> {
                    final SplitPane inner = thin("50%, *", "Left, Right");
                    inner.add(SplitPaneContent.form("left"));
                    inner.add(SplitPaneContent.form("right"));
                    final SplitPane outer = thin("100px, *", "Top, Bottom");
                    outer.setVertical(true);
                    outer.add(SplitPaneContent.form("top"));
                    outer.add(inner);
                    return frame(outer, "320px");
                })
                .withPlay(play -> {
                    play.click(play.getAllByLabelText("Name").nth(1));
                    play.keyboard("{Alt>}m{/Alt}");
                    play.expect(paneWidth(play.getByTestId("right"))).toBe(0);
                    play.expect(paneHeight(play.getByTestId("top"))).toBe(100);
                    play.keyboard("{Alt>}m{/Alt}");
                    play.expect(paneWidth(play.getByTestId("right"))).toBeGreaterThan(0);
                })
                // A splitter's menu (right click, long press or the context menu key) maximises
                // the pane either side of it, or restores them, so a pane can be resized without
                // dragging (WCAG 2.5.7); Alt+M is hinted on the item it would do
                .story("SplitterMenu", context -> {
                    StoryPopups.create(context).withPageRequests();
                    final SplitPane pane = thin("300px, *", "Properties, Content");
                    pane.add(SplitPaneContent.block("Properties", "properties", true));
                    pane.add(SplitPaneContent.block("Content", "content", false));
                    return frame(pane, "240px");
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query splitter = play.getByRole("separator", "Properties");
                    play.rightClick(splitter);
                    final Query maximiseProperties = screen.findByText("Maximise Properties", MENU_ITEM_TEXT);
                    play.expect(screen.getByText("Maximise Content", MENU_ITEM_TEXT)).toBeInTheDocument();
                    // From the splitter, Alt+M maximises the pane it resizes
                    play.expect(menuItem(screen, "Maximise Properties"))
                            .toHaveTextContent(TextMatch.containing("Alt+M"));
                    play.expect(menuItem(screen, "Maximise Content"))
                            .not().toHaveTextContent(TextMatch.containing("Alt"));
                    play.expect(menuItem(screen, "Restore Pane Sizes")).toHaveAttribute("aria-disabled", "true");
                    play.click(screen.getByText("Maximise Content", MENU_ITEM_TEXT));
                    play.expect(paneWidth(play.getByTestId("properties"))).toBe(0);
                    play.rightClick(splitter);
                    play.expect(menuItem(screen, "Restore Pane Sizes")).not().toHaveAttribute("aria-disabled");
                    play.expect(menuItem(screen, "Maximise Content")).toHaveAttribute("aria-disabled", "true");
                    play.click(screen.getByText("Restore Pane Sizes", MENU_ITEM_TEXT));
                    play.expect(paneWidth(play.getByTestId("properties"))).toBe(300);
                    play.rightClick(splitter);
                    play.click(maximiseProperties);
                    play.expect(paneWidth(play.getByTestId("content"))).toBe(0);
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
                    final Button setInCode = new Button("Set in code");
                    setInCode.setOnClick(() -> pane.setPaneSize(explorer, "200px"));
                    page.add(setInCode);
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

    // A menu item (its outer element), by its text
    private static Query menuItem(final Play screen, final String text) {
        return screen.getByText(text, MENU_ITEM_TEXT).closest(":is([role='menuitem'], [role='menuitemcheckbox'])");
    }

    // The height of the pane a widget fills
    private static Value<Integer> paneHeight(final Query widget) {
        final Value<Element> element = widget.element();
        return Value.of(widget.describe() + "'s pane height", () -> element.get().getParentElement().getOffsetHeight());
    }

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

    // SteppingElement's layout: code, the middle and the log stacked, and the middle input beside
    // output
    // The name of the splitter the pointer would find at the centre of an element
    private static native String labelAtCentre(Element element) /*-{
        var box = element.getBoundingClientRect();
        var found = $doc.elementFromPoint(box.left + box.width / 2, box.top + box.height / 2);
        var splitter = found && found.closest('.split-pane__splitter');
        return splitter ? splitter.getAttribute('aria-label') : null;
    }-*/;

    private static Widget stepping() {
        final SplitPane middle = thin("50%, *", "Input, Output");
        middle.add(SplitPaneContent.block("Input", "input", true));
        middle.add(SplitPaneContent.block("Output", "output", false));
        final SplitPane stack = thin("50%, *, 15%", "Code, Middle, Log");
        stack.setVertical(true);
        stack.add(SplitPaneContent.block("Code", "code", true));
        stack.add(middle);
        stack.add(SplitPaneContent.block("Log", "log", false));
        stack.getElement().setAttribute("data-testid", "stepping");
        return stack;
    }
}
