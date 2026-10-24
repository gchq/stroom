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
import stroom.widget.button.client.Button;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// Stories for what a [SplitPane] does when it is resized (e.g. with the window).
///
/// Each story shows a split pane (panes `a` and `b`) in a frame that buttons resize (as a window
/// would), and the plays measure the panes at each size. The split pane shrinks the pane taking
/// the rest first, down to its minimum, then the others, down to theirs, and each grows back to
/// its size as the frame widens. (Stroom's old split panels kept a fixed pane at its size however
/// small the frame became, squeezing the pane beside it to nothing, and only kept to a minimum
/// size in a drag.)
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
                // The first pane fixed (sizes="400px, *", most of Stroom's split screens): the
                // second takes the rest
                .story("FirstFixed", context -> resizable(thin("400px, *", ""), false, 1000, 600, 350))
                .withPlay(play -> {
                    expectSizes(play, false, 400, 597);
                    resizeTo(play, 600);
                    expectSizes(play, false, 400, 197);
                    // Narrower than the fixed pane: it shrinks to fit
                    resizeTo(play, 350);
                    expectSizes(play, false, 347, 0);
                    // Wider again: each is back to its size
                    resizeTo(play, 1000);
                    expectSizes(play, false, 400, 597);
                })
                // The second pane fixed (sizes="*, 400px"): the first takes the rest
                .story("SecondFixed", context -> resizable(thin("*, 400px", ""), false, 1000, 600, 350))
                .withPlay(play -> {
                    expectSizes(play, false, 597, 400);
                    resizeTo(play, 600);
                    expectSizes(play, false, 197, 400);
                    resizeTo(play, 350);
                    expectSizes(play, false, 0, 347);
                })
                // The first pane a share (sizes="30%, *"): both change
                .story("Proportional", context -> resizable(thin("30%, *", ""), false, 1000, 600, 350))
                .withPlay(play -> {
                    expectSizes(play, false, 299.4, 697.6);
                    resizeTo(play, 600);
                    expectSizes(play, false, 179.4, 417.6);
                })
                // Minimum sizes: the split pane keeps the rest's (200px) as the frame narrows,
                // shrinking the fixed pane instead, until it is at its own (150px)
                .story("WithMinimums", context -> resizable(thin("400px, *", "150px, 200px"), false,
                        1000, 450, 300))
                .withPlay(play -> {
                    expectSizes(play, false, 400, 597);
                    resizeTo(play, 450);
                    expectSizes(play, false, 247, 200);
                    // Smaller than both minimums: they are kept, and the frame cuts off the rest
                    resizeTo(play, 300);
                    expectSizes(play, false, 150, 200);
                    resizeTo(play, 1000);
                    expectSizes(play, false, 400, 597);
                })
                // Stacked, the second pane fixed (e.g. query history), with minimums
                .story("StackedSecondFixed", context -> {
                    final SplitPane pane = thin("*, 120px", "60px, 40px");
                    pane.setVertical(true);
                    return resizable(pane, true, 300, 200, 120);
                })
                .withPlay(play -> {
                    expectSizes(play, true, 177, 120);
                    resizeTo(play, 200);
                    expectSizes(play, true, 77, 120);
                    resizeTo(play, 120);
                    expectSizes(play, true, 60, 57);
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

    // The split pane (given panes A and B here) in a frame that starts at the first size, with a
    // button for each size that resizes the frame, as a window would
    private static Widget resizable(final SplitPane pane,
                                    final boolean vertical,
                                    final int... sizes) {
        pane.add(SplitPaneContent.block("A", "a", true));
        pane.add(SplitPaneContent.block("B", "b", false));
        final FlowPanel page = new FlowPanel();
        page.getElement().getStyle().setProperty("padding", "8px");
        final FlowPanel buttons = new FlowPanel();
        page.add(buttons);
        final FlowPanel frame = frame(pane, vertical, sizes[0]);
        page.add(frame);
        for (final int size : sizes) {
            final Button button = new Button(size + "px");
            button.setOnClick(() -> frame.getElement().getStyle().setProperty(vertical
                    ? "height"
                    : "width", size + "px"));
            buttons.add(button);
        }
        return page;
    }

    private static FlowPanel frame(final Widget split,
                                   final boolean vertical,
                                   final int size) {
        final FlowPanel frame = new FlowPanel();
        final Style style = frame.getElement().getStyle();
        style.setProperty("border", "1px solid var(--border-color,#444)");
        style.setProperty("boxSizing", "border-box");
        style.setProperty("position", "relative");
        style.setProperty("overflow", "hidden");
        style.setProperty("marginTop", "8px");
        style.setProperty("width", vertical
                ? "600px"
                : size + "px");
        style.setProperty("height", vertical
                ? size + "px"
                : "120px");
        split.setSize("100%", "100%");
        frame.add(split);
        return frame;
    }

    private static void resizeTo(final Play play, final int size) {
        play.click(play.getByRole("button", size + "px"));
    }

    // The panes' widths (or heights, when stacked), to the nearest pixel
    private static void expectSizes(final Play play,
                                    final boolean vertical,
                                    final double a,
                                    final double b) {
        expectSize(play, "a", vertical, a);
        expectSize(play, "b", vertical, b);
    }

    private static void expectSize(final Play play, final String testId, final boolean vertical, final double size) {
        final Query pane = play.getByTestId(testId);
        play.waitFor(() -> play.expect(vertical
                ? pane.height()
                : pane.width()).toBeCloseTo(size, 0));
    }
}
