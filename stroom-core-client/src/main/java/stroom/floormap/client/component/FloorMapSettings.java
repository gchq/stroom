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

import stroom.cell.textfield.client.TextFieldCell;
import stroom.floormap.client.FloorMapAria;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.ValueFormat;
import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionBoxCell;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.item.client.SimpleSelectionListModel;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// The *Settings* tab of a floor map: its events and facts stores (pickers its controller
/// attaches), the format of its stored values, the Value Schema (an editable grid of
/// [FloorMapFieldMapping]s, with a toolbar to add and remove them) and the two generated reads.
///
/// Per-type presentation (shape/colour/paint order) is configured on the *Layers* panel (in the
/// Map/Editor dock), not here.
public class FloorMapSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    DropDownBox eventsStore;
    @UiField
    DropDownBox factsStore;
    @UiField
    SelectionBox<ValueFormat> valueFormat;
    @UiField
    ToolbarButton addMapping;
    @UiField
    ToolbarButton removeMapping;
    @UiField
    DataGrid<FloorMapFieldMapping> schemaGrid;
    @UiField
    TextArea histogramQuery;
    @UiField
    TextArea extentQuery;

    private final ListDataProvider<FloorMapFieldMapping> schemaDataProvider = new ListDataProvider<>();
    private final MultiSelectionModelImpl<FloorMapFieldMapping> schemaSelectionModel =
            new MultiSelectionModelImpl<>();
    // Each mapping's row key, by identity: an edit's replacement takes the key of the mapping it
    // replaces. Cleared when new mappings are set
    private final Map<FloorMapFieldMapping, Integer> rowKeys = new IdentityHashMap<>();
    private int nextRowKey;

    // Held so that the read-only state can be pushed into them: the cells render `disabled` when
    // read-only, rather than looking editable while the field updaters silently discard the edit.
    private SelectionBoxCell<String, SimpleSelectionItemWrapper<String>> roleCell;
    private TextFieldCell pathCell;
    private TextFieldCell nameCell;
    private TextFieldCell defaultCell;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, with no mappings.
    public FloorMapSettings() {
        initWidget(BINDER.createAndBindUi(this));

        valueFormat.addItems(ValueFormat.values());
        valueFormat.addValueChangeHandler(e -> onChange.run());
        histogramQuery.addValueChangeHandler(e -> onChange.run());
        extentQuery.addValueChangeHandler(e -> onChange.run());

        // An edit replaces a mapping with a new one, so rows are keyed by the mapping they
        // started as (see rowKey), not by their values, and keep their row (and focus)
        schemaGrid.setKeyProvider(this::rowKey);
        // One row at a time: the selection (which enables Remove) follows the keyboard's row
        schemaGrid.setSelectionModel(schemaSelectionModel,
                new DataGridSelectionEventManager<>(schemaGrid, schemaSelectionModel, false));
        schemaDataProvider.addDataDisplay(schemaGrid);
        initSchemaColumns();

        addMapping.setOnClick(this::onAddMapping);
        removeMapping.setOnClick(this::onRemoveMapping);
        schemaSelectionModel.addSelectionHandler(e -> updateRemoveEnabled());
    }

    /// @return The box its controller attaches the Events Store picker to.
    public DropDownBox getEventsStoreBox() {
        return eventsStore;
    }

    /// @return The box its controller attaches the Facts Store picker to.
    public DropDownBox getFactsStoreBox() {
        return factsStore;
    }

    /// @param valueFormat How each stored entry's value is encoded.
    public void setValueFormat(final ValueFormat valueFormat) {
        this.valueFormat.setValue(valueFormat);
    }

    /// @return How each stored entry's value is encoded, [ValueFormat#JSON] if none is chosen.
    public ValueFormat getValueFormat() {
        return valueFormat.getValue() != null
                ? valueFormat.getValue()
                : ValueFormat.JSON;
    }

    /// Shows a new set of mappings in the Value Schema grid, forgetting the old ones' rows.
    ///
    /// @param mappings The mappings, copied.
    public void setMappings(final List<FloorMapFieldMapping> mappings) {
        rowKeys.clear();
        replaceMappings(mappings);
    }

    /// Shows a new version of the mappings shown (e.g. with some added), keeping the rows of those
    /// that are the same.
    ///
    /// @param mappings The mappings, copied.
    public void replaceMappings(final List<FloorMapFieldMapping> mappings) {
        schemaDataProvider.setList(new ArrayList<>(mappings));
        refreshGrid();
    }

    /// @return A copy of the mappings, as edited.
    public List<FloorMapFieldMapping> getMappings() {
        return new ArrayList<>(schemaDataProvider.getList());
    }

    /// @param query The timeline histogram query.
    public void setHistogramQuery(final String query) {
        histogramQuery.setValue(query);
    }

    /// @return The timeline histogram query, as edited.
    public String getHistogramQuery() {
        return histogramQuery.getValue();
    }

    /// @param query The "Show All" extent query.
    public void setExtentQuery(final String query) {
        extentQuery.setValue(query);
    }

    /// @return The "Show All" extent query, as edited.
    public String getExtentQuery() {
        return extentQuery.getValue();
    }

    /// @param onChange Run when the user changes a setting, a mapping or a query.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        histogramQuery.setEnabled(!readOnly);
        extentQuery.setEnabled(!readOnly);
        valueFormat.setEnabled(!readOnly);
        // The cells only pick it up when they re-render
        roleCell.setReadOnly(readOnly);
        pathCell.setReadOnly(readOnly);
        nameCell.setReadOnly(readOnly);
        defaultCell.setReadOnly(readOnly);
        refreshGrid();
        addMapping.setEnabled(!readOnly);
        updateRemoveEnabled();
    }

    private void updateRemoveEnabled() {
        removeMapping.setEnabled(!isReadOnly() && schemaSelectionModel.getSelected() != null);
    }

    /// Builds the accessible name for a control in the schema grid.
    ///
    /// Every row's control would otherwise announce identically ("Role", "Path", …), so the
    /// row's JSON path is included to distinguish them.
    ///
    /// Rows whose path is null or empty fall back to their 1-based position. Note a newly
    /// added row does *not* take that fallback: [#onAddMapping()] seeds the path
    /// with the placeholder `"."`, which is non-empty, so it announces as "Role for ."
    /// until the user types a real path. Only a persisted document with a blank path reaches
    /// the positional form.
    private String schemaCellLabel(final String columnName, final int rowIndex) {
        final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
        String row = null;
        if (rowIndex >= 0 && rowIndex < list.size()) {
            final FloorMapFieldMapping mapping = list.get(rowIndex);
            if (mapping != null && mapping.getPath() != null && !mapping.getPath().isEmpty()) {
                row = mapping.getPath();
            }
        }
        if (row == null) {
            row = "row " + (rowIndex + 1);
        }
        return columnName + " for " + row;
    }

    /// Initialises the columns displayed in the Value Schema data grid.
    ///
    /// Four editable columns are added in order:
    ///
    /// 1. **Role** – a selection box ([SelectionBoxCell]) of [Role] values.
    /// 2. **Path** – a text field ([TextFieldCell]) for the JSON path.
    /// 3. **Display Name** – a text field for the label.
    /// 4. **Default** – a text field for the default value.
    ///
    /// These are Stroom's selection box and text field as cells, rather than GWT's
    /// `SelectionCell` and `EditTextCell`, both of which render `tabindex="-1"` and so cannot be
    /// reached by keyboard in a Stroom grid. Each is named for its column and row.
    ///
    /// Because [FloorMapFieldMapping] is immutable, each column's
    /// `FieldUpdater` creates a replacement instance and swaps it in
    /// the data provider list at the same index.
    private void initSchemaColumns() {
        // Role column – dropdown
        final SimpleSelectionListModel<String> roleModel = new SimpleSelectionListModel<>();
        roleModel.addItems(Arrays.stream(Role.values())
                .map(Role::name)
                .collect(Collectors.toList()));
        roleCell = new SelectionBoxCell<>(roleModel, index -> schemaCellLabel("Role", index));
        final Column<FloorMapFieldMapping, String> roleColumn =
                new Column<>(roleCell) {
                    @Override
                    public String getValue(final FloorMapFieldMapping mapping) {
                        return mapping.getRole() != null ? mapping.getRole().name() : Role.CUSTOM.name();
                    }
                };
        roleColumn.setFieldUpdater((index, mapping, val) -> {
            if (!isReadOnly()) {
                final Role newRole = Role.valueOf(val);
                replaceMapping(index, new FloorMapFieldMapping(
                        mapping.getPath(), newRole, mapping.getDisplayName(), mapping.getDefaultValue()), roleColumn);
            }
        });
        schemaGrid.addColumn(roleColumn, "Role");

        // Path column – editable text
        pathCell = new TextFieldCell(index -> schemaCellLabel("Path", index));
        final Column<FloorMapFieldMapping, String> pathColumn =
                new Column<>(pathCell) {
                    @Override
                    public String getValue(final FloorMapFieldMapping mapping) {
                        return mapping.getPath() != null ? mapping.getPath() : "";
                    }
                };
        pathColumn.setFieldUpdater((index, mapping, val) -> {
            if (!isReadOnly()) {
                replaceMapping(index, new FloorMapFieldMapping(
                        val, mapping.getRole(), mapping.getDisplayName(), mapping.getDefaultValue()), pathColumn);
            }
        });
        schemaGrid.addColumn(pathColumn, "Path");

        // Display Name column – editable text
        nameCell = new TextFieldCell(index -> schemaCellLabel("Display Name", index));
        final Column<FloorMapFieldMapping, String> nameColumn =
                new Column<>(nameCell) {
                    @Override
                    public String getValue(final FloorMapFieldMapping mapping) {
                        return mapping.getDisplayName() != null ? mapping.getDisplayName() : "";
                    }
                };
        nameColumn.setFieldUpdater((index, mapping, val) -> {
            if (!isReadOnly()) {
                replaceMapping(index, new FloorMapFieldMapping(
                        mapping.getPath(), mapping.getRole(), val, mapping.getDefaultValue()), nameColumn);
            }
        });
        schemaGrid.addColumn(nameColumn, "Display Name");

        // Default Value column – editable text
        defaultCell = new TextFieldCell(index -> schemaCellLabel("Default", index));
        final Column<FloorMapFieldMapping, String> defaultColumn =
                new Column<>(defaultCell) {
                    @Override
                    public String getValue(final FloorMapFieldMapping mapping) {
                        return mapping.getDefaultValue() != null ? mapping.getDefaultValue() : "";
                    }
                };
        defaultColumn.setFieldUpdater((index, mapping, val) -> {
            if (!isReadOnly()) {
                replaceMapping(index, new FloorMapFieldMapping(
                        mapping.getPath(), mapping.getRole(), mapping.getDisplayName(), val), defaultColumn);
            }
        });
        schemaGrid.addColumn(defaultColumn, "Default");
    }

    /// Replaces the [FloorMapFieldMapping] at the given index in the
    /// data provider list with a new instance and reports the change.
    ///
    /// It does not refresh the grid: mutating the data provider's list already redraws
    /// the affected row, and a full refresh here would be actively harmful — see the
    /// comment in the body.
    ///
    /// @param index       the zero-based position in the list
    /// @param updated     the replacement mapping
    /// @param column      the column whose control was being edited, so focus can be put
    ///         back on it if the row redraw drops it. Passed as the column rather
    ///         than an index so the position is resolved from the grid at the
    ///         moment it is needed — a hard-coded index would silently point at
    ///         the wrong cell if a column were ever inserted.
    private void replaceMapping(final int index,
                                final FloorMapFieldMapping updated,
                                final Column<FloorMapFieldMapping, String> column) {
        final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
        if (index >= 0 && index < list.size()) {
            // This redraws the row on its own: getList() hands back a ListDataProvider
            // wrapper that flags itself modified and flushes on mutation, so there is no
            // unnotified path.
            //
            // Do NOT add refreshGrid() back here. The flush above marks only this row and
            // redraws just its cells; refreshGrid()'s setRowData(0, list) covers the
            // whole range and redraws every row's. The focus guard below depends on the difference: a control
            // in another row survives a single-row redraw and keeps focus, and only
            // because of that can restoreSchemaFocusAfterRedraw leave it alone.
            // The replacement takes the replaced mapping's key. The replaced one keeps it too: the
            // grid may draw the row once more before the list change reaches it
            rowKeys.put(updated, rowKey(list.get(index)));
            list.set(index, updated);
            onChange.run();
            // The redraw replaces the row's cells, destroying the control being edited and
            // dropping focus to <body>. Without this a keyboard user was ejected from the
            // grid after every single edit and had to tab all the way back in.
            restoreSchemaFocusAfterRedraw(index, schemaGrid.getColumnIndex(column));
        }
    }

    /// Re-focuses the control at the given cell once the row redraw triggered by an edit has
    /// replaced it.
    ///
    /// Only acts if focus was actually lost — if the user committed the edit by tabbing to
    /// a control in another row, that control survives the redraw and keeps focus, and
    /// stealing it back would be worse than the problem. The cost of the conservative choice
    /// is that a Tab *within* the edited row lands back on the cell just left, so the
    /// user tabs once more; that beats guessing at their intent.
    private void restoreSchemaFocusAfterRedraw(final int rowIndex, final int columnIndex) {
        Scheduler.get().scheduleDeferred(() -> {
            final Element active = getActiveElement(Document.get());
            final boolean focusLost = active == null
                    || "body".equalsIgnoreCase(active.getTagName());
            if (focusLost) {
                focusSchemaControl(rowIndex, columnIndex);
            }
        });
    }

    private void focusSchemaControl(final int rowIndex, final int columnIndex) {
        if (rowIndex < 0 || rowIndex >= schemaGrid.getVisibleItemCount()) {
            return;
        }
        final Element row = schemaGrid.getRowElement(rowIndex);
        if (row == null) {
            return;
        }
        final Element cell = cellOf(row, columnIndex);
        if (cell == null) {
            return;
        }
        // FloorMapAria rather than a local tag scan: it is the same feature's helper, it
        // skips disabled and tabindex="-1" candidates that must not be focused, and it
        // reports whether it found anything — a silent no-op here would look exactly like
        // a working restore.
        FloorMapAria.focusFirstFocusable(cell);
    }

    // The row's cell for a column (the grid marks each cell with its column's index)
    private static Element cellOf(final Element row, final int columnIndex) {
        final String column = String.valueOf(columnIndex);
        for (Element cell = row.getFirstChildElement(); cell != null; cell = cell.getNextSiblingElement()) {
            if (column.equals(cell.getAttribute("data-col"))) {
                return cell;
            }
        }
        return null;
    }

    // A mapping's row key: the first time it is seen, the next number
    private Integer rowKey(final FloorMapFieldMapping mapping) {
        return rowKeys.computeIfAbsent(mapping, unused -> nextRowKey++);
    }

    /// Returns the document's currently focused element, or `null` if nothing is
    /// focused.
    ///
    /// **Why this is JSNI.** `document.activeElement` is not exposed by
    /// GWT: [Document] has `getElementById` and `getDocumentElement` but no
    /// `getActiveElement`, in 2.13.0 or any earlier version. There is no Java API to
    /// call, so reading it at all requires dropping to JavaScript. This project has no
    /// Elemental2 dependency, so `DomGlobal.document.activeElement` is not an option
    /// either.
    ///
    /// **Why it is duplicated rather than shared.** The obvious home for this
    /// already exists — `stroom.widget.popup.client.view.CurrentFocus` declares the
    /// identical method as `public static native`. Its enclosing class is
    /// package-private, though, so nothing outside that package can reach it, and
    /// `AbstractTabBar` carries its own copy for the same reason. This is the third such
    /// copy. Making `CurrentFocus` public, or lifting the method into a shared client
    /// utility, would let the three collapse into one; that is a change to shared widget code
    /// and deliberately not made here. (`AnnotationEditController` also reads
    /// `$doc.activeElement`, but inline inside an unrelated clipboard JSNI method, so it
    /// would not be folded in by the same change.)
    ///
    /// **Why it takes a [Document] parameter** rather than using
    /// `$doc` directly, which would be shorter: purely to match the two widget copies
    /// above, so all three read alike and a future de-duplication is a straight lift.
    ///
    /// Used by [#restoreSchemaFocusAfterRedraw(int, int)] to tell "the redraw threw
    /// focus away" from "the user moved focus somewhere deliberately", which decides whether
    /// putting focus back is a repair or a theft.
    ///
    /// @param doc the document to query, normally [Document#get()]
    /// @return the focused element, or `null` if there is none
    private static native Element getActiveElement(Document doc) /*-{
        return doc.activeElement;
    }-*/;

    /// Handles the *Add* button click by creating a new
    /// [FloorMapFieldMapping] with role [Role#CUSTOM], a placeholder
    /// path of `"."`, and a display name of `"New Field"`.
    private void onAddMapping() {
        // Add a new CUSTOM field with empty path
        final FloorMapFieldMapping newMapping =
                new FloorMapFieldMapping(".", Role.CUSTOM, "New Field", null);
        final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
        list.add(newMapping);
        refreshGrid();
        onChange.run();
    }

    /// Handles the *Remove* button click by deleting the currently selected
    /// [FloorMapFieldMapping] from the schema grid. If no row is selected it does nothing.
    private void onRemoveMapping() {
        final FloorMapFieldMapping selected = schemaSelectionModel.getSelected();
        if (selected != null) {
            final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
            list.remove(selected);
            schemaSelectionModel.clear();
            removeMapping.setEnabled(false);
            refreshGrid();
            onChange.run();
        }
    }

    /// Synchronises the schema [ListDataProvider]'s list with the
    /// [DataGrid] widget, updating both the displayed rows and the total
    /// row count so the grid reflects the current state of the data provider.
    private void refreshGrid() {
        final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
        schemaGrid.setRowData(0, list);
        schemaGrid.setRowCount(list.size(), true);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapSettings> {

    }
}
