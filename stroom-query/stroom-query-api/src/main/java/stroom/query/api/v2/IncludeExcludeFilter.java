/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@JsonPropertyOrder({"includes", "excludes"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A pair of regular expression filters (inclusion and exclusion) to apply to the field.  " +
                      "Either or both can be supplied")
public final class IncludeExcludeFilter {

    @Schema(description = "Only results matching this filter will be included",
            example = "^[0-9]{3}$")
    @JsonProperty
    private final String includes;

    @Schema(description = "Only results NOT matching this filter will be included",
            example = "^[0-9]{3}$")
    @JsonProperty
    private final String excludes;

    @JsonCreator
    public IncludeExcludeFilter(@JsonProperty("includes") final String includes,
                                @JsonProperty("excludes") final String excludes) {
        this.includes = includes;
        this.excludes = excludes;
    }

    public String getIncludes() {
        return includes;
    }

    public String getExcludes() {
        return excludes;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final IncludeExcludeFilter filter = (IncludeExcludeFilter) o;
        return Objects.equals(includes, filter.includes) &&
               Objects.equals(excludes, filter.excludes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includes, excludes);
    }

    @Override
    public String toString() {
        return "Filter{" +
               "includes='" + includes + '\'' +
               ", excludes='" + excludes + '\'' +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for constructing a {@link IncludeExcludeFilter}
     */
    public static final class Builder {

        private String includes;
        private String excludes;

        private Builder() {
        }

        private Builder(final IncludeExcludeFilter filter) {
            this.includes = filter.includes;
            this.excludes = filter.excludes;
        }

        /**
         * Set the inclusion regex
         *
         * @param value Only results matching this filter will be included
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder includes(final String value) {
            this.includes = value;
            return this;
        }

        /**
         * Set the exclusion regex
         *
         * @param value Only results NOT matching this filter will be included
         * @return The {@link Builder}, enabling method chaining
         */
        public Builder excludes(final String value) {
            this.excludes = value;
            return this;
        }

        public IncludeExcludeFilter build() {
            return new IncludeExcludeFilter(includes, excludes);
        }
    }
}
