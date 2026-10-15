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

package stroom.dashboard.client.embeddedquery;

import stroom.dashboard.client.main.SettingsPresenter;
import stroom.dashboard.client.query.SelectionHandlersPresenter;
import stroom.dashboard.client.table.cf.RulesPresenter;
import stroom.dispatch.client.DefaultErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.shared.QueryResource;
import stroom.util.shared.NullSafe;
import stroom.widget.tab.client.presenter.LinkTabsLayoutView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Objects;
import java.util.function.Consumer;

public class EmbeddedQuerySettingsPresenter extends SettingsPresenter {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    // The query reference or embedded query string that currentDataSource was found for
    private Object currentDataSourceKey;
    private DocRef currentDataSource;

    @Inject
    public EmbeddedQuerySettingsPresenter(final EventBus eventBus,
                                          final LinkTabsLayoutView view,
                                          final BasicEmbeddedQuerySettingsPresenter basicSettingsPresenter,
                                          final RulesPresenter rulesPresenter,
                                          final SelectionHandlersPresenter selectionQueryPresenter,
                                          final SelectionHandlersPresenter selectionFilterPresenter,
                                          final RestFactory restFactory) {
        super(eventBus, view);
        getView().asWidget().addStyleName("settingsPresenter");

        final Consumer<Consumer<DocRef>> dataSourceRefConsumer = consumer -> {
            // An embedded (copied) query has no reference, so its data source is found from its
            // StroomQL
            final String embeddedQueryString = NullSafe
                    .get(basicSettingsPresenter, BasicEmbeddedQuerySettingsPresenter::getEmbeddedQueryString);
            final DocRef queryDocRef = NullSafe
                    .get(basicSettingsPresenter, BasicEmbeddedQuerySettingsPresenter::getQuery);
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
                        .taskMonitorFactory(this)
                        .exec();
            }
        };

        selectionQueryPresenter.setDataSourceRefConsumer(dataSourceRefConsumer);

        selectionFilterPresenter.setUseForFilter(true);
        selectionFilterPresenter.setDataSourceRefConsumer(dataSourceRefConsumer);

        addTab("Basic", basicSettingsPresenter);
        addTab("Conditional Formatting", rulesPresenter);
        addTab("Selection Query", selectionQueryPresenter);
        addTab("Selection Filter", selectionFilterPresenter);
    }
}
