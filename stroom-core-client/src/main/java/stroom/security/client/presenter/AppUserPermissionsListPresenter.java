/*
 * Copyright 2017 Crown Copyright
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
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.event.OpenUsersAndGroupsScreenEvent;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.util.shared.string.CaseType;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.MultiSelectionModelImpl;
import stroom.widget.util.client.SafeHtmlUtil;
import stroom.widget.util.client.SvgImageUtil;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

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
    private RestDataProvider<AppUserPermissions, ResultPage<AppUserPermissions>> dataProvider;
    private final MultiSelectionModelImpl<AppUserPermissions> selectionModel;
    private ResultPage<AppUserPermissions> currentData = null;
    private boolean isExternalIdp = false;

    @Inject
    public AppUserPermissionsListPresenter(final EventBus eventBus,
                                           final QuickFilterPageView view,
                                           final PagerView pagerView,
                                           final RestFactory restFactory,
                                           final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.pagerView = pagerView;
        this.uiConfigCache = uiConfigCache;

        dataGrid = new MyDataGrid<>();
        selectionModel = new MultiSelectionModelImpl<>(dataGrid);
        DataGridSelectionEventManager<AppUserPermissions> selectionEventManager = new DataGridSelectionEventManager<>(
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
    public void onFilterChange(String text) {
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                text = null;
            }
        }

        final ExpressionOperator expression = QuickFilterExpressionParser
                .parse(text, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELDS_MAP);
        requestBuilder.expression(expression);
        refresh();
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
            initDataProvider();
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
                        requestBuilder.pageRequest(PageRequestUtil.createPageRequest(range));
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
                        currentData = data;
                        if (!data.isEmpty()) {
                            selectionModel.setSelected(data.getFirst());
                        } else {
                            selectionModel.clear();
                        }
                    }
                };
        dataProvider.addDataDisplay(dataGrid);
    }

    private SafeHtml buildIconHeader() {
        // TODO this is duplicated in UserListPresenter
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

    private void setupColumns() {

        DataGridUtil.addColumnSortHandler(dataGrid, requestBuilder, this::refresh);

//        final DefaultHeaderOrFooterBuilder<AppUserPermissions> headerBuilder = new DefaultHeaderOrFooterBuilder<>(
//                dataGrid,
//                false);
//        headerBuilder.setSortIconStartOfLine(false);
//        dataGrid.setHeaderBuilder(headerBuilder);

        // Permissions col contains a lot of text, so we need multiline rows
        dataGrid.setMultiLine(true);

        // Icon
        dataGrid.addColumn(
                DataGridUtil.svgPresetColumnBuilder(false, (AppUserPermissions row) ->
                                row.getUserRef().isGroup()
                                        ? SvgPresets.USER_GROUP
                                        : SvgPresets.USER)
                        .withSorting(UserFields.FIELD_IS_GROUP)
                        .centerAligned()
                        .build(),
                DataGridUtil.headingBuilder("")
                        .headingText(buildIconHeader())
                        .centerAligned()
                        .withToolTip("Whether this row is a single user or a named user group.")
                        .build(),
                (ColumnSizeConstants.ICON_COL * 2) + 20);

        // Display name
//        final Column<AppUserPermissions, CommandLink> displayNameCol = DataGridUtil.commandLinkColumnBuilder(
//                        buildOpenAppPermissionsCommandLink())
//                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
//                .build();
//        dataGrid.addResizableColumn(
//                displayNameCol,
//                DataGridUtil.headingBuilder("Display Name")
//                        .withToolTip("The name of the user or group.")
//                        .build(),
//                400);

        final Column<AppUserPermissions, String> displayNameCol = DataGridUtil.copyTextColumnBuilder(
                        (AppUserPermissions appUsrPerms) ->
                                GwtNullSafe.get(appUsrPerms, AppUserPermissions::getUserRef, UserRef::getDisplayName))
                .enabledWhen(this::isUserEnabled)
                .withSorting(UserFields.FIELD_DISPLAY_NAME, true)
                .build();
        dataGrid.addResizableColumn(
                displayNameCol,
                DataGridUtil.headingBuilder(UserAndGroupHelper.COL_NAME_DISPLAY_NAME)
                        .withToolTip("The name of the user or group.")
                        .build(),
                350);

        // Show it as the default sort
        dataGrid.getColumnSortList().push(displayNameCol);

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
                                                this),
                                this))
//                .enabledWhen(User::isEnabled)
                .build();
        // x2 width so when it is hard right, it doesn't get in the way of the scroll bar
        dataGrid.addResizableColumn(
                actionMenuCol,
                "",
                ColumnSizeConstants.ICON_COL + 10);

        DataGridUtil.addEndColumn(dataGrid);
    }

    private static SafeHtml buildPermissionsCellValue(final AppUserPermissions appUserPermissions) {
        final DescriptionBuilder sb = new DescriptionBuilder();
        boolean notEmpty = false;
        boolean lastIsInherited = false;
        SafeHtml delimiter = new SafeHtmlBuilder()
                .append(SafeHtmlUtil.ENSP)
                .appendEscaped("|")
                .append(SafeHtmlUtil.ENSP)
                .toSafeHtml();
        for (final AppPermission permission : AppPermission.LIST) {
            if (GwtNullSafe.collectionContains(appUserPermissions.getPermissions(), permission)) {
                if (notEmpty) {
                    sb.addLine(false, lastIsInherited, true, delimiter);
                }
                sb.addLine(permission.getDisplayValue());
                notEmpty = true;
                lastIsInherited = false;
            } else if (GwtNullSafe.collectionContains(appUserPermissions.getInherited(), permission)) {
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
        return GwtNullSafe.get(appUserPermissions, AppUserPermissions::getUserRef, UserRef::isEnabled);
    }

    private Function<AppUserPermissions, CommandLink> buildOpenAppPermissionsCommandLink() {
        return (AppUserPermissions appUserPermissions) -> {
            final UserRef userRef = GwtNullSafe.get(appUserPermissions, AppUserPermissions::getUserRef);
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
