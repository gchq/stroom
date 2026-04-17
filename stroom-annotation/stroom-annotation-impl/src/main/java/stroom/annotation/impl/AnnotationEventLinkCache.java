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
import stroom.util.entityevent.EntityEvent;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AnnotationEventLinkCache implements EntityEvent.Handler {

    private volatile MapWrapper mapWrapper;

    public AnnotationEventLinkCache() {
        this.mapWrapper = new MapWrapper(new ConcurrentHashMap<>(), Instant.MIN);
    }

    public AnnotationIdentity getAnnotationIdentity(final EventId eventId) {

    }


    // --------------------------------------------------------------------------------


    // TODO change to Set<id + uuid>
    private record MapWrapper(Map<EventId, Set<CacheValue>> annotationEventIdCache,
                              Instant lastEventIdLoad) {

    }

    private record CacheValue(long id,
                              UUID uuid) {

    }
}
