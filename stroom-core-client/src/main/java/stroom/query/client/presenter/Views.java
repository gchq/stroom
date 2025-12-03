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

package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.view.shared.ViewResource;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class Views implements HasHandlers {

    private static final ViewResource VIEW_RESOURCE = GWT.create(ViewResource.class);
    private static final long VIEWS_TIME_TO_LIVE_MS = 2_000;

    private final EventBus eventBus;
    private final RestFactory restFactory;

    private List<DocRef> views;
    private long nextUpdateTimeEpochMs = 0;

    @Inject
    Views(final EventBus eventBus,
          final RestFactory restFactory) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
    }

//    public void fetchViews(final Consumer<List<DocRef>> consumer) {
//        // Need to allow for available data sources changing, so use a TTL
//        if (views != null && System.currentTimeMillis() < nextUpdateTimeEpochMs) {
//            consumer.accept(views);
//        } else {
//            restFactory
//                    .create(VIEW_RESOURCE)
//                    .method(ViewResource::list)
//                    .onSuccess(result -> {
//                        views = result;
//                        consumer.accept(result);
//                        nextUpdateTimeEpochMs = System.currentTimeMillis() + VIEWS_TIME_TO_LIVE_MS;
//                    })
//                    .onFailure(throwable -> AlertEvent.fireError(
//                            this,
//                            throwable.getMessage(),
//                            null))
//                    .exec();
//        }
//    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
