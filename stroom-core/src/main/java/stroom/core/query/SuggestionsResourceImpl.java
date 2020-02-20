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
 */

package stroom.core.query;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.docref.DocRef;
import stroom.meta.api.MetaService;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.pipeline.PipelineStore;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.SuggestionsResource;
import stroom.security.api.SecurityContext;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class SuggestionsResourceImpl implements SuggestionsResource, HasHealthCheck {
    private static final int LIMIT = 20;

    private final MetaService metaService;
    private final PipelineStore pipelineStore;
    private final SecurityContext securityContext;

    @Inject
    SuggestionsResourceImpl(final MetaService metaService,
                            final PipelineStore pipelineStore,
                            final SecurityContext securityContext) {
        this.metaService = metaService;
        this.pipelineStore = pipelineStore;
        this.securityContext = securityContext;
    }

    @Override
    public List<String> fetch(final FetchSuggestionsRequest request) {
        return securityContext.secureResult(() -> {
            List<String> result = Collections.emptyList();

            if (request.getDataSource() != null) {
                if (MetaFields.STREAM_STORE_DOC_REF.equals(request.getDataSource())) {
                    if (request.getField().getName().equals(MetaFields.FEED_NAME.getName())) {
                        result = createFeedList(request.getText());

                    } else if (request.getField().getName().equals(MetaFields.PIPELINE.getName())) {
                        result = pipelineStore.list().stream()
                                .map(DocRef::getName)
                                .filter(name -> request.getText() == null || name.contains(request.getText()))
                                .sorted()
                                .limit(LIMIT)
                                .collect(Collectors.toList());

                    } else if (request.getField().getName().equals(MetaFields.TYPE_NAME.getName())) {
                        result = createStreamTypeList(request.getText());

                    } else if (request.getField().getName().equals(MetaFields.STATUS.getName())) {
                        result = Arrays.stream(Status.values())
                                .map(Status::getDisplayValue)
                                .filter(name -> request.getText() == null || name.contains(request.getText()))
                                .sorted()
                                .limit(LIMIT)
                                .collect(Collectors.toList());
                    }
                }
            }

            return result;
        });
    }

    private List<String> createFeedList(final String text) {
        return metaService.getFeeds()
                .parallelStream()
                .filter(name -> name == null || name.startsWith(text))
                .sorted(Comparator.naturalOrder())
                .limit(LIMIT)
                .collect(Collectors.toList());
    }

    private List<String> createStreamTypeList(final String text) {
        return metaService.getTypes()
                .parallelStream()
                .filter(name -> name == null || name.startsWith(text))
                .sorted(Comparator.naturalOrder())
                .limit(LIMIT)
                .collect(Collectors.toList());
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}