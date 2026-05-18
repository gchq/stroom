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

package stroom.query.api;

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ColumnFilter {

    @JsonProperty
    private final String filter;
    @JsonProperty
    private final boolean enabled;

    @JsonCreator
    public ColumnFilter(@JsonProperty("filter") final String filter,
                        @JsonProperty("enabled") final boolean enabled) {
        if (NullSafe.isBlankString(filter)) {
            this.filter = null;
        } else {
            this.filter = filter;
        }
        this.enabled = enabled;
    }

    public String getFilter() {
        return filter;
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ColumnFilter that = (ColumnFilter) o;
        return enabled == that.enabled &&
               Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(filter, enabled);
    }

    @Override
    public String toString() {
        return "ColumnFilter{" +
               "filter='" + filter + '\'' +
               ", enabled=" + enabled +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder fromColumn(final Column column) {
        if (column != null && column.getColumnFilter() != null) {
            return column.getColumnFilter().copy();
        }
        return builder();
    }

    /**
     * Builder for constructing a {@link Column}
     */
    public static final class Builder {

        private String filter = "";
        private boolean enabled = true;

        /**
         * No args constructor, allow all building using chained methods
         */
        private Builder() {
        }

        private Builder(final ColumnFilter columnFilter) {
            this.filter = columnFilter.filter;
            this.enabled = columnFilter.enabled;
        }

        public Builder filter(final String filter) {
            this.filter = filter;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ColumnFilter build() {
            return new ColumnFilter(filter, enabled);
        }
    }
}
