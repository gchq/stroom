/*
 * Copyright 2026 Crown Copyright
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

package stroom.sqlstore.shared;

import stroom.util.shared.TemporalEntry;
import stroom.util.shared.TemporalEntryId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * A single staged change to be applied by
 * {@link SqlTemporalStoreResource#applyChanges(ApplyChangesRequest)}.
 *
 * <p>The {@link Type} discriminator determines which field is populated:</p>
 * <ul>
 *   <li>{@link Type#UPSERT} — {@link #entry} is non-null; {@link #id} is null.
 *       The server performs an insert-or-update on the entry's natural key
 *       {@code (map, key, effectiveTimeMs)}.</li>
 *   <li>{@link Type#DELETE} — {@link #id} is non-null; {@link #entry} is null.
 *       The server deletes the entry identified by the natural key.</li>
 * </ul>
 *
 * <p>Use the static factory methods {@link #upsert(TemporalEntry)} and
 * {@link #delete(TemporalEntryId)} rather than the JSON constructor directly.</p>
 *
 * <h3>Operation ordering</h3>
 * <p>Operations are applied strictly in list order within a single database
 * transaction. This means a DELETE followed by a CREATE for the same
 * {@code (map, key, effectiveTimeMs)} triple is valid and produces the expected
 * result — the original entry is removed and the new entry is inserted.</p>
 */
@JsonInclude(Include.NON_NULL)
public class ChangeOperation {

    /**
     * The type of operation to perform.
     */
    public enum Type {
        /**
         * Insert or update the entry identified by its natural key
         * {@code (map, key, effectiveTimeMs)}.
         */
        UPSERT,
        /**
         * Delete the entry identified by the natural key in {@link ChangeOperation#getId()}.
         */
        DELETE
    }

    /** The operation type; never {@code null}. */
    @JsonProperty
    private final Type type;

    /**
     * The entry to upsert; non-null when {@link #type} is {@link Type#UPSERT},
     * {@code null} otherwise.
     */
    @JsonProperty
    private final TemporalEntry entry;

    /**
     * The natural key of the entry to delete; non-null when {@link #type} is
     * {@link Type#DELETE}, {@code null} otherwise.
     */
    @JsonProperty
    private final TemporalEntryId id;

    @JsonCreator
    public ChangeOperation(
            @JsonProperty("type") final Type type,
            @JsonProperty("entry") final TemporalEntry entry,
            @JsonProperty("id") final TemporalEntryId id) {
        this.type = type;
        this.entry = entry;
        this.id = id;
    }

    /**
     * Creates an UPSERT operation for the given entry.
     *
     * @param entry the entry to insert or update; must not be {@code null}
     * @return a new {@link ChangeOperation} with {@link Type#UPSERT}
     */
    public static ChangeOperation upsert(final TemporalEntry entry) {
        return new ChangeOperation(Type.UPSERT, entry, null);
    }

    /**
     * Creates a DELETE operation for the entry identified by the given id.
     *
     * @param id the natural key of the entry to delete; must not be {@code null}
     * @return a new {@link ChangeOperation} with {@link Type#DELETE}
     */
    public static ChangeOperation delete(final TemporalEntryId id) {
        return new ChangeOperation(Type.DELETE, null, id);
    }

    /**
     * Returns the operation type.
     *
     * @return the type; never {@code null}
     */
    public Type getType() {
        return type;
    }

    /**
     * Returns the entry to upsert.
     *
     * @return the entry; non-null when {@link #getType()} is {@link Type#UPSERT},
     *         {@code null} otherwise
     */
    public TemporalEntry getEntry() {
        return entry;
    }

    /**
     * Returns the natural key of the entry to delete.
     *
     * @return the id; non-null when {@link #getType()} is {@link Type#DELETE},
     *         {@code null} otherwise
     */
    public TemporalEntryId getId() {
        return id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ChangeOperation that = (ChangeOperation) o;
        return type == that.type
                && Objects.equals(entry, that.entry)
                && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, entry, id);
    }

    @Override
    public String toString() {
        return "ChangeOperation{type=" + type
                + (entry != null ? ", entry=" + entry : "")
                + (id != null ? ", id=" + id : "")
                + '}';
    }
}
