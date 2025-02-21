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

import stroom.annotation.api.AnnotationCreator;
import stroom.annotation.api.AnnotationFields;
import stroom.annotation.shared.AnnotationDetail;
import stroom.annotation.shared.CreateEntryRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.EventLink;
import stroom.annotation.shared.SetAssignedToRequest;
import stroom.annotation.shared.SetStatusRequest;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.search.extraction.ExpressionFilter;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.NullSafe;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasUserDependencies;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserDependency;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AnnotationService implements Searchable, AnnotationCreator, HasUserDependencies {

    private static final DocRef ANNOTATIONS_PSEUDO_DOC_REF = new DocRef("Annotations", "Annotations", "Annotations");

    private final AnnotationDao annotationDao;
    private final SecurityContext securityContext;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    AnnotationService(final AnnotationDao annotationDao,
                      final SecurityContext securityContext,
                      final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.annotationDao = annotationDao;
        this.securityContext = securityContext;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
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
        checkPermission();

        final ExpressionFilter expressionFilter = ExpressionFilter.builder()
                .addReplacementFilter(
                        AnnotationFields.CURRENT_USER_FUNCTION,
                        securityContext.getUserRef().toDisplayString())
                .build();

        ExpressionOperator expression = criteria.getExpression();
        expression = expressionFilter.copy(expression);
        criteria.setExpression(expression);

        annotationDao.search(criteria, fieldIndex, consumer);
    }

    private UserRef getCurrentUser() {
        return securityContext.getUserRef();
    }

    AnnotationDetail getDetail(Long annotationId) {
        checkPermission();
        return annotationDao.getDetail(annotationId);
    }

    public AnnotationDetail createEntry(final CreateEntryRequest request) {
        checkPermission();
        return annotationDao.createEntry(request, getCurrentUser());
    }

    List<EventId> getLinkedEvents(final Long annotationId) {
        checkPermission();
        return annotationDao.getLinkedEvents(annotationId);
    }

    List<EventId> link(final EventLink eventLink) {
        checkPermission();
        return annotationDao.link(getCurrentUser(), eventLink);
    }

    List<EventId> unlink(final EventLink eventLink) {
        checkPermission();
        return annotationDao.unlink(eventLink, getCurrentUser());
    }

    Integer setStatus(SetStatusRequest request) {
        checkPermission();
        return annotationDao.setStatus(request, getCurrentUser());
    }

    Integer setAssignedTo(SetAssignedToRequest request) {
        checkPermission();
        return annotationDao.setAssignedTo(request, getCurrentUser());
    }

    private void checkPermission() {
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
                            annotation.getTitle(),
                            annotation.getSubject());
                    return new UserDependency(
                            userRef,
                            details);
                })
                .toList();
    }
}
