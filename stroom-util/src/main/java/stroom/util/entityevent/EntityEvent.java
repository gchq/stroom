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

package stroom.util.entityevent;

import stroom.docref.DocRef;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LogUtil;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

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

    @JsonProperty
    private final String dataClassName;

    @JsonProperty
    private final String data;

    @JsonCreator
    public EntityEvent(@JsonProperty("docRef") final DocRef docRef,
                       @JsonProperty("oldDocRef") final DocRef oldDocRef,
                       @JsonProperty("action") final EntityAction action,
                       @JsonProperty("dataClassName") final String dataClassName,
                       @JsonProperty("data") final String data) {
        this.docRef = Objects.requireNonNull(docRef);
        this.oldDocRef = oldDocRef;
        this.action = action;
        if (data != null) {
            Objects.requireNonNull(dataClassName);
        }
        this.dataClassName = dataClassName;
        this.data = data;
    }

    @SerialisationTestConstructor
    private EntityEvent() {
        this(new DocRef("test", "test"), null, null, null, null);
    }

    public EntityEvent(final DocRef docRef,
                       final EntityAction action) {
        this(docRef, null, action, null, null);
    }

    public EntityEvent(final DocRef docRef,
                       final DocRef oldDocRef,
                       final EntityAction action) {
        this(docRef, oldDocRef, action, null, null);

    }

    public EntityEvent(final DocRef docRef,
                       final EntityAction action,
                       final EntityEventData entityEventData) {
        this(docRef, null, action, entityEventData);
    }

    public EntityEvent(final DocRef docRef,
                       final DocRef oldDocRef,
                       final EntityAction action,
                       final EntityEventData entityEventData) {
        this.docRef = Objects.requireNonNull(docRef);
        this.oldDocRef = oldDocRef;
        this.action = action;
        if (entityEventData != null) {
            try {
                this.dataClassName = entityEventData.getClass().getName();
                this.data = JsonUtil.writeValueAsString(entityEventData, false);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message("Error serialising {} to JSON - {}",
                        LogUtil.typedValue(entityEventData),
                        LogUtil.exceptionMessage(e)), e);
            }
        } else {
            this.dataClassName = null;
            this.data = null;
        }
    }

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            @Nullable final DocRef oldDocRef,
                            final EntityAction action,
                            @Nullable final String dataClassName,
                            @Nullable final String data) {
        if (eventBus != null) {
            eventBus.fire(new EntityEvent(docRef, oldDocRef, action, dataClassName, data));
        }
    }

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            @Nullable final DocRef oldDocRef,
                            final EntityAction action,
                            @Nullable final EntityEventData entityEventData) {
        if (eventBus != null) {
            eventBus.fire(new EntityEvent(docRef, oldDocRef, action, entityEventData));
        }
    }

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            final EntityAction action,
                            @Nullable final EntityEventData entityEventData) {
        fire(eventBus, docRef, null, action, entityEventData);
    }

    public static void fire(final EntityEventBus eventBus,
                            final DocRef docRef,
                            final DocRef oldDocRef,
                            final EntityAction action) {
        fire(eventBus, docRef, oldDocRef, action, null, null);
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

    /**
     * Gets the type of {@link EntityEvent#getDocRef()}.
     *
     * @return The docRef type
     */
    @JsonIgnore
    public String getType() {
        return docRef.getType();
    }

    public EntityAction getAction() {
        return action;
    }

    /**
     * @return The fully qualified class name of the de-serialised form of the data property.
     * The class should be known to both sender and receiver.
     */
    public String getDataClassName() {
        return dataClassName;
    }

    /**
     * @return True if dataClassName matched the fully qualified class name of expectedDataClass.
     */
    public boolean hasDataClass(@NonNull final Class<? extends EntityEventData> expectedDataClass) {
        return Objects.equals(dataClassName, Objects.requireNonNull(expectedDataClass).getName());
    }

    /**
     * @return Additional data relating to the event. The data is JSON and the structure should
     * be expected and understood by sender and receiver. The format of the data will likely be
     * specific to the docRef.
     */
    public String getData() {
        return data;
    }

    /**
     * Gets the optional entity event data, de-serialised into the supplied class.
     *
     * @param dataClass The class to de-serialise the data into, if present.
     * @param <T>
     * @return The de-serialised event data or null if there is none.
     * @throws IllegalArgumentException if dataClass does not match the dataClassName in the event.
     * @throws RuntimeException         if the data cannot be deserialised.
     */
    public <T extends EntityEventData> T getDataObject(@NonNull final Class<T> dataClass) {
        if (data == null) {
            return null;
        } else {
            Objects.requireNonNull(dataClass);
            if (!Objects.equals(dataClass.getName(), dataClassName)) {
                throw new IllegalArgumentException(LogUtil.message("Invalid data class {}, expecting {}",
                        dataClass.getName(), dataClassName));
            }
            try {
                return JsonUtil.readValue(data, dataClass);
            } catch (final Exception e) {
                throw new RuntimeException(LogUtil.message("Error deserialising json to class {}, json: '{}' - {}",
                        dataClass.getName(), data, LogUtil.exceptionMessage(e)), e);
            }
        }
    }

    public <T extends EntityEventData, R> R getDataObjectAs(@NonNull final Class<T> dataClass,
                                                            @NonNull final Function<T, R> mapper) {
        final T data = getDataObject(dataClass);
        return mapper.apply(data);
    }

    public EntityEventKey asEntityEventKey() {
        return new EntityEventKey(this);
    }

    @Override
    public String toString() {
        return action + " "
               + docRef
               + (oldDocRef != null
                ? " (oldDocRef: " + oldDocRef + ")"
                : "")
               + " data: '" + data + "'" +
               " (" + dataClassName + ")";
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final EntityEvent that = (EntityEvent) o;
        return Objects.equals(docRef, that.docRef)
               && Objects.equals(oldDocRef, that.oldDocRef)
               && action == that.action
               && Objects.equals(dataClassName, that.dataClassName)
               && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, oldDocRef, action, dataClassName, data);
    }

    // --------------------------------------------------------------------------------


    public interface Handler {

        /**
         * Handle an {@link EntityEvent}.
         * Any exceptions thrown within this method will be swallowed and logged at ERROR.
         */
        void onChange(EntityEvent event);
    }


    // --------------------------------------------------------------------------------


    /**
     * Marker interface for all classes used to provide additional {@link EntityEvent} data.
     * <p>
     * Implementations must use Jackson annotations so they can be (de)serialised to JSON.
     * </p>
     */
    public interface EntityEventData {

    }
}
