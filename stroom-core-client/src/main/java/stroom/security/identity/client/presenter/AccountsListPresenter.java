package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CommandLink;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUserOrGroupEvent;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Selection;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class AccountsListPresenter
        extends MyPresenterWidget<PagerView>
        implements HasDataSelectionHandlers<Selection<Integer>> {

    private static final AccountResource ACCOUNT_RESOURCE = GWT.create(AccountResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final ClientSecurityContext securityContext;
    private final Provider<EditAccountPresenter> editAccountPresenterProvider;
    private final MultiSelectionModelImpl<Account> selectionModel;
    private final QuickFilterTimer timer = new QuickFilterTimer();
    private final RestDataProvider<Account, AccountResultPage> dataProvider;
    private final MyDataGrid<Account> dataGrid;
    private final InlineSvgButton addButton;
    private final InlineSvgButton editButton;
    private final InlineSvgButton deleteButton;
    private String filter;

    private List<CriteriaFieldSort> sortList = Collections.singletonList(
            new CriteriaFieldSort(FindAccountRequest.FIELD_NAME_USER_ID, false, true));

    @Inject
    public AccountsListPresenter(final EventBus eventBus,
                                 final PagerView view,
                                 final RestFactory restFactory,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final ClientSecurityContext securityContext,
                                 final Provider<EditAccountPresenter> editAccountPresenterProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.securityContext = securityContext;
        this.editAccountPresenterProvider = editAccountPresenterProvider;
        this.dataGrid = new MyDataGrid<>(1000);
        this.selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        final DataGridSelectionEventManager<Account> selectionEventManager =
                new DataGridSelectionEventManager<>(dataGrid, selectionModel, false);
        this.dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        view.setDataWidget(dataGrid);

        addButton = new InlineSvgButton();
        editButton = new InlineSvgButton();
        deleteButton = new InlineSvgButton();
        initButtons();
        initTableColumns();
        dataProvider = new RestDataProvider<Account, AccountResultPage>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<AccountResultPage> dataConsumer,
                                final RestErrorHandler errorHandler) {
                fetchData(range, dataConsumer, view);
            }
        };
        dataProvider.addDataDisplay(dataGrid);
        selectionModel.addSelectionHandler(event -> {
            setButtonStates();
            if (event.getSelectionType().isDoubleSelect()) {
                editSelectedAccount();
            }
        });

    }

    private void initButtons() {
        addButton.setSvg(SvgImage.ADD);
        addButton.setTitle("Add new account");
        addButton.addClickHandler(event -> createNewAccount());
        getView().addButton(addButton);

        editButton.setSvg(SvgImage.EDIT);
        editButton.setTitle("Edit account");
        editButton.addClickHandler(event -> editSelectedAccount());
        getView().addButton(editButton);

        deleteButton.setSvg(SvgImage.DELETE);
        deleteButton.setTitle("Delete selected account");
        deleteButton.addClickHandler(event -> deleteSelectedAccount());
        getView().addButton(deleteButton);

        setButtonStates();
    }

    private void setButtonStates() {
        final boolean hasSelectedItems = GwtNullSafe.hasItems(selectionModel.getSelectedItems());
        editButton.setEnabled(hasSelectedItems);
        deleteButton.setEnabled(selectionModel.getSelected() != null);
    }

    private void createNewAccount() {
        editAccountPresenterProvider.get().showCreateDialog(dataProvider::refresh);
    }

    private void editSelectedAccount() {
        final Account account = selectionModel.getSelected();
        editAccountPresenterProvider.get().showEditDialog(account, dataProvider::refresh);
    }

    private void deleteSelectedAccount() {
        // This is the one selected using row selection, not the checkbox
        final Account account = selectionModel.getSelected();
        if (account != null) {
            final String msg = "Are you sure you want to delete account '"
                               + account.getUserId()
                               + "'?";
            ConfirmEvent.fire(this, msg, ok -> {
                if (ok) {
                    restFactory
                            .create(ACCOUNT_RESOURCE)
                            .method(res -> res.delete(account.getId()))
                            .onSuccess(unused -> {
                                selectionModel.clear();
                                this.refresh();
                            })
                            .onFailure(e -> AlertEvent.fireError(this, e.getMessage(), this::refresh))
                            .taskMonitorFactory(getView())
                            .exec();
                }
            });
        }
    }

    private void setSortList(final List<CriteriaFieldSort> sortList) {
        this.sortList = sortList;
    }

    private void initTableColumns() {

        DataGridUtil.addColumnSortHandler(
                dataGrid,
                this::setSortList,
                this::refresh);

        // User Id
        final Column<Account, CommandLink> userIdCol = DataGridUtil.commandLinkColumnBuilder(buildOpenUserCommandLink())
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_USER_ID)
                .build();
        dataGrid.addResizableColumn(
                userIdCol,
                DataGridUtil.headingBuilder("User Id")
                        .withToolTip("The unique identifier for both the account and the corresponding user.")
                        .build(),
                200);

        dataGrid.getColumnSortList().push(userIdCol);

        // First Name
        final Column<Account, String> firstNameColumn = DataGridUtil.textColumnBuilder(Account::getFirstName)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_FIRST_NAME)
                .build();
        dataGrid.addResizableColumn(firstNameColumn, "First Name", 180);

        // First Name
        final Column<Account, String> lastNameColumn = DataGridUtil.textColumnBuilder(Account::getLastName)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_LAST_NAME)
                .build();
        dataGrid.addResizableColumn(lastNameColumn, "Last Name", 180);

        // Email
        final Column<Account, String> emailColumn = DataGridUtil.textColumnBuilder(Account::getEmail)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_EMAIL)
                .build();
        dataGrid.addResizableColumn(emailColumn, "Email", 250);

        // Status
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(Account::getStatus)
                        .enabledWhen(Account::isEnabled)
                        .withSorting(FindAccountRequest.FIELD_NAME_STATUS)
                        .build(),
                DataGridUtil.headingBuilder("Status")
                        .withToolTip("The status of the account. One of (Enabled|Disabled|Locked|Inactive).")
                        .build(),
                ColumnSizeConstants.SMALL_COL);

        // Last Sign In
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) ->
                                dateTimeFormatter.format(account.getLastLoginMs()))
                        .enabledWhen(Account::isEnabled)
                        .withSorting(FindAccountRequest.FIELD_NAME_LAST_LOGIN_MS)
                        .build(),
                DataGridUtil.headingBuilder("Last Sign In")
                        .withToolTip("The date/time the user last successfully signed in.")
                        .build(),
                ColumnSizeConstants.DATE_COL);

        // Sign In Failures
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder((Account account) ->
                                "" + account.getLoginFailures())
                        .enabledWhen(Account::isEnabled)
                        .withSorting(FindAccountRequest.FIELD_NAME_LOGIN_FAILURES)
                        .build(),
                DataGridUtil.headingBuilder("Sign In Failures")
                        .withToolTip("The number of login failures since the last successful login.")
                        .build(),
                130);

        // Comments
        final Column<Account, String> commentsColumn = DataGridUtil.textColumnBuilder(Account::getComments)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private Function<Account, CommandLink> buildOpenUserCommandLink() {
        return (Account account) -> {
            if (account != null) {
                final String userId = account.getUserId();

                return new CommandLink(
                        userId,
                        "Open account '" + userId + "' on the Users and Groups screen.",
                        () -> OpenUserOrGroupEvent.fire(
                                AccountsListPresenter.this, userId));
            } else {
                return null;
            }
        };
    }

    private void fetchData(final Range range,
                           final Consumer<AccountResultPage> dataConsumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        final PageRequest pageRequest = new PageRequest(range.getStart(), range.getLength());
        final FindAccountRequest criteria = new FindAccountRequest(pageRequest, sortList, filter);
        restFactory
                .create(ACCOUNT_RESOURCE)
                .method(res -> res.find(criteria))
                .onSuccess(dataConsumer)
                .onFailure(throwable ->
                        AlertEvent.fireError(
                                this,
                                "Error fetching accounts: " + throwable.getMessage(),
                                null))
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void setQuickFilter(final String userInput) {
        timer.setName(userInput);
        timer.cancel();
        timer.schedule(400);
    }

    public void refresh() {
        dataProvider.refresh();
    }

    @Override
    public HandlerRegistration addDataSelectionHandler(final DataSelectionHandler<Selection<Integer>> handler) {
        return addHandlerToSource(DataSelectionEvent.getType(), handler);
    }

    // --------------------------------------------------------------------------------


    private class QuickFilterTimer extends Timer {

        private String name;

        @Override
        public void run() {
            String filter = name;
            if (filter != null) {
                filter = filter.trim();
                if (filter.length() == 0) {
                    filter = null;
                }
            }

            if (!Objects.equals(filter, AccountsListPresenter.this.filter)) {

                // This is a new filter so reset all the expander states
                AccountsListPresenter.this.filter = filter;
                refresh();
            }
        }

        public void setName(final String name) {
            this.name = name;
        }
    }
}
