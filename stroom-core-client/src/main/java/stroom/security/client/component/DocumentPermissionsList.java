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

package stroom.security.client.component;

import stroom.explorer.shared.FindResult;
import stroom.explorer.shared.FindResultWithPermissions;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.DocumentUserPermissions;
import stroom.util.client.DataGridUtil;
import stroom.util.client.cell.DocRefCell;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.Range;

/// A user's permissions on documents, a row each (the document, its permission, the permission it
/// inherits and its path), one of which can be selected, which Edit (or a double click) edits;
/// with Filter and Batch Edit, whose enabled state its owner sets.
public class DocumentPermissionsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton filterButton;
    @UiField
    ToolbarButton batchEditButton;
    @UiField
    DataGrid<FindResultWithPermissions> dataGrid;

    private final MultiSelectionModelImpl<FindResultWithPermissions> selectionModel;
    private Runnable onEdit = () -> {
    };
    private Runnable onFilter = () -> {
    };
    private Runnable onBatchEdit = () -> {
    };

    /// Makes an empty list, with no document column until
    /// [#addColumns(DocRefCell.Builder)].
    public DocumentPermissionsList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        editButton.setOnClick(() -> onEdit.run());
        filterButton.setOnClick(() -> onFilter.run());
        batchEditButton.setOnClick(() -> onBatchEdit.run());
        selectionModel.addSelectionHandler(event -> {
            final FindResultWithPermissions selected = selectionModel.getSelected();
            editButton.setEnabled(selected != null);
            if (selected != null && event.getSelectionType().isDoubleSelect()) {
                onEdit.run();
            }
        });
    }

    /// Adds the columns.
    ///
    /// @param documentCell The document cell's builder, configured to open its documents.
    public void addColumns(final DocRefCell.Builder<FindResultWithPermissions> documentCell) {
        final Column<FindResultWithPermissions, FindResultWithPermissions> docNameCol =
                DataGridUtil.docRefColumnBuilder(documentCell
                                .docRefFunction(row -> NullSafe.get(
                                        row,
                                        FindResultWithPermissions::getFindResult,
                                        FindResult::getDocRef))
                                .showIcon(true))
                        .build();
        dataGrid.addResizableColumn(docNameCol, "Document", 400);

        dataGrid.addResizableColumn(new Column<FindResultWithPermissions, String>(new TextCell()) {
            @Override
            public String getValue(final FindResultWithPermissions row) {
                return NullSafe.get(
                        row,
                        FindResultWithPermissions::getPermissions,
                        DocumentUserPermissions::getPermission,
                        DocumentPermission::getDisplayValue);
            }
        }, "Permission", 150);

        dataGrid.addResizableColumn(new Column<FindResultWithPermissions, String>(new TextCell()) {
            @Override
            public String getValue(final FindResultWithPermissions row) {
                return NullSafe.get(
                        row,
                        FindResultWithPermissions::getPermissions,
                        DocumentUserPermissions::getInheritedPermission,
                        DocumentPermission::getDisplayValue);
            }
        }, "Inherited Permission", 150);

        dataGrid.addAutoResizableColumn(new Column<FindResultWithPermissions, String>(new TextCell()) {
            @Override
            public String getValue(final FindResultWithPermissions row) {
                return NullSafe.get(
                        row,
                        FindResultWithPermissions::getFindResult,
                        FindResult::getPath);
            }
        }, "Path", 400);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<FindResultWithPermissions> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one row at most.
    public MultiSelectionModel<FindResultWithPermissions> getSelectionModel() {
        return selectionModel;
    }

    /// Goes back to the first page.
    public void resetRange() {
        dataGrid.setVisibleRange(new Range(0, PageRequest.DEFAULT_PAGE_LENGTH));
    }

    /// @param enabled Whether Batch Edit can be pressed.
    public void setBatchEditEnabled(final boolean enabled) {
        batchEditButton.setEnabled(enabled);
    }

    /// @param onEdit Run when Edit is pressed, or a row is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onFilter Run when Filter is pressed.
    public void setOnFilter(final Runnable onFilter) {
        this.onFilter = onFilter;
    }

    /// @param onBatchEdit Run when Batch Edit is pressed.
    public void setOnBatchEdit(final Runnable onBatchEdit) {
        this.onBatchEdit = onBatchEdit;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentPermissionsList> {

    }
}
