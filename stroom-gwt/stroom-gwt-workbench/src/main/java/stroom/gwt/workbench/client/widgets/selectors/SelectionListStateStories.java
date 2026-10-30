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

package stroom.gwt.workbench.client.widgets.selectors;

import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.widget.selection.client.SelectionList;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;
import stroom.widget.selection.client.SimpleSelectionListModel;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/// The look of a [SelectionList]'s rows in each of their states, each row labelled with its state.
///
/// The rows are the data grid's, as a list's, with the grid's row looks (`data-grid.css`). A row's
/// states are partly pseudo-classes (`:hover`, `:focus` and `:focus-visible`), which a script can't
/// put an element in. So, as Storybook's pseudo-states addon does (and `Widgets/Layout/SplitPaneStates`), the story
/// copies Stroom's own row rules with each pseudo-class turned into a class (`:hover` into
/// `.pseudo-hover`), and gives each row the classes of its state. Chosen rows are chosen for real,
/// through the list's selection model.
///
/// A row has no pressed look of its own: pressing the mouse on a row chooses it and gives it the
/// focus there and then, so a row held down is chosen, focused and under the mouse; a click's focus
/// shows no ring, the keyboard's does.
public final class SelectionListStateStories {

    // The id of the style element holding the copied rules
    private static final String PSEUDO_STYLE_ID = "selection-list-pseudo-states";

    private SelectionListStateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Selectors/SelectionListStates", SelectionListStateStories.class)
                .layout(StoryLayout.PADDED)
                // A list of several choices drawn as a field, as the explorer's tag lists are, with a
                // row in each state
                .story("RowStates", context -> {
                    addPseudoStateRules(PSEUDO_STYLE_ID);
                    context.addCleanUp(() -> removeStyle(PSEUDO_STYLE_ID));
                    return render();
                })
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getByRole("option", RowState.NORMAL.label))
                            .toBeInTheDocument());
                    play.run("put each row in its state", () -> {
                        for (final RowState state : RowState.values()) {
                            final Element row = row(play, state).get();
                            for (final String pseudoClass : state.pseudoClasses) {
                                row.addClassName("pseudo-" + pseudoClass);
                            }
                        }
                    });

                    expectBackground(play, RowState.NORMAL, null);
                    expectBackground(play, RowState.HOVER, "--row__background-color--hovered");
                    // A press chooses the row and gives it the focus, which a click doesn't show
                    expectBackground(play, RowState.PRESSED, "--row__background-color--selected");
                    expectBackground(play, RowState.SELECTED, "--row__background-color--selected");
                    // A chosen row looks the same under the mouse
                    expectBackground(play, RowState.SELECTED_HOVER, "--row__background-color--selected");
                    expectBackground(play, RowState.FOCUSED, null);
                    expectBackground(play, RowState.SELECTED_FOCUSED, "--row__background-color--selected--focussed");
                    for (final RowState state : RowState.values()) {
                        final Value<Element> row = row(play, state);
                        final boolean focused = state.pseudoClasses.contains("focus-visible");
                        play.expect(Value.of(state.label + " row's focus ring is "
                                             + (focused
                                        ? "the focused row's"
                                        : "none"),
                                        () -> boxShadow(row.get()).equals(focused
                                                ? boxShadowOf(row.get())
                                                : "none")))
                                .toBe(true);
                    }
                    for (final RowState state : RowState.values()) {
                        play.expect(play.getByRole("option", state.label))
                                .toHaveAttribute("aria-selected", String.valueOf(state.selected));
                    }
                });
    }

    // --------------------------------------------------------------------------------
    // Rendering

    // The list, with a row for each state, beside a note of what each state is
    private static Widget render() {
        final SimpleSelectionListModel<String> model = new SimpleSelectionListModel<>();
        final List<String> chosen = new ArrayList<>();
        for (final RowState state : RowState.values()) {
            model.addItem(state.label);
            if (state.selected) {
                chosen.add(state.label);
            }
        }
        final SelectionList<String, SimpleSelectionItemWrapper<String>> list = new SelectionList<>();
        list.setMultiSelect(true);
        list.addStyleName("selection-list--field");
        list.setModel(model);
        list.getSelectionModel().setSelectedItems(chosen.stream().map(model::wrap).collect(Collectors.toList()));
        list.setAccessibleName("Row states");
        list.getElement().getStyle().setProperty("width", "260px");
        list.getElement().getStyle().setProperty("height", "200px");

        final FlowPanel notes = new FlowPanel();
        for (final RowState state : RowState.values()) {
            notes.add(StoryPanels.note(state.label + ": " + state.note, "#aaa", "0.8rem"));
        }
        notes.getElement().getStyle().setProperty("display", "grid");
        notes.getElement().getStyle().setProperty("rowGap", "4px");
        return StoryPanels.row(24, list, notes);
    }

    // The row (the option) of the state given
    private static Value<Element> row(final Play play, final RowState state) {
        final Value<Element> option = play.getByRole("option", state.label).element();
        return Value.of(state.label + " row", option::get);
    }

    private static void expectBackground(final Play play, final RowState state, final String variable) {
        final Value<Element> row = row(play, state);
        // Waited for, as the mouse-over colour fades in
        play.waitFor(() -> play.expect(Value.of(state.label + " row's background is "
                             + (variable == null
                ? "the list's"
                : variable),
                        () -> variable == null
                                ? "rgba(0, 0, 0, 0)".equals(background(row.get()))
                                : background(row.get()).equals(variableColour(variable))))
                .toBe(true));
    }

    // --------------------------------------------------------------------------------
    // Pseudo-states

    // Copies the row rules of Stroom's style sheets (the selection list's and the data grid's) that
    // use :hover, :active, :focus, :focus-visible or :focus-within, each pseudo-class turned into a
    // class (.pseudo-hover), into
    // a style element of their own with the id given, keeping any @media rule they are in (e.g.
    // forced colours) and following @imports
    private static native void addPseudoStateRules(String id) /*-{
        var existing = $doc.getElementById(id);
        if (existing) {
            existing.remove();
        }
        var pseudo = /:(hover|active|focus-within|focus-visible|focus)(?![\w-])/g;
        var copy = function (rules) {
            var text = '';
            for (var i = 0; i < rules.length; i++) {
                var rule = rules[i];
                if (rule.selectorText !== undefined) {
                    var selector = rule.selectorText;
                    if ((selector.indexOf('selection-list') >= 0 || selector.indexOf('data-grid') >= 0)
                        && selector.search(pseudo) >= 0) {
                        text += selector.replace(pseudo, '.pseudo-$1') + ' {' + rule.style.cssText + '}\n';
                    }
                } else if (rule.styleSheet) {
                    // An @import (app.css imports selection-list.css)
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

    private static native String background(Element element) /*-{
        return $wnd.getComputedStyle(element).backgroundColor;
    }-*/;

    private static native String boxShadow(Element element) /*-{
        return $wnd.getComputedStyle(element).boxShadow;
    }-*/;

    // The focus ring a focused row should have, chosen or not: the focus border and ring every other
    // control has, inside the row, as a computed box shadow
    private static native String boxShadowOf(Element row) /*-{
        var probe = $doc.createElement('div');
        probe.style.boxShadow = 'inset 0 0 0 2px var(--focus-ring__color)';
        $doc.body.appendChild(probe);
        var shadow = $wnd.getComputedStyle(probe).boxShadow;
        probe.remove();
        return shadow;
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

    // A row's states, a row each
    private enum RowState {
        NORMAL("Normal", "at rest", false),
        HOVER("Mouse over", ":hover", false, "hover"),
        PRESSED("Mouse down", "chosen and focused as it is pressed (a click shows no ring)", true,
                "hover", "active", "focus"),
        SELECTED("Selected", "chosen", true),
        SELECTED_HOVER("Selected, mouse over", "chosen, :hover (looks as Selected does)", true, "hover"),
        FOCUSED("Focused", ":focus-visible (the keyboard's place)", false, "focus", "focus-visible"),
        SELECTED_FOCUSED("Selected, focused", "chosen, :focus-visible", true, "focus", "focus-visible");

        private final String label;
        private final String note;
        private final boolean selected;
        private final List<String> pseudoClasses;

        RowState(final String label, final String note, final boolean selected, final String... pseudoClasses) {
            this.label = label;
            this.note = note;
            this.selected = selected;
            this.pseudoClasses = List.of(pseudoClasses);
        }
    }
}
