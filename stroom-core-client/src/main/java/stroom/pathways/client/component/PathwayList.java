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

package stroom.pathways.client.component;

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.pathways.shared.otel.trace.NanoTime;
import stroom.pathways.shared.pathway.Pathway;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.dropdowntree.client.view.QuickFilterPage;
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

/// A Pathways document's pathways: a quick filter above a page of them, a row each (its root, and when
/// it was made, last changed and last used), with New, Edit and Delete; several can be selected, and
/// a double click edits one. When read only, the buttons are disabled and say so.
public class PathwayList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (read only)";

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
    DataGrid<Pathway> dataGrid;

    private final String newTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<Pathway> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onRemove = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public PathwayList() {
        initWidget(BINDER.createAndBindUi(this));
        newTitle = newButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        // Rows keyed by name, unique in the document
        dataGrid.setKeyProvider(Pathway::getName);
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

    /// Adds the columns, once.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch.
    public void addColumns(final Function<Long, String> dateTimeFormat) {
        final Column<Pathway, String> column = DataGridUtil.textColumnBuilder(Pathway::getName)
                .withSorting("Root")
                .build();
        dataGrid.addResizableColumn(column,
                "Root",
                500);
        addTimeColumn("Create Time", Pathway::getCreateTime, dateTimeFormat);
        addTimeColumn("Update Time", Pathway::getUpdateTime, dateTimeFormat);
        addTimeColumn("Last Used", Pathway::getLastUsedTime, dateTimeFormat);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<Pathway> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<Pathway> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    /// @param onNew Run when New is pressed (not when read only).
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed, or a pathway is double clicked (not when read only).
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

        final String readOnlySuffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        newButton.setTitle(newTitle + readOnlySuffix);
        editButton.setTitle(editTitle + readOnlySuffix);
        removeButton.setTitle(removeTitle + readOnlySuffix);
    }

    private void addTimeColumn(final String name,
                               final Function<Pathway, NanoTime> function,
                               final Function<Long, String> dateTimeFormat) {
        final Function<Pathway, String> valueExtractor = pathway -> {
            final NanoTime nanoTime = function.apply(pathway);
            return nanoTime == null
                    ? ""
                    : dateTimeFormat.apply(nanoTime.toEpochMillis());
        };
        final Column<Pathway, String> column = DataGridUtil
                .textColumnBuilder(valueExtractor)
                .withSorting(name)
                .build();
        dataGrid.addResizableColumn(column,
                name,
                ColumnSizeConstants.DATE_COL);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PathwayList> {

    }
}
