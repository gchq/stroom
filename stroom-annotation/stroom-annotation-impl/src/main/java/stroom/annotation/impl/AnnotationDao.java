/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.annotation.shared.CreateAnnotationRequest;
import stroom.annotation.shared.EventId;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.annotation.shared.SingleAnnotationChangeRequest;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

public interface AnnotationDao {

    ResultPage<Annotation> findAnnotations(FindAnnotationRequest request, Predicate<Annotation> vierwPredicate);

    List<DocRef> idListToDocRefs(List<Long> idList);

    Optional<Annotation> getAnnotationById(long id);

    Optional<Annotation> getAnnotationByDocRef(DocRef annotationRef);

    List<Annotation> getAnnotationsForEvents(EventId eventId);

    Annotation createAnnotation(CreateAnnotationRequest request, UserRef currentUser);

    boolean change(SingleAnnotationChangeRequest request, UserRef currentUser);

    List<AnnotationEntry> getAnnotationEntries(DocRef annotationRef);

    List<EventId> getLinkedEvents(DocRef annotationRef);

    List<Long> getLinkedAnnotations(DocRef annotationRef);

    void search(ExpressionCriteria criteria,
                FieldIndex fieldIndex,
                ValuesConsumer consumer,
                Predicate<String> uuidPredicate);

    List<Annotation> fetchByAssignedUser(final String userUuid);

    boolean logicalDelete(DocRef annotationRef, UserRef currentUser);

    /**
     * Mark annotations deleted if they have not been updated within their specified retention time.
     */
    void markDeletedByDataRetention();

    /**
     * Physically delete annotations that have been marked as deleted since before the provided age.
     *
     * @param age Anything older than this age will be deleted.
     */
    void physicallyDelete(Instant age);

    AnnotationEntry fetchAnnotationEntry(DocRef annotationRef, UserRef currentUser, long entryId);

    boolean changeAnnotationEntry(DocRef annotationRef, UserRef currentUser, long entryId, String data);

    boolean logicalDeleteEntry(DocRef annotationRef, UserRef currentUser, long entryId);
}
