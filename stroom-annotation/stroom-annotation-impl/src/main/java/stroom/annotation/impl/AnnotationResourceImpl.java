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

import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.filter.FilterFieldMappers;
import stroom.util.filter.QuickFilterPredicateFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AnnotationResourceImpl implements AnnotationResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationResourceImpl.class);

    private static final FilterFieldMappers<String> FILTER_FIELD_MAPPERS = FilterFieldMappers.singleStringField();

    private final Provider<AnnotationService> annotationService;
    private final Provider<DocumentEventLog> documentEventLog;
    private final Provider<AnnotationConfig> annotationConfig;

    @Inject
    AnnotationResourceImpl(final Provider<AnnotationService> annotationService,
                           final Provider<DocumentEventLog> documentEventLog,
                           final Provider<AnnotationConfig> annotationConfig) {
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
            annotationDetail = annotationService.get().getDetail(annotationId);
            if (annotationDetail != null) {
                documentEventLog.get().view(annotationDetail, null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.get().view("Annotation " + annotationId, e);
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
            annotationDetail = annotationService.get().createEntry(request);
            documentEventLog.get().create(annotationDetail, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().create("Annotation entry " + request.getAnnotation(), e);
        }

        return annotationDetail;
    }

    @Override
    public List<String> getStatus(final String filter) {
        return filterValues(annotationConfig.get().getStatusValues(), filter);
    }

    @Override
    public List<String> getComment(final String filter) {
        return filterValues(annotationConfig.get().getStandardComments(), filter);
    }

    @Override
    public List<EventId> getLinkedEvents(final Long annotationId) {
        return annotationService.get().getLinkedEvents(annotationId);
    }

    @Override
    public List<EventId> link(final EventLink eventLink) {
        return annotationService.get().link(eventLink);
    }

    @Override
    public List<EventId> unlink(final EventLink eventLink) {
        return annotationService.get().unlink(eventLink);
    }

    @Override
    public Integer setStatus(final SetStatusRequest request) {
        return annotationService.get().setStatus(request);
    }

    @Override
    public Integer setAssignedTo(final SetAssignedToRequest request) {
        return annotationService.get().setAssignedTo(request);
    }

    private List<String> filterValues(final List<String> allValues, final String quickFilterInput) {
        if (allValues == null || allValues.isEmpty()) {
            return allValues;
        } else {
            final Predicate<String> quickFilterPredicate = QuickFilterPredicateFactory.createFuzzyMatchPredicate(
                    quickFilterInput, FILTER_FIELD_MAPPERS);

            return allValues.stream()
                    .filter(quickFilterPredicate)
                    .collect(Collectors.toList());
        }
    }
}
