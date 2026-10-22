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

package stroom.dashboard.client.vis;

import stroom.query.api.Format;

import java.util.Objects;

/// A column of a visualisation's table that its field controls can choose: its name and the type
/// of its format (null for a column without one, whose type isn't known until its values are seen).
public final class VisFieldOption {

    private final String name;
    private final Format.Type type;

    /// @param name The column's name.
    /// @param type The type of the column's format, or null.
    public VisFieldOption(final String name, final Format.Type type) {
        this.name = name;
        this.type = type;
    }

    /// @return The column's name.
    public String getName() {
        return name;
    }

    /// @return The type of the column's format, or null.
    public Format.Type getType() {
        return type;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final VisFieldOption that = (VisFieldOption) o;
        return Objects.equals(name, that.name) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type);
    }

    @Override
    public String toString() {
        return name + " (" + type + ")";
    }
}
