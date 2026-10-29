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

package stroom.security.client.component;

import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.data.client.component.RefCellsController;
import stroom.data.client.component.UserRefCell;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.UserAndGroupHelper;
import stroom.security.client.UserScreen;
import stroom.security.shared.AppPermissionResource;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.security.shared.PermissionShowLevel;
import stroom.security.shared.UserFields;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// Users and groups with their application permissions ([AppUserPermissionsList]), found by a quick
/// filter, a page at a time in the order chosen; the first selected when the filter changes.
public class AppUserPermissionsListController extends Controller {

    private static final AppPermissionResource APP_PERMISSION_RESOURCE = GWT.create(AppPermissionResource.class);

    private final RestFactory restFactory;
    private final FetchAppUserPermissionsRequest.Builder requestBuilder = new FetchAppUserPermissionsRequest.Builder();
    private final UiConfigCache uiConfigCache;
    private final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory;
    private final RefCellsController refCells;
    private AppUserPermissionsList view;
    private RestDataProvider<AppUserPermissions, ResultPage<AppUserPermissions>> dataProvider;
    private boolean isExternalIdp = false;
    private boolean resetSelection = false;
    private String lastQuickFilter;

    /// Made by GIN; [#attach(AppUserPermissionsList)] gives it its list.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param restFactory                      Fetches the permissions.
    /// @param uiConfigCache                    Gives the quick filter's help link and whether the
    ///                                         identity provider is external.
    /// @param quickFilterContextHandlerFactory Gives the quick filter its recent filters.
    /// @param refCells                         Lets the user cells open their users.
    @Inject
    public AppUserPermissionsListController(final EventBus eventBus,
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
    /// widget.
    ///
    /// @param view The list.
    /// @return This, for its owner to keep.
    public AppUserPermissionsListController attach(final AppUserPermissionsList view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "Quick Filter",
                        UserFields.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, view.getTaskMonitor());

        view.setOnFilterChange(this::onFilterChange);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                UserFields.QUICK_FILTER_CONTEXT, this, view.getTaskMonitor()));

        view.addColumns(refCells.configure(new UserRefCell.Builder<AppUserPermissions>()),
                (AppUserPermissions appUsrPerms) -> UserAndGroupHelper.buildUserActionMenu(
                        appUsrPerms.getUserRef(),
                        isExternalIdp,
                        UserScreen.allExcept(UserScreen.APP_PERMISSIONS),
                        this,
                        null));
        view.setOnSortChange(this::refresh);
        return this;
    }

    private void onFilterChange(String text) {
        if (text != null) {
            text = text.trim();
            if (text.isEmpty()) {
                text = null;
            }
        }

        // The filter text goes to the server verbatim - see FindUserCriteria.
        if (!Objects.equals(lastQuickFilter, text)) {
            lastQuickFilter = text;
            resetSelection = true;
            requestBuilder.quickFilter(text);
            refresh();
        }
    }

    /// Filters the users, as if the user typed it.
    ///
    /// @param quickFilterText The filter's text.
    public void setQuickFilter(final String quickFilterText) {
        view.setQuickFilterText(quickFilterText);
        onFilterChange(quickFilterText);
    }

    /// @param showLevel Which permissions to show, e.g. only the explicit ones.
    public void setShowLevel(final PermissionShowLevel showLevel) {
        requestBuilder.showLevel(showLevel);
    }

    /// Fetches the permissions again; the first time, starts the list.
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
                        requestBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));
                        restFactory
                                .create(APP_PERMISSION_RESOURCE)
                                .method(res -> res.fetchAppUserPermissions(requestBuilder.build()))
                                .onSuccess(resultPage -> {
                                    view.setFilterError(NullSafe.get(
                                            resultPage.getFilterError(), TokenError::getText));
                                    dataConsumer.accept(resultPage);
                                })
                                .onFailure(errorHandler)
                                .owner(AppUserPermissionsListController.this)
                                .taskMonitorFactory(view.getTaskMonitor())
                                .exec();
                    }

                    @Override
                    protected void changeData(final ResultPage<AppUserPermissions> data) {
                        super.changeData(data);
                        if (!data.isEmpty()) {
                            if (resetSelection) {
                                view.getSelectionModel().setSelected(data.getFirst());
                            }
                            resetSelection = false;
                        } else {
                            view.getSelectionModel().clear();
                        }
                    }
                };
        view.setDataProvider(dataProvider);
    }

    /// @return The selection, one row at most.
    public MultiSelectionModelImpl<AppUserPermissions> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// Filters to a user or group, e.g. when jumping here from another security screen.
    ///
    /// @param userRef The user or group.
    public void showUser(final UserRef userRef) {
        if (userRef != null) {
            setQuickFilter(UserAndGroupHelper.buildDisplayNameFilterInput(userRef));
        }
    }
}
