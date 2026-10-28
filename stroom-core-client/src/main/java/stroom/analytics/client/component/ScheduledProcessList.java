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
import stroom.analytics.shared.ExecutionScheduleFields;
import stroom.analytics.shared.ScheduleBounds;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.ColumnSizeConstants;
import stroom.data.client.component.UserRefCell;
import stroom.security.shared.UserFields;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.UserRef.DisplayType;
import stroom.util.shared.scheduler.Schedule;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

/// A document's execution schedules, a row each (whether it is enabled, its name, node, schedule, run
/// as user and time bounds), faded where disabled, with New, Edit and Delete; one can be selected,
/// and a double click edits it. A schedule naming a node that no longer exists is marked. When read
/// only, nothing can be added, edited, deleted, enabled or disabled.
public class ScheduledProcessList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton addButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<ExecutionSchedule> dataGrid;

    private final MultiSelectionModelImpl<ExecutionSchedule> selectionModel;
    private final Set<String> knownNodeNames = new HashSet<>();
    private Function<Long, String> dateTimeFormat;
    private UserRefCell.Builder<ExecutionSchedule> runAsUserCell;
    private Runnable onAdd = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onDelete = () -> {
    };
    private BiConsumer<ExecutionSchedule, Boolean> onEnabledChange = (schedule, enabled) -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function, UserRefCell.Builder)].
    public ScheduledProcessList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        addButton.setOnClick(() -> onAdd.run());
        editButton.setOnClick(() -> onEdit.run());
        deleteButton.setOnClick(() -> onDelete.run());
        selectionModel.addSelectionHandler(event -> {
            enableButtons();
            // A read only document's schedules can't be edited, so double clicking does nothing
            if (event.getSelectionType().isDoubleSelect() && !isReadOnly()) {
                onEdit.run();
            }
        });
        dataGrid.addColumnSortHandler(ignored -> onSortChange.run());
    }

    /// Adds the columns, once; they are added again when read only changes.
    ///
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch.
    /// @param runAsUserCell  The run as user cell's builder, configured to open its users.
    public void addColumns(final Function<Long, String> dateTimeFormat,
                           final UserRefCell.Builder<ExecutionSchedule> runAsUserCell) {
        this.dateTimeFormat = dateTimeFormat;
        this.runAsUserCell = runAsUserCell;
        addColumns();
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<ExecutionSchedule> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one schedule at most.
    public MultiSelectionModel<ExecutionSchedule> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Marks the schedules naming a node not given; none are marked until some are given.
    ///
    /// @param nodeNames The nodes that exist.
    public void setKnownNodeNames(final Collection<String> nodeNames) {
        knownNodeNames.addAll(nodeNames);
        dataGrid.redraw();
    }

    /// Stops marking the schedules' nodes, when the nodes can't be found.
    public void clearKnownNodeNames() {
        knownNodeNames.clear();
    }

    /// @param onAdd Run when New is pressed.
    public void setOnAdd(final Runnable onAdd) {
        this.onAdd = onAdd;
    }

    /// @param onEdit Run when Edit is pressed, or a schedule is double clicked (not when read only).
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onEnabledChange Given a schedule and whether it is now enabled, when its tick box is
    ///                        pressed.
    public void setOnEnabledChange(final BiConsumer<ExecutionSchedule, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        // The enabled tick box can only be changed if the document isn't read only
        if (dateTimeFormat != null) {
            while (dataGrid.getColumnCount() > 0) {
                dataGrid.removeColumn(dataGrid.getColumn(0));
            }
            addColumns();
        }
        enableButtons();
    }

    private void enableButtons() {
        final boolean editable = !isReadOnly();
        addButton.setEnabled(editable);
        editButton.setEnabled(editable && selectionModel.hasSelectedItems());
        deleteButton.setEnabled(editable && selectionModel.hasSelectedItems());
    }

    private void addColumns() {
        dataGrid.addColumn(
                DataGridUtil.updatableTickBoxColumnBuilder(
                                TickBoxState.createTickBoxFunc(ExecutionSchedule::isEnabled),
                                !isReadOnly())
                        .withSorting(ExecutionScheduleFields.ENABLED)
                        .withFieldUpdater((ignored, row, value) ->
                                onEnabledChange.accept(row, TickBoxState.getAsBoolean(value)))
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether this execution schedule is enabled or not.")
                        .build(),
                80);

        final Column<ExecutionSchedule, String> nameColumn = DataGridUtil.textColumnBuilder(ExecutionSchedule::getName)
                .withSorting(ExecutionScheduleFields.NAME, true)
                .enabledWhen(ExecutionSchedule::isEnabled)
                .build();
        dataGrid.addResizableColumn(
                nameColumn,
                DataGridUtil.headingBuilder(ExecutionScheduleFields.NAME)
                        .withToolTip("The name for this execution schedule.")
                        .build(),
                200);
        // By name, A to Z, ignoring case, unless another sort is chosen
        dataGrid.setDefaultSort(new ColumnSortInfo(nameColumn, true));

        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(this::getNodeNameAsSafeHtml)
                        .withSorting(ExecutionScheduleFields.NODE_NAME, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.NODE_NAME)
                        .withToolTip("The name of the node that it will be executed on.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataGridUtil.toStringFunc(
                                ExecutionSchedule::getSchedule,
                                Schedule::toString))
                        .withSorting(ExecutionScheduleFields.SCHEDULE, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.SCHEDULE)
                        .withToolTip("The execution schedule.")
                        .build(),
                200);

        final Column<ExecutionSchedule, ExecutionSchedule> runAsCol =
                new ColumnBuilder<ExecutionSchedule, ExecutionSchedule, Cell<ExecutionSchedule>>(
                        Function.identity(),
                        () -> runAsUserCell
                                .showIcon(true)
                                .displayType(DisplayType.AUTO)
                                .userRefFunction(ExecutionSchedule::getRunAsUser)
                                .build())
                        .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                        .enabledWhen(ScheduledProcessList::areScheduleAndUserEnabled)
                        .build();
        dataGrid.addResizableColumn(
                runAsCol,
                DataGridUtil.headingBuilder(ExecutionScheduleFields.RUN_AS_USER)
                        .withToolTip("The processor will run with the same permissions as the Run As User.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        dataGrid.addAutoResizableColumn(
                DataGridUtil.textColumnBuilder(this::getBoundsAsString)
                        .withSorting(ExecutionScheduleFields.BOUNDS, false)
                        .enabledWhen(ExecutionSchedule::isEnabled)
                        .build(),
                DataGridUtil.headingBuilder(ExecutionScheduleFields.BOUNDS)
                        .withToolTip("The time bounds for the schedule.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);
    }

    // A schedule only ever runs on the node it names, so one naming a node that no longer exists is never
    // picked up by any node and produces nothing without reporting anything. Mark it so the reason is visible.
    private SafeHtml getNodeNameAsSafeHtml(final ExecutionSchedule executionSchedule) {
        final String nodeName = executionSchedule.getNodeName();
        final SafeHtmlBuilder builder = new SafeHtmlBuilder();
        if (nodeName != null) {
            builder.append(SafeHtmlUtils.fromString(nodeName));
        }

        // Only mark unknown nodes once we know what the nodes actually are, else every row looks broken while the
        // node list is still loading or if it failed to load.
        if (!knownNodeNames.isEmpty() && !knownNodeNames.contains(nodeName)) {
            builder.append(SvgImageUtil.toSafeHtml(
                    "Node '" + nodeName + "' no longer exists so this schedule will never run.",
                    SvgImage.ALERT_SIMPLE,
                    "svgIcon", "small"));
        }

        return builder.toSafeHtml();
    }

    private static Boolean areScheduleAndUserEnabled(final ExecutionSchedule executionSchedule) {
        if (executionSchedule == null) {
            return true;
        } else {
            final boolean isUserEnabled = NullSafe.test(
                    executionSchedule,
                    ExecutionSchedule::getRunAsUser,
                    userRef -> userRef == null || userRef.isEnabled());
            return isUserEnabled && executionSchedule.isEnabled();
        }
    }

    private String getBoundsAsString(final ExecutionSchedule row) {
        final ScheduleBounds bounds = row.getScheduleBounds();
        if (bounds != null) {
            if (bounds.getStartTimeMs() != null && bounds.getEndTimeMs() != null) {
                if (bounds.getStartTimeMs().equals(bounds.getEndTimeMs())) {
                    return "On " +
                           dateTimeFormat.apply(bounds.getStartTimeMs());
                } else {
                    return "Between " +
                           dateTimeFormat.apply(bounds.getStartTimeMs()) +
                           " and " +
                           dateTimeFormat.apply(bounds.getEndTimeMs());
                }
            } else if (bounds.getStartTimeMs() != null) {
                return "After " +
                       dateTimeFormat.apply(bounds.getStartTimeMs());
            } else if (bounds.getEndTimeMs() != null) {
                return "Until " +
                       dateTimeFormat.apply(bounds.getEndTimeMs());
            }
        }
        return "Unbounded";
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ScheduledProcessList> {

    }
}
