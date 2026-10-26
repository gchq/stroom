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

package stroom.security.identity.client.component;

import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountFields;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.datagrid.client.PagerPanel;
import stroom.widget.dropdowntree.client.view.QuickFilterContextHandler;
import stroom.widget.dropdowntree.client.view.QuickFilterPage;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.client.ui.Widget;
import com.google.gwt.view.client.AbstractDataProvider;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/// Accounts: a quick filter above a page of them, a row each (user id, names, email, whether it is
/// enabled, locked or active, sign in failures, last sign in and comments), faded where disabled, with
/// New, Edit and Delete. One can be selected, and a double click edits it.
public class AccountsList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    QuickFilterPage page;
    @UiField
    PagerPanel pager;
    @UiField
    ToolbarButton newButton;
    @UiField
    ToolbarButton editButton;
    @UiField
    ToolbarButton deleteButton;
    @UiField
    DataGrid<Account> dataGrid;

    private final MultiSelectionModelImpl<Account> selectionModel;
    private Runnable onNew = () -> {
    };
    private Runnable onEdit = () -> {
    };
    private Runnable onDelete = () -> {
    };
    private Runnable onSortChange = () -> {
    };

    /// Makes an empty list, with no columns until [#addColumns(Function, Function)].
    public AccountsList() {
        initWidget(BINDER.createAndBindUi(this));
        // Rows keyed by id, so a row whose values change is still the same row
        dataGrid.setKeyProvider(Account::getId);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        newButton.setOnClick(() -> onNew.run());
        editButton.setOnClick(() -> onEdit.run());
        deleteButton.setOnClick(() -> onDelete.run());
        selectionModel.addSelectionHandler(event -> {
            final boolean selected = selectionModel.getSelected() != null;
            editButton.setEnabled(selected);
            deleteButton.setEnabled(selected);
            if (event.getSelectionType().isDoubleSelect()) {
                onEdit.run();
            }
        });
        dataGrid.addColumnSortHandler(event -> onSortChange.run());
    }

    /// Adds the columns, once.
    ///
    /// @param openUserLink   Gives an account's User Id link, which opens its user.
    /// @param dateTimeFormat Formats a time in milliseconds since the epoch.
    public void addColumns(final Function<Account, CommandLink> openUserLink,
                           final Function<Long, String> dateTimeFormat) {
        // User Id
        final Column<Account, CommandLink> userIdCol = DataGridUtil.commandLinkColumnBuilder(openUserLink)
                .enabledWhen(Account::isEnabled)
                .withSorting(AccountFields.FIELD_NAME_USER_ID)
                .build();
        dataGrid.addResizableColumn(
                userIdCol,
                DataGridUtil.headingBuilder("User Id")
                        .withToolTip("The unique identifier for both the account and the corresponding user.")
                        .build(),
                200);
        dataGrid.sort(userIdCol);

        // First Name
        final Column<Account, String> firstNameColumn = DataGridUtil.textColumnBuilder(Account::getFirstName)
                .enabledWhen(Account::isEnabled)
                .withSorting(AccountFields.FIELD_NAME_FIRST_NAME)
                .build();
        dataGrid.addResizableColumn(firstNameColumn, "First Name", 180);

        // First Name
        final Column<Account, String> lastNameColumn = DataGridUtil.textColumnBuilder(Account::getLastName)
                .enabledWhen(Account::isEnabled)
                .withSorting(AccountFields.FIELD_NAME_LAST_NAME)
                .build();
        dataGrid.addResizableColumn(lastNameColumn, "Last Name", 180);

        // Email
        final Column<Account, String> emailColumn = DataGridUtil.textColumnBuilder(Account::getEmail)
                .enabledWhen(Account::isEnabled)
                .withSorting(AccountFields.FIELD_NAME_EMAIL)
                .build();
        dataGrid.addResizableColumn(emailColumn, "Email", 250);

        // The three account states get a column each rather than being collapsed into one "status". They
        // are independent, and any single value has to pick a precedence, which hides two of the three
        // answers behind whichever it happens to rank first.

        // Enabled
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) -> account.isEnabled()
                                ? "Enabled"
                                : "Disabled")
                        .enabledWhen(Account::isEnabled)
                        .withSorting(AccountFields.FIELD_NAME_ENABLED)
                        .build(),
                DataGridUtil.headingBuilder("Enabled")
                        .withToolTip("Whether an administrator has allowed the account to be used. "
                                     + "This is the only one of the three states an administrator sets.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        // Locked
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) -> describeLock(account, dateTimeFormat))
                        .enabledWhen(Account::isEnabled)
                        .withSorting(AccountFields.FIELD_NAME_LOCKED)
                        .build(),
                DataGridUtil.headingBuilder("Locked")
                        .withToolTip("Whether repeated wrong passwords are currently barring the account. "
                                     + "Blank when it is not locked. Locks are applied automatically and "
                                     + "normally clear themselves.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        // Active
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) -> account.isInactive()
                                ? "Inactive"
                                : "Active")
                        .enabledWhen(Account::isEnabled)
                        .withSorting(AccountFields.FIELD_NAME_INACTIVE)
                        .build(),
                DataGridUtil.headingBuilder("Active")
                        .withToolTip("Whether the account has gone unused. Applied by the account "
                                     + "maintenance job, not by an administrator.")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        // Sign In Failures
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) ->
                                "" + account.getFailureCount())
                        .enabledWhen(Account::isEnabled)
                        .withSorting(AccountFields.FIELD_NAME_FAILURE_COUNT)
                        .rightAligned()
                        .build(),
                DataGridUtil.headingBuilder("Sign In Failures")
                        .withToolTip("The number of sign in failures since the last successful sign in.")
                        .rightAligned()
                        .build(),
                130);

        // Last Sign In
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) ->
                                dateTimeFormat.apply(account.getLastLoginMs()))
                        .enabledWhen(Account::isEnabled)
                        .withSorting(AccountFields.FIELD_NAME_LAST_LOGIN_MS)
                        .build(),
                DataGridUtil.headingBuilder("Last Sign In")
                        .withToolTip("The date/time the user last successfully signed in.")
                        .build(),
                ColumnSizeConstants.DATE_COL);

        // Comments
        final Column<Account, String> commentsColumn = DataGridUtil.textColumnBuilder(Account::getComments)
                .enabledWhen(Account::isEnabled)
                .withSorting(AccountFields.FIELD_NAME_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);
    }

    // Blank unless the lock is actually in force, so a glance down the column finds the locked accounts.
    // Uses the derived isLocked rather than the stored flag, which outlives the lock itself because a
    // lapsed lock is only cleared on the next sign in attempt.
    private static String describeLock(final Account account, final Function<Long, String> dateTimeFormat) {
        if (!account.isLocked()) {
            return "";
        }
        return account.getFailureLockedUntilMs() == null
                ? "Locked"
                : "Locked until " + dateTimeFormat.apply(account.getFailureLockedUntilMs());
    }

    /// @param dataProvider Gives the list its rows, a page at a time.
    public void setDataProvider(final AbstractDataProvider<Account> dataProvider) {
        dataProvider.addDataDisplay(dataGrid);
    }

    /// @return The task monitor that spins the pager's refresh button.
    public TaskMonitorFactory getTaskMonitor() {
        return pager;
    }

    /// @return The selection, one account at most.
    public MultiSelectionModel<Account> getSelectionModel() {
        return selectionModel;
    }

    /// @return How the rows are sorted, for the request for them.
    public ColumnSortList getSortList() {
        return dataGrid.getColumnSortList();
    }

    /// Sets the filter's text; if it changes, the filter change callback is told.
    ///
    /// @param quickFilterText The text.
    public void setQuickFilterText(final String quickFilterText) {
        page.setQuickFilterText(quickFilterText);
    }

    /// @param filterError Why the server rejected the filter, or `null` to clear it.
    public void setFilterError(final String filterError) {
        page.setFilterError(filterError);
    }

    /// @param popupTextSupplier Gives the filter's help popup its text.
    public void registerPopupTextProvider(final Supplier<SafeHtml> popupTextSupplier) {
        page.registerPopupTextProvider(popupTextSupplier);
    }

    /// @param contextHandler Gives the filter its recent filters and remembers the ones used.
    public void setQuickFilterContextHandler(final QuickFilterContextHandler contextHandler) {
        page.setQuickFilterContextHandler(contextHandler);
    }

    /// @param onFilterChange Given the filter's text when it changes.
    public void setOnFilterChange(final Consumer<String> onFilterChange) {
        page.setOnFilterChange(onFilterChange);
    }

    /// @param onNew Run when New is pressed.
    public void setOnNew(final Runnable onNew) {
        this.onNew = onNew;
    }

    /// @param onEdit Run when Edit is pressed, or an account is double clicked.
    public void setOnEdit(final Runnable onEdit) {
        this.onEdit = onEdit;
    }

    /// @param onDelete Run when Delete is pressed.
    public void setOnDelete(final Runnable onDelete) {
        this.onDelete = onDelete;
    }

    /// @param onSortChange Run when the user sorts by another column.
    public void setOnSortChange(final Runnable onSortChange) {
        this.onSortChange = onSortChange;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AccountsList> {

    }
}
