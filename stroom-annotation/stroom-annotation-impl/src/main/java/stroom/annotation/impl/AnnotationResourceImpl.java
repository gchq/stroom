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

import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.ChangeAnnotationEntryRequest;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.annotation.shared.DeleteAnnotationEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.FetchAnnotationEntryRequest;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import event.logging.Query;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AnnotationResourceImpl implements AnnotationResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationResourceImpl.class);

    private final Provider<AnnotationService> annotationServiceProvider;
    private final Provider<DocumentEventLog> documentEventLog;

    @Inject
    AnnotationResourceImpl(final Provider<AnnotationService> annotationServiceProvider,
                           final Provider<DocumentEventLog> documentEventLog) {
        this.annotationServiceProvider = annotationServiceProvider;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public ResultPage<Annotation> findAnnotations(final FindAnnotationRequest request) {
        LOGGER.info(() -> "Finding annotations " + request);
        final ResultPage<Annotation> result;
        try {
            result = annotationServiceProvider.get().findAnnotations(request);
            if (result != null) {
                documentEventLog.get().search(
                        "Find Annotations",
                        Query.builder().withRaw(request.getFilter()).build(),
                        "Annotation",
                        result.getPageResponse(),
                        null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.get().search(
                    "Find Annotations",
                    Query.builder().withRaw(request.getFilter()).build(),
                    "Annotation",
                    null,
                    e);
            throw e;
        }
        return result;
    }

    @Override
    public Annotation getAnnotationById(final Long annotationId) {
        LOGGER.info(() -> "Getting annotation " + annotationId);
        final Annotation annotation;
        try {
            annotation = annotationServiceProvider.get().getAnnotationById(annotationId).orElse(null);
            if (annotation != null) {
                documentEventLog.get().view(annotation, null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.get().view("Annotation " + annotationId, e);
            throw e;
        }
        return annotation;
    }

//    @Override
//    public Annotation getAnnotationByRef(final DocRef annotationRef) {
//        LOGGER.info(() -> "Getting annotation " + annotationRef);
//        final Annotation annotation;
//        try {
//            annotation = annotationServiceProvider.get().getAnnotationByRef(annotationRef).orElse(null);
//            if (annotation != null) {
//                documentEventLog.get().view(annotation, null);
//            }
//        } catch (final RuntimeException e) {
//            documentEventLog.get().view("Annotation " + annotationRef, e);
//            throw e;
//        }
//        return annotation;
//    }

    @Override
    public List<AnnotationEntry> getAnnotationEntries(final DocRef annotationRef) {
        return annotationServiceProvider.get().getAnnotationEntries(annotationRef);
    }

    @Override
    public Annotation createAnnotation(final CreateAnnotationRequest request) {
        final Annotation annotation;
        LOGGER.info(() -> "Creating annotation " + request);
        try {
            annotation = annotationServiceProvider.get().createAnnotation(request);
            documentEventLog.get().create(annotation, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().create("Annotation", e);
            throw e;
        }
        return annotation;
    }

    @Override
    public Boolean change(final SingleAnnotationChangeRequest request) {
        Annotation before = null;
        Annotation after = null;
        final boolean success;

        final AnnotationService annotationService = annotationServiceProvider.get();
        LOGGER.info(() -> "Changing annotation " + request.getAnnotationRef());
        try {
            before = annotationService.getAnnotationByRef(request.getAnnotationRef()).orElse(null);
            if (before == null) {
                throw new RuntimeException("Unable to find annotation");
            }

            success = annotationService.change(request);
            if (success) {
                after = annotationService.getAnnotationByRef(request.getAnnotationRef()).orElse(null);
            }
            documentEventLog.get().update(before, after, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().update(before == null
                    ? request.getAnnotationRef()
                    : before, after, e);
            throw e;
        }

        return success;
    }

    @Override
    public Integer batchChange(final MultiAnnotationChangeRequest request) {
        int count = 0;
        final AnnotationService annotationService = annotationServiceProvider.get();
        for (final long id : request.getAnnotationIdList()) {
            Annotation before = null;
            Annotation after = null;
            LOGGER.info(() -> "Changing annotation " + id);
            try {
                before = annotationService.getAnnotationById(id).orElse(null);
                if (before == null) {
                    throw new RuntimeException("Unable to find annotation");
                }

                final DocRef docRef = before.asDocRef();
                final boolean success = annotationService.change(new SingleAnnotationChangeRequest(docRef,
                        request.getChange()));
                if (success) {
                    after = annotationService.getAnnotationByRef(docRef).orElse(null);
                    count++;
                }
                documentEventLog.get().update(before, after, null);
            } catch (final RuntimeException e) {
                documentEventLog.get().update(before == null
                        ? id
                        : before, after, e);
                throw e;
            }
        }
        return count;
    }

//    @AutoLogged(OperationType.UNLOGGED)
//    @Override
//    public List<AnnotationTag> getStatusValues(final String filter) {
//        return annotationService.get().getStatus(filter);
//    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public List<String> getStandardComments(final String filter) {
        return annotationServiceProvider.get().getStandardComments(filter);
    }

//    @AutoLogged(OperationType.UNLOGGED)
//    @Override
//    public SimpleDuration getDefaultRetentionPeriod() {
//        return annotationService.get().getDefaultRetentionPeriod();
//    }

    @Override
    public List<EventId> getLinkedEvents(final DocRef annotationRef) {
        return annotationServiceProvider.get().getLinkedEvents(annotationRef);
    }

    @Override
    public Boolean deleteAnnotation(final DocRef annotationRef) {
        final AnnotationService annotationService = annotationServiceProvider.get();
        final Boolean success;
        LOGGER.info(() -> "Deleting annotation " + annotationRef);
        try {
            success = annotationService.deleteAnnotation(annotationRef);
            documentEventLog.get().delete(annotationRef, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().delete(annotationRef, e);
            throw e;
        }
        return success;
    }


    @Override
    public AnnotationTag createAnnotationTag(final CreateAnnotationTagRequest request) {
        return annotationServiceProvider.get().createAnnotationTag(request);
    }

    @Override
    public AnnotationTag updateAnnotationTag(final AnnotationTag annotationTag) {
        return annotationServiceProvider.get().updateAnnotationTag(annotationTag);
    }

    @Override
    public Boolean deleteAnnotationTag(final AnnotationTag annotationTag) {
        return annotationServiceProvider.get().deleteAnnotationTag(annotationTag);
    }

//    @Override
//    public AnnotationTag fetchAnnotationGroupByName(final String name) {
//        return annotationService.get().fetchAnnotationGroupByName(name);
//    }

    @Override
    public ResultPage<AnnotationTag> findAnnotationTags(final ExpressionCriteria request) {
        return annotationServiceProvider.get().findAnnotationTags(request);
    }

    @Override
    public AnnotationEntry fetchAnnotationEntry(final FetchAnnotationEntryRequest request) {
        return annotationServiceProvider.get().fetchAnnotationEntry(request);
    }

    @Override
    public Boolean changeAnnotationEntry(final ChangeAnnotationEntryRequest request) {
        final AnnotationService annotationService = annotationServiceProvider.get();
        AnnotationEntry before = AnnotationEntry.builder()
                .id(request.getAnnotationEntryId())
                .build();
        AnnotationEntry after = null;
        LOGGER.info(() -> "Changing annotation entry " + request);
        try {
            before = annotationService.fetchAnnotationEntry(new FetchAnnotationEntryRequest(
                    request.getAnnotationRef(),
                    request.getAnnotationEntryId()));
            final Boolean success = annotationService.changeAnnotationEntry(request);
            after = annotationService.fetchAnnotationEntry(new FetchAnnotationEntryRequest(
                    request.getAnnotationRef(),
                    request.getAnnotationEntryId()));
            documentEventLog.get().update(before, after, null);
            return success;
        } catch (final RuntimeException e) {
            documentEventLog.get().update(before, after, e);
            throw e;
        }
    }

    @Override
    public Boolean deleteAnnotationEntry(final DeleteAnnotationEntryRequest request) {
        final AnnotationService annotationService = annotationServiceProvider.get();
        AnnotationEntry before = AnnotationEntry.builder()
                .id(request.getAnnotationEntryId())
                .build();
        LOGGER.info(() -> "Deleting annotation entry " + request);
        try {
            before = annotationService.fetchAnnotationEntry(new FetchAnnotationEntryRequest(
                    request.getAnnotationRef(),
                    request.getAnnotationEntryId()));
            final Boolean success = annotationServiceProvider.get().deleteAnnotationEntry(request);
            documentEventLog.get().delete(before, null);
            return success;
        } catch (final RuntimeException e) {
            documentEventLog.get().delete(before, e);
            throw e;
        }
    }
}
