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
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.layout.client.SplitPane;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.DockLayoutPanel;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Frame;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.MySplitLayoutPanel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.ThinSplitLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for [SplitPane], the replacement for Stroom's split panels ([ThinSplitLayoutPanel] and
/// [MySplitLayoutPanel]).
///
/// The `Compare` stories show an old split panel (test id `old`) above a split pane set up alike
/// (test id `new`), and check their panes are the same size; the runner's real input check drags
/// both splitters with the real mouse and checks again (`test-runner/lib/real-input.mjs`). The other
/// stories cover what only the split pane does: the keyboard, what a screen reader is told, and the
/// panes' order in the page.
public final class SplitPaneStories {

    // Spy names
    private static final String ON_RESIZE = "onResize";

    private static final String HINT = "Drag the divider →";
    private static final String SPLITTER = ".split-pane__splitter";
    // How far the old and new panes' sizes may differ: the old panel takes 2px off a proportional
    // pane, and draws its thin splitter within the pane beside it
    private static final double TOLERANCE_PX = 3;

    private SplitPaneStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Layout/SplitPane", SplitPaneStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // ThinSplitLayoutPanel <g:west size="300"> and SplitPane sizes="300px, *" thin
                .story("CompareThinHorizontal", context -> compare(new ThinSplitLayoutPanel(),
                        false, false, 300, thin("300px, *")))
                .withPlay(play -> expectSameSizes(play, false))
                // <g:north size="120">
                .story("CompareThinVertical", context -> compare(new ThinSplitLayoutPanel(),
                        true, false, 120, vertical(thin("120px, *"))))
                .withPlay(play -> expectSameSizes(play, true))
                // <g:east size="250">: the second pane is the sized one
                .story("CompareThinAnchorEnd", context -> compare(new ThinSplitLayoutPanel(),
                        false, true, 250, thin("*, 250px")))
                .withPlay(play -> expectSameSizes(play, false))
                // hSplits="0.3": the first pane keeps 30% as the panel is resized
                .story("CompareThinProportional", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    old.setHSplits(0.3);
                    return compare(old, false, false, 300, thin("30%, *"));
                })
                .withPlay(play -> expectSameSizes(play, false))
                // setWidgetMinSize(first, 150): only a drag shows it (the real input check)
                .story("CompareThinWithMinimum", context -> {
                    final ThinSplitLayoutPanel old = new ThinSplitLayoutPanel();
                    final SplitPane pane = thin("300px, *");
                    pane.setMinSizes("150px");
                    final Widget widget = compare(old, false, false, 300, pane);
                    old.setWidgetMinSize(old.getWidget(0), 150);
                    return widget;
                })
                .withPlay(play -> expectSameSizes(play, false))
                // MySplitLayoutPanel, whose splitter is a 7px gap
                .story("CompareHorizontal", context -> compare(new MySplitLayoutPanel(),
                        false, false, 300, split("300px, *")))
                .withPlay(play -> expectSameSizes(play, false))
                .story("CompareVertical", context -> compare(new MySplitLayoutPanel(),
                        true, false, 120, vertical(split("120px, *"))))
                .withPlay(play -> expectSameSizes(play, true))

                // A splitter is a separator a keyboard user can reach and move: Left and Right
                // move it 10px, Home and End to its limits, and Enter collapses its pane and
                // restores it. It is named by the pane it resizes, and says its pane's size as a
                // percentage of the split pane.
                .story("Keyboard", context -> {
                    final SplitPane pane = thin("300px, *");
                    pane.setNames("Users, Groups");
                    pane.setMinSizes("100px, 100px");
                    return framed(pane);
                })
                .withPlay(play -> {
                    final Query splitter = play.getByRole("separator", "Users");
                    final Query first = play.getByTestId("pane-0");
                    play.expect(splitter).toHaveAttribute("aria-orientation", "vertical");
                    play.expect(splitter).toHaveAttribute("aria-controls");
                    play.expect(TabStops.names(play.body())).toEqual(List.of("Users"));
                    play.tab();
                    play.expect(splitter).toHaveFocus();
                    play.expect(first.width()).toBe(300.0);
                    play.keyboard("{ArrowRight}");
                    play.expect(first.width()).toBe(310.0);
                    play.keyboard("{ArrowLeft}{ArrowLeft}");
                    play.expect(first.width()).toBe(290.0);
                    // The smallest the pane may be
                    play.keyboard("{Home}");
                    play.expect(first.width()).toBe(100.0);
                    play.expect(same(splitter.attribute("aria-valuenow"), splitter.attribute("aria-valuemin")))
                            .toBe(true);
                    // The largest: the other pane keeps its smallest
                    play.keyboard("{End}");
                    play.expect(play.getByTestId("pane-1").width()).toBe(100.0);
                    play.expect(same(splitter.attribute("aria-valuenow"), splitter.attribute("aria-valuemax")))
                            .toBe(true);
                    // Enter collapses the pane, and restores it to where it was
                    play.keyboard("{Home}{ArrowRight}");
                    play.expect(first.width()).toBe(110.0);
                    play.keyboard("{Enter}");
                    play.expect(first.width()).toBe(100.0);
                    play.keyboard("{Enter}");
                    play.expect(first.width()).toBe(110.0);
                })
                // Stacked panes: Up and Down move the splitter
                .story("KeyboardVertical", context -> {
                    final SplitPane pane = vertical(thin("120px, *"));
                    pane.setNames("Users, Groups");
                    return framed(pane);
                })
                .withPlay(play -> {
                    final Query splitter = play.getByRole("separator", "Users");
                    play.expect(splitter).toHaveAttribute("aria-orientation", "horizontal");
                    play.tab();
                    play.keyboard("{ArrowDown}");
                    play.expect(play.getByTestId("pane-0").height()).toBe(130.0);
                    // Left and Right don't move it
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByTestId("pane-0").height()).toBe(130.0);
                })
                // The second pane is the sized one: the splitter resizes it, so Right shrinks it.
                // It is still second in the page, where an old panel put it first (it adds the
                // centre last)
                .story("AnchorEnd", context -> {
                    final SplitPane pane = thin("*, 250px");
                    pane.setNames("Content, Details");
                    return framed(pane);
                })
                .withPlay(play -> {
                    final Query second = play.getByTestId("pane-1");
                    final Query splitter = play.getByRole("separator", "Details");
                    play.expect(play.body()).toHaveTextContent(TextMatch.regex("Left panel.*Right panel"));
                    // The second pane's widget fills the pane the splitter controls
                    final Value<Element> widget = second.element();
                    play.expect(same(splitter.attribute("aria-controls"), Value.of("the second pane's id",
                            () -> widget.get().getParentElement().getId()))).toBe(true);
                    play.tab();
                    play.keyboard("{ArrowRight}");
                    play.expect(second.width()).toBe(240.0);
                })
                // Three panes, the middle taking the rest; hiding a pane hides its splitter, and
                // showing it again brings both back as they were
                .story("HiddenPane", context -> {
                    final SplitPane pane = thin("150px, *, 150px");
                    pane.setNames("Tree, Content, Properties");
                    addPanes(pane);
                    pane.add(SplitPanes.rightPane());
                    pane.setPaneHidden(pane.getWidget(0), true);
                    final FlowPanel frame = SplitPanes.frame(pane);
                    frame.getElement().setAttribute("data-testid", "frame");
                    return frame;
                })
                .withPlay(play -> {
                    play.expect(play.getByTestId("pane-0")).not().toBeVisible();
                    play.expect(TabStops.names(play.body())).toEqual(List.of("Properties"));
                    // The middle pane starts at the frame's edge, inside its 1px border
                    final Value<Double> left = play.getByTestId("pane-1").left();
                    final Value<Double> frameLeft = play.getByTestId("frame").left();
                    play.expect(Value.of("the middle pane's offset in the frame",
                            () -> left.get() - frameLeft.get())).toBe(1.0);
                })
                // Shift and an arrow key, or Page Up and Page Down, move a splitter further
                .story("BigSteps", context -> {
                    final SplitPane pane = thin("300px, *");
                    pane.setNames("Users, Groups");
                    return framed(pane);
                })
                .withPlay(play -> {
                    final Query first = play.getByTestId("pane-0");
                    play.tab();
                    play.keyboard("{Shift>}{ArrowRight}{/Shift}");
                    play.expect(first.width()).toBe(350.0);
                    play.keyboard("{Shift>}{ArrowLeft}{ArrowLeft}{/Shift}");
                    play.expect(first.width()).toBe(250.0);
                    // Page Up grows the pane and Page Down shrinks it, whichever side it is on
                    play.keyboard("{PageUp}");
                    play.expect(first.width()).toBe(300.0);
                    play.keyboard("{PageDown}{PageDown}");
                    play.expect(first.width()).toBe(200.0);
                })
                // On a right-to-left page the first pane is on the right, and the splitter
                // follows the screen: Left moves it left, growing the first pane (the real input
                // check drags it)
                .story("RightToLeft", context -> {
                    final SplitPane pane = thin("300px, *");
                    pane.setNames("Users, Groups");
                    final Widget frame = framed(pane);
                    frame.getElement().setAttribute("dir", "rtl");
                    return frame;
                })
                .withPlay(play -> {
                    final Query first = play.getByTestId("pane-0");
                    final Value<Double> firstLeft = first.left();
                    final Value<Double> secondLeft = play.getByTestId("pane-1").left();
                    play.expect(Value.of("the first pane is right of the second",
                            () -> firstLeft.get() > secondLeft.get())).toBe(true);
                    play.tab();
                    play.keyboard("{ArrowLeft}");
                    play.expect(first.width()).toBe(310.0);
                    play.keyboard("{ArrowRight}{ArrowRight}");
                    play.expect(first.width()).toBe(290.0);
                })
                // A frame in a pane: the real input check drags the splitter across it, which
                // must not take the pointer from the splitter
                .story("OverAFrame", context -> {
                    final SplitPane pane = thin("300px, *");
                    pane.setNames("Users, Page");
                    final Widget first = SplitPanes.leftPane(HINT);
                    first.getElement().setAttribute("data-testid", "pane-0");
                    final Frame frame = new Frame();
                    frame.getElement().setAttribute("srcdoc",
                            "<p style=\"font: 13px sans-serif; color: #aaa\">A page in a frame</p>");
                    frame.getElement().setAttribute("title", "A page in a frame");
                    frame.getElement().getStyle().setProperty("border", "none");
                    pane.add(first);
                    pane.add(frame);
                    return SplitPanes.frame(pane);
                })
                .withPlay(play -> play.expect(play.getByTitle("A page in a frame")).toBeInTheDocument())
                // A pane without a size, or two taking the rest, is a mistake the split pane
                // reports when it is shown, naming the pane
                .story("MissingSize", context -> {
                    final FlowPanel page = new FlowPanel();
                    page.add(new HTML(failure("100px")));
                    page.add(new HTML(failure("*, *")));
                    return page;
                })
                .withPlay(play -> {
                    play.getByText("The split pane's 'Groups' pane has no size: give it one with sizes, "
                                   + "e.g. \"30%\" or \"400px\"");
                    play.getByText("The split pane's 'Users' pane and 'Groups' pane both take the rest (*): "
                                   + "only one can");
                })
                // A widget that needs to know it has been resized is told when its pane is
                .story("ResizeNotifies", context -> {
                    final SplitPane pane = thin("300px, *");
                    pane.setNames("Users, Groups");
                    final ResizeCounter counter = new ResizeCounter(context.fn(ON_RESIZE)::call);
                    pane.add(counter);
                    pane.add(SplitPanes.rightPane());
                    return SplitPanes.frame(pane);
                })
                .withPlay(play -> {
                    play.tab();
                    play.waitFor(() -> play.expect(play.spy(ON_RESIZE)).toHaveBeenCalled());
                    final Value<Integer> calls = play.spy(ON_RESIZE).callCount();
                    final Value<Integer> before = play.capture("calls before", calls::get);
                    play.keyboard("{ArrowRight}");
                    play.waitFor(() -> play.expect("calls since", () -> calls.get() - before.get())
                            .toBeGreaterThan(0));
                })
                // Declared in a UiBinder template, nested as User Groups is: a list above, and the
                // groups the selected user is a member of beside its members below
                .story("Template", context -> SplitPanes.frame(new SplitPaneTemplate()))
                .withPlay(play -> {
                    play.expect(play.body()).toHaveTextContent(TextMatch.regex("Users.*Member of.*Members"));
                    play.expect(TabStops.names(play.body())).toEqual(List.of("Users", "Member of"));
                    play.expect(play.getByRole("separator", "Users"))
                            .toHaveAttribute("aria-orientation", "horizontal");
                    play.expect(play.getByRole("separator", "Member of"))
                            .toHaveAttribute("aria-orientation", "vertical");
                });
    }

    // --------------------------------------------------------------------------------
    // Rendering

    private static SplitPane split(final String sizes) {
        final SplitPane pane = new SplitPane();
        pane.setSizes(sizes);
        return pane;
    }

    private static SplitPane thin(final String sizes) {
        final SplitPane pane = split(sizes);
        pane.setThin(true);
        return pane;
    }

    private static SplitPane vertical(final SplitPane pane) {
        pane.setVertical(true);
        return pane;
    }

    // The split pane with the two panes the old panels' stories show, in the stories' frame
    private static Widget framed(final SplitPane pane) {
        addPanes(pane);
        final FlowPanel frame = SplitPanes.frame(pane);
        frame.getElement().setAttribute("data-testid", "frame");
        return frame;
    }

    private static void addPanes(final SplitPane pane) {
        final Widget first = SplitPanes.leftPane(HINT);
        final Widget second = SplitPanes.rightPane();
        first.getElement().setAttribute("data-testid", "pane-0");
        second.getElement().setAttribute("data-testid", "pane-1");
        pane.add(first);
        pane.add(second);
    }

    // An old split panel laid out as a story asks, above a split pane with the same panes
    private static Widget compare(final DockLayoutPanel old,
                                  final boolean vertical,
                                  final boolean anchorEnd,
                                  final double size,
                                  final SplitPane pane) {
        final Widget docked = SplitPanes.layout(old, vertical, anchorEnd, size, HINT);
        docked.getElement().setAttribute("data-testid", "old-sized");
        addPanes(pane);
        pane.getWidget(anchorEnd
                ? 1
                : 0).getElement().setAttribute("data-testid", "new-sized");
        final FlowPanel page = new FlowPanel();
        final Style style = page.getElement().getStyle();
        style.setProperty("display", "flex");
        style.setProperty("flexDirection", "column");
        style.setProperty("gap", "8px");
        style.setProperty("padding", "8px");
        page.add(labelled(old.getClass().getSimpleName() + " (old)", SplitPanes.frame(old), "old"));
        // In Stroom a resized window reaches an old panel through its parents' onResize calls,
        // which the story's frame doesn't make; a split pane needs none
        final HandlerRegistration resizing = Window.addResizeHandler(event -> old.onResize());
        page.addAttachHandler(event -> {
            if (!event.isAttached()) {
                resizing.removeHandler();
            }
        });
        page.add(labelled("SplitPane (new)", SplitPanes.frame(pane), "new"));
        return page;
    }

    private static Widget labelled(final String label, final FlowPanel frame, final String testId) {
        final FlowPanel section = new FlowPanel();
        section.getElement().setAttribute("data-testid", testId);
        final HTML heading = new HTML(label);
        heading.getElement().getStyle().setProperty("margin", "0 0 4px");
        heading.getElement().getStyle().setProperty("fontSize", "0.8rem");
        section.add(heading);
        frame.getElement().getStyle().setProperty("height", "260px");
        section.add(frame);
        return section;
    }

    // The old and new sized panes are the same size, as are the panes beside them
    private static void expectSameSizes(final Play play, final boolean vertical) {
        final Query oldSized = play.getByTestId("old-sized");
        final Query newSized = play.getByTestId("new-sized");
        // The old panel lays itself out once attached
        play.waitFor(() -> play.expect(difference(oldSized, newSized, vertical))
                .toBeLessThanOrEqual(TOLERANCE_PX));
        final Query old = play.getByTestId("old");
        final Query split = play.getByTestId("new");
        play.expect(play.within(split).querySelectorAll(SPLITTER)).toHaveLength(1);
        play.expect(play.within(old).querySelector(".split-pane")).toBeNull();
    }

    private static Value<Double> difference(final Query oldPane, final Query newPane, final boolean vertical) {
        final Value<Double> oldSize = vertical
                ? oldPane.height()
                : oldPane.width();
        final Value<Double> newSize = vertical
                ? newPane.height()
                : newPane.width();
        return Value.of("the difference between the old and new panes' sizes",
                () -> Math.abs(oldSize.get() - newSize.get()));
    }

    // Shows a split pane of three panes with the sizes given, and returns the error it reports
    private static String failure(final String sizes) {
        final SplitPane pane = thin(sizes);
        pane.setNames("Users, Groups, Roles");
        pane.add(new HTML("Users"));
        pane.add(new HTML("Groups"));
        pane.add(new HTML("Roles"));
        try {
            RootPanel.get().add(pane);
            return "No error";
        } catch (final RuntimeException e) {
            Throwable cause = e;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            return SafeHtmlUtils.htmlEscape(cause.getMessage());
        } finally {
            RootPanel.get().remove(pane);
        }
    }

    private static Value<Boolean> same(final Value<String> first, final Value<String> second) {
        return Value.of(first + " and " + second + " are the same",
                () -> first.get() != null && first.get().equals(second.get()));
    }

    // --------------------------------------------------------------------------------

    // A pane's widget that counts the times it is told it may have been resized
    private static final class ResizeCounter extends SimplePanel implements RequiresResize {

        private final Runnable onResize;

        private ResizeCounter(final Runnable onResize) {
            this.onResize = onResize;
            setWidget(SplitPanes.leftPane(HINT));
        }

        @Override
        public void onResize() {
            onResize.run();
        }
    }
}
