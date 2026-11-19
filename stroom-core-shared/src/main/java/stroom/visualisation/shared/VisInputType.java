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

package stroom.visualisation.shared;

import stroom.query.api.Format;

/// A type of value a visualisation's input takes (see [VisInput]), as the visualisation is given
/// values: text, numbers or dates (milliseconds since the epoch).
public enum VisInputType {
    TEXT(Format.Type.TEXT),
    NUMBER(Format.Type.NUMBER),
    DATE_TIME(Format.Type.DATE_TIME);

    private final Format.Type formatType;

    VisInputType(final Format.Type formatType) {
        this.formatType = formatType;
    }

    /// @return The format a column's values are converted to for an input of this type.
    public Format.Type getFormatType() {
        return formatType;
    }

    /// Whether a column of a type can feed an input of this type. Any column can be read as text.
    /// A general column's type isn't known until its values are seen, so it may feed any input.
    ///
    /// @param columnType The type of the column's format, null for a column without one (general).
    /// @return True if the column can feed the input.
    public boolean accepts(final Format.Type columnType) {
        if (columnType == null || columnType == Format.Type.GENERAL || this == TEXT) {
            return true;
        }
        return columnType == formatType;
    }

    /// @param name A type's name as a visualisation's settings give it, e.g. `NUMBER`.
    /// @return The type, or null if the name isn't one.
    public static VisInputType parse(final String name) {
        if (name != null) {
            for (final VisInputType type : values()) {
                if (type.name().equalsIgnoreCase(name.trim())) {
                    return type;
                }
            }
        }
        return null;
    }
}
