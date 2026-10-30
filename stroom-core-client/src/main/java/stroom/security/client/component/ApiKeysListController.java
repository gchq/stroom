/*
 * Copyright 2023 Crown Copyright
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

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.CriteriaUtil;
import stroom.data.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.client.UserScreen;
import stroom.security.client.UsersAndGroupsHelper;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.ApiKeyResource;
import stroom.security.shared.AppPermission;
import stroom.security.shared.FindApiKeyCriteria;
import stroom.security.shared.HashedApiKey;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.ExpiryFormatter;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.UserRef;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Set;
import java.util.function.Consumer;

/// API keys ([ApiKeysList]), found by a quick filter, a page at a time in the order chosen: every
/// key for those who manage users, else the user's own, or one owner's when the list is in a user's
/// tab. The first is selected on each fetch.
public class ApiKeysListController extends Controller {

    private static final ApiKeyResource API_KEY_RESOURCE = GWT.create(ApiKeyResource.class);

    private final ApiKeysList view;
    private final FindApiKeyCriteria.Builder criteriaBuilder = new FindApiKeyCriteria.Builder();
    private final RestFactory restFactory;
    private final ExpiryFormatter expiryFormatter;
    private final ClientSecurityContext securityContext;
    private final UiConfigCache uiConfigCache;

    private RestDataProvider<HashedApiKey, ResultPage<HashedApiKey>> dataProvider = null;
    private boolean isExternalIdp = false;
    private String filter;
    private UserRef owner = null;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param restFactory                      Finds the keys.
    /// @param expiryFormatter                  Formats when a key expires.
    /// @param securityContext                  Says whether the user may manage users.
    /// @param uiConfigCache                    Gives the quick filter's help link and whether the
    ///                                         identity provider is external.
    /// @param quickFilterContextHandlerFactory Gives the quick filter its recent filters.
    @Inject
    public ApiKeysListController(final EventBus eventBus,
                                 final ApiKeysList view,
                                 final RestFactory restFactory,
                                 final ExpiryFormatter expiryFormatter,
                                 final ClientSecurityContext securityContext,
                                 final UiConfigCache uiConfigCache,
                                 final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.expiryFormatter = expiryFormatter;
        this.securityContext = securityContext;
        this.uiConfigCache = uiConfigCache;
        initWidget(view);

        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)) {
            // No Manage Users perms so can only see their own keys
            criteriaBuilder.owner(securityContext.getUserRef());
        }

        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                        "API Keys Quick Filter",
                        FindApiKeyCriteria.FILTER_FIELD_DEFINITIONS,
                        uiConfig.getHelpUrlQuickFilter()));
            }
        }, view.getTaskMonitor());

        view.setOnFilterChange(this::onFilterChange);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                FindApiKeyCriteria.QUICK_FILTER_CONTEXT, this, view.getTaskMonitor()));
        view.setOnSortChange(this::refresh);
    }

    /// @return The selection.
    public MultiSelectionModelImpl<HashedApiKey> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// Filters the keys, as if the user typed it.
    ///
    /// @param userInput The filter's text.
    public void setQuickFilter(final String userInput) {
        clear();
        view.setQuickFilterText(userInput);
        onFilterChange(userInput);
    }

    /// Sets the quick filter to filter on the passed user. Use this when jumping to the screen from
    /// one of the other security screens.
    ///
    /// @param owner The keys' owner.
    public void showUser(final UserRef owner) {
        if (owner != null) {
            setQuickFilter(FindApiKeyCriteria.FIELD_DEF_OWNER_DISPLAY_NAME.getFilterQualifier()
                           + ":" + owner.getDisplayName());
        }
    }

    /// Sets the user so the list only shows one user's keys. No quick filter is set. Only use on an
    /// embedded API keys list for ONE user.
    ///
    /// @param owner The keys' owner.
    public void setOwner(final UserRef owner) {
        clear();
        this.owner = owner;
        criteriaBuilder.owner(owner);
        refresh();
    }

    /// Empties the list and clears the selection.
    public void clear() {
        view.getSelectionModel().clear();
        if (dataProvider != null) {
            view.clearRows();
        }
    }

    /// Fetches the keys again; the first time, adds the columns and starts the list.
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

    /// @return The toolbar beside the pager, for the screen's own buttons.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    private void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }

    private Set<UserScreen> getActionScreensToInclude() {
        // If single owner has been set, it means this screen is embedded in a user-centric one
        // so allow jumping to the top level API keys screen
        return owner != null
                ? UserScreen.all()
                : UserScreen.allExcept(UserScreen.API_KEYS);
    }

    private void initDataProvider() {
        // Need manage users perm to CRUD keys for other users. If you don't have it no point in
        // showing owner col. Also don't show it if this screen has been set with a single owner
        view.addColumns(
                securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION) && owner == null,
                (HashedApiKey hashedApiKey) -> expiryFormatter.formatWithDuration(
                        NullSafe.get(hashedApiKey, HashedApiKey::getExpireTimeMs)),
                (HashedApiKey hashedApiKey) -> UsersAndGroupsHelper.buildUserActionMenu(
                        NullSafe.get(hashedApiKey, HashedApiKey::getOwner),
                        isExternalIdp,
                        getActionScreensToInclude(),
                        this,
                        null));
        //noinspection Convert2Diamond // Cos GWT
        dataProvider = new RestDataProvider<HashedApiKey, ResultPage<HashedApiKey>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<HashedApiKey>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                // The filter text goes to the server verbatim - see FindApiKeyCriteria.
                criteriaBuilder.quickFilter(filter);
                criteriaBuilder.pageRequest(CriteriaUtil.createPageRequest(range));
                criteriaBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));

                restFactory
                        .create(API_KEY_RESOURCE)
                        .method(res -> res.find(criteriaBuilder.build()))
                        .onSuccess(response -> {
                            // A rejected filter comes back as an empty page like any other, so surface
                            // the reason on the filter box. See ResultPage.filterError.
                            view.setFilterError(NullSafe.get(response.getFilterError(), TokenError::getText));
                            dataConsumer.accept(response);
                        })
                        .onFailure(throwable -> AlertEvent.fireError(
                                ApiKeysListController.this,
                                "Error fetching API Keys: " + throwable.getMessage(),
                                null))
                        .owner(ApiKeysListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }

            @Override
            protected void changeData(final ResultPage<HashedApiKey> data) {
                super.changeData(data);
                if (!data.isEmpty()) {
                    view.getSelectionModel().setSelected(data.getFirst());
                } else {
                    view.getSelectionModel().clear();
                }
            }
        };
        view.setDataProvider(dataProvider);
    }
}
