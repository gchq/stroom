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

package stroom.dataconnection.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

/// The settings and data of a CSV [DataConnectionDoc].
///
/// The data is held in the document. It is stored apart from the document's JSON, in its own `csv`
/// part, so it is kept and exported as plain CSV.
@JsonPropertyOrder({
        "delimiter",
        "header",
        "columns",
        "data"})
@JsonInclude(Include.NON_NULL)
public class CsvSettings implements StoredDataSettings {

    /// The delimiter used when none is set.
    public static final String DEFAULT_DELIMITER = ",";

    @JsonProperty
    private final String delimiter;
    @JsonProperty
    private final Boolean header;
    @JsonProperty
    private final List<DataColumn> columns;
    @JsonProperty
    private final String data;

    /// Creates CSV settings.
    ///
    /// @param delimiter The character between values, or `null` for [#DEFAULT_DELIMITER].
    /// @param header    True if the first row holds the column names, `null` meaning true.
    /// @param columns   The columns, in the order they appear in each row, or `null` for none.
    /// @param data      The CSV, or `null` for none.
    @JsonCreator
    public CsvSettings(@JsonProperty("delimiter") final String delimiter,
                       @JsonProperty("header") final Boolean header,
                       @JsonProperty("columns") final List<DataColumn> columns,
                       @JsonProperty("data") final String data) {
        this.delimiter = delimiter;
        this.header = header;
        this.columns = columns;
        this.data = data;
    }

    /// @return The character between values, or `null` meaning [#DEFAULT_DELIMITER].
    public String getDelimiter() {
        return delimiter;
    }

    @Override
    public Boolean getHeader() {
        return header;
    }

    @Override
    public List<DataColumn> getColumns() {
        return columns;
    }

    /// @return The CSV, or `null` for none.
    public String getData() {
        return data;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof final CsvSettings that)) {
            return false;
        }
        return Objects.equals(delimiter, that.delimiter) &&
               Objects.equals(header, that.header) &&
               Objects.equals(columns, that.columns) &&
               Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(delimiter, header, columns, data);
    }

    // The data may be large, so only its length is shown.
    @Override
    public String toString() {
        return "CsvSettings{" +
               "delimiter='" + delimiter + '\'' +
               ", header=" + header +
               ", columns=" + columns +
               ", dataLength=" + (data == null ? 0 : data.length()) +
               '}';
    }

    /// @return A builder initialised with these settings.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return A new builder with no settings.
    public static Builder builder() {
        return new Builder();
    }

    /// Builds [CsvSettings] instances.
    public static final class Builder {

        private String delimiter;
        private Boolean header;
        private List<DataColumn> columns;
        private String data;

        private Builder() {
        }

        private Builder(final CsvSettings settings) {
            this.delimiter = settings.delimiter;
            this.header = settings.header;
            this.columns = settings.columns;
            this.data = settings.data;
        }

        /// @param delimiter The character between values, or `null` for [#DEFAULT_DELIMITER].
        /// @return This builder.
        public Builder delimiter(final String delimiter) {
            this.delimiter = delimiter;
            return this;
        }

        /// @param header True if the first row holds the column names.
        /// @return This builder.
        public Builder header(final Boolean header) {
            this.header = header;
            return this;
        }

        /// @param columns The columns, in the order they appear in each row.
        /// @return This builder.
        public Builder columns(final List<DataColumn> columns) {
            this.columns = columns;
            return this;
        }

        /// @param data The CSV.
        /// @return This builder.
        public Builder data(final String data) {
            this.data = data;
            return this;
        }

        /// @return New settings holding the values set on this builder.
        public CsvSettings build() {
            return new CsvSettings(delimiter, header, columns, data);
        }
    }
}
