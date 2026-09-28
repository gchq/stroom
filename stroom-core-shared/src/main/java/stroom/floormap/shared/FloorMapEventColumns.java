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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Which result column of the events query carries each {@link FloorMapEventRole}.
 *
 * <p>Stored on the document as a list rather than a map, matching {@code valueSchema} and keeping
 * the Events Query tab's control order stable; {@link #byRole()} gives callers the lookup they
 * actually want.</p>
 *
 * <p>A role absent from the list, or present with a blank column, is <b>unmapped</b> — which is
 * legitimate for every role but {@link FloorMapEventRole#ENTITY_ID}. An events store that only
 * ever carries fact keys has no {@link FloorMapEventRole#LOCATION} column, and saying so is better
 * than pointing the role at a column that does not exist.</p>
 */
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class FloorMapEventColumns {

    @JsonProperty
    private final List<Entry> entries;

    @JsonCreator
    public FloorMapEventColumns(@JsonProperty("entries") final List<Entry> entries) {
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
    }

    /**
     * The mapping the default events query implies.
     *
     * <p>Seeded from each role's own {@link FloorMapEventRole#getDefaultColumn()}, which is also
     * what the query builder emits after {@code as} — so a freshly created map, and a map whose
     * mapping was never set, both agree with the query text by construction. That pairing is
     * exactly what the {@code Event Type} defect broke, where the query said {@code Event Type}
     * and the parser looked for {@code type}.</p>
     *
     * @return a mapping naming every role's default column
     */
    public static FloorMapEventColumns defaults() {
        final List<Entry> list = new ArrayList<>();
        for (final FloorMapEventRole role : FloorMapEventRole.values()) {
            list.add(new Entry(role, role.getDefaultColumn()));
        }
        return new FloorMapEventColumns(list);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /**
     * The column named for {@code role}, or {@code null} if the role is unmapped.
     *
     * @param role the role to look up; may be {@code null}
     * @return the column name, or {@code null}
     */
    public String getColumn(final FloorMapEventRole role) {
        if (role == null) {
            return null;
        }
        for (final Entry entry : entries) {
            if (entry != null && role == entry.getRole()) {
                final String column = entry.getColumn();
                return NullSafe.isBlankString(column) ? null : column;
            }
        }
        return null;
    }

    /**
     * Every mapped role, for a caller that wants to iterate rather than ask role by role.
     *
     * @return role to column, omitting unmapped roles; never {@code null}
     */
    public Map<FloorMapEventRole, String> byRole() {
        final Map<FloorMapEventRole, String> map = new EnumMap<>(FloorMapEventRole.class);
        for (final FloorMapEventRole role : FloorMapEventRole.values()) {
            final String column = getColumn(role);
            if (column != null) {
                map.put(role, column);
            }
        }
        return map;
    }

    /**
     * A copy with {@code role} pointing at {@code column}, or unmapped when {@code column} is
     * blank.
     *
     * <p>Immutable rather than mutating, so the Events Query tab can build a candidate mapping
     * without disturbing the document until it is written.</p>
     *
     * @param role   the role to set; {@code null} returns this unchanged
     * @param column the column name, or {@code null}/blank to unmap the role
     * @return a new mapping
     */
    public FloorMapEventColumns with(final FloorMapEventRole role, final String column) {
        if (role == null) {
            return this;
        }
        final List<Entry> list = new ArrayList<>();
        boolean replaced = false;
        for (final Entry entry : entries) {
            if (entry != null && role == entry.getRole()) {
                list.add(new Entry(role, column));
                replaced = true;
            } else {
                list.add(entry);
            }
        }
        if (!replaced) {
            list.add(new Entry(role, column));
        }
        return new FloorMapEventColumns(list);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(entries, ((FloorMapEventColumns) o).entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }

    @Override
    public String toString() {
        return "FloorMapEventColumns" + entries;
    }

    /**
     * One role-to-column pairing.
     */
    @JsonInclude(Include.NON_NULL)
    @JsonPropertyOrder(alphabetic = true)
    public static class Entry {

        @JsonProperty
        private final FloorMapEventRole role;

        @JsonProperty
        private final String column;

        @JsonCreator
        public Entry(@JsonProperty("role") final FloorMapEventRole role,
                     @JsonProperty("column") final String column) {
            this.role = role;
            this.column = column;
        }

        public FloorMapEventRole getRole() {
            return role;
        }

        public String getColumn() {
            return column;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final Entry that = (Entry) o;
            return role == that.role && Objects.equals(column, that.column);
        }

        @Override
        public int hashCode() {
            return Objects.hash(role, column);
        }

        @Override
        public String toString() {
            return role + "=" + column;
        }
    }
}
