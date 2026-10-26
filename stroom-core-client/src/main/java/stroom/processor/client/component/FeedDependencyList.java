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

import stroom.data.client.component.FeedRefCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.processor.shared.FeedDependency;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/// A processor filter's feed dependencies, a row each (the feed and the type of stream it must have),
/// with New, Edit and Remove; one can be selected, and a double click edits it. When read only, the
/// buttons are disabled and say so.
public class FeedDependencyList extends Component implements Focus {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final String READ_ONLY_SUFFIX = " (read only)";

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton removeButton;
    @UiField
    DataGrid<FeedDependency> dataGrid;

    private final String addTitle;
    private final String editTitle;
    private final String removeTitle;
    private final MultiSelectionModelImpl<FeedDependency> selectionModel;
    private Runnable onAdd = () -> {
    };
    private Consumer<FeedDependency> onEdit = feedDependency -> {
    };
    private Consumer<FeedDependency> onRemove = feedDependency -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(FeedRefCell.Builder)].
    public FeedDependencyList() {
        initWidget(BINDER.createAndBindUi(this));
        addTitle = addButton.getTitle();
        editTitle = editButton.getTitle();
        removeTitle = removeButton.getTitle();
        selectionModel = dataGrid.addDefaultSelectionModel(false);

        addButton.setOnClick(() -> onAdd.run());
        editButton.setOnClick(() -> onEdit.accept(selectionModel.getSelected()));
        removeButton.setOnClick(() -> onRemove.accept(selectionModel.getSelected()));
        enableButtons();

        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit.accept(selectionModel.getSelected());
            }
        });
    }

    /// Adds the columns, once.
    ///
    /// @param feedCell The feed cell's builder, configured to open its feeds.
    public void addColumns(final FeedRefCell.Builder<FeedDependency> feedCell) {
        dataGrid.addResizableColumn(
                new ColumnBuilder<FeedDependency, FeedDependency, Cell<FeedDependency>>(
                        Function.identity(),
                        () -> feedCell
                                .nameFunction(FeedDependency::getFeedName)
                                .build())
                        .build(),
                "Feed",
                ColumnSizeConstants.BIG_COL);
        dataGrid.addResizableColumn(
                DataGridUtil.copyTextColumnBuilder(FeedDependency::getStreamType)
                        .build(),
                "Type",
                200);
    }

    @Override
    public void focus() {
        addButton.focus();
    }

    /// @param feedDependencies The feed dependencies.
    public void setData(final List<FeedDependency> feedDependencies) {
        dataGrid.setRowData(0, feedDependencies);
        dataGrid.setRowCount(feedDependencies.size());
        enableButtons();
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onEdit Given the selected feed dependency, or `null`, when Edit is pressed or one is
    ///               double clicked.
    public void setOnEdit(final Consumer<FeedDependency> onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onRemove Given the selected feed dependency, or `null`, when Remove is pressed.
    public void setOnRemove(final Consumer<FeedDependency> onRemove) {
        this.onRemove = onRemove;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enableButtons();
    }

    private void enableButtons() {
        final boolean readOnly = isReadOnly();
        addButton.setEnabled(!readOnly);

        final FeedDependency selected = selectionModel.getSelected();

        editButton.setEnabled(!readOnly && selected != null);
        removeButton.setEnabled(!readOnly && selected != null);

        final String readOnlySuffix = readOnly
                ? READ_ONLY_SUFFIX
                : "";
        addButton.setTitle(addTitle + readOnlySuffix);
        editButton.setTitle(editTitle + readOnlySuffix);
        removeButton.setTitle(removeTitle + readOnlySuffix);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FeedDependencyList> {

    }
}
