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
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.text.client.Frame;
import stroom.widget.text.client.Html;

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// Stories for [SplitPane], Stroom's split panes.
///
/// The `Drag` stories show a split pane set up as Stroom's screens set them up; the runner's real
/// input check drags each splitter with the real mouse, and checks the pane follows it
/// (`test-runner/lib/real-input/split-pane.mjs`). The other stories cover the keyboard, what a
/// screen reader is told, and the panes' order in the page.
public final class SplitPaneStories {

    // Spy names
    private static final String ON_RESIZE = "onResize";

    private static final String HINT = "Drag the divider →";

    private SplitPaneStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Layout/SplitPane", SplitPaneStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // A thin splitter (sizes="300px, *"): the real input check drags it with the
                // real mouse, and the pane must follow the mouse to the pixel
                .story("DragThinHorizontal", context -> framed(named(thin("300px, *"))))
                .withPlay(play -> play.expect(play.getByTestId("pane-0").width()).toBe(300.0))
                // A thin splitter between stacked panes
                .story("DragThinVertical", context -> framed(named(vertical(thin("120px, *")))))
                .withPlay(play -> play.expect(play.getByTestId("pane-0").height()).toBe(120.0))
                // The second pane is the sized one: dragging left grows it
                .story("DragThinAnchorEnd", context -> framed(named(thin("*, 250px"))))
                .withPlay(play -> play.expect(play.getByTestId("pane-1").width()).toBe(250.0))
                // The first pane keeps 30% of the split pane as it is resized
                .story("DragThinProportional", context -> framed(named(thin("30%, *"))))
                .withPlay(play -> {
                    final Value<Double> width = play.getByTestId("pane-0").width();
                    final Value<Double> frameWidth = play.getByTestId("frame").width();
                    // The frame has a 1px border either side
                    play.expect("30% of the frame's inside", () -> Math.abs(width.get()
                            - (frameWidth.get() - 2) * 0.3)).toBeLessThanOrEqual(1);
                })
                // A drag stops at the pane's minimum (150px)
                .story("DragThinWithMinimum", context -> {
                    final SplitPane pane = named(thin("300px, *"));
                    pane.setMinSizes("150px");
                    return framed(pane);
                })
                .withPlay(play -> play.expect(play.getByTestId("pane-0").width()).toBe(300.0))
                // A regular splitter, a 7px gap
                .story("DragHorizontal", context -> framed(named(split("300px, *"))))
                .withPlay(play -> play.expect(play.getByTestId("pane-0").width()).toBe(300.0))
                .story("DragVertical", context -> framed(named(vertical(split("120px, *")))))
                .withPlay(play -> play.expect(play.getByTestId("pane-0").height()).toBe(120.0))

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
                    // Said instead of the bare percentage
                    play.expect(splitter.attribute("aria-valuetext"))
                            .toMatch(TextMatch.regex("^\\d+% of the width$"));
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
                    play.expect(splitter.attribute("aria-valuetext"))
                            .toMatch(TextMatch.regex("^\\d+% of the height$"));
                    play.tab();
                    play.keyboard("{ArrowDown}");
                    play.expect(play.getByTestId("pane-0").height()).toBe(130.0);
                    // Left and Right don't move it
                    play.keyboard("{ArrowRight}");
                    play.expect(play.getByTestId("pane-0").height()).toBe(130.0);
                })
                // The second pane is the sized one: the splitter resizes it, so Right shrinks it.
                // It is still second in the page
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
                // A pane without a size, two taking the rest, or a pane a splitter resizes without
                // a name (which the splitter is announced by), is a mistake the split pane reports
                // when it is shown, naming the pane
                .story("MissingSize", context -> {
                    final FlowPanel page = new FlowPanel();
                    page.add(new Html(failure("100px", "Users, Groups, Roles")));
                    page.add(new Html(failure("*, *", "Users, Groups, Roles")));
                    page.add(new Html(failure("100px, 100px, *", "Users")));
                    return page;
                })
                .withPlay(play -> {
                    play.getByText("The split pane's 'Groups' pane has no size: give it one with sizes, "
                                   + "e.g. \"30%\" or \"400px\"");
                    play.getByText("The split pane's 'Users' pane and 'Groups' pane both take the rest (*): "
                                   + "only one can");
                    play.getByText("The split pane's pane 2 has no name, which its splitter is announced by: "
                                   + "give it one with names, e.g. \"Users, Groups\"");
                })
                // Beside the explorer, a thin splitter is drawn in the navigation colours
                // (split-pane--navigation), which a split pane nested in it doesn't take on
                .story("NavigationColour", context -> {
                    final SplitPane inner = thin("50%, *");
                    inner.setNames("Content, Details");
                    inner.add(SplitPanes.rightPane());
                    inner.add(SplitPanes.rightPane());
                    final SplitPane outer = thin("250px, *");
                    outer.addStyleName("split-pane--navigation");
                    outer.setNames("Explorer, Content");
                    outer.add(SplitPanes.leftPane(HINT));
                    outer.add(inner);
                    final FlowPanel frame = SplitPanes.frame(outer);
                    // The navigation line colour is the same grey as the others, so the story
                    // makes it red, to tell them apart
                    setVariable(frame.getElement(), "--navigation__splitter__line-color", "rgb(255, 0, 0)");
                    return frame;
                })
                .withPlay(play -> {
                    final Value<Element> explorer = play.getByRole("separator", "Explorer").element();
                    final Value<Element> content = play.getByRole("separator", "Content").element();
                    play.expect(same(
                            Value.of("the explorer's splitter colour", () -> splitterColour(explorer.get())),
                            Value.of("red", () -> "rgb(255, 0, 0)")))
                            .toBe(true);
                    play.expect(same(
                            Value.of("the nested splitter colour", () -> splitterColour(content.get())),
                            Value.of("the splitter colour", () -> variableColour("--splitter__line-color"))))
                            .toBe(true);
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
                    // Room around each splitter (WCAG 2.5.8). Known gap: where two splitters meet in
                    // a T, the centre of the one across the top touches the one below it, so neither
                    // has the room 2.5.8 asks of a target narrower than 24px
                    play.expect(TargetSpacing.crowding(play.getByRole("separator", "Users")))
                            .toEqual(List.of("Member of"));
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

    // The split pane with its two panes named as their text says
    private static SplitPane named(final SplitPane pane) {
        pane.setNames("Left panel, Right panel");
        return pane;
    }

    // The split pane with two panes (test ids pane-0 and pane-1), in the stories' frame
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

    // Shows a split pane of three panes with the sizes and names given, and returns the error it
    // reports
    private static String failure(final String sizes, final String names) {
        final SplitPane pane = thin(sizes);
        pane.setNames(names);
        pane.add(new Html("Users"));
        pane.add(new Html("Groups"));
        pane.add(new Html("Roles"));
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

    // Sets a CSS variable on an element (GWT's Style can't)
    private static native void setVariable(Element element, String name, String value) /*-{
        element.style.setProperty(name, value);
    }-*/;

    // The colour a splitter is drawn in (its ::before's background)
    private static native String splitterColour(Element splitter) /*-{
        return $wnd.getComputedStyle(splitter, '::before').backgroundColor;
    }-*/;

    // A theme colour variable's value, as a computed colour (e.g. rgb(53, 57, 68))
    private static native String variableColour(String variable) /*-{
        var probe = $doc.createElement('div');
        probe.style.backgroundColor = 'var(' + variable + ')';
        $doc.body.appendChild(probe);
        var colour = $wnd.getComputedStyle(probe).backgroundColor;
        probe.remove();
        return colour;
    }-*/;

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
