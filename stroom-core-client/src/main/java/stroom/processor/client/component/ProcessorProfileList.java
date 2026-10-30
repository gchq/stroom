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

package stroom.processor.client.component;

import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.processor.shared.ProcessorProfile;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

/// The processor profiles, a row each (its name and node group), sorted by a column, several of which
/// can be selected; New, Edit (or a double click) and Delete act on them.
public class ProcessorProfileList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<ProcessorProfile> dataGrid;

    private final MultiSelectionModelImpl<ProcessorProfile> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onDelete = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list.
    public ProcessorProfileList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(ProcessorProfile::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        addColumns();
        newButton.setOnClick(() -> onNew.run());
        editButton.setOnClick(() -> onEdit.run());
        deleteButton.setOnClick(() -> onDelete.run());
        selectionModel.addSelectionHandler(event -> {
            final boolean selected = selectionModel.getSelected() != null;
            editButton.setEnabled(selected);
            deleteButton.setEnabled(selected);
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit.run();
            }
        });
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ProcessorProfile> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @return The selection.
    public MultiSelectionModel<ProcessorProfile> getSelectionModel() {
        return selectionModel;
    }

    /// @param onNew Run when New is pressed.
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed, or a row is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private void addColumns() {
        // Name. By name, unless another sort is chosen
        final Column<ProcessorProfile, String> nameColumn = DataGridUtil.textColumnBuilder(
                        DataGridUtil.toStringFunc(ProcessorProfile::getName))
                .withSorting(FindProcessorProfileRequest.FIELD_ID_NAME)
                .build();
        dataGrid.addResizableColumn(
                nameColumn,
                DataGridUtil.headingBuilder(FindProcessorProfileRequest.FIELD_ID_NAME)
                        .withToolTip("The name of the processor profile.")
                        .build(),
                300);
        dataGrid.setDefaultSort(new ColumnSortInfo(nameColumn, true));
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(ProcessorProfile::getNodeGroupName))
                        .withSorting(FindProcessorProfileRequest.FIELD_ID_NODE_GROUP)
                        .build(),
                DataGridUtil.headingBuilder(FindProcessorProfileRequest.FIELD_ID_NODE_GROUP)
                        .withToolTip("The name of the node group.")
                        .build(),
                300);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorProfileList> {

    }
}
