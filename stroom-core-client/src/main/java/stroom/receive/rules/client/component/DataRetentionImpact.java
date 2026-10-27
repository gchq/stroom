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

package stroom.receive.rules.client.component;

import stroom.cell.expander.client.ExpanderCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.button.client.ToolbarToggleButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;

import com.google.gwt.core.client.GWT;
import com.google.gwt.i18n.client.NumberFormat;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.HasData;

import java.util.function.Consumer;

/// The data retention rules' Impact Summary: a row per rule (and, nested, per type and feed) with
/// how many streams it would delete, and buttons to run and stop the query, filter it, show it
/// nested or flat, and expand or collapse it.
public class DataRetentionImpact extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final NumberFormat COMMA_INTEGER_FORMAT = NumberFormat.getFormat("#,##0");

    @UiField
    ToolbarButton runButton;
    @UiField
    ToolbarButton stopButton;
    @UiField
    ToolbarButton filterButton;
    @UiField
    ToolbarToggleButton nestedButton;
    @UiField
    ToolbarButton expandAllButton;
    @UiField
    ToolbarButton collapseAllButton;
    @UiField
    DataGrid<DataRetentionImpactRow> dataGrid;

    private Consumer<ImpactAction> onAction = action -> {
    };
    private Consumer<Boolean> onNestedChange = nested -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes the list, with no columns until [#addColumns].
    public DataRetentionImpact() {
        initWidget(BINDER.createAndBindUi(this));
        dataGrid.addDefaultSelectionModel(false);
        runButton.setOnClick(() -> onAction.accept(ImpactAction.RUN));
        stopButton.setOnClick(() -> onAction.accept(ImpactAction.STOP));
        filterButton.setOnClick(() -> onAction.accept(ImpactAction.FILTER));
        nestedButton.setOnToggle(nested -> onNestedChange.accept(nested));
        expandAllButton.setOnClick(() -> onAction.accept(ImpactAction.EXPAND_ALL));
        collapseAllButton.setOnClick(() -> onAction.accept(ImpactAction.COLLAPSE_ALL));
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns: the rows' expanders, the rule's number, name and age, the type, the feed
    /// and the count, each sortable.
    ///
    /// @param treeAction       Which rows are expanded.
    /// @param onExpanderChange Run when a row is expanded or collapsed.
    public void addColumns(final DataRetentionImpactTreeAction treeAction, final Runnable onExpanderChange) {
        DataGridUtil.addExpanderColumn(
                dataGrid,
                DataRetentionImpactRow::getExpander,
                treeAction,
                onExpanderChange,
                ExpanderCell.getColumnWidth(3)); // Need space for three expander levels

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder((DataRetentionImpactRow row) ->
                                NullSafe.toString(row.getRuleNumber()))
                        .rightAligned()
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_NO)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_RULE_NO)
                        .rightAligned()
                        .withToolTip("The lower the rule number, the higher priority when matching streams. " +
                                     "A stream's retention will be governed by the matching rule with the " +
                                     "highest priority.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleName)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_NAME)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_RULE_NAME)
                        .withToolTip("The name of the rule.")
                        .build(),
                200);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getRuleAgeStr)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_RULE_AGE)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_RULE_AGE)
                        .withToolTip("The retention age of this rule.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getType)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_TYPE)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_TYPE)
                        .withToolTip("The stream type.")
                        .build(),
                ColumnSizeConstants.MEDIUM_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.textColumnBuilder(DataRetentionImpactRow::getFeed)
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_FEED)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_FEED)
                        .withToolTip("The feed name.")
                        .build(),
                ColumnSizeConstants.BIG_COL);

        dataGrid.addResizableColumn(
                DataGridUtil.htmlColumnBuilder(DataRetentionImpact::getIndentedCountCellText)
                        .rightAligned()
                        .withSorting(DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT)
                        .build(),
                DataGridUtil.headingBuilder(DataRetentionImpactRow.FIELD_NAME_DELETE_COUNT)
                        .rightAligned()
                        .withToolTip("The number of streams that would be deleted by this rule.")
                        .build(),
                150);
    }

    // The count, indented below its group's
    private static SafeHtml getIndentedCountCellText(final DataRetentionImpactRow row) {
        if (row != null) {
            final SafeHtmlBuilder countCellText = new SafeHtmlBuilder();
            final String countStr = COMMA_INTEGER_FORMAT.format(row.getCount());
            countCellText.appendEscaped(countStr);

            if (row.getExpander() != null) {
                final String singleIndent = "&ensp;&ensp;";

                switch (row.getExpander().getDepth()) {
                    case 0:
                        countCellText.appendHtmlConstant(singleIndent);
                        countCellText.appendHtmlConstant(singleIndent);
                        break;
                    case 1:
                        countCellText.appendHtmlConstant(singleIndent);
                        break;
                    default:
                        break;  // no indent for the detail level
                }
            }
            return countCellText.toSafeHtml();
        } else {
            return null;
        }
    }

    /// @return The grid, for a data provider to fill.
    public HasData<DataRetentionImpactRow> getDataDisplay() {
        return dataGrid;
    }

    /// @return The columns the rows are sorted by.
    public ColumnSortList getColumnSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Unsorts the rows, showing their headings unsorted.
    public void clearSort() {
        dataGrid.clearColumnSortList();
        dataGrid.redrawHeaders();
    }

    /// Sets which buttons can be pressed.
    ///
    /// @param running     Whether the query is running, so it can be stopped but not run or filtered.
    /// @param nested      Whether the rows are shown nested, so the Nested Results button is pressed.
    /// @param canExpand   Whether some rows are collapsed, so Expand All can be pressed.
    /// @param canCollapse Whether some rows are expanded, so Collapse All can be pressed.
    public void setButtonStates(final boolean running,
                                final boolean nested,
                                final boolean canExpand,
                                final boolean canCollapse) {
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        filterButton.setEnabled(!running);
        nestedButton.setState(nested);
        expandAllButton.setEnabled(canExpand);
        collapseAllButton.setEnabled(canCollapse);
    }

    /// @param onAction Given the button pressed.
    public void setOnAction(final Consumer<ImpactAction> onAction) {
        this.onAction = onAction;
    }

    /// @param onNestedChange Given whether the rows are to be shown nested, when the Nested Results
    ///                       button is pressed.
    public void setOnNestedChange(final Consumer<Boolean> onNestedChange) {
        this.onNestedChange = onNestedChange;
    }

    /// @param onSortChange Run when the rows are sorted by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }


    // --------------------------------------------------------------------------------


    /// The list's buttons, other than Nested Results.
    public enum ImpactAction {
        /// Runs the query.
        RUN,
        /// Stops the query.
        STOP,
        /// Sets the query's filter.
        FILTER,
        /// Expands every row.
        EXPAND_ALL,
        /// Collapses every row.
        COLLAPSE_ALL
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DataRetentionImpact> {

    }
}
