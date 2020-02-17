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
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.event.logging.api.DocumentEventLog;
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.RestResource;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;

class AnnotationResourceImpl implements AnnotationResource, RestResource, HasHealthCheck {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationResourceImpl.class);

    private final AnnotationService annotationService;
    private final DocumentEventLog documentEventLog;
    private final AnnotationConfig annotationConfig;

    @Inject
    AnnotationResourceImpl(final AnnotationService annotationService,
                           final DocumentEventLog documentEventLog,
                           final AnnotationConfig annotationConfig) {
        this.annotationService = annotationService;
        this.documentEventLog = documentEventLog;
        this.annotationConfig = annotationConfig;
    }

    @Override
    public AnnotationDetail get(final Long annotationId) {
        AnnotationDetail annotationDetail = null;

//        if (annotationId != null) {
        LOGGER.info(() -> "Getting annotation " + annotationId);
        try {
            annotationDetail = annotationService.getDetail(annotationId);
            if (annotationDetail != null) {
                documentEventLog.view(annotationDetail, null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.view("Annotation " + annotationId, e);
        }
//        } else {
//            LOGGER.info(() -> "Getting annotation " + streamId + ":" + eventId);
//            try {
//                annotationDetail = annotationService.getDetail(streamId, eventId);
//                if (annotationDetail != null) {
//                    documentEventLog.view(annotationDetail, null);
//                }
//            } catch (final RuntimeException e) {
//                documentEventLog.view("Annotation " + streamId + ":" + eventId, e);
//            }
//        }

        return annotationDetail;
    }

    @Override
    public AnnotationDetail createEntry(final CreateEntryRequest request) {
        AnnotationDetail annotationDetail = null;

        LOGGER.info(() -> "Creating annotation entry " + request.getAnnotation());
        try {
            annotationDetail = annotationService.createEntry(request);
            documentEventLog.create(annotationDetail, null);
        } catch (final RuntimeException e) {
            documentEventLog.create("Annotation entry " + request.getAnnotation(), e);
        }

        return annotationDetail;
    }

    @Override
    public List<String> getStatus(final String filter) {
        final List<String> values = annotationConfig.getStatusValues();
        if (filter == null || filter.isEmpty()) {
            return values;
        }

        return values
                .stream()
                .filter(value -> value.toLowerCase().contains(filter.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getComment(final String filter) {
        final List<String> values = annotationConfig.getStandardComments();
        if (filter == null || filter.isEmpty()) {
            return values;
        }

        return values
                .stream()
                .filter(value -> value.toLowerCase().contains(filter.toLowerCase()))
                .collect(Collectors.toList());
    }

    @Override
    public List<EventId> getLinkedEvents(final Long annotationId) {
        return annotationService.getLinkedEvents(annotationId);
    }

    @Override
    public List<EventId> link(final EventLink eventLink) {
        return annotationService.link(eventLink);
    }

    @Override
    public List<EventId> unlink(final EventLink eventLink) {
        return annotationService.unlink(eventLink);
    }

    @Override
    public Integer setStatus(final SetStatusRequest request) {
        return annotationService.setStatus(request);
    }

    @Override
    public Integer setAssignedTo(final SetAssignedToRequest request) {
        return annotationService.setAssignedTo(request);
    }

    @Override
    public Result getHealth() {
        return Result.healthy();
    }
}