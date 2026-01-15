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

package stroom.query.client;

import stroom.datasource.shared.DataSourceResource;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docstore.shared.Documentation;
import stroom.query.api.StringExpressionUtil;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;

import java.util.Optional;
import java.util.function.Consumer;
import javax.inject.Inject;

public class DataSourceClient {

    private static final DataSourceResource DATA_SOURCE_RESOURCE = GWT.create(DataSourceResource.class);

    private final RestFactory restFactory;

    @Inject
    public DataSourceClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void findFields(final FindFieldCriteria findFieldInfoCriteria,
                           final Consumer<ResultPage<QueryField>> consumer,
                           final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DATA_SOURCE_RESOURCE)
                .method(res -> res.findFields(findFieldInfoCriteria))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void findFieldByName(final DocRef dataSourceRef,
                                final String fieldName,
                                final Boolean queryable,
                                final Consumer<QueryField> consumer,
                                final TaskMonitorFactory taskMonitorFactory) {
        if (dataSourceRef != null) {
            final FindFieldCriteria findFieldInfoCriteria = new FindFieldCriteria(
                    PageRequest.oneRow(),
                    FindFieldCriteria.DEFAULT_SORT_LIST,
                    dataSourceRef,
                    StringExpressionUtil.equalsCaseSensitive(fieldName),
                    queryable);
            restFactory
                    .create(DATA_SOURCE_RESOURCE)
                    .method(res -> res.findFields(findFieldInfoCriteria))
                    .onSuccess(result -> {
                        if (result.getValues().size() > 0) {
                            consumer.accept(result.getFirst());
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    public void fetchDataSourceDescription(final DocRef dataSourceDocRef,
                                           final Consumer<Optional<String>> descriptionConsumer,
                                           final TaskMonitorFactory taskMonitorFactory) {

        if (dataSourceDocRef != null) {
            restFactory
                    .create(DATA_SOURCE_RESOURCE)
                    .method(res -> res.fetchDocumentation(dataSourceDocRef))
                    .onSuccess(documentation -> {
                        final Optional<String> optMarkDown = NullSafe.getAsOptional(documentation,
                                Documentation::getMarkdown);
                        if (descriptionConsumer != null) {
                            descriptionConsumer.accept(optMarkDown);
                        }
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    public void fetchDefaultExtractionPipeline(final DocRef dataSourceRef,
                                               final Consumer<DocRef> consumer,
                                               final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(DATA_SOURCE_RESOURCE)
                .method(res -> res.fetchDefaultExtractionPipeline(dataSourceRef))
                .onSuccess(consumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
