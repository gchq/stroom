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

package stroom.dashboard.client.component;

import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.shared.QueryResource;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

/// An Embedded Query pane's settings: Basic, Conditional Formatting, Selection Query and Selection
/// Filter. The selection tabs' fields are the data source of the query chosen (or embedded),
/// fetched once for each.
public class EmbeddedQuerySettingsController extends PaneSettingsController {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    // The query reference or embedded query string that currentDataSource was found for
    private Object currentDataSourceKey;
    private DocRef currentDataSource;

    /// Made by GIN, with its content and its tabs.
    ///
    /// @param eventBus        The application's event bus.
    /// @param view            Its component.
    /// @param basicSettings   The basic settings.
    /// @param rules           The rules.
    /// @param selectionQuery  The selection query.
    /// @param selectionFilter The selection filter.
    /// @param restFactory     Makes REST requests.
    @Inject
    public EmbeddedQuerySettingsController(final EventBus eventBus,
                                           final PaneSettings view,
                                           final BasicEmbeddedQuerySettingsController basicSettings,
                                           final ConditionalFormattingRulesController rules,
                                           final SelectionHandlersController selectionQuery,
                                           final SelectionHandlersController selectionFilter,
                                           final RestFactory restFactory) {
        super(eventBus, view);
        own(basicSettings);
        own(rules);
        own(selectionQuery);
        own(selectionFilter);

        final Consumer<Consumer<DocRef>> dataSourceRefConsumer = consumer -> {
            // An embedded (copied) query has no reference, so its data source is found from its
            // StroomQL
            final String embeddedQueryString = NullSafe
                    .get(basicSettings, BasicEmbeddedQuerySettingsController::getEmbeddedQueryString);
            final DocRef queryDocRef = NullSafe
                    .get(basicSettings, BasicEmbeddedQuerySettingsController::getQuery);
            final Object key = NullSafe.isNonBlankString(embeddedQueryString)
                    ? embeddedQueryString
                    : queryDocRef;
            if (key == null) {
                consumer.accept(null);
            } else if (Objects.equals(key, currentDataSourceKey)) {
                consumer.accept(currentDataSource);
            } else {
                restFactory
                        .create(QUERY_RESOURCE)
                        .method(res -> key instanceof final String queryString
                                ? res.fetchDataSourceFromQueryString(queryString)
                                : res.fetchQueryDataSource(queryDocRef))
                        .onSuccess(result -> {
                            currentDataSource = result;
                            currentDataSourceKey = key;
                            consumer.accept(result);
                        })
                        .onFailure(new DefaultErrorHandler(this, () -> {
                            currentDataSource = null;
                            currentDataSourceKey = key;
                            consumer.accept(null);
                        }))
                        .owner(this)
                        .taskMonitorFactory(taskMonitor())
                        .exec();
            }
        };

        selectionQuery.setDataSourceRefConsumer(dataSourceRefConsumer);

        selectionFilter.setUseForFilter(true);
        selectionFilter.setDataSourceRefConsumer(dataSourceRefConsumer);

        addTab("Basic", basicSettings);
        addTab("Conditional Formatting", rules);
        addTab("Selection Query", selectionQuery);
        addTab("Selection Filter", selectionFilter);
    }

}
