/*
 * Copyright 2021 Crown Copyright
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

import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.component.UserRefCell;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.UserScreen;
import stroom.security.client.UsersAndGroupsHelper;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.ExtendedUiConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/// Users and groups ([UserList]) found by a quick filter (and terms its owner adds), a page at a
/// time, sorted as chosen; each with an action menu, and enabled or disabled with its tick box where
/// shown.
public class UserListController extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final RestFactory restFactory;
    private final FindUserCriteria.Builder criteriaBuilder = new FindUserCriteria.Builder();
    private final UiConfigCache uiConfigCache;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private UserList view;
    private RestDataProvider<User, ResultPage<User>> dataProvider;
    private ExpressionTerm additionalTerm;
    private ExpressionTerm isGroupTerm;
    private String filter;
    private ResultPage<User> currentData = null;
    private Consumer<ResultPage<User>> resultPageConsumer;

    private boolean showUniqueUserIdCol = false;
    private boolean showEnabledCol = false;
    private boolean isExternalIdp = false;
    private Mode mode = Mode.USERS_AND_GROUPS;
    private Set<UserScreen> validUserScreensForActionMenu = UserScreen.all();
    private Consumer<UserRef> onCopyPermissions;

    private final RefCellsController refCells;

    /// Made by GIN; [#attach(UserList)] gives it its list.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param restFactory                      Finds and updates the users.
    /// @param uiConfigCache                    Gives the quick filter's help link and whether the
    ///                                         identity provider is external.
    /// @param quickFilterContextHandlerFactory Gives the quick filter its recent filters.
    /// @param refCells                         Lets the user cells open their users.
    @Inject
    public UserListController(final EventBus eventBus,
                              final RestFactory restFactory,
                              final UiConfigCache uiConfigCache,
                              final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory,
                              final RefCellsController refCells) {
        super(eventBus);
        this.refCells = own(refCells);
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;
        this.quickFilterContextHandlerFactory = quickFilterContextHandlerFactory;
    }

    /// Attaches this to a list, e.g. one a template declares. The list is then this controller's
    /// widget, e.g. to show it as a screen of its own.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public UserListController attach(final UserList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(
                uiConfig -> {
                    if (uiConfig != null) {
                        view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                                "Quick Filter",
                                UserFields.FILTER_FIELD_DEFINITIONS,
                                uiConfig.getHelpUrlQuickFilter()));
                    }
                },
                view.getTaskMonitor());

        view.setOnFilterChange(this::onFilterChange);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                UserFields.QUICK_FILTER_CONTEXT, this, view.getTaskMonitor()));
        view.setOnSortChange(this::refresh);
        view.setOnEnabledChange(this::onEnabledChange);
        return this;
    }

    private void onEnabledChange(final User user, final boolean enabled) {
        restFactory.create(USER_RESOURCE)
                .method(userResource -> userResource.update(user
                        .copy()
                        .enabled(enabled)
                        .build()))
                .onSuccess(UsersAndGroupsHelper.createAfterChangeConsumer(this))
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    /// Whether to show each user's unique ID, set before the list is first shown.
    ///
    /// @param showUniqueUserIdCol `true` to show the column.
    /// @throws RuntimeException If the columns have already been added.
    public void setShowUniqueUserIdCol(final boolean showUniqueUserIdCol) {
        if (dataProvider == null) {
            this.showUniqueUserIdCol = showUniqueUserIdCol;
        } else {
            throw new RuntimeException("Columns have already been initialised");
        }
    }

    /// Whether to show each user's enabled tick box, set before the list is first shown.
    ///
    /// @param showEnabledCol `true` to show the column.
    /// @throws RuntimeException If the columns have already been added.
    public void setShowEnabledCol(final boolean showEnabledCol) {
        if (dataProvider == null) {
            this.showEnabledCol = showEnabledCol;
        } else {
            throw new RuntimeException("Columns have already been initialised");
        }
    }

    /// Sets whether the list holds users, groups or both.
    ///
    /// @param mode What it lists, not `null`.
    public void setMode(final Mode mode) {
        Objects.requireNonNull(mode);
        this.mode = mode;
        if (Mode.USERS_AND_GROUPS == mode) {
            isGroupTerm = null;
        } else {
            isGroupTerm = ExpressionTerm.builder()
                    .field(UserFields.FIELD_IS_GROUP)
                    .condition(Condition.EQUALS)
                    .value(String.valueOf(mode.includesGroups))
                    .build();
        }
    }

    /// Finds the users again with a new filter.
    ///
    /// @param text The filter's text.
    public void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }

    /// Clears the selection and finds the users again, the filter showing this text.
    ///
    /// @param quickFilterText The filter's text.
    public void setQuickFilterText(final String quickFilterText) {
        clear();
        view.setQuickFilterText(quickFilterText);
        onFilterChange(quickFilterText);
    }

    /// Filters the list to show one user, by their display name.
    ///
    /// @param userRef The user, or `null` to leave the list as it is.
    public void showUser(final UserRef userRef) {
        if (userRef != null) {
            setQuickFilterText(UsersAndGroupsHelper.buildDisplayNameFilterInput(userRef));
        }
    }

    /// Clears the selection and, once the list has been filled, its rows.
    public void clear() {
        view.getSelectionModel().clear();
        if (dataProvider != null) {
            view.clearRows();
        }
    }

    /// Finds the users again, first setting the list up once the UI config is known.
    public void refresh() {
        if (dataProvider == null) {
            uiConfigCache.get(extendedUiConfig -> {
                // Another refresh may have set up the data provider while the config was fetched
                if (dataProvider == null) {
                    // The config is null if it couldn't be fetched (an error will have been shown)
                    isExternalIdp = NullSafe.isTrue(extendedUiConfig,
                            ExtendedUiConfig::isExternalIdentityProvider);
                    initDataProvider();
                } else {
                    dataProvider.refresh();
                }
            });
        } else {
            dataProvider.refresh();
        }
    }

    private void initDataProvider() {
        view.addColumns(mode,
                showEnabledCol,
                showUniqueUserIdCol,
                () -> refCells.configure(new UserRefCell.Builder<User>()),
                (User user) -> UsersAndGroupsHelper.buildUserActionMenu(
                        NullSafe.get(user, User::asRef),
                        isExternalIdp,
                        Objects.requireNonNullElseGet(validUserScreensForActionMenu, UserScreen::all),
                        this,
                        onCopyPermissions));
        //noinspection Convert2Diamond // GWT
        this.dataProvider = new RestDataProvider<User, ResultPage<User>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<User>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                // The filter text goes to the server verbatim; only the structural terms this
                // screen adds on the user's behalf are composed here. See FindUserCriteria.
                ExpressionOperator expression = ExpressionOperator.builder().build();

                if (additionalTerm != null) {
                    expression = expression.copy()
                            .addTerm(additionalTerm)
                            .build();
                }
                if (isGroupTerm != null) {
                    expression = expression.copy()
                            .addTerm(isGroupTerm)
                            .build();
                }
                criteriaBuilder.expression(expression);
                criteriaBuilder.quickFilter(filter);
                criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                criteriaBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));
                restFactory
                        .create(USER_RESOURCE)
                        .method(res -> res.find(criteriaBuilder.build()))
                        .onSuccess(userResultPage -> {
                            // A rejected filter comes back as an empty page like any other, so
                            // surface the reason on the filter box. See ResultPage.filterError.
                            view.setFilterError(
                                    NullSafe.get(userResultPage.getFilterError(), TokenError::getText));
                            dataConsumer.accept(userResultPage);
                        })
                        .onFailure(errorHandler)
                        .owner(UserListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }

            @Override
            protected void changeData(final ResultPage<User> data) {
                currentData = data;
                super.changeData(data);
                if (resultPageConsumer != null) {
                    resultPageConsumer.accept(data);
                }
            }
        };
        view.setDataProvider(dataProvider);
    }

    /// @return The selection.
    public MultiSelectionModelImpl<User> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// @param label The filter's label.
    public void setLabel(final String label) {
        view.setLabel(label);
    }

    /// @param helpText The filter's help.
    public void setHelpText(final SafeHtml helpText) {
        view.setHelpText(helpText);
    }

    /// @param additionalTerm A term every user listed must also match, or `null` for none.
    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }

    /// @return The task monitor that spins the pager's refresh button, e.g. for a change made
    ///         to a user listed.
    public TaskMonitorFactory getTaskMonitor() {
        return view.getTaskMonitor();
    }

    /// @return The page of users last fetched, or `null` before the first.
    public ResultPage<User> getCurrentData() {
        return currentData;
    }

    /// @param resultPageConsumer Given each page of users as it is fetched, or `null` for none.
    public void setResultPageConsumer(final Consumer<ResultPage<User>> resultPageConsumer) {
        this.resultPageConsumer = resultPageConsumer;
    }

    /// @param onCopyPermissions Given a user to copy groups and permissions to, from the action
    ///                          menu, or null for no such item.
    public void setOnCopyPermissions(final Consumer<UserRef> onCopyPermissions) {
        this.onCopyPermissions = onCopyPermissions;
    }

    /// @param validUserScreensForActionMenu The screens a user's action menu may open.
    public void setValidUserScreensForActionMenu(final Set<UserScreen> validUserScreensForActionMenu) {
        this.validUserScreensForActionMenu = validUserScreensForActionMenu;
    }

    // --------------------------------------------------------------------------------


    /// What the list holds: users, groups or both.
    public enum Mode {
        /// Users and not groups.
        USERS_ONLY(true, false),
        /// Groups and not users.
        GROUPS_ONLY(false, true),
        /// Users and groups.
        USERS_AND_GROUPS(true, true);

        private final boolean includesUsers;
        private final boolean includesGroups;

        Mode(final boolean includesUsers, final boolean includesGroups) {
            this.includesUsers = includesUsers;
            this.includesGroups = includesGroups;
        }

        /// @return `true` if it lists users.
        public boolean includesUsers() {
            return includesUsers;
        }

        /// @return `true` if it lists groups.
        public boolean includesGroups() {
            return includesGroups;
        }
    }
}
