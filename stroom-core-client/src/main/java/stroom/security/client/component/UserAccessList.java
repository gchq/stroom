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

import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.security.shared.UserAccessRow;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.dropdowntree.client.view.QuickFilterPage;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.Collections;
import java.util.function.Consumer;
import java.util.function.Function;

/// Who currently holds access: one row per user, showing their sessions and the tokens the internal
/// IdP has issued them side by side; a quick filter above them, and a pager with a toolbar for the
/// screen's own buttons.
public class UserAccessList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    DataGrid<UserAccessRow> dataGrid;

    private final MultiSelectionModelImpl<UserAccessRow> selectionModel;
    private Function<Long, String> dateFormat = String::valueOf;

    /// Makes an empty list, with no columns until [#addColumns(Function)].
    public UserAccessList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(UserAccessRow::getSubjectId);
        selectionModel = dataGrid.addDefaultSelectionModel(true);
    }

    /// Adds the columns.
    ///
    /// @param dateFormat Formats a time with how long ago (or until) it is, in the user's preferences.
    public void addColumns(final Function<Long, String> dateFormat) {
        this.dateFormat = dateFormat;
        addColumns();
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<UserAccessRow> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection.
    public MultiSelectionModelImpl<UserAccessRow> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Empties the list.
    public void clearRows() {
        dataGrid.setRowData(0, Collections.emptyList());
        dataGrid.setRowCount(0, true);
    }

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return pager.getToolbar();
    }

    /// Sets the filter's text; if it changes, the filter change callback is told.
    ///
    /// @param quickFilterText The text.
    public void setQuickFilterText(final String quickFilterText) {
        page.setQuickFilterText(quickFilterText);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    private void addColumns() {
        // User. Falls back to the subject id server side, so this is never blank even for a subject that has
        // no stroom user - which is exactly the kind of row an administrator needs to be able to see.
        final Column<UserAccessRow, String> userColumn = DataGridUtil.textColumnBuilder(
                        UserAccessRow::getDisplayName)
                .build();
        dataGrid.addResizableColumn(userColumn, "User", 250);

        // Subject id, so a row whose display name is ambiguous can still be told apart, and so the id an
        // administrator sees in a log line can be matched to a row here.
        final Column<UserAccessRow, String> subjectColumn = DataGridUtil.textColumnBuilder(
                        UserAccessRow::getSubjectId)
                .build();
        dataGrid.addResizableColumn(subjectColumn, "Subject Id", 250);

        final Column<UserAccessRow, String> sessionsColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> String.valueOf(row.getSessionCount()))
                .rightAligned()
                .build();
        dataGrid.addColumn(sessionsColumn, "Sessions", ColumnSizeConstants.SMALL_COL);

        final Column<UserAccessRow, String> nodesColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> String.join(", ", NullSafe.list(row.getNodeNames())))
                .build();
        dataGrid.addResizableColumn(nodesColumn, "Nodes", 180);

        final Column<UserAccessRow, String> lastAccessedColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> row.getLastAccessedMs() == null
                                ? ""
                                : dateFormat.apply(row.getLastAccessedMs()))
                .build();
        dataGrid.addColumn(lastAccessedColumn, "Last Accessed", ColumnSizeConstants.DATE_AND_DURATION_COL);

        // Always zero against an external IdP, since nothing is minted here.
        final Column<UserAccessRow, String> tokensColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> String.valueOf(row.getTokenCount()))
                .rightAligned()
                .build();
        dataGrid.addColumn(tokensColumn, "Tokens", ColumnSizeConstants.SMALL_COL);

        // How long token-based access would persist if left alone - usually driven by a refresh token, so
        // weeks rather than the access token's hour. This is the number that makes the case for revoking.
        final Column<UserAccessRow, String> tokenExpiryColumn = DataGridUtil.textColumnBuilder(
                        (UserAccessRow row) -> row.getLastTokenExpiryMs() == null
                                ? ""
                                : dateFormat.apply(row.getLastTokenExpiryMs()))
                .build();
        dataGrid.addAutoResizableColumn(
                tokenExpiryColumn, "Access Expires", ColumnSizeConstants.DATE_AND_DURATION_COL);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, UserAccessList> {

    }
}
