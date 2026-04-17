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


import stroom.annotation.shared.EventId;
import stroom.util.entityevent.EntityEvent.EntityEventData;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AnnotationEventLinks implements EntityEventData {

    @JsonProperty
    private final long annotationId;
    @JsonProperty
    private final List<EventId> eventIds;

    @JsonCreator
    public AnnotationEventLinks(@JsonProperty("annotationId") final long annotationId,
                                @JsonProperty("eventIds") final List<EventId> eventIds) {
        this.annotationId = annotationId;
        this.eventIds = NullSafe.unmodifiableList(eventIds);
    }

    public long getAnnotationId() {
        return annotationId;
    }

    public List<EventId> getEventIds() {
        return eventIds;
    }

    @Override
    public String toString() {
        return "AnnotationEventLinks{" +
               "annotationId=" + annotationId +
               ", eventIds=" + eventIds +
               '}';
    }
}
