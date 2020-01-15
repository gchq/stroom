/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.core.query;

import stroom.docref.DocRef;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaService;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
import stroom.query.shared.FetchSuggestionsAction;
import stroom.security.api.SecurityContext;
import stroom.task.api.AbstractTaskHandler;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Comparator;
import java.util.stream.Collectors;


class FetchSuggestionsHandler extends AbstractTaskHandler<FetchSuggestionsAction, SharedList<SharedString>> {
    private static final int LIMIT = 20;

    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;

    @Inject
    FetchSuggestionsHandler(final MetaService metaService,
                            final PipelineStore pipelineStore,
                            final SecurityContext securityContext) {
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
    }

    @Override
    public SharedList<SharedString> exec(final FetchSuggestionsAction task) {
        return securityContext.secureResult(() -> {
            if (task.getDataSource() != null) {
                if (MetaFields.STREAM_STORE_DOC_REF.equals(task.getDataSource())) {
                    if (task.getField().getName().equals(MetaFields.FEED_NAME)) {
                        return createFeedList(task.getText());
                    }

                    if (task.getField().getName().equals(MetaFields.PIPELINE)) {
                        return new SharedList<>(pipelineStore.list().stream()
                                .map(DocRef::getName)
                                .filter(name -> task.getText() == null || name.contains(task.getText()))
                                .map(SharedString::wrap)
                                .sorted()
                                .limit(LIMIT)
                                .collect(Collectors.toList()));
                    }

                    if (task.getField().getName().equals(MetaFields.TYPE_NAME)) {
                        return createStreamTypeList(task.getText());
                    }

                    if (task.getField().getName().equals(MetaFields.STATUS)) {
                        return new SharedList<>(Arrays.stream(Status.values())
                                .map(Status::getDisplayValue)
                                .filter(name -> task.getText() == null || name.contains(task.getText()))
                                .map(SharedString::wrap)
                                .sorted()
                                .limit(LIMIT)
                                .collect(Collectors.toList()));
                    }
                }
            }

            return new SharedList<>();
        });
    }

    private SharedList<SharedString> createFeedList(final String text) {
        return metaService.getFeeds()
                .parallelStream()
                .filter(name -> name == null || name.startsWith(text))
                .sorted(Comparator.naturalOrder())
                .limit(LIMIT)
                .map(SharedString::wrap)
                .collect(Collectors.toCollection(SharedList::new));
    }

    private SharedList<SharedString> createStreamTypeList(final String text) {
        return metaService.getTypes()
                .parallelStream()
                .filter(name -> name == null || name.startsWith(text))
                .sorted(Comparator.naturalOrder())
                .limit(LIMIT)
                .map(SharedString::wrap)
                .collect(Collectors.toCollection(SharedList::new));
    }
}
