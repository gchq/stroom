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

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

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

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            final EntityAction action) {
        if (eventBus != null) {
            eventBus.fire(new EntityEvent(docRef, action));
        }
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

    @Override
    public String toString() {
        return "EntityEvent{" +
                "docRef=" + docRef +
                ", action=" + action +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final EntityEvent that = (EntityEvent) o;
        return Objects.equals(docRef, that.docRef) && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, action);
    }
}
