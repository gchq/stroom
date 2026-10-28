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

import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.CellIcon;
import stroom.cell.info.client.IconCell;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.client.TickBoxCell.Appearance;
import stroom.cell.tickbox.client.TickBoxCell.DefaultAppearance;
import stroom.cell.tickbox.client.TickBoxCell.NoBorderAppearance;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.cell.valuespinner.client.ValueSpinnerCell;
import stroom.cell.valuespinner.shared.EditableInteger;
import stroom.data.client.ColumnSizeConstants;
import stroom.data.client.component.DocRefCell;
import stroom.data.client.component.UserRefCell;
import stroom.docref.DocRef;
import stroom.entity.client.TreeRowHandler;
import stroom.processor.shared.Processor;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorRow;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.TreeAction;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.cell.client.NumberCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Element;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// The processors and their filters, a tree of rows (each processor's filters under it), several
/// of which can be selected: each with its info, whether it is enabled, its pipeline, priority,
/// status, last poll, tasks, whether it reprocesses and who it runs as. Its toolbar's buttons
/// ([ProcessorAction]) show only where they are available.
public class ProcessorList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton duplicateButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    ToolbarButton filterButton;
    @UiField
    ToolbarButton batchEditButton;
    @UiField
    ToolbarButton showTasksButton;
    @UiField
    DataGrid<ProcessorListRow> dataGrid;

    private final MultiSelectionModelImpl<ProcessorListRow> selectionModel;
    private final Map<ProcessorAction, ToolbarButton> buttons = new EnumMap<>(ProcessorAction.class);
    private Column<ProcessorListRow, Expander> expanderColumn;
    private boolean allowUpdate;
    private DocRefCell.Builder<ProcessorListRow> pipelineCell;
    private UserRefCell.Builder<ProcessorListRow> runAsUserCell;
    private Consumer<ProcessorAction> onAction = action -> {
    };
    private InfoHandler onShowInfo = (row, popupPosition, anchor) -> {
    };
    private BiConsumer<ProcessorListRow, Boolean> onExpand = (row, expanded) -> {
    };
    private BiConsumer<ProcessorListRow, Boolean> onEnabledChange = (row, enabled) -> {
    };
    private BiConsumer<ProcessorListRow, Integer> onPriorityChange = (row, priority) -> {
    };

    /// Makes an empty list, with no columns until [#addColumns] and no buttons shown until
    /// [#setAvailableActions(Set)].
    public ProcessorList() {
        initWidget(BINDER.createAndBindUi(this));
        selectionModel = dataGrid.addDefaultSelectionModel(true);
        buttons.put(ProcessorAction.NEW, newButton);
        buttons.put(ProcessorAction.EDIT, editButton);
        buttons.put(ProcessorAction.DUPLICATE, duplicateButton);
        buttons.put(ProcessorAction.DELETE, deleteButton);
        buttons.put(ProcessorAction.FILTER, filterButton);
        buttons.put(ProcessorAction.BATCH_EDIT, batchEditButton);
        buttons.put(ProcessorAction.SHOW_TASKS, showTasksButton);
        buttons.forEach((action, button) -> button.setOnClick(() -> onAction.accept(action)));
        setAvailableActions(Set.of());
    }

    /// Adds the columns, once.
    ///
    /// @param allowUpdate   Whether the enabled tick boxes and priorities can be changed.
    /// @param pipelineCell  The pipeline cell's builder, configured to open its pipelines.
    /// @param runAsUserCell The run as user cell's builder, configured to open its users.
    public void addColumns(final boolean allowUpdate,
                           final DocRefCell.Builder<ProcessorListRow> pipelineCell,
                           final UserRefCell.Builder<ProcessorListRow> runAsUserCell) {
        if (expanderColumn != null) {
            return;
        }
        this.allowUpdate = allowUpdate;
        this.pipelineCell = pipelineCell;
        this.runAsUserCell = runAsUserCell;

        // TODO Change all the cols to use DataGridUtil and enabledWhen() so the disabled
        //  ones get low-lighted

        // TODO Add tooltips to all the cols

        // TODO Why show the Pipeline col when you are viewing a PipelineDoc? Appreciate the filter
        //  automatically includes a term for the pipe and that this presenter is used on folders but might
        //  be better to hard code the pipe term on a PipelineDoc, make it not visible in the filter and prevent
        //  its use. Then you can remove the col.

        addExpanderColumn();
        addIconColumn();
        addInfoColumn();
        addEnabledColumn();
        addPipelineColumn();
        addPriorityColumn();
        addStatusColumn();
        addLastPollColumns();
        addTasksColumn();
        addReprocessColumn();
        addRunAsUserColumn();
    }

    /// @return Whether the columns have been added.
    public boolean hasColumns() {
        return expanderColumn != null;
    }

    /// Makes what sizes the expander column for the rows fetched (wide enough for the deepest), and
    /// notes the rows that come expanded.
    ///
    /// @param action Notes a row as expanded.
    /// @return The handler, for the data provider.
    public TreeRowHandler<ProcessorListRow> createTreeRowHandler(final TreeAction<ProcessorListRow> action) {
        return new TreeRowHandler<>(action, dataGrid, expanderColumn);
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<ProcessorListRow> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModel<ProcessorListRow> getSelectionModel() {
        return selectionModel;
    }

    /// Shows the buttons for the actions available, and the toolbar if there are any.
    ///
    /// @param actions The actions available.
    public void setAvailableActions(final Set<ProcessorAction> actions) {
        buttons.forEach((action, button) -> button.setVisible(actions.contains(action)));
        pager.setToolbarVisible(!actions.isEmpty());
    }

    /// @param action  An action.
    /// @param enabled Whether its button can be pressed.
    public void setActionEnabled(final ProcessorAction action, final boolean enabled) {
        buttons.get(action).setEnabled(enabled);
    }

    /// @param onAction Given the action whose button is pressed.
    public void setOnAction(final Consumer<ProcessorAction> onAction) {
        this.onAction = onAction;
    }

    /// @param onShowInfo Shows a row's info, by the info icon pressed.
    public void setOnShowInfo(final InfoHandler onShowInfo) {
        this.onShowInfo = onShowInfo;
    }

    /// @param onExpand Given a row and whether it is to be expanded, when its expander is pressed.
    public void setOnExpand(final BiConsumer<ProcessorListRow, Boolean> onExpand) {
        this.onExpand = onExpand;
    }

    /// @param onEnabledChange Given a row and whether it is now enabled, when its tick box is pressed.
    public void setOnEnabledChange(final BiConsumer<ProcessorListRow, Boolean> onEnabledChange) {
        this.onEnabledChange = onEnabledChange;
    }

    /// @param onPriorityChange Given a filter's row and its new priority.
    public void setOnPriorityChange(final BiConsumer<ProcessorListRow, Integer> onPriorityChange) {
        this.onPriorityChange = onPriorityChange;
    }

    private void addInfoColumn() {
        // Info column.
        final InfoColumn<ProcessorListRow> infoColumn = new InfoColumn<ProcessorListRow>() {
            @Override
            protected void showInfo(final ProcessorListRow row,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                onShowInfo.show(row, popupPosition, anchor);
            }
        };

        dataGrid.addColumn(infoColumn, "<br/>", ColumnSizeConstants.ICON_COL);
    }

    private void addExpanderColumn() {
        expanderColumn = new Column<ProcessorListRow, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final ProcessorListRow row) {
                Expander expander = null;
                if (row != null) {
                    expander = row.getExpander();
                }
                return expander;
            }
        };
        expanderColumn.setFieldUpdater((index, row, value) -> onExpand.accept(row, !value.isExpanded()));
        dataGrid.addColumn(expanderColumn, "<br/>", 0);
    }

    private void addIconColumn() {
        dataGrid.addColumn(new Column<ProcessorListRow, CellIcon>(new IconCell()) {
            @Override
            public CellIcon getValue(final ProcessorListRow row) {
                CellIcon icon = null;
                if (row instanceof ProcessorFilterRow) {
                    icon = CellIcon.of(SvgImage.FILTER, "Filter");
                } else if (row instanceof ProcessorRow) {
                    icon = CellIcon.of(SvgImage.PROCESS, "Process");
                }
                return icon;
            }
        }, "", ColumnSizeConstants.ICON_COL);
    }

    private void addPipelineColumn() {
        final Function<ProcessorListRow, DocRef> extractionFunction = row -> {
            DocRef docRef = null;
            if (row instanceof ProcessorFilterRow) {
                //noinspection PatternVariableCanBeUsed Not in GWT
                final ProcessorFilterRow processorFilterRow = (ProcessorFilterRow) row;
                final ProcessorFilter processorFilter = processorFilterRow.getProcessorFilter();

                if (processorFilter.getPipelineUuid() != null) {
                    docRef = processorFilter.getPipeline();
                }

                if (docRef == null) {
                    final Processor processor = processorFilter.getProcessor();
                    if (processor != null) {
                        if (processor.getPipelineUuid() != null || processor.getPipelineName() != null) {
                            docRef = processor.getPipeline();
                        }
                    }
                }
            } else if (row instanceof ProcessorRow) {
                //noinspection PatternVariableCanBeUsed Not in GWT
                final ProcessorRow processorRow = (ProcessorRow) row;
                final Processor processor = processorRow.getProcessor();
                if (processor != null) {
                    if (processor.getPipelineUuid() != null || processor.getPipelineName() != null) {
                        docRef = processor.getPipeline();
                    }
                }
            }

            return docRef;
        };

        DataGridUtil.addDocRefColumn(pipelineCell, dataGrid, "Pipeline", extractionFunction, true);
    }

    private void addLastPollColumns() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String lastPoll = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    lastPoll = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getLastPollAge();
                }
                return lastPoll;
            }
        }, "Last Poll Age", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addPriorityColumn() {
        final Column<ProcessorListRow, Number> priorityColumn = new Column<ProcessorListRow, Number>(
                new ValueSpinnerCell("Priority", ProcessorFilter.MIN_PRIORITY, ProcessorFilter.MAX_PRIORITY)) {
            @Override
            public Number getValue(final ProcessorListRow row) {
                Number priority = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    if (allowUpdate) {
                        priority = new EditableInteger(processorFilterRow.getProcessorFilter().getPriority());
                    } else {
                        priority = processorFilterRow.getProcessorFilter().getPriority();
                    }
                }
                return priority;
            }
        };
        if (allowUpdate) {
            priorityColumn.setFieldUpdater((index, row, value) -> onPriorityChange.accept(row, value.intValue()));
        }
        dataGrid.addColumn(priorityColumn, "Priority", ColumnSizeConstants.VALUE_SPINNER_COL);
    }

    private void addTasksColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, Number>(new NumberCell()) {
            @Override
            public Number getValue(final ProcessorListRow row) {
                Number value = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    value = processorFilterRow.getProcessorFilter().getProcessorFilterTracker().getMetaCount();
                }
                return value;
            }
        }, "Tasks", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addStatusColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                return ProcessorStatusUtil.getValue(row);
            }
        }, "Status", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addEnabledColumn() {
        final Appearance appearance = allowUpdate
                ? new DefaultAppearance()
                : new NoBorderAppearance();

        // Enabled.
        final Column<ProcessorListRow, TickBoxState> enabledColumn = new Column<ProcessorListRow, TickBoxState>(
                TickBoxCell.create(appearance, false, false, allowUpdate)) {
            @Override
            public TickBoxState getValue(final ProcessorListRow row) {
                if (row instanceof ProcessorFilterRow) {
                    return TickBoxState.fromBoolean(((ProcessorFilterRow) row).getProcessorFilter().isEnabled());
                } else if (row instanceof ProcessorRow) {
                    return TickBoxState.fromBoolean(((ProcessorRow) row).getProcessor().isEnabled());
                }
                return null;
            }
        };

        if (allowUpdate) {
            enabledColumn.setFieldUpdater((index, row, value) -> onEnabledChange.accept(row, value.toBoolean()));
        }
        dataGrid.addColumn(enabledColumn, "Enabled", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addReprocessColumn() {
        dataGrid.addResizableColumn(new Column<ProcessorListRow, String>(new TextCell()) {
            @Override
            public String getValue(final ProcessorListRow row) {
                String reprocess = null;
                if (row instanceof final ProcessorFilterRow processorFilterRow) {
                    reprocess = processorFilterRow.getProcessorFilter().isReprocess()
                            ? "True"
                            : "False";
                }
                return reprocess;
            }
        }, "Reprocess", ColumnSizeConstants.MEDIUM_COL);
    }

    private void addRunAsUserColumn() {
        dataGrid.addResizableColumn(
                new ColumnBuilder<ProcessorListRow, ProcessorListRow, Cell<ProcessorListRow>>(Function.identity(),
                        () -> runAsUserCell
                                .showIcon(true)
                                .displayType(DisplayType.AUTO)
                                .userRefFunction((final ProcessorListRow row) -> {
                                    if (row instanceof final ProcessorFilterRow processorFilterRow) {
                                        return Optional
                                                .of(processorFilterRow)
                                                .map(ProcessorFilterRow::getProcessorFilter)
                                                .map(ProcessorFilter::getRunAsUser)
                                                .orElse(null);
                                    } else {
                                        return null;
                                    }
                                })
                                .build())
                        .enabledWhen((final ProcessorListRow row) -> {
                            if (row instanceof final ProcessorFilterRow processorFilterRow) {
                                return Optional
                                        .of(processorFilterRow)
                                        .map(ProcessorFilterRow::getProcessorFilter)
                                        .map(ProcessorFilter::getRunAsUser)
                                        .map(UserRef::isEnabled)
                                        .orElse(true);
                            } else {
                                return true;
                            }
                        })
                        .build(),
                DataGridUtil.headingBuilder("Run As User")
                        .withToolTip("The processor will run with the same permissions as the Run As User.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorList> {

    }

    /// Shows a row's info.
    public interface InfoHandler {

        /// @param row           The row.
        /// @param popupPosition Where to show it.
        /// @param anchor        The info icon pressed.
        void show(ProcessorListRow row, PopupPosition popupPosition, Element anchor);
    }
}
