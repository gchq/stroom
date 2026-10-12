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

import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// Stories for what a [SplitPane] does when it is resized (e.g. with the window), beside what
/// Stroom's old split panel ([ThinSplitLayoutPanel]) did, set up alike.
///
/// Each story shows the old panel (panes `old-a` and `old-b`) above the split pane (`new-a` and
/// `new-b`), in frames that buttons resize (as a window would), and the plays measure the panes at
/// each size. The old panel kept a fixed pane at its size however small the frame became, so the
/// pane beside it was squeezed to nothing, and it only applied a minimum size to drags. The split
/// pane shrinks the pane taking the rest first, down to its minimum, then the others, down to
/// theirs, and each grows back to its size as the frame widens.
public final class SplitPaneResizingStories {

    private SplitPaneResizingStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Layout/SplitPaneResizing", SplitPaneResizingStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // The first pane fixed (<g:west size="400">, most of Stroom's split screens):
                // the second takes the rest
                .story("FirstFixed", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    old.addWest(SplitPaneContent.block("A", "old-a", true), 400);
                    old.add(SplitPaneContent.block("B", "old-b", false));
                    return resizable(old, thin("400px, *", ""), false, 1000, 600, 350);
                })
                .withPlay(play -> {
                    expectSizes(play, false, 400, 597, 400, 597);
                    resizeTo(play, 600);
                    expectSizes(play, false, 400, 197, 400, 197);
                    // Narrower than the fixed pane: the old one overflows the frame, the new one fits
                    resizeTo(play, 350);
                    expectSizes(play, false, 400, 0, 347, 0);
                    // Wider again: each is back to its size
                    resizeTo(play, 1000);
                    expectSizes(play, false, 400, 597, 400, 597);
                })
                // The second pane fixed (<g:east size="400">): the first takes the rest
                .story("SecondFixed", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    old.addEast(SplitPaneContent.block("B", "old-b", false), 400);
                    old.add(SplitPaneContent.block("A", "old-a", true));
                    return resizable(old, thin("*, 400px", ""), false, 1000, 600, 350);
                })
                .withPlay(play -> {
                    expectSizes(play, false, 597, 400, 597, 400);
                    resizeTo(play, 600);
                    expectSizes(play, false, 197, 400, 197, 400);
                    resizeTo(play, 350);
                    expectSizes(play, false, 0, 400, 0, 347);
                })
                // The first pane a share (hSplits="0.3"): both change. The old panel takes 2px
                // off the share, and gives them to the other pane
                .story("Proportional", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    old.setHSplits(0.3);
                    old.addWest(SplitPaneContent.block("A", "old-a", true), 300);
                    old.add(SplitPaneContent.block("B", "old-b", false));
                    return resizable(old, thin("30%, *", ""), false, 1000, 600, 350);
                })
                .withPlay(play -> {
                    expectSizes(play, false, 297.4, 699.6, 299.4, 697.6);
                    resizeTo(play, 600);
                    expectSizes(play, false, 177.4, 419.6, 179.4, 417.6);
                })
                // Minimum sizes. The old panel could only give the fixed pane one, and only kept
                // to it in a drag; the split pane keeps the rest's (200px) as the frame narrows,
                // shrinking the fixed pane instead, until it is at its own (150px)
                .story("WithMinimums", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    final Widget oldFixed = SplitPaneContent.block("A", "old-a", true);
                    old.addWest(oldFixed, 400);
                    old.add(SplitPaneContent.block("B", "old-b", false));
                    old.setWidgetMinSize(oldFixed, 150);
                    return resizable(old, thin("400px, *", "150px, 200px"), false, 1000, 450, 300);
                })
                .withPlay(play -> {
                    expectSizes(play, false, 400, 597, 400, 597);
                    resizeTo(play, 450);
                    expectSizes(play, false, 400, 47, 247, 200);
                    // Smaller than both minimums: they are kept, and the frame cuts off the rest
                    resizeTo(play, 300);
                    expectSizes(play, false, 400, 0, 150, 200);
                    resizeTo(play, 1000);
                    expectSizes(play, false, 400, 597, 400, 597);
                })
                // Stacked, the second pane fixed (<g:south size="120">, e.g. query history), with
                // minimums on the split pane
                .story("StackedSecondFixed", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    old.addSouth(SplitPaneContent.block("B", "old-b", false), 120);
                    old.add(SplitPaneContent.block("A", "old-a", true));
                    final SplitPane pane = thin("*, 120px", "60px, 40px");
                    pane.setVertical(true);
                    return resizable(old, pane, true, 300, 200, 120);
                })
                .withPlay(play -> {
                    expectSizes(play, true, 177, 120, 177, 120);
                    resizeTo(play, 200);
                    expectSizes(play, true, 77, 120, 77, 120);
                    resizeTo(play, 120);
                    expectSizes(play, true, 0, 120, 60, 57);
                });
    }

    // --------------------------------------------------------------------------------

    private static SplitPane thin(final String sizes, final String minSizes) {
        final SplitPane pane = new SplitPane();
        pane.setThin(true);
        pane.setSizes(sizes);
        pane.setMinSizes(minSizes);
        pane.setNames("A, B");
        return pane;
    }

    // The old panel and the split pane (given panes A and B here) in frames that start at the
    // first size, with a button for each size that resizes both frames, as a window would, and
    // tells the old panel, as Stroom's panels do
    private static Widget resizable(final DockLayoutPanel old,
                                    final SplitPane pane,
                                    final boolean vertical,
                                    final int... sizes) {
        pane.add(SplitPaneContent.block("A", "new-a", true));
        pane.add(SplitPaneContent.block("B", "new-b", false));
        final List<FlowPanel> frames = new ArrayList<>();
        final FlowPanel page = new FlowPanel();
        page.getElement().getStyle().setProperty("padding", "8px");
        final FlowPanel buttons = new FlowPanel();
        page.add(buttons);
        page.add(section("ThinSplitLayoutPanel (old)", frame(old, vertical, sizes[0], frames)));
        page.add(section("SplitPane (new)", frame(pane, vertical, sizes[0], frames)));
        for (final int size : sizes) {
            buttons.add(new Button(size + "px", (ClickHandler) event -> {
                for (final FlowPanel frame : frames) {
                    frame.getElement().getStyle().setProperty(vertical
                            ? "height"
                            : "width", size + "px");
                }
                old.onResize();
            }));
        }
        return page;
    }

    private static FlowPanel frame(final Widget split,
                                   final boolean vertical,
                                   final int size,
                                   final List<FlowPanel> frames) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("border", "1px solid var(--border-color,#444)");
        style.setProperty("boxSizing", "border-box");
        style.setProperty("position", "relative");
        style.setProperty("overflow", "hidden");
        style.setProperty("width", vertical
                ? "600px"
                : size + "px");
        style.setProperty("height", vertical
                ? size + "px"
                : "120px");
        split.setSize("100%", "100%");
        frame.add(split);
        frames.add(frame);
        return frame;
    }

    private static Widget section(final String label, final Widget frame) {
        final FlowPanel section = new FlowPanel();
        final HTML heading = new HTML(label);
        heading.getElement().getStyle().setProperty("margin", "8px 0 4px");
        heading.getElement().getStyle().setProperty("fontSize", "0.8rem");
        section.add(heading);
        section.add(frame);
        return section;
    }

    private static void resizeTo(final Play play, final int size) {
        play.click(play.getByRole("button", size + "px"));
    }

    // The panes' widths (or heights, when stacked), to the nearest pixel
    private static void expectSizes(final Play play,
                                    final boolean vertical,
                                    final double oldA,
                                    final double oldB,
                                    final double newA,
                                    final double newB) {
        expectSize(play, "old-a", vertical, oldA);
        expectSize(play, "old-b", vertical, oldB);
        expectSize(play, "new-a", vertical, newA);
        expectSize(play, "new-b", vertical, newB);
    }

    private static void expectSize(final Play play, final String testId, final boolean vertical, final double size) {
        final Query pane = play.getByTestId(testId);
        // The old panel lays out proportional panes after a resize (deferred)
        play.waitFor(() -> play.expect(vertical
                ? pane.height()
                : pane.width()).toBeCloseTo(size, 0));
    }
}
