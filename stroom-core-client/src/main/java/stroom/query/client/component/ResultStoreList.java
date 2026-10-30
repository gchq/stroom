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

package stroom.query.client.component;

import stroom.query.api.ResultStoreInfo;
import stroom.query.api.SearchRequestSource.SourceType;
import stroom.util.client.cell.UserRefCell;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.ColumnSizeConstants;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Function;

/// The search result stores, a row each (its owner, size, age, creation time, node and whether it
/// is complete); one can be selected, and terminated, deleted or its settings changed (unless it
/// belongs to an analytic's table builder).
public class ResultStoreList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton terminateButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    ToolbarButton settingsButton;
    @UiField
    DataGrid<ResultStoreInfo> dataGrid;

    private final MultiSelectionModelImpl<ResultStoreInfo> selectionModel;
    private Runnable onTerminate = () -> {
    };
    private Runnable onDelete = () -> {
    };
    private Runnable onSettings = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public ResultStoreList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(ResultStoreInfo::getQueryKey);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        terminateButton.setOnClick(() -> onTerminate.run());
        deleteButton.setOnClick(() -> onDelete.run());
        settingsButton.setOnClick(() -> onSettings.run());
        selectionModel.addSelectionHandler(event -> {
            final ResultStoreInfo selected = selectionModel.getSelected();
            if (selected == null ||
                SourceType.TABLE_BUILDER_ANALYTIC.equals(selected.getSearchRequestSource().getSourceType())) {
                terminateButton.setEnabled(false);
                deleteButton.setEnabled(false);
                settingsButton.setEnabled(false);
            } else {
                terminateButton.setEnabled(!selected.isComplete());
                deleteButton.setEnabled(true);
                settingsButton.setEnabled(true);
            }
            if (event.getSelectionType().isDoubleSelect()) {
                onSettings.run();
            }
        });
    }

    /// Adds the columns.
    ///
    /// @param dateFormat Formats a time, in the user's preferences.
    /// @param ownerCell  The owner cell's builder, configured to open its users.
    public void addColumns(final Function<Long, String> dateFormat,
                           final UserRefCell.Builder<ResultStoreInfo> ownerCell) {
        // User Id
        dataGrid.addResizableColumn(
                new ColumnBuilder<ResultStoreInfo, ResultStoreInfo, Cell<ResultStoreInfo>>(Function.identity(),
                        () -> ownerCell
                                .showIcon(false)
                                .displayType(DisplayType.AUTO)
                                .userRefFunction(ResultStoreInfo::getOwner)
                                .build())
                        .enabledWhen(taskProgress -> taskProgress.getOwner().isEnabled())
                        .build(),
                "User Display Name", 300);

        // Store size
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return ModelStringUtil.formatIECByteSizeString(resultStoreInfo.getStoreSize());
            }
        }, "Size", ColumnSizeConstants.SMALL_COL);

        // Age
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return ModelStringUtil
                        .formatDurationString(System.currentTimeMillis() - resultStoreInfo.getCreationTime());
            }
        }, "Age", ColumnSizeConstants.SMALL_COL);

        // Creation time
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return dateFormat.apply(resultStoreInfo.getCreationTime());
            }
        }, "Creation Time", ColumnSizeConstants.DATE_COL);

        // Node name
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return resultStoreInfo.getNodeName();
            }
        }, "Node", ColumnSizeConstants.MEDIUM_COL);

        // Complete
        dataGrid.addResizableColumn(new Column<ResultStoreInfo, String>(new TextCell()) {
            @Override
            public String getValue(final ResultStoreInfo resultStoreInfo) {
                return Boolean.toString(resultStoreInfo.isComplete());
            }
        }, "Complete", ColumnSizeConstants.SMALL_COL);
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ResultStoreInfo> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one store at most.
    public MultiSelectionModel<ResultStoreInfo> getSelectionModel() {
        return selectionModel;
    }

    /// @param onTerminate Run when Terminate is pressed.
    public void setOnTerminate(final Runnable onTerminate) {
        this.onTerminate = onTerminate;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onSettings Run when Settings is pressed, or a row is double clicked.
    public void setOnSettings(final Runnable onSettings) {
        this.onSettings = onSettings;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ResultStoreList> {

    }
}
