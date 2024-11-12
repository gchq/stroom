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

import stroom.cell.info.client.SvgCell;
import stroom.data.client.presenter.ColumnSizeConstants;
import stroom.data.client.presenter.PageRequestUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.data.grid.client.DataGridSelectionEventManager;
import stroom.data.grid.client.EndColumn;
import stroom.data.grid.client.MyDataGrid;
import stroom.data.grid.client.PagerView;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.query.api.v2.ExpressionOperator;
import stroom.security.shared.AppPermission;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.QuickFilterExpressionParser;
import stroom.security.shared.UserFields;
import stroom.svg.client.Preset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.ButtonView;
import stroom.widget.dropdowntree.client.view.QuickFilterPageView;
import stroom.widget.dropdowntree.client.view.QuickFilterTooltipUtil;
import stroom.widget.dropdowntree.client.view.QuickFilterUiHandlers;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.cellview.client.ColumnSortEvent;
import com.google.gwt.user.cellview.client.ColumnSortList;
import com.google.gwt.user.cellview.client.ColumnSortList.ColumnSortInfo;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AppUserPermissionsListPresenter
        extends MyPresenterWidget<QuickFilterPageView>
        implements QuickFilterUiHandlers {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final FetchAppUserPermissionsRequest.Builder builder = new FetchAppUserPermissionsRequest.Builder();
    private final MyDataGrid<AppUserPermissions> dataGrid;
    private final PagerView pagerView;
    private RestDataProvider<AppUserPermissions, ResultPage<AppUserPermissions>> dataProvider;
    private final MultiSelectionModelImpl<AppUserPermissions> selectionModel;


    @Inject
    public AppUserPermissionsListPresenter(final EventBus eventBus,
                                           final QuickFilterPageView view,
                                           final PagerView pagerView,
                                           final RestFactory restFactory,
                                           final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.restFactory = restFactory;
        this.pagerView = pagerView;

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

        builder.sortList(List.of(
                new CriteriaFieldSort(
                        UserFields.DISPLAY_NAME.getFldName(),
                        false,
                        true),
                new CriteriaFieldSort(
                        UserFields.NAME.getFldName(),
                        false,
                        true)));

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
                .parse(text, UserFields.DEFAULT_FIELDS, UserFields.ALL_FIELD_MAP);
        builder.expression(expression);
        refresh();
    }

    public void setAllUsers(final boolean allUsers) {
        builder.allUsers(allUsers);
    }

    public void refresh() {
        if (dataProvider == null) {
            dataProvider =
                    new RestDataProvider<AppUserPermissions, ResultPage<AppUserPermissions>>(
                            getEventBus()) {
                        @Override
                        protected void exec(final Range range,
                                            final Consumer<ResultPage<AppUserPermissions>> dataConsumer,
                                            final RestErrorHandler errorHandler) {
                            builder.pageRequest(PageRequestUtil.createPageRequest(range));
                            restFactory
                                    .create(APP_PERMISSION_RESOURCE)
                                    .method(res -> res.fetchAppUserPermissions(builder.build()))
                                    .onSuccess(dataConsumer)
                                    .onFailure(errorHandler)
                                    .taskMonitorFactory(pagerView)
                                    .exec();
                        }
                    };
            dataProvider.addDataDisplay(dataGrid);
        } else {
            dataProvider.refresh();
        }
    }

    private void setupColumns() {
        // Icon
        final Column<AppUserPermissions, Preset> iconCol =
                new Column<AppUserPermissions, Preset>(new SvgCell()) {
                    @Override
                    public Preset getValue(final AppUserPermissions documentUserPermissions) {
                        final UserRef userRef = documentUserPermissions.getUserRef();
                        if (!userRef.isGroup()) {
                            return SvgPresets.USER;
                        }

                        return SvgPresets.USER_GROUP;
                    }
                };
        iconCol.setSortable(true);
        dataGrid.addColumn(iconCol, "</br>", ColumnSizeConstants.ICON_COL);

        // User Or Group Name
        final Column<AppUserPermissions, String> nameCol =
                new Column<AppUserPermissions, String>(new TextCell()) {
                    @Override
                    public String getValue(final AppUserPermissions appUserPermissions) {
                        return appUserPermissions.getUserRef().getDisplayName();
                    }
                };
        nameCol.setSortable(true);
        dataGrid.addResizableColumn(nameCol, "Display Name", 400);

        // Permissions
        final Column<AppUserPermissions, SafeHtml> permissionCol =
                new Column<AppUserPermissions, SafeHtml>(new SafeHtmlCell()) {
                    @Override
                    public SafeHtml getValue(final AppUserPermissions appUserPermissions) {
                        final DescriptionBuilder sb = new DescriptionBuilder();
//                        if (appUserPermissions.getPermissions() != null &&
//                            appUserPermissions.getPermissions().contains(AppPermission.ADMINISTRATOR)) {
//                            sb.addLine(true, false, AppPermission.ADMINISTRATOR.getDisplayValue());
//                        } else if (appUserPermissions.getInherited() != null &&
//                                   appUserPermissions.getInherited().contains(AppPermission.ADMINISTRATOR)) {
//                            sb.addLine(true, true, AppPermission.ADMINISTRATOR.getDisplayValue());
//                        } else {
                            boolean notEmpty = false;
                            boolean lastInherited = false;
                            for (final AppPermission permission : AppPermission.LIST) {
                                if (appUserPermissions.getPermissions() != null &&
                                    appUserPermissions.getPermissions().contains(permission)) {
                                    if (notEmpty) {
                                        sb.addLine(false, lastInherited, ", ");
                                    }
                                    sb.addLine(permission.getDisplayValue());
                                    notEmpty = true;
                                    lastInherited = false;
                                } else if (appUserPermissions.getInherited() != null &&
                                           appUserPermissions.getInherited().contains(permission)) {
                                    if (notEmpty) {
                                        sb.addLine(false, lastInherited, ", ");
                                    }
                                    sb.addLine(false, true, permission.getDisplayValue());
                                    notEmpty = true;
                                    lastInherited = true;
                                }
//                            }
                        }
                        return sb.toSafeHtml();
                    }
                };
        dataGrid.addResizableColumn(permissionCol, "Permissions", 2000);

        dataGrid.addEndColumn(new EndColumn<AppUserPermissions>());


        final ColumnSortEvent.Handler columnSortHandler = event -> {
            final List<CriteriaFieldSort> sortList = new ArrayList<>();
            if (event != null) {
                final ColumnSortList columnSortList = event.getColumnSortList();
                if (columnSortList != null) {
                    for (int i = 0; i < columnSortList.size(); i++) {
                        final ColumnSortInfo columnSortInfo = columnSortList.get(i);
                        final Column<?, ?> column = columnSortInfo.getColumn();
                        final boolean isAscending = columnSortInfo.isAscending();

                        if (column.equals(iconCol)) {
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.IS_GROUP.getFldName(),
                                    !isAscending,
                                    true));
                        } else if (column.equals(nameCol)) {
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.DISPLAY_NAME.getFldName(),
                                    !isAscending,
                                    true));
                            sortList.add(new CriteriaFieldSort(
                                    UserFields.NAME.getFldName(),
                                    !isAscending,
                                    true));
                        }
                    }
                }
            }
            builder.sortList(sortList);
            refresh();
        };
        dataGrid.addColumnSortHandler(columnSortHandler);
        dataGrid.getColumnSortList().push(nameCol);
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
}
