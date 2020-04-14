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

package stroom.util.entityevent;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

@JsonPropertyOrder({"docRef", "action"})
@JsonInclude(Include.NON_NULL)
public class EntityEvent {
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final EntityAction action;

    @JsonCreator
    public EntityEvent(@JsonProperty("docRef") final DocRef docRef,
                       @JsonProperty("action") final EntityAction action) {
        this.docRef = docRef;
        this.action = action;
    }

    public static void fire(final EntityEventBus eventBus, final DocRef docRef,
                            final EntityAction action) {
        eventBus.fire(new EntityEvent(docRef, action));
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public EntityAction getAction() {
        return action;
    }

    public interface Handler {
        void onChange(EntityEvent event);
    }
}
