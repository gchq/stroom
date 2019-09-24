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

package stroom.annotation.impl;

import com.codahale.metrics.health.HealthCheck.Result;
import org.springframework.stereotype.Component;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.security.SecurityContext;
import stroom.task.server.TaskContext;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import javax.inject.Inject;

@Component
public class AnnotationResourceImpl implements AnnotationResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationResourceImpl.class);

    private final AnnotationsService annotationsService;

    @Inject
    public AnnotationResourceImpl(final AnnotationsService annotationsService) {
        this.annotationsService = annotationsService;
    }

    @Override
    public AnnotationDetail get(final String id) {
        // TODO : Add logging.
        LOGGER.info(() -> "Getting annotation " + id);
        return annotationsService.getDetail(id);
    }

    @Override
    public AnnotationDetail createEntry(final CreateEntryRequest request) {
        // TODO : Add logging.

        return annotationsService.createEntry(request);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}