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

package stroom.analytics.client.component;

import stroom.analytics.shared.ExecutionSchedule;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.DataGridSelectionEventManager;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.text.client.Html;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// The Execution Schedules screen: the schedules of analytic rules and reports, several of which
/// can be selected, with how many are, and the actions on them. Its controller gives the grid its
/// columns and rows.
public class ExecutionSchedules extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton batchEditButton;
    @UiField
    ToolbarButton filterButton;
    @UiField
    ToolbarButton clearFilterButton;
    @UiField
    ToolbarButton runNowButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    Html selectionCount;
    @UiField
    DataGrid<ExecutionSchedule> dataGrid;

    private final MultiSelectionModelImpl<ExecutionSchedule> selectionModel = new MultiSelectionModelImpl<>();
    private Consumer<ScheduleAction> onAction = action -> {
    };

    /// Makes an empty list, with no columns.
    public ExecutionSchedules() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.setSelectionModel(selectionModel,
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, true));
        editButton.setOnClick(() -> onAction.accept(ScheduleAction.EDIT));
        batchEditButton.setOnClick(() -> onAction.accept(ScheduleAction.BATCH_EDIT));
        filterButton.setOnClick(() -> onAction.accept(ScheduleAction.FILTER));
        clearFilterButton.setOnClick(() -> onAction.accept(ScheduleAction.CLEAR_FILTER));
        runNowButton.setOnClick(() -> onAction.accept(ScheduleAction.RUN_NOW));
        deleteButton.setOnClick(() -> onAction.accept(ScheduleAction.DELETE));
    }

    /// @return The grid of schedules, for the controller to give columns and rows.
    public DataGrid<ExecutionSchedule> getDataGrid() {
        return dataGrid;
    }

    /// @return The selection, of any number of schedules.
    public MultiSelectionModelImpl<ExecutionSchedule> getSelectionModel() {
        return selectionModel;
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// Brings the actions up to date: Edit is enabled for one schedule selected, Delete for any,
    /// and Clear Filter while the schedules are filtered.
    ///
    /// @param selectedCount How many schedules are selected.
    /// @param filtered      Whether the schedules are filtered.
    public void updateActions(final int selectedCount, final boolean filtered) {
        editButton.setEnabled(selectedCount == 1);
        deleteButton.setEnabled(selectedCount > 0);
        clearFilterButton.setEnabled(filtered);
    }

    /// @param count How many schedules are selected, shown after the toolbar.
    public void setSelectionCount(final int count) {
        final String noun = count == 1
                ? "schedule"
                : "schedules";
        selectionCount.setText(count + " " + noun + " selected");
    }

    /// @param onAction Given each action the user takes.
    public void setOnAction(final Consumer<ScheduleAction> onAction) {
        this.onAction = onAction;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExecutionSchedules> {

    }


    // --------------------------------------------------------------------------------


    /// An action on the schedules.
    public enum ScheduleAction {
        /// Edits the schedule selected.
        EDIT,
        /// Edits the schedules selected, or all those filtered, at once.
        BATCH_EDIT,
        /// Filters the schedules.
        FILTER,
        /// Clears the filter.
        CLEAR_FILTER,
        /// Runs the schedules selected, or all those filtered, now.
        RUN_NOW,
        /// Deletes the schedules selected.
        DELETE
    }
}
