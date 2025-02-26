/*
 * Copyright 2024 Crown Copyright
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

import stroom.annotation.shared.AnnotationCreator;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetDescriptionRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.entity.shared.ExpressionCriteria;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.Meta;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamKeys;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.ExpressionFilter;
import stroom.searchable.api.Searchable;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AnnotationService implements Searchable, AnnotationCreator, HasUserDependencies {

    private static final DocRef ANNOTATIONS_PSEUDO_DOC_REF = new DocRef("Annotations", "Annotations", "Annotations");

    private final AnnotationDao annotationDao;
    private final SecurityContext securityContext;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final Provider<DocumentPermissionService> documentPermissionServiceProvider;
    private final Provider<MetaService> metaServiceProvider;
    private final Provider<DocRefInfoService> docRefInfoServiceProvider;

    @Inject
    AnnotationService(final AnnotationDao annotationDao,
                      final SecurityContext securityContext,
                      final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                      final Provider<DocumentPermissionService> documentPermissionServiceProvider,
                      final Provider<MetaService> metaServiceProvider,
                      final Provider<DocRefInfoService> docRefInfoServiceProvider) {
        this.annotationDao = annotationDao;
        this.securityContext = securityContext;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.metaServiceProvider = metaServiceProvider;
        this.docRefInfoServiceProvider = docRefInfoServiceProvider;
    }

    @Override
    public String getDataSourceType() {
        return ANNOTATIONS_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        if (securityContext.hasAppPermission(AppPermission.ANNOTATIONS)) {
            return Collections.singletonList(ANNOTATIONS_PSEUDO_DOC_REF);
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.of(AnnotationFields.UPDATED_ON_FIELD);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!ANNOTATIONS_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, AnnotationFields.FIELDS);
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(AnnotationFields.FIELDS);
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        checkAppPermission();

        final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                .addReplacementFilter(
                        ParamKeys.CURRENT_USER,
                        securityContext.getUserRef().toDisplayString())
                .build();

        ExpressionOperator expression = criteria.getExpression();
        expression = expressionFilter.copy(expression);
        criteria.setExpression(expression);

        annotationDao.search(criteria, fieldIndex, consumer, uuid ->
                securityContext.hasDocumentPermission(new DocRef(Annotation.TYPE, uuid), DocumentPermission.VIEW));
    }

    private UserRef getCurrentUser() {
        return securityContext.getUserRef();
    }

    AnnotationDetail getDetail(Long annotationId) {
        checkAppPermission();
        final AnnotationDetail annotationDetail = annotationDao.getDetail(annotationId);
        if (annotationDetail != null && annotationDetail.getAnnotation() != null) {
            checkViewPermission(annotationDetail.getAnnotation());
        }
        return annotationDetail;
    }

    private void checkViewPermission(final Annotation annotation) {
        if (annotation == null) {
            throw new RuntimeException("Annotation not found");
        }
        if (!securityContext.hasDocumentPermission(annotation.asDocRef(),
                DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have read permission on the annotation");
        }
    }

    private void checkEditPermission(final Annotation annotation) {
        if (annotation == null) {
            throw new RuntimeException("Annotation not found");
        }
        if (!securityContext.hasDocumentPermission(annotation.asDocRef(),
                DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have edit permission on the annotation");
        }
    }

    public AnnotationDetail createAnnotation(final CreateAnnotationRequest request) {
        checkAppPermission();

        // Create the annotation.
        final AnnotationDetail annotationDetail = annotationDao.createAnnotation(request, getCurrentUser());
        final Annotation annotation = annotationDetail.getAnnotation();
        final DocRef docRef = annotation.asDocRef();

        // Create permissions.
        final DocumentPermissionService documentPermissionService = documentPermissionServiceProvider.get();

        // Add owner permission.
        documentPermissionService.setPermission(docRef, securityContext.getUserRef(), DocumentPermission.OWNER);

        // Copy feed permissions to the annotation.
        if (!request.getLinkedEvents().isEmpty()) {
            final EventId eventId = request.getLinkedEvents().getFirst();
            final Meta meta = metaServiceProvider.get().getMeta(eventId.getStreamId());
            if (meta != null) {
                final List<DocRef> docRefs = docRefInfoServiceProvider.get()
                        .findByName(FeedDoc.TYPE, meta.getFeedName(), false);
                if (!docRefs.isEmpty()) {
                    final DocRef feedDocRef = docRefs.getFirst();
                    documentPermissionService.addDocumentPermissions(feedDocRef, docRef);
                }
            }
        }

        return annotationDetail;
    }

    public AnnotationDetail createEntry(final CreateEntryRequest request) {
        checkAppPermission();
        final Annotation annotation = annotationDao.get(request.getAnnotation().getId());
        checkEditPermission(annotation);
        return annotationDao.createEntry(request, getCurrentUser());
    }

    List<EventId> getLinkedEvents(final Long annotationId) {
        checkAppPermission();
        final Annotation annotation = annotationDao.get(annotationId);
        checkViewPermission(annotation);
        return annotationDao.getLinkedEvents(annotationId);
    }

    List<EventId> link(final EventLink eventLink) {
        checkAppPermission();
        final Annotation annotation = annotationDao.get(eventLink.getAnnotationId());
        checkEditPermission(annotation);
        return annotationDao.link(getCurrentUser(), eventLink);
    }

    List<EventId> unlink(final EventLink eventLink) {
        checkAppPermission();
        final Annotation annotation = annotationDao.get(eventLink.getAnnotationId());
        checkEditPermission(annotation);
        return annotationDao.unlink(eventLink, getCurrentUser());
    }

    Integer setStatus(SetStatusRequest request) {
        checkAppPermission();
        for (long id : request.getAnnotationIdList()) {
            final Annotation annotation = annotationDao.get(id);
            checkEditPermission(annotation);
        }
        return annotationDao.setStatus(request, getCurrentUser());
    }

    Integer setAssignedTo(SetAssignedToRequest request) {
        checkAppPermission();
        for (long id : request.getAnnotationIdList()) {
            final Annotation annotation = annotationDao.get(id);
            checkEditPermission(annotation);
        }
        return annotationDao.setAssignedTo(request, getCurrentUser());
    }

    Integer setDescription(final SetDescriptionRequest request) {
        checkAppPermission();
        final Annotation annotation = annotationDao.get(request.getAnnotationId());
        checkEditPermission(annotation);
        return annotationDao.setDescription(request);
    }

    private void checkAppPermission() {
        if (!securityContext.hasAppPermission(AppPermission.ANNOTATIONS)) {
            throw new PermissionException(
                    securityContext.getUserRef(),
                    "You do not have permission to use annotations");
        }
    }

    @Override
    public List<UserDependency> getUserDependencies(final UserRef userRef) {
        Objects.requireNonNull(userRef);

        if (!securityContext.hasAppPermission(AppPermission.MANAGE_USERS_PERMISSION)
            && !securityContext.isCurrentUser(userRef)) {
            throw new PermissionException(
                    userRef,
                    "You do not have permission to view the Annotations that are assigned to user "
                    + userRef.toInfoString());
        }

        return NullSafe.stream(annotationDao.fetchByAssignedUser(userRef.getUuid()))
                .map(annotation -> {
                    final String details = LogUtil.message(
                            "Annotation with title '{}' and subject '{}' is assigned to the user.",
                            annotation.getName(),
                            annotation.getSubject());
                    return new UserDependency(
                            userRef,
                            details);
                })
                .toList();
    }
}
