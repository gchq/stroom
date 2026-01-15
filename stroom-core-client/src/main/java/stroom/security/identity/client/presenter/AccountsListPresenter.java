/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.security.identity.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountFields;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserResource;
import stroom.svg.shared.SvgImage;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.function.Consumer;
import java.util.function.Function;

public class AccountsListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final AccountResource ACCOUNT_RESOURCE = GWT.create(AccountResource.class);
    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;
    private final Provider<EditAccountPresenter> editAccountPresenterProvider;
    private final MultiSelectionModelImpl<Account> selectionModel;
    private RestDataProvider<Account, ResultPage<Account>> dataProvider;
    private final MyDataGrid<Account> dataGrid;
    private final InlineSvgButton addButton;
    private final InlineSvgButton editButton;
    private final InlineSvgButton deleteButton;
    private final PagerView pagerView;
    private final FindAccountRequest.Builder requestBuilder = new FindAccountRequest.Builder();

    @Inject
    public AccountsListPresenter(final EventBus eventBus,
                                 final QuickFilterPageView view,
                                 final PagerView pagerView,
                                 final RestFactory restFactory,
                                 final DateTimeFormatter dateTimeFormatter,
                                 final Provider<EditAccountPresenter> editAccountPresenterProvider,
                                 final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.pagerView = pagerView;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        this.editAccountPresenterProvider = editAccountPresenterProvider;
        this.dataGrid = new MyDataGrid<>(this, 1000);
        this.selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<Account> selectionEventManager = new DataGridSelectionEventManager<>(
                dataGrid, selectionModel, false);
        this.dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        pagerView.setDataWidget(dataGrid);

        addButton = new InlineSvgButton();
        editButton = new InlineSvgButton();
        deleteButton = new InlineSvgButton();
        initButtons();
        initTableColumns();

        selectionModel.addSelectionHandler(event -> {
            setButtonStates();
            if (event.getSelectionType().isDoubleSelect()) {
                editSelectedAccount();
            }
        });

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Accounts Quick Filter",
                        AccountFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        view.setDataView(pagerView);
        view.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void initButtons() {
        addButton.setSvg(SvgImage.ADD);
        addButton.setTitle("Add new account");
        addButton.addClickHandler(event -> createNewAccount());
        pagerView.addButton(addButton);

        editButton.setSvg(SvgImage.EDIT);
        editButton.setTitle("Edit account");
        editButton.addClickHandler(event -> editSelectedAccount());
        pagerView.addButton(editButton);

        deleteButton.setSvg(SvgImage.DELETE);
        deleteButton.setTitle("Delete selected account");
        deleteButton.addClickHandler(event -> deleteSelectedAccount());
        pagerView.addButton(deleteButton);

        setButtonStates();
    }

    private void setButtonStates() {
        final boolean hasSelectedItems = NullSafe.hasItems(selectionModel.getSelectedItems());
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
                            .onFailure(e ->
                                    AlertEvent.fireError(this, e.getMessage(), this::refresh))
                            .taskMonitorFactory(pagerView)
                            .exec();
                }
            });
        }
    }

    private void initTableColumns() {
        // User Id
        final Column<Account, CommandLink> userIdCol = DataGridUtil.commandLinkColumnBuilder(buildOpenUserCommandLink())
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

        // Status
        dataGrid.addColumn(
                DataGridUtil.textColumnBuilder(Account::getStatus)
                        .enabledWhen(Account::isEnabled)
                        .withSorting(AccountFields.FIELD_NAME_STATUS)
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
                        .withSorting(AccountFields.FIELD_NAME_LAST_LOGIN_MS)
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
                        .withSorting(AccountFields.FIELD_NAME_LOGIN_FAILURES)
                        .build(),
                DataGridUtil.headingBuilder("Sign In Failures")
                        .withToolTip("The number of login failures since the last successful login.")
                        .build(),
                130);

        // Comments
        final Column<Account, String> commentsColumn = DataGridUtil.textColumnBuilder(Account::getComments)
                .enabledWhen(Account::isEnabled)
                .withSorting(AccountFields.FIELD_NAME_COMMENTS)
                .build();
        dataGrid.addAutoResizableColumn(commentsColumn, "Comments", ColumnSizeConstants.BIG_COL);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private Function<Account, CommandLink> buildOpenUserCommandLink() {
        return (final Account account) -> {
            if (account != null) {
                final String userId = account.getUserId();

                return new CommandLink(
                        userId,
                        "Open account '" + userId + "' on the Users and Groups screen.",
                        () -> {
                            restFactory
                                    .create(USER_RESOURCE)
                                    .method(userResource ->
                                            userResource.fetchBySubjectId(userId))
                                    .onSuccess(user -> {
                                        if (user != null) {
                                            OpenUsersAndGroupsScreenEvent.fire(
                                                    AccountsListPresenter.this,
                                                    user.asRef());
                                        }
                                    })
                                    .taskMonitorFactory(pagerView)
                                    .exec();
                        });
            } else {
                return null;
            }
        };
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<Account, ResultPage<Account>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<Account>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    requestBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                    requestBuilder.sortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));
                    restFactory
                            .create(ACCOUNT_RESOURCE)
                            .method(res -> res.find(requestBuilder.build()))
                            .onSuccess(dataConsumer)
                            .onFailure(throwable ->
                                    AlertEvent.fireError(
                                            this,
                                            "Error fetching accounts: " + throwable.getMessage(),
                                            null))
                            .taskMonitorFactory(pagerView)
                            .exec();
                }
            };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    @Override
    public void onFilterChange(String text) {
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                text = null;
            }
        }

        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(text, AccountFields.DEFAULT_FIELDS, AccountFields.ALL_FIELDS_MAP);
        requestBuilder.expression(expression);
        refresh();
    }

    public void setQuickFilterText(final String filterInput) {
        getView().setQuickFilterText(filterInput);
    }
}
