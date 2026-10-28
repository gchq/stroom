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

package stroom.task.client.component;

import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.InfoColumn;
import stroom.cell.tickbox.client.TickBoxCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.ColumnSizeConstants;
import stroom.data.client.component.UserRefCell;
import stroom.entity.client.TreeRowHandler;
import stroom.task.client.TaskMonitorFactory;
import stroom.task.client.TaskProgressUtil;
import stroom.task.shared.FindTaskProgressCriteria;
import stroom.task.shared.TaskProgress;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TreeAction;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.ColumnBuilder;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.menu.client.Item;
import stroom.widget.popup.client.PopupPosition;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.DivElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/// The tasks running on every node, a tree of them (each with a tick box to choose it for
/// terminating, its info, node, name, user, when it was submitted, its age and info, those matching
/// the filter highlighted), sorted by a column. Its toolbar turns auto refresh on and off,
/// terminates the tasks ticked, expands or collapses them all, wraps their text and shows the
/// warnings, when there are any.
public class TaskManagerList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    PagerPanel pager;
    @UiField
    ToolbarToggleButton autoRefreshButton;
    @UiField
    ToolbarButton terminateButton;
    @UiField
    ToolbarButton expandAllButton;
    @UiField
    ToolbarButton collapseAllButton;
    @UiField
    ToolbarToggleButton wrapToggleButton;
    @UiField
    ToolbarButton warningsButton;
    @UiField
    DataGrid<TaskProgress> dataGrid;

    // The tasks ticked, to terminate
    private final Set<TaskProgress> selectedTaskProgress = new HashSet<>();
    private Column<TaskProgress, Expander> expanderColumn;
    private InfoHandler onShowInfo = (row, popupPosition, anchor) -> {
    };
    private BiConsumer<TaskProgress, Boolean> onExpand = (row, expanded) -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns].
    public TaskManagerList() {
        initWidget(BINDER.createAndBindUi(this));
        wrapToggleButton.setOnToggle(dataGrid::setMultiLine);
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns.
    ///
    /// @param nodeName     Gives a task's node's name, saying whether it is disabled.
    /// @param dateFormat   Formats a time, in the user's preferences.
    /// @param userCell     The user cell's builder, configured to open its users.
    /// @param taskInfoMenu Gives the menu of a task's info, from its text.
    public void addColumns(final Function<TaskProgress, String> nodeName,
                           final Function<Long, String> dateFormat,
                           final UserRefCell.Builder<TaskProgress> userCell,
                           final Function<String, List<Item>> taskInfoMenu) {
        // Select Column
        final Column<TaskProgress, TickBoxState> checkBoxColumn = new Column<TaskProgress, TickBoxState>(
                TickBoxCell.create(false, false)) {
            @Override
            public TickBoxState getValue(final TaskProgress taskProgress) {
                if (TaskProgressUtil.DEAD_TASK_NAME.equals(taskProgress.getTaskName())) {
                    // Dead tasks cannot be deleted so don't show a checkbox
                    // They are only there to show that an orphaned child task was a child of something
                    return null;
                } else {
                    return TickBoxState.fromBoolean(selectedTaskProgress.contains(taskProgress));
                }
            }
        };

        dataGrid.addColumn(checkBoxColumn, "", ColumnSizeConstants.CHECKBOX_COL);

        // Expander column.
        expanderColumn = new Column<TaskProgress, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final TaskProgress row) {
                return buildExpander(row);
            }
        };
        dataGrid.addColumn(expanderColumn, "");

        expanderColumn.setFieldUpdater((index, row, value) -> onExpand.accept(row, !value.isExpanded()));

        final InfoColumn<TaskProgress> furtherInfoColumn = new InfoColumn<TaskProgress>() {
            @Override
            protected void showInfo(final TaskProgress row,
                                    final PopupPosition popupPosition,
                                    final Element anchor) {
                onShowInfo.show(row, popupPosition, anchor);
            }
        };
        dataGrid.addColumn(furtherInfoColumn, "<br/>", ColumnSizeConstants.ICON_COL);

        // Add Handlers
        checkBoxColumn.setFieldUpdater((index, object, value) -> {
            if (value.toBoolean()) {
                selectedTaskProgress.add(object);
            } else {
                // De-selecting one and currently matching all ?
                selectedTaskProgress.remove(object);
            }
        });

        // Node.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(nodeName))
                        .withSorting(FindTaskProgressCriteria.FIELD_NODE)
                        .build(),
                FindTaskProgressCriteria.FIELD_NODE,
                150);

        // Name.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getWrapableColouredCellFunc(TaskProgress::getTaskName))
                        .withSorting(FindTaskProgressCriteria.FIELD_NAME)
                        .build(),
                FindTaskProgressCriteria.FIELD_NAME,
                250);

        // User.
        dataGrid.addResizableColumn(
                new ColumnBuilder<TaskProgress, TaskProgress, Cell<TaskProgress>>(Function.identity(),
                        () -> userCell
                                .showIcon(false)
                                .displayType(DisplayType.AUTO)
                                .userRefFunction(TaskProgress::getUserRef)
                                .build())
                        .enabledWhen(taskProgress -> NullSafe
                                .getOrElse(taskProgress, TaskProgress::getUserRef, UserRef::isEnabled, false))
                        .withSorting(FindTaskProgressCriteria.FIELD_USER)
                        .build(),
                FindTaskProgressCriteria.FIELD_USER,
                100);

        // Submit Time.
        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(getColouredCellFunc(taskProgress ->
                                dateFormat.apply(taskProgress.getSubmitTimeMs())))
                        .withSorting(FindTaskProgressCriteria.FIELD_SUBMIT_TIME)
                        .build(),
                FindTaskProgressCriteria.FIELD_SUBMIT_TIME,
                ColumnSizeConstants.DATE_COL);

        // Age. The oldest first, unless another sort is chosen
        final Column<TaskProgress, SafeHtml> ageColumn = DataGridUtil.htmlColumnBuilder(getColouredCellFunc(
                        taskProgress -> ModelStringUtil.formatDurationString(taskProgress.getAgeMs())))
                .withSorting(FindTaskProgressCriteria.FIELD_AGE)
                .build();
        dataGrid.addResizableColumn(ageColumn, FindTaskProgressCriteria.FIELD_AGE, ColumnSizeConstants.SMALL_COL);
        dataGrid.setDefaultSort(new ColumnSortInfo(ageColumn, false));

        // Info
        dataGrid.addAutoResizableColumn(
                DataGridUtil.hasContextMenusColumnBuilder(getWrapableColouredCellFunc(TaskProgress::getTaskInfo),
                                (context, safeHtml) -> getContextMenus(safeHtml, taskInfoMenu))
                        .withSorting(FindTaskProgressCriteria.FIELD_INFO)
                        .build(),
                FindTaskProgressCriteria.FIELD_INFO,
                200);
    }

    /// Makes what sizes the expander column for the rows fetched (wide enough for the deepest), and
    /// notes the rows that come expanded.
    ///
    /// @param action Notes a row as expanded.
    /// @return The handler, for the data provider.
    public TreeRowHandler<TaskProgress> createTreeRowHandler(final TreeAction<TaskProgress> action) {
        return new TreeRowHandler<>(action, dataGrid, expanderColumn);
    }

    /// @param dataProvider Gives the list its rows.
    public void setDataProvider(final AbstractDataProvider<TaskProgress> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The pager, whose refresh button spins while the tasks are fetched.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// @return The tasks ticked, to terminate.
    public Set<TaskProgress> getSelectedTasks() {
        return new HashSet<>(selectedTaskProgress);
    }

    /// Unticks the tasks no longer shown.
    ///
    /// @param shown The tasks shown.
    public void retainSelectedTasks(final Set<TaskProgress> shown) {
        selectedTaskProgress.retainAll(shown);
    }

    /// @param expandAll   Whether Expand All can be pressed.
    /// @param collapseAll Whether Collapse All can be pressed.
    public void setExpandCollapseEnabled(final boolean expandAll, final boolean collapseAll) {
        expandAllButton.setEnabled(expandAll);
        collapseAllButton.setEnabled(collapseAll);
    }

    /// @param visible Whether Show Warnings shows, as there are warnings.
    public void setWarningsVisible(final boolean visible) {
        warningsButton.setVisible(visible);
    }

    /// @param onAutoRefreshToggle Given whether auto refresh is now on, when its button is pressed.
    public void setOnAutoRefreshToggle(final Consumer<Boolean> onAutoRefreshToggle) {
        autoRefreshButton.setOnToggle(onAutoRefreshToggle);
    }

    /// @param onTerminate Run when Terminate is pressed.
    public void setOnTerminate(final Runnable onTerminate) {
        terminateButton.setOnClick(onTerminate);
    }

    /// @param onExpandAll Run when Expand All is pressed.
    public void setOnExpandAll(final Runnable onExpandAll) {
        expandAllButton.setOnClick(onExpandAll);
    }

    /// @param onCollapseAll Run when Collapse All is pressed.
    public void setOnCollapseAll(final Runnable onCollapseAll) {
        collapseAllButton.setOnClick(onCollapseAll);
    }

    /// @param onShowWarnings Run when Show Warnings is pressed.
    public void setOnShowWarnings(final Runnable onShowWarnings) {
        warningsButton.setOnClick(onShowWarnings);
    }

    /// @param onShowInfo Shows a task's info, by the info icon pressed.
    public void setOnShowInfo(final InfoHandler onShowInfo) {
        this.onShowInfo = onShowInfo;
    }

    /// @param onExpand Given a row and whether it is to be expanded, when its expander is pressed.
    public void setOnExpand(final BiConsumer<TaskProgress, Boolean> onExpand) {
        this.onExpand = onExpand;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }

    private Function<TaskProgress, SafeHtml> getColouredCellFunc(final Function<TaskProgress, String> extractor) {
        return DataGridUtil.highlightedCellExtractor(
                extractor,
                TaskProgress::isMatchedInFilter);
    }

    private Function<TaskProgress, SafeHtml> getWrapableColouredCellFunc(
            final Function<TaskProgress, String> extractor) {
        final Function<TaskProgress, SafeHtml> colouredCellFunc = getColouredCellFunc(extractor);

        return (final TaskProgress row) -> {
            final SafeHtml colouredText = colouredCellFunc.apply(row);
            if (wrapToggleButton.isOn()) {
                return HtmlBuilder.builder()
                        .div(htmlBuilder -> htmlBuilder.append(colouredText))
                        .toSafeHtml();
            } else {
                return colouredText;
            }
        };
    }

    // The menu of a task's info, from its text with any html tags caused by line wrap stripped
    private static List<Item> getContextMenus(final SafeHtml safeHtml,
                                              final Function<String, List<Item>> taskInfoMenu) {
        final DivElement tempDiv = Document.get().createDivElement();
        tempDiv.setInnerHTML(safeHtml.asString());
        final String taskInfo = tempDiv.getInnerText();
        if (taskInfo == null) {
            return new ArrayList<>();
        }
        return taskInfoMenu.apply(taskInfo);
    }

    private Expander buildExpander(final TaskProgress row) {
        return row.getExpander();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TaskManagerList> {

    }

    /// Shows a task's info.
    public interface InfoHandler {

        /// @param row           The task.
        /// @param popupPosition Where to show it.
        /// @param anchor        The info icon pressed.
        void show(TaskProgress row, PopupPosition popupPosition, Element anchor);
    }
}
