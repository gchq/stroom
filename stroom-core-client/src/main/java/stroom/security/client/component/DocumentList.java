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

import stroom.explorer.client.FindResultCell;
import stroom.explorer.shared.FindResult;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.FindResultLists;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;

/// The documents whose permissions can be changed (the Document Permissions tab's list): a striped
/// list of a page of them, the one with the focus chosen, which Enter or a double click opens; and
/// Filter, Edit and Batch Edit, whose enabled states its owner sets.
public class DocumentList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton filterButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton batchEditButton;
    @UiField
    DataGrid<FindResult> resultList;

    private final MultiSelectionModelImpl<FindResult> selectionModel = new MultiSelectionModelImpl<>();
    private Consumer<FindResult> onOpen = result -> {
    };
    private Runnable onFilter = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onBatchEdit = () -> {
    };

    /// Makes an empty list.
    public DocumentList() {
        initWidget(BINDER.createAndBindUi(this));
        FindResultLists.wire(
                resultList,
                new FindResultCell(),
                selectionModel,
                result -> onOpen.accept(result),
                // Up on the first result stays there: there's no filter above to go back to
                () -> {
                });
        filterButton.setOnClick(() -> onFilter.run());
        editButton.setOnClick(() -> onEdit.run());
        batchEditButton.setOnClick(() -> onBatchEdit.run());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<FindResult> dataProvider) {
        dataProvider.addDataDisplay(resultList);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection: the document with the focus.
    public MultiSelectionModel<FindResult> getSelectionModel() {
        return selectionModel;
    }

    /// @return The absolute index of the first row shown.
    public int getPageStart() {
        return resultList.getPageStart();
    }

    /// @param pageStart The absolute index of the first row to show.
    public void setPageStart(final int pageStart) {
        resultList.setPageStart(pageStart);
    }

    /// @param enabled Whether Edit can be pressed.
    public void setEditEnabled(final boolean enabled) {
        editButton.setEnabled(enabled);
    }

    /// @param enabled Whether Batch Edit can be pressed.
    public void setBatchEditEnabled(final boolean enabled) {
        batchEditButton.setEnabled(enabled);
    }

    /// @param onOpen Given a document to open (Enter, or a double click, on it).
    public void setOnOpen(final Consumer<FindResult> onOpen) {
        this.onOpen = onOpen;
    }

    /// @param onFilter Run when Filter is pressed.
    public void setOnFilter(final Runnable onFilter) {
        this.onFilter = onFilter;
    }

    /// @param onEdit Run when Edit is pressed.
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onBatchEdit Run when Batch Edit is pressed.
    public void setOnBatchEdit(final Runnable onBatchEdit) {
        this.onBatchEdit = onBatchEdit;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentList> {

    }
}
