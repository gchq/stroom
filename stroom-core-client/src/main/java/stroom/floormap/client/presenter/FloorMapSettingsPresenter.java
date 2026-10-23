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

import stroom.cell.textfield.client.TextFieldCell;
import stroom.docref.DocRef;
import stroom.document.client.event.DirtyUiHandlers;
import stroom.entity.client.presenter.DocPresenter;
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.floormap.client.FloorMapAria;
import stroom.floormap.client.presenter.FloorMapSettingsPresenter.FloorMapSettingsView;
import stroom.floormap.shared.FloorMapDoc;
import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.ValueFormat;
import stroom.item.client.SelectionBox;
import stroom.item.client.SelectionBoxCell;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.item.client.SimpleSelectionListModel;
import stroom.security.shared.DocumentPermission;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Contexts;
import stroom.widget.datagrid.client.DataGridWidget;
import stroom.widget.datagrid.client.DataGridWidgetSelectionEventManager;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.ListDataProvider;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Presenter for the *Settings* tab of a [FloorMapDoc].
///
/// This presenter lets users configure:
///
/// - The **Events Store** reference – a [FloorMapEventStoreDoc] read for
///   floor-map event data. Nothing writes to it from here.
/// - The **Facts Store** reference – a [SqlTemporalStoreDoc] used to
///   persist floor-map fact data.
/// - The **Value Format** – the serialisation format for map values
///   (selected from the [ValueFormat] enum).
/// - The **Value Schema** – an editable grid of
///   [FloorMapFieldMapping] entries that define the fields, their roles, paths,
///   display names, and default values.
///
/// Per-type presentation (shape/colour/paint order) is configured on the
/// *Layers* panel (in the Map/Editor dock), not here.
///
/// It extends [DocPresenter] and is embedded as a tab within
/// [FloorMapPresenter]'s tabbed view. The companion view interface is
/// [FloorMapSettingsView].
///
/// @see FloorMapPresenter
/// @see FloorMapDoc
/// @see FloorMapFieldMapping
public class FloorMapSettingsPresenter
        extends DocPresenter<FloorMapSettingsView, FloorMapDoc>
        implements DirtyUiHandlers {

    private final DocSelectionBoxPresenter eventsStoreRefPresenter;
    private final DocSelectionBoxPresenter factsStoreRefPresenter;

    // Value Format
    private final SelectionBox<ValueFormat> valueFormatListBox;

    // Value Schema grid
    private final DataGridWidget<FloorMapFieldMapping> schemaGrid;
    private final ListDataProvider<FloorMapFieldMapping> schemaDataProvider;
    private final MultiSelectionModelImpl<FloorMapFieldMapping> schemaSelectionModel;
    // Each mapping's row key, by identity: an edit's replacement takes the key of the mapping it
    // replaces. Cleared when a document is read
    private final Map<FloorMapFieldMapping, Integer> rowKeys = new IdentityHashMap<>();
    private int nextRowKey;

    // Held so that onRead can push the read-only state into them: the cells render
    // `disabled` when read-only, rather than looking editable while the field updaters
    // silently discard the edit.
    private SelectionBoxCell<String, SimpleSelectionItemWrapper<String>> roleCell;
    private TextFieldCell pathCell;
    private TextFieldCell nameCell;
    private TextFieldCell defaultCell;
    private final ToolbarButton addButton;
    private final ToolbarButton removeButton;
    private boolean readOnly;

    /// Set once the Editor tab enables area support on this document; onRead
    /// then re-applies [#applyAreaPatch()] so the grid never reverts the
    /// upgrade before it is persisted.
    private boolean areaPatchActive;

    /// Set once the Editor tab enables status support on this document; onRead
    /// then re-applies [#applyStatusPatch()], as for [#areaPatchActive].
    private boolean statusPatchActive;

    @Inject
    public FloorMapSettingsPresenter(final EventBus eventBus,
                                     final FloorMapSettingsView view,
                                     final Provider<DocSelectionBoxPresenter> docSelectionBoxPresenterProvider) {
        super(eventBus, view);
        // Read only as its document is
        watch(Contexts.READ_ONLY, getView()::onReadOnly);

        view.setUiHandlers(this);

        this.eventsStoreRefPresenter = make(docSelectionBoxPresenterProvider);
        this.eventsStoreRefPresenter.setCaption("Choose Events Store");
        this.eventsStoreRefPresenter.setIncludedTypes(FloorMapEventStoreDoc.TYPE);
        this.eventsStoreRefPresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setEventsStoreRefView(this.eventsStoreRefPresenter.getView());

        this.factsStoreRefPresenter = make(docSelectionBoxPresenterProvider);
        this.factsStoreRefPresenter.setCaption("Choose Facts Store");
        this.factsStoreRefPresenter.setIncludedTypes(SqlTemporalStoreDoc.TYPE);
        this.factsStoreRefPresenter.setRequiredPermissions(DocumentPermission.USE);
        view.setFactsStoreRefView(this.factsStoreRefPresenter.getView());

        // Value Format dropdown
        valueFormatListBox = new SelectionBox<>();
        valueFormatListBox.addItems(ValueFormat.values());
        //noinspection unused e
        valueFormatListBox.addValueChangeHandler(e -> onChange());
        view.setValueFormatWidget(valueFormatListBox);

        // Value Schema grid
        schemaGrid = new DataGridWidget<>(this);
        schemaGrid.setTableName("Value Schema");
        // An edit replaces a mapping with a new one, so rows are keyed by the mapping they
        // started as (see rowKey), not by their values, and keep their row (and focus)
        schemaGrid.setKeyProvider(this::rowKey);
        // One row at a time: the selection (which enables Remove) follows the keyboard's row
        schemaSelectionModel = new MultiSelectionModelImpl<>();
        schemaGrid.setSelectionModel(schemaSelectionModel,
                new DataGridWidgetSelectionEventManager<>(schemaGrid, schemaSelectionModel, false));
        schemaDataProvider = new ListDataProvider<>();
        schemaDataProvider.addDataDisplay(schemaGrid);
        initSchemaColumns();

        // Schema toolbar
        final Toolbar toolbar = new Toolbar("Field mapping actions");
        addButton = toolbar.button(SvgImage.ADD, "Add Field Mapping", this::onAddMapping);
        removeButton = toolbar.button(SvgImage.DELETE, "Remove Field Mapping", this::onRemoveMapping);
        removeButton.setEnabled(false);

        view.setSchemaToolbar(toolbar);
        view.setSchemaGrid(schemaGrid);

        //noinspection unused e
        // No validation on selection: the picker only offers FloorMapEventStoreDoc, and that
        // type fixes its own state type, so there is no longer a wrong store to choose.
        //noinspection unused e
        own(eventsStoreRefPresenter.addSelectionListener(e -> onChange()));
        //noinspection unused e
        own(factsStoreRefPresenter.addSelectionListener(e -> onChange()));
        //noinspection unused e
        own(schemaSelectionModel.addSelectionHandler(
                e -> removeButton.setEnabled(schemaSelectionModel.getSelected() != null)));
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
            if (!readOnly) {
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
            if (!readOnly) {
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
            if (!readOnly) {
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
            if (!readOnly) {
                replaceMapping(index, new FloorMapFieldMapping(
                        mapping.getPath(), mapping.getRole(), mapping.getDisplayName(), val), defaultColumn);
            }
        });
        schemaGrid.addColumn(defaultColumn, "Default");
    }

    /// Replaces the [FloorMapFieldMapping] at the given index in the
    /// data provider list with a new instance and marks the document as dirty.
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
            onChange();
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
    /// and deliberately not made here. (`AnnotationEditPresenter` also reads
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
    ///
    /// The new mapping is appended to the schema data provider, the grid is
    /// refreshed, and the document is marked as dirty via [#onChange()].
    private void onAddMapping() {
        // Add a new CUSTOM field with empty path
        final FloorMapFieldMapping newMapping =
                new FloorMapFieldMapping(".", Role.CUSTOM, "New Field", null);
        final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
        list.add(newMapping);
        refreshGrid();
        onChange();
    }

    /// Handles the *Remove* button click by deleting the currently selected
    /// [FloorMapFieldMapping] from the schema grid.
    ///
    /// If no row is selected the method is a no-op. After removal the selection
    /// is cleared, the remove button is disabled, the grid is refreshed, and the
    /// document is marked as dirty via [#onChange()].
    private void onRemoveMapping() {
        final FloorMapFieldMapping selected = schemaSelectionModel.getSelected();
        if (selected != null) {
            final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
            list.remove(selected);
            schemaSelectionModel.clear();
            removeButton.setEnabled(false);
            refreshGrid();
            onChange();
        }
    }

    /// Synchronises the schema [ListDataProvider]'s list with the
    /// [DataGridWidget] widget, updating both the displayed rows and the total
    /// row count so the grid reflects the current state of the data provider.
    private void refreshGrid() {
        final List<FloorMapFieldMapping> list = schemaDataProvider.getList();
        schemaGrid.setRowData(0, list);
        schemaGrid.setRowCount(list.size(), true);
    }

    /// Pushes the read-only state into the schema grid's cells. Call before
    /// [#refreshGrid()], since the cells only pick it up when they re-render.
    private void setSchemaCellsReadOnly(final boolean readOnly) {
        roleCell.setReadOnly(readOnly);
        pathCell.setReadOnly(readOnly);
        nameCell.setReadOnly(readOnly);
        defaultCell.setReadOnly(readOnly);
    }

    /// Populates the view from a persisted [FloorMapDoc].
    ///
    /// The method performs the following steps:
    ///
    /// 1. Sets the Events Store and Facts Store selection boxes to the
    ///    references stored in the document and enables/disables them
    ///    according to the `readOnly` flag.
    /// 2. Selects the matching [ValueFormat] entry in the dropdown.
    /// 3. Copies the document's value schema list into the data provider and
    ///    refreshes the grid.
    /// 4. Enables or disables the *Add* and *Remove* toolbar
    ///    buttons based on whether the document is read only ([#isReadOnly()]).
    ///
    /// @param docRef      the [DocRef] identifying the document being read
    /// @param floorMapDoc the persisted document whose settings are displayed
    @Override
    protected void onRead(final DocRef docRef, final FloorMapDoc floorMapDoc) {
        this.readOnly = isReadOnly();
        eventsStoreRefPresenter.setSelectedEntityReference(floorMapDoc.getEventsStoreRef(), true);
        eventsStoreRefPresenter.setEnabled(!isReadOnly());
        factsStoreRefPresenter.setSelectedEntityReference(floorMapDoc.getFactsStoreRef(), true);
        factsStoreRefPresenter.setEnabled(!isReadOnly());

        // Shown with their defaults rather than blank, so the shape a user is editing is visible.
        getView().setHistogramQuery(NullSafe.isBlankString(floorMapDoc.getHistogramQuery())
                ? FloorMapQueryBuilder.defaultHistogramQuery()
                : floorMapDoc.getHistogramQuery());
        getView().setExtentQuery(NullSafe.isBlankString(floorMapDoc.getExtentQuery())
                ? FloorMapQueryBuilder.defaultExtentQuery()
                : floorMapDoc.getExtentQuery());

        // Value Format
        valueFormatListBox.setValue(floorMapDoc.getValueFormat());
        valueFormatListBox.setEnabled(!isReadOnly());

        // Value Schema
        final List<FloorMapFieldMapping> schema =
                new ArrayList<>(floorMapDoc.getValueSchema());
        // A new document's mappings: forget the old ones' keys
        rowKeys.clear();
        schemaDataProvider.setList(schema);
        setSchemaCellsReadOnly(isReadOnly());
        refreshGrid();

        addButton.setEnabled(!isReadOnly());
        removeButton.setEnabled(!isReadOnly() && schemaSelectionModel.getSelected() != null);

        // Keep an Editor-tab area-support upgrade visible in the schema grid: the
        // document just read may predate the upgrade (it is only persisted on
        // save), and this tab's onWrite replaces the schema wholesale.
        if (areaPatchActive) {
            applyAreaPatch();
        }
        if (statusPatchActive) {
            applyStatusPatch();
        }

        // Note: measurementUnits is deliberately NOT read or written by this tab.
        // A map's scale is set with the Editor's Set Scale tool and staged in its
        // doc session; because nothing here touches the field, copy() carries it
        // through untouched and no cross-tab patching is needed.
    }

    /// Merges the default area mappings (`GEOMETRY`/`FILL`/
    /// `OPACITY`) into the Value Schema grid, if absent. Called when the
    /// Editor tab enables area support on this document — this tab writes
    /// `valueSchema` wholesale from its grid state on save, so an unpatched
    /// grid would silently revert the upgrade. Idempotent; stays active so
    /// subsequent reads re-apply it until the upgrade is persisted (after which
    /// the merge is a no-op). The matching `"area"` type style is handled
    /// by the Layers panel.
    public void applyAreaPatch() {
        areaPatchActive = true;
        final ValueFormat vf = getEntity() != null
                ? getEntity().getValueFormat()
                : ValueFormat.JSON;
        schemaDataProvider.setList(new ArrayList<>(
                FloorMapFieldMapping.withAreaMappings(schemaDataProvider.getList(), vf)));
        refreshGrid();
    }

    /// Merges the default `STATUS` mapping into the Value Schema grid, if
    /// absent. Called when the Editor tab enables status support on this
    /// document, for the same reason as [#applyAreaPatch()]: this tab writes
    /// `valueSchema` wholesale from its grid state on save, so an unpatched
    /// grid would silently revert the upgrade. Idempotent; stays active so
    /// subsequent reads re-apply it until the upgrade is persisted.
    public void applyStatusPatch() {
        statusPatchActive = true;
        final ValueFormat vf = getEntity() != null
                ? getEntity().getValueFormat()
                : ValueFormat.JSON;
        schemaDataProvider.setList(new ArrayList<>(
                FloorMapFieldMapping.withStatusMapping(schemaDataProvider.getList(), vf)));
        refreshGrid();
    }

    /// Reads the current UI state and produces an updated [FloorMapDoc].
    ///
    /// The selected value format is parsed from the dropdown; if parsing fails
    /// it falls back to [ValueFormat#JSON]. A new [FloorMapDoc] is
    /// built via the document's copy-builder, incorporating the currently selected
    /// events store reference, facts store reference, value format, and value
    /// schema list.
    ///
    /// @param doc the existing document to base the updated copy on
    /// @return a new [FloorMapDoc] instance reflecting the current UI state
    @Override
    protected FloorMapDoc onWrite(final FloorMapDoc doc) {
        // Read value format from dropdown
        final ValueFormat vf = valueFormatListBox.getValue() != null
                ? valueFormatListBox.getValue()
                : ValueFormat.JSON;

        // Note: typeStyles is intentionally NOT written here — it is owned by
        // the Layers panel (persisted via the Editor tab). copy() preserves the
        // document's existing typeStyles.
        return doc.copy()
                .eventsStoreRef(eventsStoreRefPresenter.getSelectedEntityReference())
                .factsStoreRef(factsStoreRefPresenter.getSelectedEntityReference())
                .valueFormat(vf)
                .valueSchema(new ArrayList<>(schemaDataProvider.getList()))
                .histogramQuery(getView().getHistogramQuery())
                .extentQuery(getView().getExtentQuery())
                .build();
    }

    /// {@inheritDoc}
    ///
    /// Delegates to [#onChange()] to propagate the dirty state up to
    /// the parent [FloorMapPresenter], which manages the document
    /// save lifecycle.
    @Override
    public void onDirty() {
        onChange();
    }

    /// View contract for [FloorMapSettingsPresenter].
    ///
    /// Provides slots for the events/facts store selection widgets, the value
    /// format dropdown, and the value schema grid with its toolbar.
    public interface FloorMapSettingsView extends View, HasUiHandlers<DirtyUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);

        /// Sets the view widget for the Events Store document-selection box.
        ///
        /// @param view the [DocSelectionBoxPresenter] view
        void setEventsStoreRefView(View view);

        /// Sets the view widget for the Facts Store document-selection box.
        ///
        /// @param view the [DocSelectionBoxPresenter] view
        void setFactsStoreRefView(View view);

        /// The timeline histogram query, as edited.
        String getHistogramQuery();

        void setHistogramQuery(String query);

        /// The "Show All" extent query, as edited.
        String getExtentQuery();

        void setExtentQuery(String query);

        /// Sets the widget used for the Value Format dropdown.
        ///
        /// @param widget the selection box
        void setValueFormatWidget(Widget widget);

        /// Sets the toolbar widget (add/remove buttons) above the schema grid.
        ///
        /// @param toolbar the [Toolbar] widget
        void setSchemaToolbar(Widget toolbar);

        /// Sets the data grid widget that displays the Value Schema mappings.
        ///
        /// @param grid the [DataGridWidget] widget
        void setSchemaGrid(Widget grid);
    }
}
