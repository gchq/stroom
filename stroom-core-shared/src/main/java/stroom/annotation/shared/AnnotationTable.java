/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.annotation.shared;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public final class AnnotationTable implements EntryValue {

    @JsonProperty
    private final List<String> columns;
    @JsonProperty
    private final List<List<String>> values;

    @JsonCreator
    public AnnotationTable(@JsonProperty("columns") final List<String> columns,
                           @JsonProperty("values") final List<List<String>> values) {
        this.columns = columns;
        this.values = values;
    }

    public List<String> getColumns() {
        return columns;
    }

    public List<List<String>> getValues() {
        return values;
    }

    @Override
    public String asUiValue() {
        return toString();
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        append(sb);
        return sb.toString();
    }

    public void append(final StringBuilder sb) {
        boolean first = true;
        for (final String col : NullSafe.list(columns)) {
            if (!first) {
                sb.append(",");
            }
            quote(sb, col);
            first = false;
        }
        for (final List<String> row : NullSafe.list(values)) {
            sb.append("\n");
            first = true;
            for (final String val : NullSafe.list(row)) {
                if (!first) {
                    sb.append(",");
                }
                quote(sb, val);
                first = false;
            }
        }
    }

    private void quote(final StringBuilder text, final String value) {
        text.append("'");
        if (value != null) {
            text.append(value);
        }
        text.append("'");
    }
}
