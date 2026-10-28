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

package stroom.job.client.component;

import stroom.cell.info.client.CellIcon;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.ColumnSizeConstants;
import stroom.job.shared.Job;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;
import java.util.function.Function;

/// The jobs, a row each (whether it is enabled, its name, a help link and its description), the
/// advanced ones after a gap; one can be selected.
public class JobList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<JobWrapper> dataGrid;

    private final MultiSelectionModelImpl<JobWrapper> selectionModel;
    private EnabledHandler onEnabledChange = (rowIndex, row, enabled) -> {
    };
    private Consumer<JobWrapper> onHelp = row -> {
    };

    /// Makes an empty list.
    public JobList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addColumns();
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<JobWrapper> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one job at most.
    public MultiSelectionModelImpl<JobWrapper> getSelectionModel() {
        return selectionModel;
    }

    /// @param rowIndex A row to draw again, e.g. once its job is saved.
    public void redrawRow(final int rowIndex) {
        dataGrid.redrawRow(rowIndex);
    }

    /// @param onEnabledChange Given a row and whether its job is now enabled, when its tick box is
    ///                        pressed.
    public void setOnEnabledChange(final EnabledHandler onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onHelp Given a row whose help icon is pressed.
    public void setOnHelp(final Consumer<JobWrapper> onHelp) {
        this.onHelp = onHelp;
    }

    private void addColumns() {
        final Function<JobWrapper, Boolean> function = ref -> ref.getJob().isEnabled();
        final Function<JobWrapper, TickBoxState> stateFunction = TickBoxState.createTickBoxFunc(function);

        dataGrid.addResizableColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(stateFunction)
                        .withFieldUpdater((rowIndex, ref, tickBoxState) ->
                                onEnabledChange.onChange(rowIndex, ref, tickBoxState.toBoolean()))
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this job is enabled. " +
                                     "The parent job and the node must both be enabled for the job to execute.")
                        .build(),
                ColumnSizeConstants.ENABLED_COL);

        // Job name, allow for null rows
        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((JobWrapper job) ->
                                NullSafe.get(job, JobWrapper::getJob, Job::getName))
                        .enabledWhen(job ->
                                NullSafe.getOrElse(job, JobWrapper::getJob, Job::isEnabled, false))
                        .build(),
                DataGridUtil.headingBuilder("Job")
                        .withToolTip("The name of the job")
                        .build(),
                350);

        // Help
        dataGrid.addColumn(
                DataGridUtil.cellIconColumnBuilder(true, (JobWrapper job) ->
                                CellIcon.of(SvgImage.HELP, "Help (opens in new tab)"))
                        .enabledWhen(job ->
                                NullSafe.getOrElse(job, JobWrapper::getJob, Job::isEnabled, false))
                        .withBrowserEventHandler((context, elem, row, event) -> onHelp.accept(row))
                        .build(),
                "<br/>", ColumnSizeConstants.ICON_COL);

        // Description col, allow for null rows
        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder((JobWrapper job) ->
                                NullSafe.get(job, JobWrapper::getJob, Job::getDescription))
                        .enabledWhen(job ->
                                NullSafe.getOrElse(job, JobWrapper::getJob, Job::isEnabled, false))
                        .build(),
                DataGridUtil.headingBuilder("Description")
                        .withToolTip("The description of the job")
                        .build(),
                300);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, JobList> {

    }

    /// Told when a job's enabled tick box is pressed.
    public interface EnabledHandler {

        /// @param rowIndex The row's index.
        /// @param row      The row.
        /// @param enabled  Whether the job is now enabled.
        void onChange(int rowIndex, JobWrapper row, boolean enabled);
    }
}
