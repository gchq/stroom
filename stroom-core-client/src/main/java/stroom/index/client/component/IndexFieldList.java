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

package stroom.index.client.component;

import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.IndexFieldFields;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.quickfilter.client.QuickFilterContextHandler;
import stroom.widget.quickfilter.client.QuickFilterPage;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;
import java.util.function.Function;

/// An index's fields: a quick filter above a page of them, a row each (its name, type, whether it is
/// stored and indexed and has term positions, its analyser and whether it is case sensitive), with
/// New, Edit and Delete; several can be selected, and a double click edits one. When read only, the
/// buttons are disabled and say why.
public class IndexFieldList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_REASON = " (index is read only)";

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<IndexFieldImpl> dataGrid;

    private final String newTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<IndexFieldImpl> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public IndexFieldList() {
        initWidget(BINDER.createAndBindUi(this));
        newTitle = newButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        // Rows keyed by name, unique in an index
        dataGrid.setKeyProvider(IndexFieldImpl::getFldName);
        selectionModel = dataGrid.addDefaultSelectionModel(true);

        newButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onNew.run();
            }
        });
        editButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onEdit.run();
            }
        });
        removeButton.setOnClick(() -> {
            if (!isReadOnly()) {
                onRemove.run();
            }
        });

        addColumns();
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            if (!isReadOnly()) {
                enableButtons();
                if (event.getSelectionType().isDoubleSelect()) {
                    onEdit.run();
                }
            }
        });
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<IndexFieldImpl> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<IndexFieldImpl> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param indexName The index's name, which names the table.
    public void setIndexName(final String indexName) {
        dataGrid.setTableName("Index '" + indexName + "' Fields");
    }

    /// @param filterError Why the server rejected the filter, or `null` to clear it.
    public void setFilterError(final String filterError) {
        page.setFilterError(filterError);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        page.setQuickFilterContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    /// @param onNew Run when New is pressed (not when read only).
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed, or a field is double clicked (not when read only).
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Run when Delete is pressed (not when read only).
    public void setOnRemove(final Runnable onRemove) {
        this.onRemove = onRemove;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        newButton.setEnabled(!readOnly);
        final boolean enabled = !readOnly && selectionModel.getSelected() != null;
        editButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);

        // The names stay, with why the buttons are disabled for a read-only index
        final String suffix = readOnly
                ? READ_ONLY_REASON
                : "";
        newButton.setTitle(newTitle + suffix);
        editButton.setTitle(editTitle + suffix);
        removeButton.setTitle(removeTitle + suffix);
    }

    private void addColumns() {
        final Column<IndexFieldImpl, String> nameColumn = DataGridUtil.textColumnBuilder(IndexFieldImpl::getFldName)
                .withSorting(IndexFieldFields.NAME)
                .build();
        dataGrid.addResizableColumn(nameColumn,
                IndexFieldFields.NAME,
                150);
        dataGrid.sort(nameColumn);

        addTextColumn(IndexFieldFields.TYPE, IndexFieldFields.TYPE, row -> row.getFldType().getDisplayValue());
        addTextColumn(IndexFieldFields.STORE, IndexFieldFields.STORE, row -> getYesNoString(row.isStored()));
        addTextColumn(IndexFieldFields.INDEX, IndexFieldFields.INDEX, row -> getYesNoString(row.isIndexed()));
        addTextColumn(IndexFieldFields.POSITIONS, IndexFieldFields.POSITIONS,
                row -> getYesNoString(row.isTermPositions()));
        addTextColumn(IndexFieldFields.ANALYSER, IndexFieldFields.ANALYSER,
                row -> row.getAnalyzerType().getDisplayValue());
        addTextColumn("Case Sensitive", IndexFieldFields.CASE_SENSITIVE,
                row -> String.valueOf(row.isCaseSensitive()));
    }

    private void addTextColumn(final String name,
                               final String sortField,
                               final Function<IndexFieldImpl, String> function) {
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(function)
                        .withSorting(sortField)
                        .build(),
                name,
                100);
    }

    private static String getYesNoString(final boolean bool) {
        return bool
                ? "Yes"
                : "No";
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndexFieldList> {

    }
}
