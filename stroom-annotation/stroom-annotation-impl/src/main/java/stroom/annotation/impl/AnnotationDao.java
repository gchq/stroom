/*
 * Copyright 2016 Crown Copyright
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

import stroom.annotation.shared.*;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.shared.UserRef;

import java.util.List;

public interface AnnotationDao {

    Annotation get(long annotationId);

    AnnotationDetail getDetail(long annotationId);

    List<Annotation> getAnnotationsForEvents(long streamId, long eventId);

    AnnotationDetail createEntry(CreateEntryRequest request, UserRef currentUser);

    List<EventId> getLinkedEvents(Long annotationId);

    List<EventId> link(UserRef currentUser, EventLink eventLink);

    List<EventId> unlink(EventLink eventLink, UserRef currentUser);

    Integer setStatus(SetStatusRequest request, UserRef currentUser);

    Integer setAssignedTo(SetAssignedToRequest request, UserRef currentUser);

    void search(ExpressionCriteria criteria, FieldIndex fieldIndex, ValuesConsumer consumer);
}
