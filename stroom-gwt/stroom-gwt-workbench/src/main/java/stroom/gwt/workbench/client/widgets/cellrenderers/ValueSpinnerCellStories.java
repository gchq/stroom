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

package stroom.gwt.workbench.client.widgets.cellrenderers;

import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.ColumnSizeConstants;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.client.DataGridUtil;
import stroom.widget.datagrid.client.DataGrid;

import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Stories for [ValueSpinnerCell], the data grid's spinner, as the Jobs, Nodes and Processors lists
/// show it: a column of priorities from 1 to 100, built with `DataGridUtil.valueSpinnerColumnBuilder`.
/// It looks as the `ValueSpinner` widget does (its markup and value-spinner.css).
///
/// Each row's field is a spin button named by the column, with its value and bounds. Its arrows step
/// it while pressed and commit it when let go; its keys step and commit it while it has the focus.
/// A row whose value can't be edited shows a read-only spinner. The grid's keyboard doesn't reach the
/// field yet (it is `tabindex="-1"`), so the plays focus it with the mouse.
public final class ValueSpinnerCellStories {

    private static final String ON_CHANGE = "onChange";
    private static final String NAME = "Priority";
    private static final String INCREASE = "Increase value";
    private static final String DECREASE = "Decrease value";
    private static final long MIN = 1;
    private static final long MAX = 100;
    // The rows: Alpha and Bravo can be changed, Charlie can't
    private static final List<String> ROWS = List.of("Alpha", "Bravo", "Charlie");
    private static final String READ_ONLY_ROW = "Charlie";

    private ValueSpinnerCellStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Cell Renderers/ValueSpinnerCell", ValueSpinnerCellStories.class)
                .layout(StoryLayout.PADDED)
                // Each row's field is a spin button named by the column, with its value and bounds
                .story("InGrid", ValueSpinnerCellStories::grid)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.getAllByRole("spinbutton", NAME)).toHaveLength(ROWS.size()));
                    final Query alpha = field(play, "Alpha");
                    play.expect(alpha).toHaveValue("20");
                    play.expect(alpha).toHaveAttribute("aria-valuenow", "20");
                    play.expect(alpha).toHaveAttribute("aria-valuemin", String.valueOf(MIN));
                    play.expect(alpha).toHaveAttribute("aria-valuemax", String.valueOf(MAX));
                    // The arrows are for the pointer: no tab stop, and not heard by screen readers
                    play.expect(play.queryAllByRole("button")).toHaveLength(0);
                    play.expect(within(play, "Alpha").getByTitle(INCREASE)).toHaveAttribute("aria-hidden", "true");
                    play.expect(field(play, READ_ONLY_ROW)).toHaveAttribute("readonly");
                })
                // An arrow steps the value while pressed and commits it when let go
                .story("ArrowsStepAndCommit", ValueSpinnerCellStories::grid)
                .withPlay(play -> {
                    final Spy onChange = play.spy(ON_CHANGE);
                    play.waitFor(() -> play.expect(field(play, "Alpha")).toHaveValue("20"));
                    play.click(within(play, "Alpha").getByTitle(INCREASE));
                    play.expect(field(play, "Alpha")).toHaveValue("21");
                    play.expect(onChange).toHaveBeenLastCalledWith("Alpha=21");
                    play.click(within(play, "Alpha").getByTitle(DECREASE));
                    play.click(within(play, "Alpha").getByTitle(DECREASE));
                    play.expect(field(play, "Alpha")).toHaveValue("19");
                    play.expect(field(play, "Alpha")).toHaveAttribute("aria-valuenow", "19");
                    play.expect(onChange).toHaveBeenLastCalledWith("Alpha=19");
                    play.expect(onChange).toHaveBeenCalledTimes(3);
                })
                // While the field has the focus, Up and Down step it, Page Up and Page Down take ten
                // steps, and Home and End go to its bounds, each committing it and leaving the focus
                // in the field
                .story("KeysWhileFocused", ValueSpinnerCellStories::grid)
                .withPlay(play -> {
                    final Spy onChange = play.spy(ON_CHANGE);
                    play.waitFor(() -> play.expect(field(play, "Bravo")).toHaveValue("5"));
                    play.click(field(play, "Bravo"));
                    play.keyboard("{ArrowUp}");
                    play.expect(field(play, "Bravo")).toHaveValue("6");
                    play.expect(onChange).toHaveBeenLastCalledWith("Bravo=6");
                    play.expect(field(play, "Bravo")).toHaveFocus();
                    play.keyboard("{PageUp}");
                    play.expect(field(play, "Bravo")).toHaveValue("16");
                    play.keyboard("{End}");
                    play.expect(field(play, "Bravo")).toHaveValue(String.valueOf(MAX));
                    play.expect(field(play, "Bravo")).toHaveAttribute("aria-valuenow", String.valueOf(MAX));
                    play.keyboard("{Home}");
                    play.expect(field(play, "Bravo")).toHaveValue(String.valueOf(MIN));
                    play.expect(onChange).toHaveBeenLastCalledWith("Bravo=" + MIN);
                })
                // A typed value that isn't a whole number within the bounds is rejected, not changed
                // into one without a word (it once became 100): the value before it is put back, the
                // field is marked invalid and described by why, and why is announced; a value then
                // accepted clears it
                .story("TypedValueOutOfBoundsRejected", ValueSpinnerCellStories::grid)
                .withPlay(play -> {
                    final Spy onChange = play.spy(ON_CHANGE);
                    final String message = "\"500\" isn't allowed. Enter a whole number from 1 to 100.";
                    play.waitFor(() -> play.expect(field(play, "Alpha")).toHaveValue("20"));
                    play.clear(field(play, "Alpha"));
                    play.type(field(play, "Alpha"), "500{Enter}");
                    play.expect(field(play, "Alpha")).toHaveValue("20");
                    play.expect(onChange).not().toHaveBeenCalled();
                    play.expect(field(play, "Alpha")).toHaveAttribute("aria-invalid", "true");
                    play.expect(field(play, "Alpha")).toHaveAccessibleDescription(message);
                    play.expect(field(play, "Alpha")).toHaveAttribute("title", message);
                    play.expect(within(play, "Alpha").getByText(message)).toHaveAttribute("aria-live", "polite");

                    play.clear(field(play, "Alpha"));
                    play.type(field(play, "Alpha"), "7x{Enter}");
                    play.expect(field(play, "Alpha")).toHaveValue("20");
                    play.expect(onChange).not().toHaveBeenCalled();

                    play.clear(field(play, "Alpha"));
                    play.type(field(play, "Alpha"), "30{Enter}");
                    play.expect(field(play, "Alpha")).toHaveValue("30");
                    play.expect(onChange).toHaveBeenLastCalledWith("Alpha=30");
                    play.expect(field(play, "Alpha")).not().toHaveAttribute("aria-invalid");
                    play.expect(play.queryByText(message)).toBeNull();
                })
                // A value that can't be edited: its arrows are dimmed and do nothing
                .story("ReadOnly", ValueSpinnerCellStories::grid)
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(field(play, READ_ONLY_ROW)).toHaveValue("50"));
                    play.click(within(play, READ_ONLY_ROW).getByTitle(INCREASE));
                    play.expect(field(play, READ_ONLY_ROW)).toHaveValue("50");
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                });
    }

    /// @return The row with the name.
    private static Play within(final Play play, final String row) {
        return play.within(play.getByText(row).closest("[role='row']"));
    }

    /// @return The spinner's field in the row with the name.
    private static Query field(final Play play, final String row) {
        return within(play, row).getByRole("spinbutton", NAME);
    }

    /// A grid of [#ROWS] with a column of spinners, as Stroom's list presenters build them.
    private static Widget grid(final StoryContext context) {
        final Spy onChange = context.fn(ON_CHANGE);
        final Map<String, Integer> values = new LinkedHashMap<>();
        values.put("Alpha", 20);
        values.put("Bravo", 5);
        values.put(READ_ONLY_ROW, 50);

        final DataGrid<String> grid = new DataGrid<>();
        grid.setWidth("100%");
        grid.setHeight("100%");
        grid.addResizableColumn(DataGridUtil.textColumnBuilder((String name) -> name).build(), "Name", 150);
        grid.addColumn(DataGridUtil.valueSpinnerColumnBuilder(NAME,
                                (String row) -> {
                                    final EditableInteger value = new EditableInteger(values.get(row));
                                    value.setEditable(!READ_ONLY_ROW.equals(row));
                                    return value;
                                },
                                MIN,
                                MAX)
                        .withFieldUpdater((index, row, value) -> {
                            values.put(row, value.intValue());
                            onChange.call(row + "=" + value);
                        })
                        .build(),
                DataGridUtil.headingBuilder(NAME).build(),
                ColumnSizeConstants.VALUE_SPINNER_COL);
        grid.setRowData(ROWS);

        final SimplePanel panel = new SimplePanel(grid);
        panel.getElement().getStyle().setProperty("height", "160px");
        panel.getElement().getStyle().setProperty("width", "320px");
        return panel;
    }
}
