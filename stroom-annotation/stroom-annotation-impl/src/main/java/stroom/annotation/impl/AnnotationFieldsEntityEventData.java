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


import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.AnnotationIdentity;
import stroom.docref.DocRef;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEvent.EntityEventData;
import stroom.util.entityevent.EntityEventBus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

/**
 * Used for changes to one or more fields on an annotation
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class AnnotationFieldsEntityEventData implements EntityEventData {

    @JsonProperty
    private final long annotationId;
    @JsonProperty
    private final Set<String> changedFields;

    @JsonCreator
    public AnnotationFieldsEntityEventData(@JsonProperty("annotationId") final long annotationId,
                                           @JsonProperty("changedFields") final Set<String> changedFields) {
        this.annotationId = annotationId;
        this.changedFields = changedFields;
    }

    public long getAnnotationId() {
        return annotationId;
    }

    public Set<String> getChangedFields() {
        return changedFields;
    }

    @Override
    public String toString() {
        return "AnnotationFieldsEntityEventData{" +
               "annotationId=" + annotationId +
               ", changedFields=" + changedFields +
               '}';
    }

    public static EntityEvent createEntityEvent(final EntityAction entityAction,
                                                final AnnotationIdentity annotationId,
                                                final Set<String> changedFields) {
        Objects.requireNonNull(annotationId);
        return new EntityEvent(
                new DocRef(Annotation.TYPE, annotationId.getUuid()),
                entityAction,
                new AnnotationFieldsEntityEventData(annotationId.getId(), changedFields));
    }

    public static void fireEvent(final EntityEventBus entityEventBus,
                                 final EntityAction entityAction,
                                 final AnnotationIdentity annotationId,
                                 final Set<String> changedFields) {
        if (entityEventBus != null) {
            final EntityEvent entityEvent = createEntityEvent(entityAction, annotationId, changedFields);
            entityEventBus.fire(entityEvent);
        }
    }
}
