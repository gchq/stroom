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
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationResource;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.shared.SingleDocumentPermissionChangeRequest;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class AnnotationResourceImpl implements AnnotationResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationResourceImpl.class);

    private final Provider<AnnotationService> annotationService;
    private final Provider<DocumentEventLog> documentEventLog;

    @Inject
    AnnotationResourceImpl(final Provider<AnnotationService> annotationService,
                           final Provider<DocumentEventLog> documentEventLog) {
        this.annotationService = annotationService;
        this.documentEventLog = documentEventLog;
    }

    @Override
    public AnnotationDetail getById(final Long annotationId) {
        LOGGER.info(() -> "Getting annotation " + annotationId);
        AnnotationDetail annotationDetail;
        try {
            annotationDetail = annotationService.get().getDetailById(annotationId);
            if (annotationDetail != null) {
                documentEventLog.get().view(annotationDetail, null);
            }
        } catch (final RuntimeException e) {
            documentEventLog.get().view("Annotation " + annotationId, e);
            throw e;
        }
        return annotationDetail;
    }

    @Override
    public AnnotationDetail createAnnotation(final CreateAnnotationRequest request) {
        AnnotationDetail annotationDetail;
        LOGGER.info(() -> "Creating annotation " + request);
        try {
            annotationDetail = annotationService.get().createAnnotation(request);
            documentEventLog.get().create(annotationDetail, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().create("Annotation", e);
            throw e;
        }
        return annotationDetail;
    }

    @Override
    public AnnotationDetail change(final SingleAnnotationChangeRequest request) {
        Annotation before = null;
        Annotation after = null;

        AnnotationDetail annotationDetail;
        LOGGER.info(() -> "Changing annotation " + request.getAnnotationRef());
        try {
            before = annotationService.get().getByRef(request.getAnnotationRef()).orElse(null);
            if (before == null) {
                throw new RuntimeException("Unable to find annotation");
            }

            annotationDetail = annotationService.get().change(request);
            after = NullSafe.get(annotationDetail, AnnotationDetail::getAnnotation);
            documentEventLog.get().update(before, after, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().update(before == null
                    ? request.getAnnotationRef()
                    : before, after, e);
            throw e;
        }

        return annotationDetail;
    }

    @Override
    public Integer batchChange(final MultiAnnotationChangeRequest request) {
        int count = 0;
        for (final long id : request.getAnnotationIdList()) {
            Annotation before = null;
            Annotation after = null;
            AnnotationDetail annotationDetail;
            LOGGER.info(() -> "Changing annotation " + id);
            try {
                before = annotationService.get().getById(id).orElse(null);
                if (before == null) {
                    throw new RuntimeException("Unable to find annotation");
                }

                DocRef docRef = before.asDocRef();
                annotationDetail = annotationService.get().change(new SingleAnnotationChangeRequest(docRef,
                        request.getChange()));
                after = NullSafe.get(annotationDetail, AnnotationDetail::getAnnotation);
                documentEventLog.get().update(before, after, null);
                count++;
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
        return annotationService.get().getStandardComments(filter);
    }

//    @AutoLogged(OperationType.UNLOGGED)
//    @Override
//    public SimpleDuration getDefaultRetentionPeriod() {
//        return annotationService.get().getDefaultRetentionPeriod();
//    }

    @Override
    public List<EventId> getLinkedEvents(final DocRef annotationRef) {
        return annotationService.get().getLinkedEvents(annotationRef);
    }

    @Override
    public Boolean changeDocumentPermissions(final SingleDocumentPermissionChangeRequest request) {
        return annotationService.get().changeDocumentPermissions(request);
    }

    @Override
    public Boolean deleteAnnotation(final DocRef annotationRef) {
        Boolean success;
        LOGGER.info(() -> "Deleting annotation " + annotationRef);
        try {
            success = annotationService.get().deleteAnnotation(annotationRef);
            documentEventLog.get().delete(annotationRef, null);
        } catch (final RuntimeException e) {
            documentEventLog.get().delete(annotationRef, e);
            throw e;
        }
        return success;
    }


    @Override
    public AnnotationTag createAnnotationTag(final CreateAnnotationTagRequest request) {
        return annotationService.get().createAnnotationTag(request);
    }

    @Override
    public AnnotationTag updateAnnotationTag(final AnnotationTag annotationTag) {
        return annotationService.get().updateAnnotationTag(annotationTag);
    }

    @Override
    public Boolean deleteAnnotationTag(final AnnotationTag annotationTag) {
        return annotationService.get().deleteAnnotationTag(annotationTag);
    }

//    @Override
//    public AnnotationTag fetchAnnotationGroupByName(final String name) {
//        return annotationService.get().fetchAnnotationGroupByName(name);
//    }

    @Override
    public ResultPage<AnnotationTag> findAnnotationTags(final ExpressionCriteria request) {
        return annotationService.get().findAnnotationTags(request);
    }

//    @Override
//    public List<AnnotationTag> getAnnotationTags(final String filter) {
//        return annotationService.get().getAnnotationGroups(filter);
//    }
}
