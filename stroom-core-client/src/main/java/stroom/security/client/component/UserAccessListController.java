/*
 * Copyright 2026 Crown Copyright
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
import stroom.preferences.client.DateTimeFormatter;
import stroom.security.shared.FindUserAccessCriteria;
import stroom.security.shared.UserAccessResource;
import stroom.security.shared.UserAccessRow;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// Who currently holds access ([UserAccessList]), found by a quick filter, a page at a time; the
/// first is selected on each fetch.
public class UserAccessListController extends Controller {

    private static final UserAccessResource USER_ACCESS_RESOURCE = GWT.create(UserAccessResource.class);

    private final UserAccessList view;
    private final RestFactory restFactory;
    private final DateTimeFormatter dateTimeFormatter;

    private RestDataProvider<UserAccessRow, ResultPage<UserAccessRow>> dataProvider = null;
    private String filter;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param restFactory       Finds who holds access.
    /// @param dateTimeFormatter Formats times in the user's preferences.
    @Inject
    public UserAccessListController(final EventBus eventBus,
                                    final UserAccessList view,
                                    final RestFactory restFactory,
                                    final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
        view.setOnFilterChange(this::onFilterChange);
    }

    /// @return The selection.
    public MultiSelectionModelImpl<UserAccessRow> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// Empties the list and clears the selection.
    public void clear() {
        view.getSelectionModel().clear();
        if (dataProvider != null) {
            view.clearRows();
        }
    }

    /// Fetches who holds access again; the first time, adds the columns and starts the list.
    public void refresh() {
        if (dataProvider == null) {
            view.addColumns(dateTimeFormatter::formatWithDuration);
            //noinspection Convert2Diamond // Cos GWT
            dataProvider = new RestDataProvider<UserAccessRow, ResultPage<UserAccessRow>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<UserAccessRow>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    final FindUserAccessCriteria criteria = new FindUserAccessCriteria();
                    criteria.setFilter(filter);
                    criteria.setPageRequest(CriteriaUtil.createPageRequest(range));
                    criteria.setSortList(CriteriaUtil.createSortList(view.getSortList()));

                    restFactory
                            .create(USER_ACCESS_RESOURCE)
                            .method(res -> res.find(criteria))
                            .onSuccess(dataConsumer)
                            .onFailure(throwable -> AlertEvent.fireError(
                                    UserAccessListController.this,
                                    "Error fetching user access: " + throwable.getMessage(),
                                    null))
                            .owner(UserAccessListController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }

                @Override
                protected void changeData(final ResultPage<UserAccessRow> data) {
                    super.changeData(data);
                    if (!data.isEmpty()) {
                        view.getSelectionModel().setSelected(data.getFirst());
                    } else {
                        view.getSelectionModel().clear();
                    }
                }
            };
            view.setDataProvider(dataProvider);
        } else {
            dataProvider.refresh();
        }
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Filters the users, as if the user typed it.
    ///
    /// @param userInput The filter's text.
    public void setQuickFilter(final String userInput) {
        clear();
        view.setQuickFilterText(userInput);
        onFilterChange(userInput);
    }

    private void onFilterChange(final String text) {
        filter = NullSafe.trim(text);
        if (filter.isEmpty()) {
            filter = null;
        }
        refresh();
    }
}
