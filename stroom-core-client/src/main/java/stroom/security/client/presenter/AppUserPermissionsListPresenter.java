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
import stroom.cell.info.client.CommandLink;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.ExpressionOperator;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.UserRef.DisplayType;
import stroom.util.shared.string.CaseType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class AppUserPermissionsListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final FetchAppUserPermissionsRequest.Builder requestBuilder = new FetchAppUserPermissionsRequest.Builder();
    private final MyDataGrid<AppUserPermissions> dataGrid;
    private final PagerView pagerView;
    private final UiConfigCache uiConfigCache;
    private final ClientSecurityContext securityContext;
    private RestDataProvider<AppUserPermissions, ResultPage<AppUserPermissions>> dataProvider;
    private final MultiSelectionModelImpl<AppUserPermissions> selectionModel;
    private boolean isExternalIdp = false;
    private boolean resetSelection = false;

    @Inject
    public AppUserPermissionsListPresenter(final EventBus eventBus,
                                           final QuickFilterPageView view,
                                           final PagerView pagerView,
                                           final RestFactory restFactory,
                                           final UiConfigCache uiConfigCache,
                                           final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.pagerView = pagerView;
        this.uiConfigCache = uiConfigCache;
        this.securityContext = securityContext;

        dataGrid = new MyDataGrid<>(this);
        selectionModel = new MultiSelectionModelImpl<>();
        final DataGridSelectionEventManager<AppUserPermissions> selectionEventManager =
                new DataGridSelectionEventManager<>(
                        dataGrid,
                        selectionModel,
                        false);
        dataGrid.setSelectionModel(selectionModel, selectionEventManager);
        pagerView.setDataWidget(dataGrid);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, this);

        view.setDataView(pagerView);
        view.setUiHandlers(this);

        setupColumns();
    }

    @Override
    protected void onBind() {
        super.onBind();
        registerHandler(dataGrid.addColumnSortHandler(event -> refresh()));
    }

    @Override
    public void onFilterChange(String text) {
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                text = null;
            }
        }

        final ExpressionOperator existingExpr = requestBuilder.getExpression();
        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(text, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);
        if (!Objects.equals(existingExpr, expression)) {
            resetSelection = true;
            requestBuilder.expression(expression);
            refresh();
        }
    }

    public void setQuickFilter(final String quickFilterText) {
        getView().setQuickFilterText(quickFilterText);
        onFilterChange(quickFilterText);
    }

    public void setShowLevel(final PermissionShowLevel showLevel) {
        requestBuilder.showLevel(showLevel);
    }

    public void refresh() {
        if (dataProvider == null) {
            uiConfigCache.get(extendedUiConfig -> {
                isExternalIdp = extendedUiConfig.isExternalIdentityProvider();
                initDataProvider();
            });
        } else {
            dataProvider.refresh();
        }
    }

    private boolean isExternalIdp() {
        return isExternalIdp;
    }

    private void initDataProvider() {
        //noinspection Convert2Diamond // Cos GWT
        dataProvider =
                new RestDataProvider<AppUserPermissions, ResultPage<AppUserPermissions>>(
                        getEventBus()) {
                    @Override
                    protected void exec(final Range range,
                                        final Consumer<ResultPage<AppUserPermissions>> dataConsumer,
                                        final RestErrorHandler errorHandler) {
                        requestBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                        requestBuilder.sortList(CriteriaUtil.createSortList(dataGrid.getColumnSortList()));
                        restFactory
                                .create(APP_PERMISSION_RESOURCE)
                                .method(res -> res.fetchAppUserPermissions(requestBuilder.build()))
                                .onSuccess(dataConsumer)
                                .onFailure(errorHandler)
                                .taskMonitorFactory(pagerView)
                                .exec();
                    }

                    @Override
                    protected void changeData(final ResultPage<AppUserPermissions> data) {
                        super.changeData(data);
                        if (!data.isEmpty()) {
                            if (resetSelection) {
                                selectionModel.setSelected(data.getFirst());
                            }
                            resetSelection = false;
                        } else {
                            selectionModel.clear();
                        }
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
    }

    private void setupColumns() {
        // Permissions col contains a lot of text, so we need multiline rows
        dataGrid.setMultiLine(true);

        // Icon
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (AppUserPermissions row) ->
                                UserAndGroupHelper.mapUserRefTypeToIcon(row.getUserRef()))
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .enabledWhen(this::isUserEnabled)
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(UserAndGroupHelper.buildUserAndGroupIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        final Column<AppUserPermissions, AppUserPermissions> displayNameCol =
                DataGridUtil.userRefColumnBuilder(
                                AppUserPermissions::getUserRef,
                                getEventBus(),
                                securityContext,
                                false,
                                DisplayType.DISPLAY_NAME)
                        .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                        .enabledWhen(this::isUserEnabled)
                        .build();

        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                ColumnSizeConstants.USER_DISPLAY_NAME_COL);

        // Show it as the default sort
        dataGrid.sort(displayNameCol);

        // Permissions
        dataGrid.addAutoResizableColumn(
                DataGridUtil.columnBuilder(
                                AppUserPermissionsListPresenter::buildPermissionsCellValue,
                                SafeHtmlCell::new)
                        .enabledWhen(this::isUserEnabled)
                        .build(),
                DataGridUtil.headingBuilder("Permissions")
                        .withToolTip("The permissions held by this user/group. Inherited permissions are in grey.")
                        .build(),
                400);

        // Actions Menu btn
        final Column<AppUserPermissions, AppUserPermissions> actionMenuCol = DataGridUtil.columnBuilder(
                        Function.identity(),
                        () -> new ActionMenuCell<>(
                                (AppUserPermissions appUsrPerms) ->
                                        UserAndGroupHelper.buildUserActionMenu(
                                                appUsrPerms.getUserRef(),
                                                isExternalIdp(),
                                                UserScreen.allExcept(UserScreen.APP_PERMISSIONS),
                                                this,
                                                null),
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

    private static SafeHtml buildPermissionsCellValue(final AppUserPermissions appUserPermissions) {
        final DescriptionBuilder sb = new DescriptionBuilder();
        boolean notEmpty = false;
        boolean lastIsInherited = false;
        final SafeHtml delimiter = new SafeHtmlBuilder()
                .append(SafeHtmlUtil.ENSP)
                .appendEscaped("|")
                .append(SafeHtmlUtil.ENSP)
                .toSafeHtml();
        for (final AppPermission permission : AppPermission.LIST) {
            if (NullSafe.collectionContains(appUserPermissions.getPermissions(), permission)) {
                if (notEmpty) {
                    sb.addLine(false, lastIsInherited, true, delimiter);
                }
                sb.addLine(permission.getDisplayValue());
                notEmpty = true;
                lastIsInherited = false;
            } else if (NullSafe.collectionContains(appUserPermissions.getInherited(), permission)) {
                if (notEmpty) {
                    sb.addLine(false, lastIsInherited, true, delimiter);
                }
                sb.addLine(false, true, permission.getDisplayValue());
                notEmpty = true;
                lastIsInherited = true;
            }
        }
        return sb.toSafeHtml();
    }

    private boolean isUserEnabled(final AppUserPermissions appUserPermissions) {
        return NullSafe.get(appUserPermissions, AppUserPermissions::getUserRef, UserRef::isEnabled);
    }

    private Function<AppUserPermissions, CommandLink> buildOpenAppPermissionsCommandLink() {
        return (final AppUserPermissions appUserPermissions) -> {
            final UserRef userRef = NullSafe.get(appUserPermissions, AppUserPermissions::getUserRef);
            if (userRef != null) {
                final String displayName = userRef.getDisplayName();
                return new CommandLink(
                        displayName,
                        "Open " + userRef.getType(CaseType.LOWER)
                        + " '" + displayName + "' on the "
                        + UsersAndGroupsPlugin.SCREEN_NAME + " screen.",
                        () -> OpenUsersAndGroupsScreenEvent.fire(AppUserPermissionsListPresenter.this, userRef));
            } else {
                return null;
            }
        };
    }

    public ButtonView addButton(final Preset preset) {
        return pagerView.addButton(preset);
    }

    public void addButton(final ButtonView buttonView) {
        pagerView.addButton(buttonView);
    }

    public PagerView getPagerView() {
        return pagerView;
    }

    public MultiSelectionModelImpl<AppUserPermissions> getSelectionModel() {
        return selectionModel;
    }

    public void showUser(final UserRef userRef) {
        if (userRef != null) {
            setQuickFilter(UserAndGroupHelper.buildDisplayNameFilterInput(userRef));
        }
    }
}
