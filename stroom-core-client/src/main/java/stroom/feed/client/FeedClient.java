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

package stroom.feed.client;

import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.feed.shared.FeedResource;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;

import java.util.List;
import java.util.function.Consumer;

public class FeedClient {

    private static final FeedResource FEED_RESOURCE = GWT.create(FeedResource.class);

    private final RestFactory restFactory;

    @Inject
    FeedClient(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void getDocRefForName(final String name,
                                 final Consumer<DocRef> consumer,
                                 final TaskMonitorFactory taskMonitorFactory) {
        if (NullSafe.isBlankString(name)) {
            consumer.accept(null);
        } else {
            restFactory
                    .create(FEED_RESOURCE)
                    .method(res -> res.getDocRefForName(name))
                    .onSuccess(consumer)
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        }
    }

    public void load(final DocRef docRef,
                     final Consumer<FeedDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(FEED_RESOURCE)
                .method(res -> res.fetch(docRef.getUuid()))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void save(final FeedDoc document,
                     final Consumer<FeedDoc> resultConsumer,
                     final RestErrorHandler errorHandler,
                     final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(FEED_RESOURCE)
                .method(res -> res.update(document.getUuid(), document))
                .onSuccess(resultConsumer)
                .onFailure(errorHandler)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }

    public void fetchSupportedEncodings(final Consumer<List<String>> resultConsumer,
                                        final TaskMonitorFactory taskMonitorFactory) {
        restFactory
                .create(FEED_RESOURCE)
                .method(FeedResource::fetchSupportedEncodings)
                .onSuccess(resultConsumer)
                .taskMonitorFactory(taskMonitorFactory)
                .exec();
    }
}
