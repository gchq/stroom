/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.annotation.shared.AbstractAnnotationChange;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationCreator;
import stroom.annotation.shared.AnnotationEntry;
import stroom.annotation.shared.AnnotationFields;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.ChangeAnnotationEntryRequest;
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.CreateAnnotationTagRequest;
import stroom.annotation.shared.DeleteAnnotationEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.FetchAnnotationEntryRequest;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.annotation.shared.LinkEvents;
import stroom.annotation.shared.MultiAnnotationChangeRequest;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.annotation.shared.UnlinkEvents;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.ExpressionPredicateFactory;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ParamKeys;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.search.extraction.ExpressionFilter;
import stroom.searchable.api.Searchable;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserGroupsService;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventBatch;
import stroom.util.entityevent.EntityEventBus;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

public class AnnotationService implements Searchable, AnnotationCreator, HasUserDependencies {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AnnotationService.class);

    public static final String ANNOTATION_RETENTION_JOB_NAME = "Annotation Retention";
    private static final String LOCK_NAME = "ANNOTATION_RETENTION";
    public static final int RETENTION_EVENTS_BATCH_SIZE = 5_000;

    private final AnnotationDao annotationDao;
    private final AnnotationTagDao annotationTagDao;
    private final SecurityContext securityContext;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;
    private final Provider<DocumentPermissionService> documentPermissionServiceProvider;
    private final Provider<AnnotationConfig> annotationConfigProvider;
    private final Provider<ExpressionPredicateFactory> expressionPredicateFactoryProvider;
    private final Provider<UserGroupsService> userGroupsServiceProvider;
    private final EntityEventBus entityEventBus;
    private final ClusterLockService clusterLockService;

    @Inject
    AnnotationService(final AnnotationDao annotationDao,
                      final AnnotationTagDao annotationTagDao,
                      final SecurityContext securityContext,
                      final FieldInfoResultPageFactory fieldInfoResultPageFactory,
                      final Provider<DocumentPermissionService> documentPermissionServiceProvider,
                      final Provider<AnnotationConfig> annotationConfigProvider,
                      final Provider<ExpressionPredicateFactory> expressionPredicateFactoryProvider,
                      final Provider<UserGroupsService> userGroupsServiceProvider,
                      final EntityEventBus entityEventBus, final ClusterLockService clusterLockService) {
        this.annotationDao = annotationDao;
        this.annotationTagDao = annotationTagDao;
        this.securityContext = securityContext;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
        this.documentPermissionServiceProvider = documentPermissionServiceProvider;
        this.annotationConfigProvider = annotationConfigProvider;
        this.expressionPredicateFactoryProvider = expressionPredicateFactoryProvider;
        this.userGroupsServiceProvider = userGroupsServiceProvider;
        this.entityEventBus = entityEventBus;
        this.clusterLockService = clusterLockService;
    }

    public ResultPage<Annotation> findAnnotations(final FindAnnotationRequest request) {
        checkAppPermission();

        final DocumentPermission permission;
        if (DocumentPermission.EDIT.equals(request.getRequiredPermission())) {
            permission = DocumentPermission.EDIT;
        } else {
            permission = DocumentPermission.VIEW;
        }

        return annotationDao.findAnnotations(request, annotation ->
                securityContext.hasDocumentPermission(annotation.asDocRef(), permission));
    }

    public Optional<Annotation> getAnnotationByRef(final DocRef annotationRef) {
        checkAppPermission();
        checkViewPermission(annotationRef);
        return annotationDao.getAnnotationByDocRef(annotationRef);
    }

    public List<AnnotationEntry> getAnnotationEntries(final DocRef annotationRef) {
        checkAppPermission();
        checkViewPermission(annotationRef);
        return annotationDao.getAnnotationEntries(annotationRef);
    }

    public Optional<Annotation> getAnnotationById(final long id) {
        final Optional<Annotation> optionalAnnotation = annotationDao.getAnnotationById(id);
        return optionalAnnotation.filter(annotation ->
                securityContext.hasDocumentPermission(annotation.asDocRef(), DocumentPermission.VIEW));
    }

    public Collection<AnnotationIdentity> getAnnotationIdListForEvent(final EventId eventId) {
        return annotationDao.getAnnotationIdsForEvent(eventId);
    }

    public Collection<AnnotationValues> getAnnotationValues(final Collection<AnnotationIdentity> idList,
                                                            final Set<QueryField> requiredAnnotationFields) {
        return LOGGER.logDurationIfInfoEnabled(() -> {
            // Filter the annotations by user permission.
            final Collection<AnnotationIdentity> filtered = idList.stream()
                    .filter(annotationIdentity ->
                            securityContext.hasDocumentPermission(annotationIdentity.getDocRef(),
                                    DocumentPermission.VIEW))
                    .toList();

            // Get annotation values from the cache or DB if required.
            return annotationDao.getAnnotationValues(filtered, requiredAnnotationFields);
        }, collection ->
                LogUtil.message("getAnnotationValues() - count: {}", collection.size()));
    }

    @Override
    public String getDataSourceType() {
        return AnnotationFields.ANNOTATIONS_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        if (securityContext.hasAppPermission(AppPermission.ANNOTATIONS)) {
            return Collections.singletonList(AnnotationFields.ANNOTATIONS_PSEUDO_DOC_REF);
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.of(AnnotationFields.UPDATED_ON_FIELD);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!AnnotationFields.ANNOTATIONS_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
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
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer valuesConsumer,
                       final ErrorConsumer errorConsumer) {
        checkAppPermission();

        final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                .addReplacementFilter(
                        ParamKeys.CURRENT_USER,
                        securityContext.getUserRef().toDisplayString())
                .build();

        ExpressionOperator expression = criteria.getExpression();
        expression = expressionFilter.copy(expression);
        criteria.setExpression(expression);

        final Predicate<String> viewPermissionPredicate = getViewPermissionPredicate();
        annotationDao.search(criteria, fieldIndex, valuesConsumer, viewPermissionPredicate);
    }

    private Predicate<String> getViewPermissionPredicate() {
        if (securityContext.isAdmin()) {
            return ignored -> true;
        }
        return uuid -> securityContext
                .hasDocumentPermission(new DocRef(Annotation.TYPE, uuid), DocumentPermission.VIEW);
    }

    private UserRef getCurrentUser() {
        return securityContext.getUserRef();
    }

    private void checkViewPermission(final DocRef annotationRef) {
        if (annotationRef == null) {
            throw new RuntimeException("Annotation not found");
        }
        if (!securityContext.hasDocumentPermission(annotationRef,
                DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to read this annotation");
        }
    }

    private void checkEditPermission(final DocRef annotationRef) {
        if (annotationRef == null) {
            throw new RuntimeException("Annotation not found");
        }
        if (!securityContext.hasDocumentPermission(annotationRef,
                DocumentPermission.EDIT)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to edit this annotation");
        }
    }

    private void checkDeletePermission(final DocRef annotationRef) {
        if (annotationRef == null) {
            throw new RuntimeException("Annotation not found");
        }
        if (!securityContext.hasDocumentPermission(annotationRef,
                DocumentPermission.DELETE)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have permission to delete this annotation");
        }
    }

    @Override
    public Annotation createAnnotation(final CreateAnnotationRequest request) {
        checkAppPermission();

        // Create the annotation.
        final Annotation annotation = annotationDao.createAnnotation(request, getCurrentUser());
        final DocRef docRef = annotation.asDocRef();
        final UserRef userRef = securityContext.getUserRef();

        securityContext.asProcessingUser(() -> {
            // Create permissions.
            final DocumentPermissionService documentPermissionService = documentPermissionServiceProvider.get();

            // Add owner permission.
            documentPermissionService.setPermission(docRef, userRef, DocumentPermission.OWNER);

            // Add ownership perms to parent groups.
            final Set<UserRef> parentGroups = userGroupsServiceProvider.get().getGroups(userRef);
            if (NullSafe.hasItems(parentGroups)) {
                parentGroups.forEach(group ->
                        documentPermissionService.setPermission(docRef, group, DocumentPermission.OWNER));
            }
        });

        fireEntityEvent(EntityAction.CREATE, annotation.asDocRef(), annotation.getId());
        return annotation;
    }

    public boolean change(final SingleAnnotationChangeRequest request) {
        Objects.requireNonNull(request);
        checkAppPermission();
        checkEditPermission(request.getAnnotationRef());
        final AbstractAnnotationChange change = request.getChange();
        final boolean result = annotationDao.change(request, getCurrentUser());
        final DocRef annotationRef = request.getAnnotationRef();
        final long annotationId = Objects.requireNonNullElseGet(
                request.getAnnotationId(),
                () -> getIdOrThrow(annotationRef));

        switch (change) {
            case final LinkEvents ignored -> LOGGER.debug("change() - Skipping linkEvents, handled by DAO");
            case final UnlinkEvents ignored -> LOGGER.debug("change() - Skipping unlinkEvents, handled by DAO");
            default -> fireEntityEvent(EntityAction.UPDATE, annotationRef, annotationId);
        }
        return result;
    }

    private long getIdOrThrow(final DocRef annotationRef) {
        Objects.requireNonNull(annotationRef);
        return annotationDao.getIdOrThrow(annotationRef);
    }

    public Integer batchChange(final MultiAnnotationChangeRequest request) {
        final List<AnnotationIdentity> annotationIdentities = getRefsForEdit(request.getAnnotationIdList());

        for (final AnnotationIdentity annotationIdentity : annotationIdentities) {
            final SingleAnnotationChangeRequest singleAnnotationChangeRequest = new SingleAnnotationChangeRequest(
                    annotationIdentity, request.getChange());
            annotationDao.change(singleAnnotationChangeRequest, getCurrentUser());
        }

        if (!annotationIdentities.isEmpty()) {
            final AbstractAnnotationChange change = request.getChange();
            if (change instanceof UnlinkEvents || change instanceof LinkEvents) {
                LOGGER.debug("batchChange() - Skipping linkEvents/unlinkEvents, handled by DAO");
            } else {
                fireEntityChangeEvents(EntityAction.UPDATE, annotationIdentities);
            }
        }
        return annotationIdentities.size();
    }

    private List<AnnotationIdentity> getRefsForEdit(final List<Long> annotationIdList) {
        checkAppPermission();
        final List<AnnotationIdentity> annotationIdentities = annotationDao.idListToDocRefs(annotationIdList);
        annotationIdentities.forEach(annotationIdentity ->
                checkEditPermission(annotationIdentity.getDocRef()));
        return annotationIdentities;
    }

    List<EventId> getLinkedEvents(final DocRef annotationRef) {
        checkAppPermission();
        checkViewPermission(annotationRef);
        return annotationDao.getLinkedEvents(annotationRef);
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

    public Boolean deleteAnnotation(final DocRef annotationRef) {
        Objects.requireNonNull(annotationRef);
        checkAppPermission();
        checkDeletePermission(annotationRef);

        documentPermissionServiceProvider.get()
                .removeAllDocumentPermissions(annotationRef);
        final long id = annotationDao.getIdOrThrow(annotationRef);
        final Boolean result = annotationDao.logicalDelete(annotationRef, securityContext.getUserRef());
        fireEntityEvent(EntityAction.DELETE, annotationRef, id);
        return result;
    }

    private List<String> filterValues(final List<String> allValues, final String quickFilterInput) {
        if (allValues == null || allValues.isEmpty()) {
            return allValues;
        } else {
            return expressionPredicateFactoryProvider.get()
                    .filterAndSortStream(allValues.stream(),
                            quickFilterInput,
                            Optional.of(Comparator.naturalOrder()))
                    .toList();
        }
    }

    public List<String> getStandardComments(final String filter) {
        return filterValues(annotationConfigProvider.get().getStandardComments(), filter);
    }

    public void performDataRetention() {
        performDataRetention(RETENTION_EVENTS_BATCH_SIZE);
    }

    void performDataRetention(final int batchSize) {
        LOGGER.debug("performDataRetention() - batchSize = {}", batchSize);

        clusterLockService.tryLock(LOCK_NAME, () -> {
            // First mark annotations as deleted if they haven't been updated since their data retention time.
            final List<AnnotationIdentity> logicallyDeletedIds = annotationDao.markDeletedByDataRetention();

            if (NullSafe.hasItems(logicallyDeletedIds)) {
                fireEntityDeleteEvents(logicallyDeletedIds, batchSize);
            }

            // Now delete items that have been deleted longer than the max deletion age.
            final StroomDuration physicalDeleteAge = annotationConfigProvider.get().getPhysicalDeleteAge();
            final Instant age = Instant.now().minus(physicalDeleteAge);
            final List<AnnotationIdentity> physicallyDeletedIds = annotationDao.physicallyDelete(age);
            if (NullSafe.hasItems(physicallyDeletedIds)) {
                fireEntityDeleteEvents(physicallyDeletedIds, batchSize);
            }
            LOGGER.info(() -> LogUtil.message(
                    "Annotation data retention - logically deleted count: {}, physically deleted count: {}",
                    logicallyDeletedIds.size(), physicallyDeletedIds.size()));
        });
    }

    private void fireEntityDeleteEvents(final List<AnnotationIdentity> annotationIdentities,
                                        final int batchSize) {
        // Limit the size of the event batches so we are not sending massive requests
        final int count = annotationIdentities.size();
        int fromIdxInc = 0;
        while (true) {
            final int remaining = count - fromIdxInc;
            if (remaining == 0 || fromIdxInc > count) {
                break;
            }
            final int thisBatchSize = Math.min(batchSize, remaining);
            final int toIdxExc = fromIdxInc + thisBatchSize;
            final List<AnnotationIdentity> batchIds = annotationIdentities.subList(fromIdxInc, toIdxExc);
            final int finalFromIdxInc = fromIdxInc;
            LOGGER.debug(() -> LogUtil.message(
                    "fireEntityChangeEvents() - fromIdxInc: {}, toIdxExc: {}, ids: {}, batchIds: {}, docRefs: {}",
                    finalFromIdxInc, toIdxExc, annotationIdentities.size(), batchIds.size(), batchIds.size()));
            fireEntityChangeEvents(EntityAction.DELETE, batchIds);
            fromIdxInc += batchSize;
        }
    }

    public AnnotationTag createAnnotationTag(final CreateAnnotationTagRequest request) {
        checkAppPermission();
        return annotationTagDao.createAnnotationTag(request);
    }

    public AnnotationTag updateAnnotationTag(final AnnotationTag annotationTag) {
        checkAppPermission();
        return annotationTagDao.updateAnnotationTag(annotationTag);
    }

    public Boolean deleteAnnotationTag(final AnnotationTag annotationTag) {
        checkAppPermission();
        return annotationTagDao.deleteAnnotationTag(annotationTag);
    }

    public ResultPage<AnnotationTag> findAnnotationTags(final ExpressionCriteria request) {
        checkAppPermission();
        final Predicate<String> viewPermissionPredicate = getViewPermissionPredicate();
        return annotationTagDao.findAnnotationTags(request, viewPermissionPredicate);
    }

    public AnnotationEntry fetchAnnotationEntry(final FetchAnnotationEntryRequest request) {
        checkAppPermission();
        checkViewPermission(request.getAnnotationRef());
        return annotationDao.fetchAnnotationEntry(
                request.getAnnotationRef(),
                securityContext.getUserRef(),
                request.getAnnotationEntryId());
    }

    public Boolean changeAnnotationEntry(final ChangeAnnotationEntryRequest request) {
        checkAppPermission();
        checkEditPermission(request.getAnnotationRef());
        return annotationDao.changeAnnotationEntry(
                request.getAnnotationRef(),
                securityContext.getUserRef(),
                request.getAnnotationEntryId(),
                request.getData());
    }

    public Boolean deleteAnnotationEntry(final DeleteAnnotationEntryRequest request) {
        checkAppPermission();
        checkDeletePermission(request.getAnnotationRef());
        return annotationDao.logicalDeleteEntry(
                request.getAnnotationRef(),
                securityContext.getUserRef(),
                request.getAnnotationEntryId());
    }

    private void fireEntityEvent(final EntityAction entityAction, final DocRef annotationRef, final long id) {
        EntityEvent.fire(
                entityEventBus,
                Objects.requireNonNull(annotationRef),
                null,
                Objects.requireNonNull(entityAction),
                new AnnotationIdEntityEventData(id));
    }

    private void fireEntityChangeEvents(final EntityAction entityAction,
                                        final List<AnnotationIdentity> annotationIdentities) {
        final List<EntityEvent> events = NullSafe.stream(annotationIdentities)
                .filter(Objects::nonNull)
                .map(annotationIdentity ->
                        AnnotationIdEntityEventData.createEntityEvent(entityAction, annotationIdentity))
                .toList();
        final EntityEventBatch entityEventBatch = new EntityEventBatch(events, true);
        entityEventBus.fire(entityEventBatch);
    }
}
