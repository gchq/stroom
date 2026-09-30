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

package stroom.floormap.client.presenter;

import stroom.data.grid.client.MyDataGrid;
import stroom.editor.client.presenter.ChangeCurrentPreferencesEvent;
import stroom.floormap.client.FloorMapEditorHelp;
import stroom.floormap.client.editor.FloorMapEditorModel;
import stroom.floormap.client.presenter.FloorMapTimeListPresenter.FloorMapTimeListView;
import stroom.floormap.shared.FloorMapJsonKeys;
import stroom.preferences.client.DateTimeFormatter;
import stroom.svg.client.SvgPresets;
import stroom.util.client.JSONUtil;
import stroom.util.shared.TemporalEntry;
import stroom.widget.button.client.ButtonPanel;
import stroom.widget.button.client.ButtonView;
import stroom.widget.help.client.HelpButton;

import com.google.gwt.dom.client.TableRowElement;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.TextColumn;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.gwt.view.client.SingleSelectionModel;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/// Presenter for the Time List panel — the centre column of the Editor tab's
/// bottom strip.
///
/// Shows every [TemporalEntry] for the currently selected fact,
/// sorted ascending by effective time (oldest first, most recent last).
/// When a fact is selected, [#setData(List)] populates the grid.
///
/// ### Toolbar
///
/// Edit, Add, and Delete buttons. Edit and Delete are enabled only when a row
/// is selected. Clicks are reported via consumers that the Editor presenter sets.
public class FloorMapTimeListPresenter extends MyPresenterWidget<FloorMapTimeListView> {

    private final MyDataGrid<TemporalEntry> dataGrid;
    private final ListDataProvider<TemporalEntry> dataProvider = new ListDataProvider<>();
    private final SingleSelectionModel<TemporalEntry> selectionModel = new SingleSelectionModel<>();

    private final ButtonView editButton;
    private final ButtonView addButton;
    private final ButtonView deleteButton;

    private Consumer<TemporalEntry> selectionConsumer;
    private Consumer<TemporalEntry> editConsumer;
    private Runnable addConsumer;
    private Consumer<TemporalEntry> deleteConsumer;

    private final DateTimeFormatter dateTimeFormatter;

    @Inject
    public FloorMapTimeListPresenter(final EventBus eventBus,
                                     final FloorMapTimeListView view,
                                     final DateTimeFormatter dateTimeFormatter) {
        super(eventBus, view);
        this.dateTimeFormatter = dateTimeFormatter;

        dataGrid = new MyDataGrid<>(this);
        dataGrid.setSelectionModel(selectionModel);
        view.setGridView(dataGrid);
        initGridColumns();
        dataProvider.addDataDisplay(dataGrid);

        final ButtonPanel toolbar = new ButtonPanel();
        editButton = toolbar.addButton(SvgPresets.EDIT);
        editButton.setEnabled(false);
        addButton = toolbar.addButton(SvgPresets.ADD);
        deleteButton = toolbar.addButton(SvgPresets.DELETE);
        deleteButton.setEnabled(false);

        // Help button (Editor-only panel).
        final HelpButton helpButton = HelpButton.create("Time List help");
        helpButton.setHelpContentHeading("Time List");
        helpButton.setHelpContent(FloorMapEditorHelp.timeList());
        toolbar.addButton(helpButton);

        view.setToolbar(toolbar);
    }

    @Override
    protected void onBind() {
        super.onBind();

        // The Effective Time column is rendered from the user's date/time
        // preference, so a change to it has to redraw the rows already shown.
        //noinspection unused e
        registerHandler(getEventBus().addHandler(ChangeCurrentPreferencesEvent.getType(),
                e -> dataGrid.redraw()));

        //noinspection unused
        registerHandler(selectionModel.addSelectionChangeHandler(e -> {
            final TemporalEntry selected = selectionModel.getSelectedObject();
            editButton.setEnabled(selected != null);
            deleteButton.setEnabled(selected != null);
            if (selectionConsumer != null) {
                selectionConsumer.accept(selected);
            }
            // Scroll selected row into view; headers + toolbar remain fixed
            if (selected != null) {
                final List<TemporalEntry> list = dataProvider.getList();
                if (list != null) {
                    final int index = list.indexOf(selected);
                    if (index >= 0) {
                        com.google.gwt.core.client.Scheduler.get().scheduleDeferred(() -> {
                            if (index < dataGrid.getVisibleItemCount()) {
                                try {
                                    final TableRowElement rowEl = dataGrid.getRowElement(index);
                                    if (rowEl != null) {
                                        stroom.widget.util.client.ElementUtil
                                                .scrollIntoViewNearest(rowEl);
                                    }
                                } catch (final Exception ex) {
                                    // Ignore
                                }
                            }
                        });
                    }
                }
            }
        }));

        //noinspection unused
        registerHandler(editButton.addClickHandler(e -> {
            if (editConsumer != null) {
                final TemporalEntry selected = selectionModel.getSelectedObject();
                if (selected != null) {
                    editConsumer.accept(selected);
                }
            }
        }));

        //noinspection unused
        registerHandler(addButton.addClickHandler(e -> {
            if (addConsumer != null) {
                addConsumer.run();
            }
        }));

        //noinspection unused
        registerHandler(deleteButton.addClickHandler(e -> {
            if (deleteConsumer != null) {
                final TemporalEntry selected = selectionModel.getSelectedObject();
                if (selected != null) {
                    deleteConsumer.accept(selected);
                }
            }
        }));
    }

    // -----------------------------------------------------------------------
    // Data
    // -----------------------------------------------------------------------

    /// Replaces the grid contents with the supplied entries.
    /// The entries should already be sorted ascending by effective time.
    ///
    /// @param entries entries to display; may be `null` (treated as empty)
    public void setData(final List<TemporalEntry> entries) {
        selectionModel.clear();
        editButton.setEnabled(false);
        deleteButton.setEnabled(false);
        if (entries != null) {
            dataProvider.setList(entries);
            dataGrid.setRowData(0, entries);
        } else {
            dataProvider.setList(Collections.emptyList());
            dataGrid.setRowData(0, Collections.emptyList());
        }
    }

    /// Selects the entry that would be active at `timeMs` — i.e. the
    /// most recent entry whose effective time is at or before `timeMs`.
    ///
    /// If all entries are later than `timeMs` the selection is **cleared**
    /// and the Edit and Delete buttons are disabled - no entry is active at that time, so the
    /// Properties form has nothing to act on, matching the canvas which also shows nothing for
    /// this object then. No-op when the list is empty.
    ///
    /// @param timeMs the point in time to match against
    public void selectAtTime(final long timeMs) {
        final List<TemporalEntry> list = dataProvider.getList();
        if (list == null || list.isEmpty()) {
            return;
        }
        // List is sorted ascending by effective time; find the last entry whose
        // time is <= timeMs using the shared, unit-tested selection logic.
        final int index = FloorMapEditorModel.findActiveIndexAtTime(list, timeMs);
        if (index >= 0) {
            selectionModel.setSelected(list.get(index), true);
            // Explicitly enable the edit and delete buttons: setData() disables them
            // synchronously, and GWT's deferred SelectionChangeEvent may not
            // re-enable them reliably when clear() and setSelected() are coalesced.
            editButton.setEnabled(true);
            deleteButton.setEnabled(true);
        } else {
            // Timeline is before all entries — no entry is active at this time.
            // Clear the selection so the Properties form is disabled, matching
            // the canvas which also shows nothing for this object at this time.
            selectionModel.clear();
            editButton.setEnabled(false);
            deleteButton.setEnabled(false);
        }
    }

    /// Selects the entry at the given index.
    ///
    /// If the index is out of range the selection is clamped to the nearest
    /// valid row (first or last). No-op when the list is empty.
    ///
    /// @param index the zero-based row index to select
    public void selectAtIndex(final int index) {
        final List<TemporalEntry> list = dataProvider.getList();
        if (list == null || list.isEmpty()) {
            selectionModel.clear();
            editButton.setEnabled(false);
            deleteButton.setEnabled(false);
            return;
        }
        final int clamped = Math.max(0, Math.min(index, list.size() - 1));
        final TemporalEntry entry = list.get(clamped);
        selectionModel.setSelected(entry, true);
        editButton.setEnabled(true);
        deleteButton.setEnabled(true);
    }

    // -----------------------------------------------------------------------
    // Consumer wiring
    // -----------------------------------------------------------------------

    /// Sets the consumer called whenever the row selection changes.
    ///
    /// @param selectionConsumer called with the selected entry, or `null`
    ///         when the selection is cleared
    public void setSelectionConsumer(final Consumer<TemporalEntry> selectionConsumer) {
        this.selectionConsumer = selectionConsumer;
    }

    /// Sets the action to perform when the Edit button is clicked.
    /// Called with the currently selected entry.
    ///
    /// @param editConsumer called with the entry to edit
    public void setEditConsumer(final Consumer<TemporalEntry> editConsumer) {
        this.editConsumer = editConsumer;
    }

    /// Sets the action to perform when the Add button is clicked.
    ///
    /// @param addConsumer the runnable to invoke on Add
    public void setAddConsumer(final Runnable addConsumer) {
        this.addConsumer = addConsumer;
    }

    /// Sets the action to perform when the Delete button is clicked.
    /// Called with the currently selected entry.
    ///
    /// @param deleteConsumer called with the entry to delete
    public void setDeleteConsumer(final Consumer<TemporalEntry> deleteConsumer) {
        this.deleteConsumer = deleteConsumer;
    }

    // -----------------------------------------------------------------------
    // Grid columns
    // -----------------------------------------------------------------------

    private void initGridColumns() {
        // No key column: every row in this list belongs to the selected fact by
        // construction, since FloorMapEditorModel.resolveEntryKey derives an edit's
        // key from the entry being edited rather than from the current selection.
        final Column<TemporalEntry, String> timeColumn = new TextColumn<>() {
            @Override
            public String getValue(final TemporalEntry entry) {
                // The platform formatter, as every other grid in Stroom uses: it honours
                // the user's configured time zone and pattern. This column used
                // new Date(ms).toString(), which is browser-local and verbose, so the
                // effective times here disagreed with the timeline, the canvas and the
                // user's own preference.
                return dateTimeFormatter.format(entry.getEffectiveTimeMs());
            }
        };
        dataGrid.addColumn(timeColumn, "Effective Time");

        final Column<TemporalEntry, String> summaryColumn = new TextColumn<>() {
            @Override
            public String getValue(final TemporalEntry entry) {
                return extractSummary(entry);
            }
        };
        dataGrid.addColumn(summaryColumn, "Summary");
    }

    private static String extractSummary(final TemporalEntry entry) {
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
                    return sb.length() > 0 ? sb.toString() : value;
                }
            }
        } catch (final Exception ex) {
            // Fall through to raw value
        }
        return value;
    }

    // -----------------------------------------------------------------------
    // View interface
    // -----------------------------------------------------------------------

    public interface FloorMapTimeListView extends View {

        void setGridView(Widget gridWidget);

        void setToolbar(Widget toolbarWidget);

    }
}
