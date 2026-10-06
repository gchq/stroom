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
import stroom.util.shared.NullSafe;
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

    /// IF data is used, this is the class name of the serialized data.
    @JsonProperty
    private final String dataClassName;

    /// This is additional event data in JSON form. Both sender and receiver should know how to parse it.
    /// For simple string/long/int data, this is just the string form of the value.
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
     * Starts building an event-firing operation.
     *
     * @param eventBus The event bus to fire the event on.
     * @return A builder that requires the document reference and action to be supplied.
     */
    public static FiringBuilder buildFiring(final EntityEventBus eventBus) {
        return new FiringBuilder(eventBus);
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
     * be expected and understood by sender and receiver. The format of the data will be
     * specific to the use case for the event type.
     */
    public String getData() {
        return data;
    }

    /// When the entity event data is expected to be a simple string value, return the string value.
    ///
    /// @throws IllegalArgumentException if dataClassName does not match String.class.getName()
    @JsonIgnore
    public String getDataAsString() {
        final String expectedClassName = String.class.getName();
        if (expectedClassName.equals(dataClassName)) {
            return data;
        } else {
            throw new IllegalArgumentException(LogUtil.message(
                    "dataClassName '{}' does not match '{}'", dataClassName, expectedClassName));
        }
    }

    /// When the entity event data is expected to be a simple {@link Long} value, return the {@link Long} value.
    ///
    /// @throws IllegalArgumentException if dataClassName does not match Long.class.getName()
    @JsonIgnore
    public Long getDataAsLong() {
        final String expectedClassName = Long.class.getName();
        if (expectedClassName.equals(dataClassName)) {
            if (NullSafe.isNonBlankString(data)) {
                return Long.parseLong(data.trim());
            } else {
                return null;
            }
        } else {
            throw new IllegalArgumentException(LogUtil.message(
                    "dataClassName '{}' does not match '{}'", dataClassName, expectedClassName));
        }
    }

    /// When the entity event data is expected to be a simple {@link Integer} value,
    /// return the {@link Integer} value.
    ///
    /// @throws IllegalArgumentException if dataClassName does not match Integer.class.getName()
    @JsonIgnore
    public Integer getDataAsInteger() {
        final String expectedClassName = Integer.class.getName();
        if (expectedClassName.equals(dataClassName)) {
            if (NullSafe.isNonBlankString(data)) {
                return Integer.parseInt(data.trim());
            } else {
                return null;
            }
        } else {
            throw new IllegalArgumentException(LogUtil.message(
                    "dataClassName '{}' does not match '{}'", dataClassName, expectedClassName));
        }
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

    /// A helper metod for when you want part of the EntityData
    public <T extends EntityEventData, R> R getDataObjectAs(@NonNull final Class<T> dataClass,
                                                            @NonNull final Function<T, R> mapper) {
        Objects.requireNonNull(dataClass, "dataClass must not be null");
        final T data = getDataObject(dataClass);
        return Objects.requireNonNull(mapper, "mapper must not be null")
                .apply(data);
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
     * The first stage of an event firing builder.
     */
    public static final class FiringBuilder {

        private final EntityEventBus eventBus;
        private DocRef docRef;
        private DocRef oldDocRef;
        private EntityAction action;
        private String dataClassName;
        private String data;
        private EntityEventData entityEventData;

        private FiringBuilder(final EntityEventBus eventBus) {
            this.eventBus = eventBus;
        }

        /**
         * Sets the document reference affected by the event.
         *
         * @param docRef The document reference.
         * @return The stage that accepts the optional old document reference or action.
         */
        public DocRefStage withDocRef(final DocRef docRef) {
            this.docRef = Objects.requireNonNull(docRef, "docRef");
            return new DocRefStage(this);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * The builder stage after the current document reference has been supplied.
     */
    public static final class DocRefStage {

        private final FiringBuilder builder;

        private DocRefStage(final FiringBuilder builder) {
            this.builder = builder;
        }

        /**
         * Sets the document reference before a rename.
         *
         * @param oldDocRef The document reference before the event.
         * @return The stage that accepts the action.
         */
        public OldDocRefStage withOldDocRef(final DocRef oldDocRef) {
            builder.oldDocRef = Objects.requireNonNull(oldDocRef, "oldDocRef");
            return new OldDocRefStage(builder);
        }

        /**
         * Sets the action performed on the document.
         *
         * @param action The event action.
         * @return The stage that accepts optional event data and can fire the event.
         */
        public ActionStage withAction(final EntityAction action) {
            builder.action = Objects.requireNonNull(action, "action");
            return new ActionStage(builder);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * The builder stage after the old document reference has been supplied.
     */
    public static final class OldDocRefStage {

        private final FiringBuilder builder;

        private OldDocRefStage(final FiringBuilder builder) {
            this.builder = builder;
        }

        /**
         * Sets the action performed on the document.
         *
         * @param action The event action.
         * @return The stage that accepts optional event data and can fire the event.
         */
        public ActionStage withAction(final EntityAction action) {
            builder.action = Objects.requireNonNull(action, "action");
            return new ActionStage(builder);
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * The builder stage after the action has been supplied.
     */
    public static final class ActionStage {

        private final FiringBuilder builder;

        private ActionStage(final FiringBuilder builder) {
            this.builder = builder;
        }

        /**
         * Sets additional event data that will be serialised as JSON.
         *
         * @param entityEventData The additional event data.
         * @return This stage.
         */
        public ActionStage withData(final EntityEventData entityEventData) {
            builder.dataClassName = null;
            builder.data = null;
            builder.entityEventData = entityEventData;
            return this;
        }

        /**
         * Sets additional event data that has already been serialised as JSON.
         *
         * @param dataClassName The fully qualified class name of the data.
         * @param json          The JSON encoded data.
         * @return This stage.
         */
        public ActionStage withJsonData(final String dataClassName, final String json) {
            builder.dataClassName = dataClassName;
            builder.data = json;
            builder.entityEventData = null;
            return this;
        }

        /**
         * Sets additional event data that is a simple string, i.e. a single field value, e.g. a name.
         *
         * @param strValue The string value.
         * @return This stage.
         */
        public ActionStage withStringData(@Nullable final String strValue) {
            builder.dataClassName = String.class.getName();
            builder.data = strValue;
            builder.entityEventData = null;
            return this;
        }

        /**
         * Sets additional event data that is a simple long, i.e. a single field value, e.g. an ID.
         *
         * @param longValue The long value.
         * @return This stage.
         */
        public ActionStage withLongData(@Nullable final Long longValue) {
            builder.dataClassName = Long.class.getName();
            builder.data = longValue != null
                    ? longValue.toString()
                    : null;
            builder.entityEventData = null;
            return this;
        }

        /**
         * Sets additional event data that is a simple integer, i.e. a single field value, e.g. an ID.
         *
         * @param intValue The integer value.
         * @return This stage.
         */
        public ActionStage withIntData(@Nullable final Integer intValue) {
            builder.dataClassName = Integer.class.getName();
            builder.data = intValue != null
                    ? intValue.toString()
                    : null;
            builder.entityEventData = null;
            return this;
        }

        /**
         * Validates the builder and fires the event.
         */
        public void fire() {
            final DocRef docRef = Objects.requireNonNull(builder.docRef, "docRef");
            final EntityAction action = Objects.requireNonNull(builder.action, "action");
            if (builder.eventBus != null) {
                if (builder.entityEventData != null) {
                    builder.eventBus.fire(new EntityEvent(
                            docRef,
                            builder.oldDocRef,
                            action,
                            builder.entityEventData));
                } else {
                    builder.eventBus.fire(new EntityEvent(
                            docRef,
                            builder.oldDocRef,
                            action,
                            builder.dataClassName,
                            builder.data));
                }
            }
        }
    }
}
