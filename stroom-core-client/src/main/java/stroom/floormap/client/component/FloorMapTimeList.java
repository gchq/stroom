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

package stroom.floormap.client.component;

import stroom.floormap.client.model.FloorMapEditorModel;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.util.client.JSONUtil;
import stroom.util.shared.TemporalEntry;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.SingleSelectionEventManager;
import stroom.widget.util.client.ElementUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// The floor map editor's Time List panel: the selected fact's versions, a row per effective time,
/// one of which can be selected, and the actions on it.
public class FloorMapTimeList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    ToolbarButton helpButton;
    @UiField
    DataGrid<TemporalEntry> dataGrid;

    private final ListDataProvider<TemporalEntry> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<TemporalEntry> selectionModel = new SingleSelectionModel<>();

    private Function<Long, String> timeFormatter = String::valueOf;
    // Whether a version ends its fact: the editor, which has the value schema, says
    private Predicate<TemporalEntry> deletedTest = entry -> false;
    private Consumer<TemporalEntry> onSelectionChange = entry -> {
    };
    private Consumer<TemporalEntry> onEdit = entry -> {
    };
    private Runnable onAdd = () -> {
    };
    private Consumer<TemporalEntry> onDelete = entry -> {
    };

    /// Makes an empty panel.
    public FloorMapTimeList() {
        initWidget(BINDER.createAndBindUi(this));
        // The list holds one fact's entries, one per time
        dataGrid.setKeyProvider(TemporalEntry::getEffectiveTimeMs);
        dataGrid.setSelectionModel(selectionModel, new SingleSelectionEventManager<>(dataGrid, selectionModel));
        addColumns();
        dataProvider.addDataDisplay(dataGrid);
        editButton.setOnClick(() -> withSelected(onEdit));
        addButton.setOnClick(() -> onAdd.run());
        deleteButton.setOnClick(() -> withSelected(onDelete));
        selectionModel.addSelectionChangeHandler(e -> onSelection());
    }

    /// @return The help button, for its controller to show the help from.
    public ToolbarButton getHelpButton() {
        return helpButton;
    }

    /// @param timeFormatter Shows an effective time (ms since the epoch), e.g. in the user's
    ///                      date and time preference.
    public void setTimeFormatter(final Function<Long, String> timeFormatter) {
        this.timeFormatter = timeFormatter;
    }

    /// Draws the rows again, e.g. once the time format has changed.
    public void redraw() {
        dataGrid.redraw();
    }

    /// Shows the entries in place of those shown, with none selected.
    ///
    /// @param entries The entries, by effective time, earliest first; null for none.
    public void setEntries(final List<TemporalEntry> entries) {
        selectionModel.clear();
        setButtonsEnabled(false);
        final List<TemporalEntry> list = entries == null
                ? Collections.emptyList()
                : entries;
        dataProvider.setList(list);
        dataGrid.setRowData(0, list);
    }

    /// Selects the entry active at a time: the latest whose effective time is at or before it. If
    /// every entry is later, none is (nothing of the fact is shown then, as on the canvas). Nothing
    /// happens when there are no entries.
    ///
    /// @param timeMs The time.
    public void selectAtTime(final long timeMs) {
        final List<TemporalEntry> list = dataProvider.getList();
        if (list == null || list.isEmpty()) {
            return;
        }
        final int index = FloorMapEditorModel.findActiveIndexAtTime(list, timeMs);
        if (index >= 0) {
            selectionModel.setSelected(list.get(index), true);
            // setEntries disabled them, and the deferred selection change may not enable them
            // again when a clear and a select are coalesced
            setButtonsEnabled(true);
        } else {
            selectionModel.clear();
            setButtonsEnabled(false);
        }
    }

    /// Selects the entry at a row, or the nearest row there is.
    ///
    /// @param index The row, from 0.
    public void selectAtIndex(final int index) {
        final List<TemporalEntry> list = dataProvider.getList();
        if (list == null || list.isEmpty()) {
            selectionModel.clear();
            setButtonsEnabled(false);
            return;
        }
        final int clamped = Math.max(0, Math.min(index, list.size() - 1));
        selectionModel.setSelected(list.get(clamped), true);
        setButtonsEnabled(true);
    }

    /// @param deletedTest Whether a version ends its fact, which its Summary then says; null for
    ///                    none.
    public void setDeletedTest(final Predicate<TemporalEntry> deletedTest) {
        this.deletedTest = deletedTest != null
                ? deletedTest
                : entry -> false;
    }

    /// @param onSelectionChange Given the entry selected, or null for none, each time it changes.
    public void setOnSelectionChange(final Consumer<TemporalEntry> onSelectionChange) {
        this.onSelectionChange = onSelectionChange;
    }

    /// @param onEdit Given the selected entry when Edit is pressed.
    public void setOnEdit(final Consumer<TemporalEntry> onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onAdd Run when Add is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onDelete Given the selected entry when Delete is pressed.
    public void setOnDelete(final Consumer<TemporalEntry> onDelete) {
        this.onDelete = onDelete;
    }

    private void setButtonsEnabled(final boolean enabled) {
        editButton.setEnabled(enabled);
        deleteButton.setEnabled(enabled);
    }

    private void withSelected(final Consumer<TemporalEntry> action) {
        final TemporalEntry selected = selectionModel.getSelectedObject();
        if (selected != null) {
            action.accept(selected);
        }
    }

    private void onSelection() {
        final TemporalEntry selected = selectionModel.getSelectedObject();
        setButtonsEnabled(selected != null);
        onSelectionChange.accept(selected);
        if (selected != null) {
            scrollIntoView(selected);
        }
    }

    // The selected row is scrolled into view once it is drawn (the toolbar and headings stay put)
    private void scrollIntoView(final TemporalEntry entry) {
        final List<TemporalEntry> list = dataProvider.getList();
        final int index = list == null
                ? -1
                : list.indexOf(entry);
        if (index >= 0) {
            Scheduler.get().scheduleDeferred(() -> {
                if (index < dataGrid.getVisibleItemCount()) {
                    try {
                        final Element row = dataGrid.getRowElement(index);
                        if (row != null) {
                            ElementUtil.scrollIntoViewNearest(row);
                        }
                    } catch (final RuntimeException e) {
                        // Not scrolling into view does no harm
                    }
                }
            });
        }
    }

    private void addColumns() {
        // No key column: every row is the selected fact's (an edit's key comes from the entry
        // edited, FloorMapEditorModel.resolveEntryKey)
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final TemporalEntry entry) {
                return timeFormatter.apply(entry.getEffectiveTimeMs());
            }
        }, "Effective Time");
        dataGrid.addColumn(new TextColumn<>() {
            @Override
            public String getValue(final TemporalEntry entry) {
                // A deleted version usually carries the fact's last state too, so it says so first:
                // otherwise it reads exactly like the version before it
                final String summary = summaryOf(entry);
                return deletedTest.test(entry)
                        ? "Deleted - " + summary
                        : summary;
            }
        }, "Summary");
    }

    /// @param entry A fact's version.
    /// @return Its type and name ("type - name"), or its raw value if it has neither.
    static String summaryOf(final TemporalEntry entry) {
        final String value = entry.getValue();
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            if (value.trim().startsWith("{")) {
                final JSONObject json = JSONUtil.getObject(JSONUtil.parse(value));
                if (json != null) {
                    final String name = JSONUtil.getString(json.get(FloorMapJsonKeys.NAME));
                    final String type = JSONUtil.getString(json.get(FloorMapJsonKeys.TYPE));
                    final StringBuilder sb = new StringBuilder();
                    if (type != null && !type.isEmpty()) {
                        sb.append(type);
                    }
                    if (name != null && !name.isEmpty()) {
                        //noinspection SizeReplaceableByIsEmpty Not in GWT
                        if (sb.length() > 0) {
                            sb.append(" - ");
                        }
                        sb.append(name);
                    }
                    //noinspection SizeReplaceableByIsEmpty Not in GWT
                    return sb.length() > 0
                            ? sb.toString()
                            : value;
                }
            }
        } catch (final RuntimeException e) {
            // The raw value, then
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapTimeList> {

    }
}
