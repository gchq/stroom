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

package stroom.dataconnection.client.presenter;

import stroom.dataconnection.shared.CsvSettings;
import stroom.docref.HasDisplayValue;

/// The delimiters offered for a CSV Data Connection.
public enum CsvDelimiter implements HasDisplayValue {
    /// A comma, the default.
    COMMA(",", "Comma"),
    /// A semicolon, common where the comma is the decimal separator.
    SEMICOLON(";", "Semicolon"),
    /// A tab.
    TAB("\t", "Tab"),
    /// A vertical bar.
    PIPE("|", "Pipe");

    private final String value;
    private final String displayValue;

    CsvDelimiter(final String value, final String displayValue) {
        this.value = value;
        this.displayValue = displayValue;
    }

    /// @return The delimiter character, as stored.
    public String getValue() {
        return value;
    }

    /// Gets the name of the delimiter as shown to users.
    ///
    /// @return The display name.
    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /// @param value A stored delimiter, or `null` for the default.
    /// @return The delimiter it is. One not offered here is shown as a comma.
    public static CsvDelimiter fromValue(final String value) {
        final String stored = value == null
                ? CsvSettings.DEFAULT_DELIMITER
                : value;
        for (final CsvDelimiter delimiter : values()) {
            if (delimiter.value.equals(stored)) {
                return delimiter;
            }
        }
        return COMMA;
    }
}
