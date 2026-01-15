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

package stroom.security.client.presenter;

import stroom.cell.info.client.ActionMenuCell;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class UserListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final ClientSecurityContext securityContext;
    private final PagerView pagerView;
    private final MyDataGrid<User> dataGrid;
    private final MultiSelectionModelImpl<User> selectionModel;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder criteriaBuilder = new FindUserCriteria.Builder();
    private final UiConfigCache uiConfigCache;
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
    @SuppressWarnings({"unused", "FieldCanBeLocal"}) // Used in commented debug
    private String name = this.getClass().getSimpleName();
    private Function<UserRef, UserRefPopupPresenter> copyPermissionsPopupFunction;

    @Inject
    public UserListPresenter(final EventBus eventBus,
                             final QuickFilterPageView userListView,
                             final ClientSecurityContext securityContext,
                             final PagerView pagerView,
                             final RestFactory restFactory,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.securityContext = securityContext;
        this.pagerView = pagerView;
        this.restFactory = restFactory;
        this.uiConfigCache = uiConfigCache;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(
                uiConfig -> {
                    if (uiConfig != null) {
                        userListView.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                                "Quick Filter",
                                UserFields.FILTER_FIELD_DEFINITIONS,
                                uiConfig.getHelpUrlQuickFilter()));
                    }
                },
                this);

        userListView.setDataView(pagerView);
        userListView.setUiHandlers(this);
    }

    @Override
    protected void onBind() {
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    private void setupColumns() {
        if (showEnabledCol) {
            dataGrid.addColumn(
                    DataGridUtil.updatableTickBoxColumnBuilder(TickBoxState.createTickBoxFunc(User::isEnabled))
//                        .enabledWhen(this::isJobNodeEnabled)
                            .withFieldUpdater((final int index, final User user, final TickBoxState value) -> {
                                if (user != null) {
                                    user.setEnabled(value.toBoolean());
                                    restFactory.create(USER_RESOURCE)
                                            .method(userResource -> userResource.update(user))
                                            .onSuccess(UserAndGroupHelper.createAfterChangeConsumer(
                                                    this))
                                            .taskMonitorFactory(this)
                                            .exec();
                                }
                            })
                            .withSorting(UserFields.FIELD_ENABLED, true)
                            .build(),
                    DataGridUtil.headingBuilder("Enabled")
                            .withToolTip("The enabled state of the user. A disabled user effectively has no " +
                                         "permissions and cannot login.")
                            .build(),
                    ColumnSizeConstants.ENABLED_COL);

//            dataGrid.addResizableColumn(
//                    DataGridUtil.textColumnBuilder((User user) -> user.isEnabled()
//                                    ? "Yes"
//                                    : "No")
//                            .enabledWhen(User::isEnabled)
//                            .withSorting(UserFields.FIELD_ENABLED, true)
//                            .build(),
//                    DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_ENABLED)
//                            .withToolTip("The enabled state of the user. A disabled user effectively has no " +
//                                         "permissions and cannot login.")
//                            .build(),
//                    ColumnSizeConstants.ENABLED_COL);
        }

        // Icon
        if (mode == Mode.USERS_AND_GROUPS) {
            dataGrid.addColumn(
                    DataGridUtil.svgPresetColumnBuilder(false, (User user) ->
                                    UserAndGroupHelper.mapUserTypeToIcon(user))
                            .withSorting(UserFields.FIELD_IS_GROUP)
                            .enabledWhen(User::isEnabled)
                            .centerAligned()
                            .build(),
                    DataGridUtil.headingBuilder("")
                            .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                            .centerAligned()
                            .withToolTip("Whether this row is a single user or a named user group.")
                            .build(),
                    (ColumnSizeConstants.ICON_COL * 2) + 20);
        } else {
            dataGrid.addColumn(
                    DataGridUtil.svgPresetColumnBuilder(false, (User user) ->
                                    UserAndGroupHelper.mapUserTypeToIcon(user))
                            .enabledWhen(User::isEnabled)
                            .centerAligned()
                            .build(),
                    DataGridUtil.headingBuilder("")
                            .headingText(UserAndGroupHelper.buildSingleIconHeader(mode == Mode.GROUPS_ONLY))
                            .centerAligned()
                            .withToolTip(mode.includesUsers
                                    ? "Users"
                                    : "Groups")
                            .build(),
                    (ColumnSizeConstants.ICON_COL + 2));
        }

        // Display Name
        final String displayNameTooltip;
        if (mode == Mode.USERS_ONLY) {
            displayNameTooltip = "The name of the user.";
        } else if (mode == Mode.GROUPS_ONLY) {
            displayNameTooltip = "The name of the group.";
        } else {
            displayNameTooltip = "The name of the user or group.";
        }
//        final Column<User, String> displayNameCol = DataGridUtil.copyTextColumnBuilder(User::getDisplayName)
//                .enabledWhen(User::isEnabled)
//                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
//                .build();
        final Column<User, User> displayNameCol = DataGridUtil.userRefColumnBuilder(
                        User::getUserRef, getEventBus(), securityContext, DisplayType.DISPLAY_NAME)
                .enabledWhen(User::isEnabled)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip(displayNameTooltip)
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);
        dataGrid.sort(displayNameCol);

//        final Column<User, User> displayNameCol = DataGridUtil.columnBuilder(
//                        Function.identity(),
//                        () -> new HoverActionMenuCell<>(
//                                User::getDisplayName,
//                                this::buildActionMenu,
//                                this))
//                .enabledWhen(User::isEnabled)
//                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
//                .build();
//        dataGrid.addResizableColumn(
//                displayNameCol,
//                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
//                        .withToolTip(displayNameTooltip)
//                        .build(),
//                400);

        // Full name
        if (mode.includesUsers()) {
            dataGrid.addResizableColumn(
                    DataGridUtil.userRefColumnBuilder(
                                    User::getUserRef, getEventBus(), securityContext, DisplayType.FULL_NAME)
                            .enabledWhen(User::isEnabled)
                            .withSorting(UserFields.FIELD_FULL_NAME, true)
                            .build(),
//                    DataGridUtil.textColumnBuilder(User::getFullName)
//                            .enabledWhen(User::isEnabled)
//                            .withSorting(UserFields.FIELD_FULL_NAME, true)
//                            .build(),
                    DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_FULL_NAME)
                            .withToolTip(mode.includesGroups()
                                    ? "The full name of the user. Groups do not have a full name."
                                    : "The full name of the user.")
                            .build(),
                    ColumnSizeConstants.USER_FULL_NAME_COL);
        }

        // Unique User ID
        if (mode == Mode.USERS_ONLY && showUniqueUserIdCol) {
            dataGrid.addResizableColumn(
                    DataGridUtil.userRefColumnBuilder(
                                    User::getUserRef, getEventBus(), securityContext, DisplayType.SUBJECT_ID)
                            .enabledWhen(User::isEnabled)
                            .withSorting(UserFields.FIELD_UNIQUE_ID, true)
                            .build(),
//                    DataGridUtil.copyTextColumnBuilder(User::getSubjectId)
//                            .enabledWhen(User::isEnabled)
//                            .withSorting(UserFields.FIELD_UNIQUE_ID, true)
//                            .build(),
                    DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_UNIQUE_USER_ID)
                            .withToolTip("The unique user ID on the identity provider.")
                            .build(),
                    ColumnSizeConstants.UUID_COL);
        }

        // Actions Menu btn
        final Column<User, User> actionMenuCol = DataGridUtil.columnBuilder(
                        Function.identity(),
                        () -> new ActionMenuCell<>(
                                (User user) -> UserAndGroupHelper.buildUserActionMenu(
                                        NullSafe.get(user, User::asRef),
                                        isExternalIdp(),
                                        NullSafe.requireNonNullElseGet(
                                                validUserScreensForActionMenu, UserScreen::all),
                                        this,
                                        copyPermissionsPopupFunction),
                                this))
//                .enabledWhen(User::isEnabled)
                .build();

        // x2 width so when it is hard right, it doesn't get in the way of the scroll bar
        dataGrid.addColumn(
                actionMenuCol,
                "",
                ColumnSizeConstants.ICON_COL + 10);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private boolean isExternalIdp() {
        return isExternalIdp;
    }

    public void setShowUniqueUserIdCol(final boolean showUniqueUserIdCol) {
        if (dataProvider == null) {
            this.showUniqueUserIdCol = showUniqueUserIdCol;
        } else {
            throw new RuntimeException("Columns have already been initialised");
        }
    }

    public void setShowEnabledCol(final boolean showEnabledCol) {
        if (dataProvider == null) {
            this.showEnabledCol = showEnabledCol;
        } else {
            throw new RuntimeException("Columns have already been initialised");
        }
    }

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

    @Override
    public void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }

    public void setQuickFilterText(final String quickFilterText) {
//        GWT.log(name + " - setQuickFilterText: " + quickFilterText);
        clear();
        getView().setQuickFilterText(quickFilterText);
        onFilterChange(quickFilterText);
    }

    public void showUser(final UserRef userRef) {
        if (userRef != null) {
//            GWT.log(name + " - showUser: " + userRef);
            setQuickFilterText(UserAndGroupHelper.buildDisplayNameFilterInput(userRef));
//            final ResultPage<User> currentData = getCurrentData();
//            if (currentData == null) {
//                // First time the screen is opened so get the data in the hope our
//                // user is on the first page
//                userToShow = userRef;
//                refresh();
//            } else {
//                boolean found = UserAndGroupHelper.selectUserIfShown(
//                        userRef,
//                        getCurrentData(),
//                        selectionModel,
//                        dataGrid);
//                if (!found) {
//                    setQuickFilterText(UserAndGroupHelper.buildDisplayNameFilterInput(userRef));
//                }
//            }
        }
    }

    public void clear() {
//        GWT.log(name + " - clear");
        selectionModel.clear();
        if (dataProvider != null) {
            dataProvider.getDataDisplays().forEach(hasData -> {
                hasData.setRowData(0, Collections.emptyList());
                hasData.setRowCount(0, true);
            });
        }
    }

    public void refresh() {
        if (dataProvider == null) {
            uiConfigCache.get(extendedUiConfig -> {
                isExternalIdp = extendedUiConfig.isExternalIdentityProvider();
                initDataProvider();
            });
        } else {
//            GWT.log(name + " - refresh");
            dataProvider.refresh();
        }
    }

    private void initDataProvider() {
//        GWT.log(name + " - initDataProvider");
        setupColumns();
        //noinspection Convert2Diamond // GWT
        this.dataProvider = new RestDataProvider<User, ResultPage<User>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<User>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                ExpressionOperator expression = QuickFilterExpressionParser
                        .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);

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
                criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                criteriaBuilder.sortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));
                restFactory
                        .create(USER_RESOURCE)
                        .method(res -> res.find(criteriaBuilder.build()))
                        .onSuccess(userResultPage -> {
//                            GWT.log(name + " - onSuccess, size: " + userResultPage.size()
//                                    + ", expr: " + criteriaBuilder.getExpression());
                            dataConsumer.accept(userResultPage);
                        })
                        .onFailure(errorHandler)
                        .taskMonitorFactory(pagerView)
                        .exec();
            }

            @Override
            protected void changeData(final ResultPage<User> data) {
                currentData = data;
                super.changeData(data);
                if (resultPageConsumer != null) {
                    resultPageConsumer.accept(data);
                }
//                if (!data.isEmpty()) {
//                    selectionModel.setSelected(data.getFirst());
//                } else {
//                    selectionModel.clear();
//                }
            }
        };
        dataProvider.addDataDisplay(dataGrid);
    }

    public MultiSelectionModelImpl<User> getSelectionModel() {
        return selectionModel;
    }

    public DataGrid<User> getDataGrid() {
        return dataGrid;
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void setAdditionalTerm(final ExpressionTerm additionalTerm) {
        this.additionalTerm = additionalTerm;
    }

    public PagerView getPagerView() {
        return pagerView;
    }

    public ResultPage<User> getCurrentData() {
        return currentData;
    }

    public void setResultPageConsumer(final Consumer<ResultPage<User>> resultPageConsumer) {
        this.resultPageConsumer = resultPageConsumer;
    }

    public void setCopyPermissionsPopupFunction(
            final Function<UserRef, UserRefPopupPresenter> copyPermissionsPopupFunction) {
        this.copyPermissionsPopupFunction = copyPermissionsPopupFunction;
    }

    /**
     * To aid debugging when there are >1 {@link UserListPresenter} on a parent presenter
     */
    public void setName(final String name) {
        this.name = name;
    }

    public void setValidUserScreensForActionMenu(final Set<UserScreen> validUserScreensForActionMenu) {
        this.validUserScreensForActionMenu = validUserScreensForActionMenu;
    }

    // --------------------------------------------------------------------------------


    public enum Mode {
        USERS_ONLY(true, false),
        GROUPS_ONLY(false, true),
        USERS_AND_GROUPS(true, true);

        private final boolean includesUsers;
        private final boolean includesGroups;

        Mode(final boolean includesUsers, final boolean includesGroups) {
            this.includesUsers = includesUsers;
            this.includesGroups = includesGroups;
        }

        public boolean includesUsers() {
            return includesUsers;
        }

        public boolean includesGroups() {
            return includesGroups;
        }
    }
}
