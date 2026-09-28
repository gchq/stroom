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

package stroom.floormap.shared;

import stroom.docref.DocRef;
import stroom.docstore.shared.DocumentType;
import stroom.docstore.shared.DocumentTypeRegistry;
import stroom.planb.shared.AbstractPlanBDoc;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.KeyType;
import stroom.planb.shared.StateType;
import stroom.planb.shared.StateValueSchema;
import stroom.planb.shared.StateValueType;
import stroom.planb.shared.TemporalPrecision;
import stroom.planb.shared.TemporalStateKeySchema;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * A Plan B temporal state store configured for, and read by, the Floor Map.
 *
 * <p><b>Why a document type of its own rather than a Plan B store.</b> The Map tab's read seeks to
 * each entity's answer instead of scanning, and that is only correct where no key's encoded bytes
 * can be a prefix of another's — see {@code TemporalStateDb.searchSnapshot}. On a general-purpose
 * Plan B document the key encoding is a dropdown, so the correctness of the read would depend on a
 * field anyone can edit. Here it is a property of the type: {@link #KEY_SCHEMA} is applied by the
 * constructor, so there is no dropdown to get wrong, nothing an import or a REST call can set, and
 * no runtime guard to write. A store that would need a scan cannot be expressed.</p>
 *
 * <p>The same reasoning fixes the temporal precision and the value schema. Coarser precision makes
 * two events for one entity within a tick share a key, so the later silently overwrites the
 * earlier — a data-loss knob wearing a performance hat, which {@code condense} already covers
 * honestly. The value schema is worth roughly five times the storage when values repeat, because
 * {@code VARIABLE} deduplicates them through a lookup table.</p>
 *
 * <p><b>What is left configurable</b> is what a user can safely change after data exists: how long
 * an entity stays on the map ({@link #getEventExpiry()}), how long data is kept, how large the store
 * may grow, and whether to condense. A Plan B key or value schema is immutable once data is written;
 * none of these are.</p>
 *
 * <p><b>This is still a Plan B store.</b> {@code stateType} is always {@code TEMPORAL_STATE}, so
 * ingest, merging, condensing, retention and shard deletion are Plan B's and behave exactly as they
 * do for any other temporal state store. The type is registered through
 * {@code PlanBDocumentTypes} so that {@code PlanBDocCache} resolves it by map name like any other,
 * which is how a pipeline writes to it. Only the <em>read</em> is ours.</p>
 */
@JsonPropertyOrder({
        "type",
        "uuid",
        "name",
        "version",
        "createTimeMs",
        "updateTimeMs",
        "createUser",
        "updateUser",
        "description",
        "stateType",
        "settings",
        "eventExpiry"
})
@JsonInclude(Include.NON_NULL)
public class FloorMapEventStoreDoc extends AbstractPlanBDoc {

    public static final String TYPE = "FloorMapEventStore";
    public static final DocumentType DOCUMENT_TYPE =
            DocumentTypeRegistry.FLOOR_MAP_EVENT_STORE_DOCUMENT_TYPE;

    /**
     * The key encoding, fixed so that the Map tab's per-key seek is always valid.
     *
     * <p>{@link KeyType#TERMINATED_STRING} terminates every key with a byte no key may contain, so
     * no key's bytes can extend another's. Without that, {@code door10} is silently skipped because
     * {@code door1} prefixes it.</p>
     */
    public static final TemporalStateKeySchema KEY_SCHEMA = new TemporalStateKeySchema(
            KeyType.TERMINATED_STRING,
            null,
            TemporalPrecision.MILLISECOND);

    /** Values repeat heavily for a stationary entity, and {@code VARIABLE} deduplicates them. */
    public static final StateValueSchema VALUE_SCHEMA = new StateValueSchema(
            StateValueType.VARIABLE,
            null);

    /**
     * How long an entity stays on the map after its last event, where the document sets none.
     *
     * <p>Absent means this default rather than "off". Events lasting forever is the behaviour this
     * store exists to remove, so there is deliberately no way to disable expiry.</p>
     */
    public static final SimpleDuration DEFAULT_EVENT_EXPIRY = FloorMapEventExpiry.DEFAULT;

    @JsonProperty
    private final SimpleDuration eventExpiry;

    /**
     * Creates a store, fixing the parts of a Plan B document that this type does not leave open.
     *
     * <p>{@code stateType} is accepted and then discarded: the call to {@code super} always passes
     * {@link StateType#TEMPORAL_STATE}. The parameter is here because the property cannot simply be
     * left out.</p>
     *
     * <p>It is part of the serialised shape. {@code stateType} is a Jackson property of
     * {@link AbstractPlanBDoc}, so every document written carries it, and this constructor is the
     * {@code @JsonCreator}, so reading one back needs somewhere for that property to land. An
     * unbound property is tolerated by the doc store's mapper, which disables
     * {@code FAIL_ON_UNKNOWN_PROPERTIES}, but is fatal over REST, where Stroom deliberately enables
     * it.</p>
     *
     * <p>Naming it here is also what keeps the value fixed. Were it not a creator property, Jackson
     * would bind it to the inherited field instead — final fields are settable by default — and a
     * hand-edited export or a REST call claiming {@link StateType#SESSION} would take effect.
     * Taking the argument and dropping it is what makes the state type unforgeable, in the same way
     * that {@link #withFixedSchemas(AbstractPlanBSettings)} overwrites rather than validates the
     * schemas. The inherited {@code stateType} builder method is ignored for the same reason.</p>
     *
     * <p>The sibling {@code TracesDoc} takes the same argument and honours it where non-null, so
     * the unconditional discard here is a deliberate difference rather than a copy of that
     * class.</p>
     *
     * @param stateType   ignored; present only so the serialised property has somewhere to land
     * @param settings    the Plan B settings, whose key and value schemas are replaced with this
     *                    type's own before use
     * @param eventExpiry how long an entity stays on the map after its last event, or {@code null}
     *                    to use {@link #DEFAULT_EVENT_EXPIRY}
     */
    @JsonCreator
    public FloorMapEventStoreDoc(
            @JsonProperty("uuid") final String uuid,
            @JsonProperty("name") final String name,
            @JsonProperty("version") final String version,
            @JsonProperty("createTimeMs") final Long createTimeMs,
            @JsonProperty("updateTimeMs") final Long updateTimeMs,
            @JsonProperty("createUser") final String createUser,
            @JsonProperty("updateUser") final String updateUser,
            @JsonProperty("description") final String description,
            @JsonProperty("stateType") final StateType stateType,
            @JsonProperty("settings") final AbstractPlanBSettings settings,
            @JsonProperty("eventExpiry") final SimpleDuration eventExpiry) {
        super(TYPE, uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser,
                description, StateType.TEMPORAL_STATE, withFixedSchemas(settings));
        this.eventExpiry = eventExpiry;
    }

    /**
     * The given settings with this type's schemas applied, whatever arrived.
     *
     * <p>Deliberately overwriting rather than validating. The schemas are not a user's choice, so a
     * document that carries different ones is not an error to report but a value to ignore — and
     * ignoring it here means a hand-edited export, a REST call or an older document all converge on
     * a store the read can serve. A mismatch against data already written is caught later by Plan
     * B's own schema check at merge, which is where it belongs.</p>
     */
    private static TemporalStateSettings withFixedSchemas(final AbstractPlanBSettings settings) {
        final TemporalStateSettings.Builder builder =
                settings instanceof final TemporalStateSettings temporalStateSettings
                        ? new TemporalStateSettings.Builder(temporalStateSettings)
                        : new TemporalStateSettings.Builder();
        return builder
                .keySchema(KEY_SCHEMA)
                .valueSchema(VALUE_SCHEMA)
                .build();
    }

    /**
     * How long an entity stays on the map after its last event.
     *
     * <p>Returned raw, so a caller can tell "unset" from "set to twenty-four hours" — equal in
     * effect but not in meaning, and the editor needs the difference. Use
     * {@link #getEventExpiryOrDefault()} to resolve it for a read.</p>
     *
     * @return the configured duration, or {@code null} where the document sets none
     */
    public SimpleDuration getEventExpiry() {
        return eventExpiry;
    }

    /**
     * The configured expiry, or {@link #DEFAULT_EVENT_EXPIRY} where none is set.
     *
     * <p>{@link JsonIgnore} because it is derived from {@link #eventExpiry} and has no field of its
     * own. Without it Jackson writes an {@code eventExpiryOrDefault} property into every exported
     * document - a value nothing reads back, which also erases the distinction between unset and
     * set-to-the-default that {@link #getEventExpiry()} exists to preserve.</p>
     *
     * @return the configured duration, or the default where the document sets none
     */
    @JsonIgnore
    public SimpleDuration getEventExpiryOrDefault() {
        return eventExpiry == null
                ? DEFAULT_EVENT_EXPIRY
                : eventExpiry;
    }

    public static DocRef getDocRef(final String uuid) {
        return DocRef.builder(TYPE)
                .uuid(uuid)
                .build();
    }

    public static DocRef.TypedBuilder buildDocRef() {
        return DocRef.builder(TYPE);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final FloorMapEventStoreDoc that = (FloorMapEventStoreDoc) o;
        return Objects.equals(eventExpiry, that.eventExpiry);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), eventExpiry);
    }

    @Override
    public String toString() {
        return "FloorMapEventStoreDoc{" +
               "name='" + getName() + '\'' +
               ", stateType=" + getStateType() +
               ", settings=" + getSettings() +
               ", eventExpiry=" + eventExpiry +
               '}';
    }

    public Builder copyEventStore() {
        return new Builder(this);
    }

    public static Builder eventStoreBuilder() {
        return new Builder();
    }

    public static class Builder
            extends AbstractPlanBDoc.AbstractBuilder<FloorMapEventStoreDoc, Builder> {

        private SimpleDuration eventExpiry;

        public Builder() {
        }

        public Builder(final FloorMapEventStoreDoc doc) {
            super(doc);
            this.eventExpiry = doc.eventExpiry;
        }

        public Builder eventExpiry(final SimpleDuration eventExpiry) {
            this.eventExpiry = eventExpiry;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FloorMapEventStoreDoc build() {
            return new FloorMapEventStoreDoc(
                    uuid,
                    name,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    description,
                    stateType,
                    settings,
                    eventExpiry);
        }
    }
}
