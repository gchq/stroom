/*
 * Copyright 2024 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.ConfirmEvent;
import stroom.cell.info.client.CommandLink;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.preferences.client.DateTimeFormatter;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.identity.shared.Account;
import stroom.security.identity.shared.AccountFields;
import stroom.security.identity.shared.AccountResource;
import stroom.security.identity.shared.FindAccountRequest;
import stroom.security.shared.UserResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.component.client.Controller;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The accounts ([AccountsList]), a page at a time, found by its quick filter; New and Edit open the
/// account dialog, Delete deletes the selected account once confirmed, and an account's User Id
/// opens its user on the Users and Groups screen.
public class AccountsListController extends Controller {

    private static final AccountResource ACCOUNT_RESOURCE = GWT.create(AccountResource.class);
    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final AccountsList view;
    private final RestFactory restFactory;
    private final Provider<AccountEditorController> editAccountProvider;
    private final FindAccountRequest.Builder requestBuilder = new FindAccountRequest.Builder();
    private RestDataProvider<Account, ResultPage<Account>> dataProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param restFactory                      Finds, and deletes, the accounts.
    /// @param dateTimeFormatter                Formats the times shown.
    /// @param editAccountProvider              Makes the account dialog.
    /// @param uiConfigCache                    Gives the quick filter's help link.
    /// @param quickFilterContextHandlerFactory Makes the quick filter's recent filters.
    @Inject
    public AccountsListController(final EventBus eventBus,
                                  final AccountsList view,
                                  final RestFactory restFactory,
                                  final DateTimeFormatter dateTimeFormatter,
                                  final Provider<AccountEditorController> editAccountProvider,
                                  final UiConfigCache uiConfigCache,
                                  final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.editAccountProvider = editAccountProvider;
        initWidget(view);
        view.addColumns(this::openUserLink, dateTimeFormatter::format);
        view.setOnNew(this::createNewAccount);
        view.setOnEdit(this::editSelectedAccount);
        view.setOnDelete(this::deleteSelectedAccount);
        view.setOnSortChange(this::refresh);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Accounts Quick Filter",
                        AccountFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, view.getTaskMonitor());

        view.setOnFilterChange(this::onFilterChange);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                AccountFields.QUICK_FILTER_CONTEXT, this, view.getTaskMonitor()));
    }

    private void createNewAccount() {
        editAccountProvider.get().create(this, dataProvider::refresh);
    }

    private void editSelectedAccount() {
        final Account account = view.getSelectionModel().getSelected();
        if (account != null) {
            editAccountProvider.get().edit(this, account, dataProvider::refresh);
        }
    }

    private void deleteSelectedAccount() {
        final Account account = view.getSelectionModel().getSelected();
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
                                view.getSelectionModel().clear();
                                refresh();
                            })
                            .onFailure(e ->
                                    AlertEvent.fireError(this, e.getMessage(), this::refresh))
                            .owner(this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            });
        }
    }

    // An account's User Id link, which opens its user on the Users and Groups screen
    private CommandLink openUserLink(final Account account) {
        if (account == null) {
            return null;
        }
        final String userId = account.getUserId();
        return new CommandLink(
                userId,
                "Open account '" + userId + "' on the Users and Groups screen.",
                () -> restFactory
                        .create(USER_RESOURCE)
                        .method(userResource -> userResource.fetchBySubjectId(userId))
                        .onSuccess(user -> {
                            if (user != null) {
                                OpenUsersAndGroupsScreenEvent.fire(this, user.asRef());
                            }
                        })
                        .owner(this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec());
    }

    /// Finds the accounts again; the first time, starts the list.
    public void refresh() {
        if (dataProvider == null) {
            dataProvider = new RestDataProvider<Account, ResultPage<Account>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<Account>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    requestBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                    requestBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));
                    restFactory
                            .create(ACCOUNT_RESOURCE)
                            .method(res -> res.find(requestBuilder.build()))
                            .onSuccess(resultPage -> {
                                view.setFilterError(NullSafe.get(
                                        resultPage.getFilterError(), TokenError::getText));
                                dataConsumer.accept(resultPage);
                            })
                            .onFailure(throwable ->
                                    AlertEvent.fireError(
                                            this,
                                            "Error fetching accounts: " + throwable.getMessage(),
                                            null))
                            .owner(AccountsListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    private void onFilterChange(final String text) {
        final String trimmed = NullSafe.trim(text);
        // The filter text goes to the server verbatim - see FindAccountRequest.
        requestBuilder.quickFilter(trimmed.isEmpty()
                ? null
                : trimmed);
        refresh();
    }

    /// Sets the quick filter's text, finding the accounts it matches.
    ///
    /// @param filterInput The text.
    public void setQuickFilterText(final String filterInput) {
        view.setQuickFilterText(filterInput);
    }
}
