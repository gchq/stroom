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

package stroom.gwt.workbench.client.widgets.datagrid;

import stroom.cell.textfield.client.TextFieldCell;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.ColumnSizeConstants;
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.story.ComponentStories;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.item.client.SelectionBoxCell;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.item.client.SimpleSelectionListModel;
import stroom.ui.config.shared.Theme;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NodeList;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.Header;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;

/// The looks of a data grid's ([DataGrid]'s) rows in each of their states (`data-grid.css`), in
/// the light and dark themes and the comfortable and compact densities (a story each, as the page
/// doesn't scroll).
///
/// The grid's columns are those of Stroom's grids with controls in their cells: a tick box and a
/// value spinner (as `ProcessorList`'s), and a text field and a selection box (as
/// `FloorMapSettings`'s value schema).
///
/// A row's states are partly pseudo-classes (`:hover`, `:focus-within`, `:focus-visible`), which a
/// script can't put an element in. So, as `Widgets/Selectors/SelectionListStates` does, the story
/// copies the grid's rules that use them with each pseudo-class turned into a class (`:hover` into
/// `.pseudo-hover`), and gives each row (or cell) the classes of its state. Selected rows are
/// selected for real, through the grid's selection model.
///
/// The keyboard focus is shown on a row, or on a cell holding a control that uses the arrow keys
/// (here the Path text field), where the grid's keyboard puts it.
public final class DataGridRowStateStories {

    // The id of the style element holding the copied rules
    private static final String PSEUDO_STYLE_ID = "data-grid-pseudo-states";
    // The column whose cell takes the focus in the focused cell states (Path)
    private static final int FOCUS_CELL_COLUMN = 3;
    private static final String COMFORTABLE = "stroom-density-comfortable";
    private static final String COMPACT = "stroom-density-compact";

    private DataGridRowStateStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        final ComponentStories component = registry.component("Widgets/Data Grid/DataGridRowStates",
                        DataGridRowStateStories.class)
                .layout(StoryLayout.PADDED);
        // A story for each theme and density, as the page doesn't scroll
        for (final Section section : Section.values()) {
            component.story(section.exportName, context -> render(context, section))
                    .withPlay(play -> playSection(play, section));
        }
    }

    // --------------------------------------------------------------------------------
    // Play

    // Checks the grid's rows
    private static void playSection(final Play play, final Section section) {
        final String testId = section.testId();
        play.waitFor(() -> play.expect(play.getByTestId(testId)).toBeInTheDocument());
        // Each row says whether it is selected
        play.waitFor(() -> play.expect(section.title + " selected rows",
                        () -> countSelected(play.getByTestId(testId).element().get()))
                .toBe(RowState.selectedCount()));
        // Mouse over: the hovered colour, which fades in
        play.waitFor(() -> play.expect(section.title + " mouse over",
                        () -> background(play, testId, RowState.HOVER)
                                .equals(colour(play, testId, "--row__background-color--hovered")))
                .toBe(true));
        // No mouse-over look on a selected row
        play.waitFor(() -> play.expect(section.title + " selected, mouse over",
                        () -> background(play, testId, RowState.SELECTED_HOVER)
                                .equals(colour(play, testId, "--row__background-color--selected")))
                .toBe(true));
        // Selected and focused: the selected-and-focused colour, and the focus ring inside
        play.expect(section.title + " selected, focused row",
                        () -> background(play, testId, RowState.SELECTED_FOCUSED)
                                .equals(colour(play, testId, "--row__background-color--selected--focussed")))
                .toBe(true);
        play.expect(section.title + " focused row's ring",
                        () -> boxShadow(row(play, testId, RowState.FOCUSED))
                                .equals(focusRing(play.getByTestId(testId).element().get())))
                .toBe(true);
        // A focused cell has the ring, its row has none
        play.expect(section.title + " focused cell's ring",
                        () -> boxShadow(cell(row(play, testId, RowState.SELECTED_FOCUSED_CELL)))
                                .equals(focusRing(play.getByTestId(testId).element().get())))
                .toBe(true);
        play.expect(section.title + " focused cell's row has no ring",
                        () -> boxShadow(row(play, testId, RowState.SELECTED_FOCUSED_CELL)))
                .toBe("none");
    }

    private static Element row(final Play play, final String testId, final RowState state) {
        return rowWithText(play.getByTestId(testId).element().get(), state.label);
    }

    private static String background(final Play play, final String testId, final RowState state) {
        return backgroundColour(row(play, testId, state));
    }

    private static String colour(final Play play, final String testId, final String variable) {
        return variableColour(play.getByTestId(testId).element().get(), variable);
    }

    private static Element cell(final Element row) {
        return rowCell(row, FOCUS_CELL_COLUMN);
    }

    // --------------------------------------------------------------------------------
    // Rendering

    private static Widget render(final StoryContext context, final Section section) {
        addPseudoStateRules(PSEUDO_STYLE_ID);
        context.addCleanUp(() -> removeStyle(PSEUDO_STYLE_ID));
        return section(section);
    }

    // The section's theme and density, holding the grid (each row's label says its state)
    private static Widget section(final Section section) {
        final FlowPanel panel = StoryPanels.column(8,
                StoryPanels.note(section.title, "#aaa", "0.9rem"),
                newGrid(section));
        panel.addStyleName(section.theme.getCssClass());
        panel.addStyleName(section.density);
        panel.getElement().getStyle().setProperty("background", "var(--page__background-color)");
        panel.getElement().getStyle().setProperty("color", "var(--text-color)");
        panel.getElement().getStyle().setProperty("padding", "12px");
        panel.getElement().getStyle().setProperty("width", "max-content");
        return panel;
    }

    // The grid, with a row for each state, put in their states once drawn
    private static Widget newGrid(final Section section) {
        final DataGrid<RowState> grid = new DataGrid<>();
        grid.setAccessibleName(section.title + ", new");
        addColumns(grid::addResizableColumn, grid::addColumn, grid::addColumn);
        final MultiSelectionModelImpl<RowState> selectionModel = grid.addDefaultSelectionModel(true);
        selectionModel.setSelectedItems(selectedStates());
        grid.setRowData(List.of(RowState.values()));
        grid.setRowCount(RowState.values().length);
        grid.addAttachHandler(event -> {
            if (event.isAttached()) {
                Scheduler.get().scheduleDeferred(() -> {
                    grid.flush();
                    applyStates(grid.getElement());
                });
            }
        });
        return framed(grid, section.testId());
    }

    // The columns of Stroom's grids with controls in their cells, added by the grid's own methods
    private static void addColumns(final ColumnAdder<String> resizable,
                                   final ColumnAdder<String> fixed,
                                   final ColumnAdder<Header<?>> withHeader) {
        resizable.add(DataGridUtil.textColumnBuilder((RowState state) -> state.label)
                        .enabledWhen(state -> !state.disabled)
                        .build(),
                "State",
                160);
        final Column<RowState, TickBoxState> enabled = new Column<RowState, TickBoxState>(
                TickBoxCell.create(new TickBoxCell.DefaultAppearance(), false, false, true)) {
            @Override
            public TickBoxState getValue(final RowState state) {
                return TickBoxState.fromBoolean(!state.disabled);
            }
        };
        fixed.add(enabled, "Enabled", 70);
        withHeader.add(DataGridUtil.valueSpinnerColumnBuilder("Priority",
                                (RowState state) -> new EditableInteger(10),
                                1,
                                100)
                        .build(),
                DataGridUtil.headingBuilder("Priority").build(),
                ColumnSizeConstants.VALUE_SPINNER_COL);
        final Column<RowState, String> path = new Column<RowState, String>(
                new TextFieldCell(index -> "Path for " + RowState.values()[index].label)) {
            @Override
            public String getValue(final RowState state) {
                return "/event/path";
            }
        };
        fixed.add(path, "Path", 130);
        final SimpleSelectionListModel<String> roleModel = new SimpleSelectionListModel<>();
        roleModel.addItems(List.of("Name", "Status", "Custom"));
        final Column<RowState, String> role = new Column<RowState, String>(
                new SelectionBoxCell<String, SimpleSelectionItemWrapper<String>>(
                        roleModel, index -> "Role for " + RowState.values()[index].label)) {
            @Override
            public String getValue(final RowState state) {
                return "Name";
            }
        };
        fixed.add(role, "Role", 110);
    }

    private static List<RowState> selectedStates() {
        final List<RowState> selected = new ArrayList<>();
        for (final RowState state : RowState.values()) {
            if (state.selected) {
                selected.add(state);
            }
        }
        return selected;
    }

    private static Widget framed(final Widget grid, final String testId) {
        grid.setWidth("100%");
        grid.setHeight("100%");
        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().setAttribute("data-testid", testId);
        // The same in both densities: the controls in the cells keep their own padding, so set the
        // rows' height in either
        panel.getElement().getStyle().setProperty("height", "470px");
        panel.getElement().getStyle().setProperty("width", "580px");
        return panel;
    }

    // Gives each row (and the cell of a focused cell state) the classes of its pseudo-states
    private static void applyStates(final Element gridElement) {
        final NodeList<Element> rows = bodyRows(gridElement);
        for (int i = 0; i < rows.getLength() && i < RowState.values().length; i++) {
            final RowState state = RowState.values()[i];
            final Element row = rows.getItem(i);
            if (state.hover) {
                row.addClassName("pseudo-hover");
            }
            if (state.rowFocus) {
                row.addClassName("pseudo-focus");
                row.addClassName("pseudo-focus-visible");
                row.addClassName("pseudo-focus-within");
            }
            if (state.cellFocus) {
                final Element cell = rowCell(row, FOCUS_CELL_COLUMN);
                cell.addClassName("pseudo-focus");
                cell.addClassName("pseudo-focus-visible");
                row.addClassName("pseudo-focus-within");
            }
        }
    }

    // --------------------------------------------------------------------------------
    // Pseudo-states

    // Copies the rules of Stroom's style sheets for the data grids (cell-table.css and
    // data-grid.css) that use :hover, :focus, :focus-within or
    // :focus-visible, each pseudo-class
    // turned into a class (.pseudo-hover), into a style element of their own with the id given,
    // keeping any @media rule they are in and following @imports
    private static native void addPseudoStateRules(String id) /*-{
        var existing = $doc.getElementById(id);
        if (existing) {
            existing.remove();
        }
        var pseudo = /:(hover|focus-within|focus-visible|focus)(?![\w-])/g;
        var copy = function (rules) {
            var text = '';
            for (var i = 0; i < rules.length; i++) {
                var rule = rules[i];
                if (rule.selectorText !== undefined) {
                    var selector = rule.selectorText;
                    if ((selector.indexOf('dataGrid') >= 0 || selector.indexOf('.data-grid') >= 0)
                        && selector.search(pseudo) >= 0) {
                        text += selector.replace(pseudo, '.pseudo-$1') + ' {' + rule.style.cssText + '}\n';
                    }
                } else if (rule.styleSheet) {
                    // An @import (app.css imports the grid's style sheets)
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

    // The grid's body rows
    private static native NodeList<Element> bodyRows(Element container) /*-{
        return container.querySelectorAll('[role="row"][data-row]');
    }-*/;

    // A row's cell in a column
    private static native Element rowCell(Element row, int column) /*-{
        return row.querySelector(':scope > [data-col="' + column + '"]');
    }-*/;

    private static native int countSelected(Element container) /*-{
        return container.querySelectorAll('[role="row"][data-row][aria-selected="true"]').length;
    }-*/;

    // The body row of the grid in the container whose first cell's text is the text given
    private static native Element rowWithText(Element container, String text) /*-{
        var rows = container.querySelectorAll('[role="row"][data-row]');
        for (var i = 0; i < rows.length; i++) {
            var first = rows[i].querySelector('td, [data-col]');
            if (first && first.textContent.trim() === text) {
                return rows[i];
            }
        }
        return null;
    }-*/;

    private static native String backgroundColour(Element element) /*-{
        return $wnd.getComputedStyle(element).backgroundColor;
    }-*/;

    private static native String boxShadow(Element element) /*-{
        return $wnd.getComputedStyle(element).boxShadow;
    }-*/;

    // The focus ring of a focused row or cell, as a computed box shadow in the context given (for its
    // theme's colours)
    private static native String focusRing(Element context) /*-{
        var probe = $doc.createElement('div');
        probe.style.boxShadow = 'inset 0 0 0 2px var(--focus-ring__color)';
        context.appendChild(probe);
        var shadow = $wnd.getComputedStyle(probe).boxShadow;
        probe.remove();
        return shadow;
    }-*/;

    // A theme colour variable's value in the context given, as a computed colour
    private static native String variableColour(Element context, String variable) /*-{
        var probe = $doc.createElement('div');
        probe.style.backgroundColor = 'var(' + variable + ')';
        context.appendChild(probe);
        var colour = $wnd.getComputedStyle(probe).backgroundColor;
        probe.remove();
        return colour;
    }-*/;

    // --------------------------------------------------------------------------------

    // A theme and density, each a story
    private enum Section {
        LIGHT_COMFORTABLE("LightComfortable", "Light, comfortable", Theme.LIGHT, COMFORTABLE),
        LIGHT_COMPACT("LightCompact", "Light, compact", Theme.LIGHT, COMPACT),
        DARK_COMFORTABLE("DarkComfortable", "Dark, comfortable", Theme.DARK, COMFORTABLE),
        DARK_COMPACT("DarkCompact", "Dark, compact", Theme.DARK, COMPACT);

        private final String exportName;
        private final String title;
        private final Theme theme;
        private final String density;

        Section(final String exportName, final String title, final Theme theme, final String density) {
            this.exportName = exportName;
            this.title = title;
            this.theme = theme;
            this.density = density;
        }

        private String testId() {
            return name().toLowerCase().replace('_', '-') + "-new";
        }
    }

    // A row's states, a row each, in the grid's order (the stripes alternate from the first). Mouse
    // over is :hover, a focused row or cell has the keyboard focus (:focus-visible), the focused cell
    // being the Path text field's; a disabled row's item is disabled (its text greyed)
    private enum RowState {
        NORMAL("Normal"),
        HOVER("Mouse over", Flag.HOVER),
        SELECTED("Selected", Flag.SELECTED),
        SELECTED_HOVER("Selected, mouse over", Flag.SELECTED, Flag.HOVER),
        FOCUSED("Focused row", Flag.ROW_FOCUS),
        SELECTED_FOCUSED("Selected, focused row", Flag.SELECTED, Flag.ROW_FOCUS),
        SELECTED_FOCUSED_CELL("Selected, focused cell", Flag.SELECTED, Flag.CELL_FOCUS),
        DISABLED("Disabled", Flag.DISABLED),
        SELECTED_DISABLED("Selected, disabled", Flag.SELECTED, Flag.DISABLED),
        RUN_1("Selected run 1", Flag.SELECTED),
        RUN_2("Selected run 2", Flag.SELECTED),
        RUN_3("Selected run 3", Flag.SELECTED),
        AFTER("Normal after");

        private final String label;
        private final boolean selected;
        private final boolean hover;
        private final boolean rowFocus;
        private final boolean cellFocus;
        private final boolean disabled;

        RowState(final String label, final Flag... flags) {
            this.label = label;
            final List<Flag> list = List.of(flags);
            this.selected = list.contains(Flag.SELECTED);
            this.hover = list.contains(Flag.HOVER);
            this.rowFocus = list.contains(Flag.ROW_FOCUS);
            this.cellFocus = list.contains(Flag.CELL_FOCUS);
            this.disabled = list.contains(Flag.DISABLED);
        }

        private static int selectedCount() {
            int count = 0;
            for (final RowState state : values()) {
                if (state.selected) {
                    count++;
                }
            }
            return count;
        }
    }

    // A grid's method adding a column with a name or header, and a width
    @FunctionalInterface
    private interface ColumnAdder<H> {

        void add(Column<RowState, ?> column, H nameOrHeader, int width);
    }

    private enum Flag {
        SELECTED,
        HOVER,
        ROW_FOCUS,
        CELL_FOCUS,
        DISABLED
    }
}
