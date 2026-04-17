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


import stroom.annotation.shared.AnnotationIdentity;
import stroom.annotation.shared.EventId;
import stroom.util.entityevent.EntityEvent.EntityEventData;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.Collections;

public class AnnotationEventLinks implements EntityEventData {

    @JsonProperty
    private final AnnotationIdentity annotationIdentity;
    @JsonProperty
    private final Collection<EventId> eventIds;

    @JsonCreator
    public AnnotationEventLinks(@JsonProperty("annotationId") final AnnotationIdentity annotationIdentity,
                                @JsonProperty("eventIds") final Collection<EventId> eventIds) {
        this.annotationIdentity = annotationIdentity;
        this.eventIds = eventIds == null
                ? Collections.emptyList()
                : Collections.unmodifiableCollection(eventIds);
    }

    public AnnotationIdentity getAnnotationIdentity() {
        return annotationIdentity;
    }

    public Collection<EventId> getEventIds() {
        return eventIds;
    }

    @Override
    public String toString() {
        return "AnnotationEventLinks{" +
               "annotationId=" + annotationIdentity +
               ", eventIds=" + eventIds +
               '}';
    }
}
