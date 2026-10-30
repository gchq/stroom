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

import stroom.data.client.component.RefCellsController;
import stroom.dispatch.client.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.security.shared.FindUserDependenciesCriteria;
import stroom.security.shared.UserResource;
import stroom.ui.config.client.UiConfigCache;
import stroom.util.client.cell.DocRefCell;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;
import stroom.widget.component.client.Controller;
import stroom.widget.datagrid.client.CriteriaUtil;
import stroom.widget.quickfilter.client.QuickFilterTooltipUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The documents that depend on a user ([UserDependenciesList]), found by a quick filter in the
/// order chosen; the first is selected on each fetch.
public class UserDependenciesListController extends Controller {

    private static final UserResource USER_RESOURCE = GWT.create(UserResource.class);

    private final UserDependenciesList view;
    private final RestFactory restFactory;
    private final RefCellsController refCells;
    private final FindUserDependenciesCriteria.Builder criteriaBuilder = FindUserDependenciesCriteria.builder();

    private UserRef userRef;
    private RestDataProvider<UserDependency, ResultPage<UserDependency>> dataProvider;
    private String filter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param restFactory                      Finds the dependencies.
    /// @param uiConfigCache                    Gives the quick filter's help link.
    /// @param quickFilterContextHandlerFactory Gives the quick filter its recent filters.
    /// @param refCells                         Lets the document cells open their documents.
    @Inject
    public UserDependenciesListController(final EventBus eventBus,
                                          final UserDependenciesList view,
                                          final RestFactory restFactory,
                                          final UiConfigCache uiConfigCache,
                                          final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory,
                                          final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.refCells = own(refCells);
        initWidget(view);

        // Not easy to determine if we are dealing in users or groups at this point so just
        // call it Quick Filter
        uiConfigCache.get(
                uiConfig -> {
                    if (uiConfig != null) {
                        view.registerPopupTextProvider(() -> QuickFilterTooltipUtil.createTooltip(
                                "Quick Filter",
                                FindUserDependenciesCriteria.FILTER_FIELD_DEFINITIONS,
                                uiConfig.getHelpUrlQuickFilter()));
                    }
                },
                view.getTaskMonitor());

        view.setOnFilterChange(this::onFilterChange);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                FindUserDependenciesCriteria.QUICK_FILTER_CONTEXT, this, view.getTaskMonitor()));
        view.setOnSortChange(this::refresh);
    }

    /// Shows a user's dependencies.
    ///
    /// @param userRef The user.
    public void setUserRef(final UserRef userRef) {
        this.userRef = Objects.requireNonNull(userRef);
        this.criteriaBuilder.userRef(userRef);
        refresh();
    }

    /// Fetches the dependencies again; the first time, adds the columns and starts the list.
    public void refresh() {
        if (dataProvider == null) {
            initDataProvider();
        } else {
            dataProvider.refresh();
        }
    }

    private void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }

    private void initDataProvider() {
        view.addColumns(refCells.configure(new DocRefCell.Builder<UserDependency>()));
        this.dataProvider = new RestDataProvider<UserDependency, ResultPage<UserDependency>>(getEventBus()) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<UserDependency>> dataConsumer,
                                final RestErrorHandler errorHandler) {

                if (userRef != null) {
                    // The filter text goes to the server verbatim - see FindUserCriteria.
                    criteriaBuilder.quickFilter(filter);
                    criteriaBuilder.sortList(CriteriaUtil.createSortList(view.getSortList()));

                    restFactory
                            .create(USER_RESOURCE)
                            .method(res -> res.findDependencies(criteriaBuilder.build()))
                            .onSuccess(userResultPage -> {
                                view.setFilterError(NullSafe.get(
                                        userResultPage.getFilterError(), TokenError::getText));
                                dataConsumer.accept(userResultPage);
                            })
                            .onFailure(errorHandler)
                            .owner(UserDependenciesListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            }

            @Override
            protected void changeData(final ResultPage<UserDependency> data) {
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
