/*
 * Copyright 2016 Crown Copyright
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

import stroom.cell.info.client.CommandLink;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.client.AppPermissionsPlugin;
import stroom.security.client.event.OpenAppPermissionsEvent;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserResource;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.DataGrid;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class UserListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final PagerView pagerView;
    private final MyDataGrid<User> dataGrid;
    private final MultiSelectionModelImpl<User> selectionModel;
    private final RestFactory restFactory;
    private final FindUserCriteria.Builder criteriaBuilder = new FindUserCriteria.Builder();
    private RestDataProvider<User, ResultPage<User>> dataProvider;
    private ExpressionTerm additionalTerm;
    private ExpressionTerm isGroupTerm;
    private String filter;
    private ResultPage<User> currentData;
    private Consumer<ResultPage<User>> resultPageConsumer;

    private boolean showUniqueUserIdCol = false;
    private boolean showEnabledCol = false;
    private Mode mode = Mode.USERS_AND_GROUPS;

    @Inject
    public UserListPresenter(final EventBus eventBus,
                             final QuickFilterPageView userListView,
                             final PagerView pagerView,
                             final RestFactory restFactory,
                             final UiConfigCache uiConfigCache) {
        super(eventBus, userListView);
        this.pagerView = pagerView;
        this.restFactory = restFactory;

        dataGrid = new MyDataGrid<>();
        selectionModel = dataGrid.addDefaultSelectionModel(false);
        pagerView.setDataWidget(dataGrid);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                userListView.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        userListView.setDataView(pagerView);
        userListView.setUiHandlers(this);
    }

    private void setupColumns() {
        DataGridUtil.addColumnSortHandler(dataGrid, criteriaBuilder, this::refresh);

        if (showEnabledCol) {
            dataGrid.addColumn(
                    DataGridUtil.updatableTickBoxColumnBuilder(
                                    User::isEnabled)
//                        .enabledWhen(this::isJobNodeEnabled)
                            .withFieldUpdater((int index, User user, TickBoxState value) -> {
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
                            .headingText(buildDualIconHeader())
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
        final Column<User, CommandLink> displayNameCol = DataGridUtil.commandLinkColumnBuilder(
                        buildOpenAppPermissionsCommandLink())
                .enabledWhen(User::isEnabled)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip(displayNameTooltip)
                        .build(),
                400);

        // Full name
        if (mode.includesUsers()) {
            dataGrid.addResizableColumn(
                    DataGridUtil.textColumnBuilder(User::getFullName)
                            .enabledWhen(User::isEnabled)
                            .withSorting(UserFields.FIELD_FULL_NAME, true)
                            .build(),
                    DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_FULL_NAME)
                            .withToolTip(mode.includesGroups()
                                    ? "The full name of the user. Groups do not have a full name."
                                    : "The full name of the user.")
                            .build(),
                    400);
        }

        // Unique User ID
        if (mode == Mode.USERS_ONLY && showUniqueUserIdCol) {
            dataGrid.addResizableColumn(
                    DataGridUtil.copyTextColumnBuilder(User::getSubjectId)
                            .enabledWhen(User::isEnabled)
                            .withSorting(UserFields.FIELD_NAME, true)
                            .build(),
                    DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_UNIQUE_USER_ID)
                            .withToolTip("The unique user ID on the identity provider.")
                            .build(),
                    400);
        }


        DataGridUtil.addEndColumn(dataGrid);
        dataGrid.getColumnSortList().push(displayNameCol);
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
        filter = text;
        if (filter != null) {
            filter = filter.trim();
            if (filter.isEmpty()) {
                filter = null;
            }
        }
        refresh();
    }

    private SafeHtml buildDualIconHeader() {
        // TODO this is duplicated in AppUserPermissionsListPresenter
        final String iconClassName = "svgCell-icon";
        final Preset userPreset = SvgPresets.USER.title("");
        final Preset groupPreset = SvgPresets.USER_GROUP.title("");
        return HtmlBuilder.builder()
                .div(
                        divBuilder -> {
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    userPreset.getTitle(),
                                    userPreset.getSvgImage(),
                                    iconClassName));
                            divBuilder.append("/");
                            divBuilder.append(SvgImageUtil.toSafeHtml(
                                    groupPreset.getTitle(),
                                    groupPreset.getSvgImage(),
                                    iconClassName));
                        },
                        Attribute.className("two-icon-column-header"))
                .toSafeHtml();
    }

    private Function<User, CommandLink> buildOpenAppPermissionsCommandLink() {
        return (User user) -> {
            if (user != null) {
                final String displayName = user.getDisplayName();
                return new CommandLink(
                        displayName,
                        "Open " + user.getType() + " '" + user.getDisplayName() + "' on the "
                        + AppPermissionsPlugin.SCREEN_NAME + " screen.",
                        () -> OpenAppPermissionsEvent.fire(
                                UserListPresenter.this, user.getSubjectId()));
            } else {
                return null;
            }
        };
    }

    public void setQuickFilterText(final String quickFilterText) {
        getView().setQuickFilterText(quickFilterText);
    }

    public void refresh() {
        if (dataProvider == null) {

            setupColumns();
            //noinspection Convert2Diamond // GWT
            this.dataProvider = new RestDataProvider<User, ResultPage<User>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<User>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    ExpressionOperator expression = QuickFilterExpressionParser
                            .parse(filter, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);

                    if (additionalTerm != null) {
                        expression = expression.copy()
                                .addTerm(additionalTerm).build();
                    }
                    if (isGroupTerm != null) {
                        expression = expression.copy()
                                .addTerm(isGroupTerm).build();
                    }
                    criteriaBuilder.expression(expression);
                    criteriaBuilder.pageRequest(PageRequestUtil.createPageRequest(range));
                    restFactory
                            .create(USER_RESOURCE)
                            .method(res -> res.find(criteriaBuilder.build()))
                            .onSuccess(dataConsumer)
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
                }
            };
            dataProvider.addDataDisplay(dataGrid);

        } else {
            dataProvider.refresh();
        }
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


    // --------------------------------------------------------------------------------


    public enum Mode {
        USERS_ONLY(true, false),
        GROUPS_ONLY(false, true),
        USERS_AND_GROUPS(true, true),
        ;

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
