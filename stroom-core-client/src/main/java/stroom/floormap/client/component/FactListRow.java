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

package stroom.floormap.client.component;

import stroom.floormap.client.value.ValuePathAccessor;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.util.client.JSONUtil;

import com.google.gwt.json.client.JSONObject;

import java.util.List;

/// Represents a single object (fact) entry shown in the list.
/// Identified by its temporal-store key; carries display name, type, and whether the fact has
/// been deleted (only listed when showing all facts).
@SuppressWarnings("ClassCanBeRecord")
public class FactListRow {

    private final String key;
    private final String name;
    private final String type;
    private final boolean deleted;

    /// Creates a fact object that isn't deleted.
    ///
    /// @param key  the fact's temporal-store key
    /// @param name the display name
    /// @param type the fact type
    public FactListRow(final String key, final String name, final String type) {
        this(key, name, type, false);
    }

    /// Creates a fact object, marked deleted or not.
    ///
    /// @param key     the fact's temporal-store key
    /// @param name    the display name
    /// @param type    the fact type
    /// @param deleted whether the fact's current version hides it
    public FactListRow(final String key, final String name, final String type, final boolean deleted) {
        this.key = key;
        this.name = name;
        this.type = type;
        this.deleted = deleted;
    }

    /// Returns a copy of this object marked as deleted.
    ///
    /// @return the marked copy
    public FactListRow asDeleted() {
        return new FactListRow(key, name, type, true);
    }

    /// Creates a [FactListRow] from a [stroom.util.shared.TemporalEntry]
    /// by parsing the JSON value for `name` and `type`.
    ///
    /// @param entry  the temporal entry; must not be `null`
    /// @param schema the value schema used to resolve field paths
    /// @return a new fact object; never `null`
    public static FactListRow fromEntry(final stroom.util.shared.TemporalEntry entry,
                                       final List<FloorMapFieldMapping> schema) {
        String name = entry.getKey();
        String type = "";
        try {
            if (entry.getValue() != null && entry.getValue().trim().startsWith("{")) {
                final JSONObject json = ValuePathAccessor.parse(entry.getValue());
                if (json != null) {
                    final String parsedName = JSONUtil.getString(
                            ValuePathAccessor.get(json,
                                    FloorMapFieldMapping.findPath(schema, Role.LABEL)));
                    final String parsedType = JSONUtil.getString(
                            ValuePathAccessor.get(json,
                                    FloorMapFieldMapping.findPath(schema, Role.TYPE)));
                    if (parsedName != null && !parsedName.isEmpty()) {
                        name = parsedName;
                    }
                    if (parsedType != null) {
                        type = parsedType;
                    }
                }
            }
        } catch (final Exception ex) {
            // Use key as display name
        }
        return new FactListRow(entry.getKey(), name, type);
    }

    /// @return the fact's temporal-store key
    public String getKey() {
        return key;
    }

    /// @return the display name
    public String getName() {
        return name;
    }

    /// @return the fact type
    public String getType() {
        return type;
    }

    /// Whether the fact's current version hides it.
    ///
    /// @return `true` if the fact is deleted
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FactListRow that = (FactListRow) o;
        return java.util.Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(key);
    }
}
