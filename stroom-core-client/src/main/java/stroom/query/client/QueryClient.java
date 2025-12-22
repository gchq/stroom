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

import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.shared.QueryDoc;
import stroom.query.shared.QueryResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;

import java.util.function.Consumer;

public class QueryClient {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;

    @Inject
    public QueryClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void loadQueryDoc(final DocRef queryRef,
                             final Consumer<QueryDoc> consumer,
                             final RestErrorHandler errorHandler,
                             final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetch(queryRef.getUuid()))
                .onSuccess(consumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
