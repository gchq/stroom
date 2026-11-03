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

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.layout.client.SplitPane;
import stroom.widget.text.client.InlineLabel;

import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

/// The look of [SplitPane]'s splitters, thin and regular, between panes side by side and stacked,
/// in each of their states.
///
/// A splitter's states are pseudo-classes in `split-pane.css` (`:hover`, `:active`,
/// `:focus-visible`), which a script can't put an element in, and a press made by a script can't
/// capture the pointer, so [SplitPane] ignores it. So, as Storybook's pseudo-states addon does, the
/// story copies Stroom's own split pane rules with each pseudo-class turned into a class
/// (`:active` into `.pseudo-active`), and gives each splitter the class of its state. The dragging
/// split pane also has `split-pane--resizing`, which `SplitPane.onPointerDown` adds. Dragging the
/// splitters for real is in the `Drag` stories of `Widgets/Layout/SplitPane`.
///
/// A regular splitter is a gap between bordered panes, so its panes are bordered as Stroom's views
/// border them (`control-box`, as `FindInContent` does).
/// Its states are drawn as a thin splitter's are, a line in the middle of its gap.
public final class SplitPaneStateStories {

    private static final String SPLITTER = ".split-pane__splitter";
    // What SplitPane adds to the split pane while a splitter is dragged
    private static final String RESIZING = "split-pane--resizing";
    // The id of the style element holding the copied rules
    private static final String PSEUDO_STYLE_ID = "split-pane-pseudo-states";

    // The first pane's size, and where the dragging splitter has been dragged to: left or up, so
    // that neither pane's text overflows
    private static final String SIZE = "120px, *";
    private static final String DRAGGED_SIZE = "90px, *";
    private static final String STACKED_SIZE = "90px, *";
    private static final String STACKED_DRAGGED_SIZE = "65px, *";

    private SplitPaneStateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Layout/SplitPaneStates", SplitPaneStateStories.class)
                .layout(StoryLayout.PADDED)
                // Thin (a 1px line) and regular (a gap) splitters: at rest, hovered, pressed,
                // dragged and with the keyboard focus
                .story("States", context -> {
                    addPseudoStateRules(PSEUDO_STYLE_ID);
                    context.addCleanUp(() -> removeStyle(PSEUDO_STYLE_ID));
                    return grid();
                })
                .withPlay(play -> {
                    for (final Kind kind : Kind.values()) {
                        play.expect(kind.label + " hovered splitter's cursor",
                                        () -> cursor(splitter(play, kind, State.HOVER).get()))
                                .toBe(kind.vertical
                                        ? "row-resize"
                                        : "col-resize");
                        // At rest a thin splitter is a line, and a regular one a gap
                        if (kind.thin) {
                            expectColour(play, kind, State.NORMAL, "--splitter__line-color");
                        } else {
                            play.expect(kind.label + " Normal splitter",
                                            () -> splitterColour(splitter(play, kind, State.NORMAL).get()))
                                    .toBe("rgba(0, 0, 0, 0)");
                        }
                        expectThickness(play, kind, State.NORMAL, 1);
                        // Hovered, pressed or dragged, both are a 3px line (a regular splitter's in
                        // the middle of its gap) in a scrollbar thumb's colours: a hovered thumb's,
                        // and pressed or dragged, a dragged thumb's
                        expectColour(play, kind, State.HOVER, "--splitter__line-color--hover");
                        expectColour(play, kind, State.PRESSED, "--splitter__line-color--active");
                        expectColour(play, kind, State.DRAGGING, "--splitter__line-color--active");
                        for (final State state : new State[]{State.HOVER, State.PRESSED, State.DRAGGING}) {
                            expectThickness(play, kind, state, 3);
                            // Centred in the splitter, 1px either side of its middle
                            final Value<Element> shown = splitter(play, kind, state);
                            play.expect(kind.label + " " + state.label + " line's offset from the splitter's centre",
                                            () -> lineOffset(shown.get(), kind.vertical))
                                    .toBe(0.0);
                        }
                        // With the keyboard focus, the line is in the focus colour and ringed
                        final Value<Element> focused = splitter(play, kind, State.FOCUS);
                        expectColour(play, kind, State.FOCUS, "--splitter__focus-color");
                        expectThickness(play, kind, State.FOCUS, 1);
                        play.expect(kind.label + " focused line's ring",
                                        () -> shadow(focused.get(), "::before"))
                                .not().toBe("none");
                        // No handle
                        play.expect(kind.label + " focused splitter's ::after",
                                        () -> content(focused.get(), "::after"))
                                .toBe("none");
                    }
                });
    }

    // --------------------------------------------------------------------------------
    // Rendering

    // A grid with a row for each kind of splitter and a column for each state
    private static Widget grid() {
        final FlowPanel grid = new FlowPanel();
        final Style style = grid.getElement().getStyle();
        style.setProperty("display", "grid");
        style.setProperty("gridTemplateColumns", "80px repeat(" + State.values().length + ", 210px)");
        style.setProperty("gap", "12px");
        style.setProperty("alignItems", "center");

        grid.add(new InlineLabel());
        for (final State state : State.values()) {
            grid.add(heading(state.label, state.note));
        }
        for (final Kind kind : Kind.values()) {
            grid.add(heading(kind.label, null));
            for (final State state : State.values()) {
                grid.add(cell(kind, state));
            }
        }
        return grid;
    }

    private static Widget heading(final String text, final String note) {
        final InlineLabel label = new InlineLabel(text);
        label.getElement().getStyle().setProperty("fontWeight", "600");
        label.getElement().getStyle().setProperty("fontSize", "0.85rem");
        if (note == null) {
            return label;
        }
        final FlowPanel heading = StoryPanels.column(2, label,
                StoryPanels.note(note, "#aaa", "0.75rem"));
        heading.getElement().getStyle().setProperty("alignSelf", "end");
        return heading;
    }

    // A split pane of two panes, its splitter in the state given
    private static Widget cell(final Kind kind, final State state) {
        final boolean dragged = state == State.DRAGGING;
        final SplitPane pane = new SplitPane();
        pane.setThin(kind.thin);
        pane.setVertical(kind.vertical);
        final Widget first;
        final Widget second;
        if (kind.vertical) {
            pane.setSizes(dragged
                    ? STACKED_DRAGGED_SIZE
                    : STACKED_SIZE);
            pane.setNames("Top panel, Bottom panel");
            first = SplitPanes.topPane("");
            second = SplitPanes.bottomPane();
        } else {
            pane.setSizes(dragged
                    ? DRAGGED_SIZE
                    : SIZE);
            pane.setNames("Left panel, Right panel");
            first = SplitPanes.leftPane("");
            second = SplitPanes.rightPane();
        }
        pane.add(first);
        pane.add(second);
        if (!kind.thin) {
            bordered(first);
            bordered(second);
        }
        if (dragged) {
            pane.addStyleName(RESIZING);
        }
        final FlowPanel frame = SplitPanes.frame(pane);
        frame.getElement().getStyle().setProperty("height", kind.vertical
                ? "200px"
                : "120px");
        if (!kind.thin) {
            // The panes draw their own borders
            frame.getElement().getStyle().setProperty("border", "none");
            frame.getElement().getStyle().setProperty("borderRadius", "0");
        }
        frame.getElement().setAttribute("data-testid", testId(kind, state));
        if (state.pseudoClass != null) {
            // The splitter was created as the second pane was added
            splitter(pane.getElement()).addClassName("pseudo-" + state.pseudoClass);
        }
        return frame;
    }

    // A pane beside a regular splitter, which Stroom's views give a border and background, e.g.
    // FindInContent's "max control-box"
    private static void bordered(final Widget pane) {
        // In place of the left pane's tint
        pane.getElement().getStyle().clearProperty("background");
        pane.addStyleName("control-box");
    }

    private static String testId(final Kind kind, final State state) {
        return kind.name().toLowerCase() + "-" + state.name().toLowerCase();
    }

    private static Value<Element> splitter(final Play play, final Kind kind, final State state) {
        return play.within(play.getByTestId(testId(kind, state))).querySelector(SPLITTER).element();
    }

    private static void expectColour(final Play play,
                                     final Kind kind,
                                     final State state,
                                     final String variable) {
        final Value<Element> splitter = splitter(play, kind, state);
        play.expect(Value.of(kind.label + " " + state.label + " splitter is drawn in " + variable,
                        () -> splitterColour(splitter.get()).equals(variableColour(variable))))
                .toBe(true);
    }

    // A splitter's line is the thickness given across it, in pixels
    private static void expectThickness(final Play play,
                                        final Kind kind,
                                        final State state,
                                        final double pixels) {
        final Value<Element> splitter = splitter(play, kind, state);
        play.expect(kind.label + " " + state.label + " line's thickness",
                        () -> lineThickness(splitter.get(), kind.vertical))
                .toBe(pixels);
    }

    // --------------------------------------------------------------------------------
    // Pseudo-states

    // Copies the split pane rules of Stroom's style sheets that use :hover, :active or
    // :focus-visible, each pseudo-class turned into a class (.pseudo-hover), into a style element
    // of their own with the id given, keeping any @media rule they are in (e.g. forced colours) and
    // following @imports
    private static native void addPseudoStateRules(String id) /*-{
        var existing = $doc.getElementById(id);
        if (existing) {
            existing.remove();
        }
        var pseudo = /:(hover|active|focus-visible)(?![\w-])/g;
        var copy = function (rules) {
            var text = '';
            for (var i = 0; i < rules.length; i++) {
                var rule = rules[i];
                if (rule.selectorText !== undefined) {
                    var selector = rule.selectorText;
                    if (selector.indexOf('split-pane') >= 0 && selector.search(pseudo) >= 0) {
                        text += selector.replace(pseudo, '.pseudo-$1') + ' {' + rule.style.cssText + '}\n';
                    }
                } else if (rule.styleSheet) {
                    // An @import (app.css imports split-pane.css)
                    text += copy(rule.styleSheet.cssRules);
                } else if (rule.media && rule.cssRules) {
                    var inner = copy(rule.cssRules);
                    if (inner) {
                        text += '@media ' + rule.media.mediaText + ' {\n' + inner + '}\n';
                    }
                }
            }
            return text;
        };
        var css = '';
        for (var s = 0; s < $doc.styleSheets.length; s++) {
            try {
                css += copy($doc.styleSheets[s].cssRules);
            } catch (e) {
                // A style sheet from another origin can't be read
            }
        }
        var style = $doc.createElement('style');
        style.id = id;
        style.textContent = css;
        $doc.head.appendChild(style);
    }-*/;

    private static native void removeStyle(String id) /*-{
        var style = $doc.getElementById(id);
        if (style) {
            style.remove();
        }
    }-*/;

    private static native Element splitter(Element splitPane) /*-{
        return splitPane.querySelector(@stroom.gwt.workbench.client.widgets.layout.SplitPaneStateStories::SPLITTER);
    }-*/;

    // How thick a splitter's line (::before) is across it
    private static native double lineThickness(Element splitter, boolean vertical) /*-{
        var style = $wnd.getComputedStyle(splitter, '::before');
        return parseFloat(vertical ? style.height : style.width);
    }-*/;

    // How far the centre of a splitter's line (::before) is from the centre of the splitter, across
    // the line
    private static native double lineOffset(Element splitter, boolean vertical) /*-{
        var style = $wnd.getComputedStyle(splitter, '::before');
        return vertical
            ? parseFloat(style.top) + parseFloat(style.height) / 2 - splitter.offsetHeight / 2
            : parseFloat(style.left) + parseFloat(style.width) / 2 - splitter.offsetWidth / 2;
    }-*/;

    // The box shadow of a splitter's pseudo-element (the focus ring)
    private static native String shadow(Element splitter, String pseudoElement) /*-{
        return $wnd.getComputedStyle(splitter, pseudoElement).boxShadow;
    }-*/;

    // The content of a splitter's pseudo-element ("none" when it isn't drawn)
    private static native String content(Element splitter, String pseudoElement) /*-{
        return $wnd.getComputedStyle(splitter, pseudoElement).content;
    }-*/;

    private static native String cursor(Element element) /*-{
        return $wnd.getComputedStyle(element).cursor;
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

    // --------------------------------------------------------------------------------

    // The kinds of splitter, the rows of the grid
    private enum Kind {
        THIN("Thin, side by side", true, false),
        REGULAR("Regular, side by side", false, false),
        THIN_STACKED("Thin, stacked", true, true),
        REGULAR_STACKED("Regular, stacked", false, true);

        private final String label;
        private final boolean thin;
        private final boolean vertical;

        Kind(final String label, final boolean thin, final boolean vertical) {
            this.label = label;
            this.thin = thin;
            this.vertical = vertical;
        }
    }

    // A splitter's states, the columns of the grid
    private enum State {
        NORMAL("Normal", "At rest", null),
        HOVER("Hover", "A 3px line", "hover"),
        PRESSED("Click", "Pressed (:active)", "active"),
        DRAGGING("Drag", "Pressed, split pane resizing", "active"),
        FOCUS("Keyboard focus", ":focus-visible", "focus-visible");

        private final String label;
        private final String note;
        private final String pseudoClass;

        State(final String label, final String note, final String pseudoClass) {
            this.label = label;
            this.note = note;
            this.pseudoClass = pseudoClass;
        }
    }
}
