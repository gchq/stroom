package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.data.client.event.DataSelectionEvent;
import stroom.data.client.event.DataSelectionEvent.DataSelectionHandler;
import stroom.data.client.event.HasDataSelectionHandlers;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.OrderByColumn;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.AccountResultPage;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.shared.PermissionNames;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskListener;
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
        dataGrid.addColumnSortHandler(event -> {
            if (event.getColumn() instanceof OrderByColumn<?, ?>) {
                final OrderByColumn<?, ?> orderByColumn = (OrderByColumn<?, ?>) event.getColumn();
                sortList = Collections.singletonList(
                        new CriteriaFieldSort(
                                orderByColumn.getField(),
                                !event.isSortAscending(),
                                orderByColumn.isIgnoreCase()));
                dataProvider.refresh();
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
                            .taskListener(getView())
                            .exec();
                }
            });
        }
    }

    private void initTableColumns() {
        // User Id
        if (securityContext.hasAppPermission(PermissionNames.MANAGE_USERS_PERMISSION)) {
            final Column<Account, String> userIdColumn = DataGridUtil.textColumnBuilder(
                            Account::getUserId)
                    .enabledWhen(Account::isEnabled)
                    .withSorting(FindAccountRequest.FIELD_NAME_USER_ID)
                    .build();
            dataGrid.addResizableColumn(userIdColumn, "User Id", 250);
        }

        // Email
        final Column<Account, String> emailColumn = DataGridUtil.textColumnBuilder(Account::getEmail)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_EMAIL)
                .build();
        dataGrid.addResizableColumn(emailColumn, "Email", 250);

        // Status
        final Column<Account, String> statusColumn = DataGridUtil.textColumnBuilder(Account::getStatus)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_STATUS)
                .build();
        dataGrid.addColumn(statusColumn, "Status", ColumnSizeConstants.MEDIUM_COL);

        // Last Sign In
        final Column<Account, String> lastSignInColumn = DataGridUtil.textColumnBuilder((Account account) ->
                        dateTimeFormatter.format(account.getLastLoginMs()))
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_LAST_LOGIN_MS)
                .build();
        dataGrid.addColumn(lastSignInColumn, "Last Sign In", ColumnSizeConstants.DATE_COL);

        // Sign In Failures
        final Column<Account, String> signInFailuresColumn = DataGridUtil.textColumnBuilder((Account account) ->
                        "" + account.getLoginFailures())
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_LOGIN_FAILURES)
                .build();
        dataGrid.addColumn(signInFailuresColumn, "Sign In Failures", 130);

        // Comments
        final Column<Account, String> commentsColumn = DataGridUtil.textColumnBuilder(Account::getComments)
                .enabledWhen(Account::isEnabled)
                .withSorting(FindAccountRequest.FIELD_NAME_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private void fetchData(final Range range,
                           final Consumer<AccountResultPage> dataConsumer,
                           final TaskListener taskListener) {
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
                .taskListener(taskListener)
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
