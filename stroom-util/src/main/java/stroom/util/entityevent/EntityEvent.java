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

package stroom.util.entityevent;

import stroom.docref.DocRef;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class EntityEvent {

    public static final String TYPE_WILDCARD = "*";

    @JsonProperty
    private final DocRef docRef;

    @JsonProperty
    private final DocRef oldDocRef; // For a re-name, the docRef before the change

    @JsonProperty
    private final EntityAction action;

    @JsonCreator
    public EntityEvent(@JsonProperty("docRef") final DocRef docRef,
                       @JsonProperty("oldDocRef") final DocRef oldDocRef,
                       @JsonProperty("action") final EntityAction action) {
        this.docRef = Objects.requireNonNull(docRef);
        this.oldDocRef = oldDocRef;
        this.action = action;
    }

    @SerialisationTestConstructor
    private EntityEvent() {
        this(new DocRef("test", "test"), null, null);
    }

    public EntityEvent(final DocRef docRef,
                       final EntityAction action) {
        this(docRef, null, action);
    }

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            final DocRef oldDocRef,
                            final EntityAction action) {
        if (eventBus != null) {
            eventBus.fire(new EntityEvent(docRef, oldDocRef, action));
        }
    }

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            final EntityAction action) {
        fire(eventBus, docRef, null, action);
    }

    /**
     * @return The {@link DocRef} of the {@link stroom.util.shared.Document} affected by this event,
     * as it is after the event happened.
     */
    public DocRef getDocRef() {
        return docRef;
    }

    /**
     * @return The {@link DocRef} of the {@link stroom.util.shared.Document} affected by this event,
     * as it is before the event happened. May be null.
     */
    public DocRef getOldDocRef() {
        return oldDocRef;
    }

    public EntityAction getAction() {
        return action;
    }

    @Override
    public String toString() {
        return action + " "
                + docRef
                + (oldDocRef != null
                ? " (oldDocRef: " + oldDocRef + ")"
                : "");
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityEvent that = (EntityEvent) o;
        return Objects.equals(docRef, that.docRef) && Objects.equals(oldDocRef,
                that.oldDocRef) && action == that.action;
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, oldDocRef, action);
    }


    // --------------------------------------------------------------------------------


    public interface Handler {

        void onChange(EntityEvent event);
    }
}
