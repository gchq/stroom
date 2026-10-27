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

package stroom.activity.client.component;

import stroom.activity.shared.Activity;
import stroom.activity.shared.ActivityResource;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;
import stroom.util.shared.TokenError;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// The user's activities ([ActivityList]) that match the filter its owner gives, a page at a time.
public class ActivityListController extends Controller {

    private static final ActivityResource ACTIVITY_RESOURCE = GWT.create(ActivityResource.class);

    private final ActivityList view;
    private final RestDataProvider<Activity, ResultPage<Activity>> dataProvider;
    private Consumer<String> onFilterError = error -> {
    };
    private String name;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param restFactory Finds the activities.
    @Inject
    public ActivityListController(final EventBus eventBus,
                                  final ActivityList view,
                                  final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        initWidget(view);

        dataProvider = new RestDataProvider<Activity, ResultPage<Activity>>(eventBus) {
            @Override
            protected void exec(final Range range,
                                final Consumer<ResultPage<Activity>> dataConsumer,
                                final RestErrorHandler errorHandler) {
                restFactory
                        .create(ACTIVITY_RESOURCE)
                        .method(res -> res.list(name))
                        .onSuccess(resultPage -> {
                            // A rejected filter comes back as an empty page like any other, so
                            // surface the reason on the filter box. See ResultPage.filterError.
                            onFilterError.accept(NullSafe.get(resultPage.getFilterError(), TokenError::getText));
                            dataConsumer.accept(resultPage);
                        })
                        .onFailure(errorHandler)
                        .owner(ActivityListController.this)
                        .taskMonitorFactory(view.getTaskMonitor())
                        .exec();
            }
        };
        view.setDataProvider(dataProvider);
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Finds the activities again.
    public void refresh() {
        dataProvider.refresh();
    }

    /// @return The selection.
    public MultiSelectionModel<Activity> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// @param name The filter the activities are found by, at the next [#refresh()].
    public void setCriteria(final String name) {
        this.name = name;
    }

    /// @return The filter the activities are found by.
    public String getCriteria() {
        return name;
    }

    /// @param onFilterError Given why the filter was rejected after each find, or `null` if it
    ///                      wasn't.
    public void setFilterErrorConsumer(final Consumer<String> onFilterError) {
        this.onFilterError = Objects.requireNonNull(onFilterError, "onFilterError");
    }
}
