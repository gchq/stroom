/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.util.shared.ToStringBuilder;

import java.util.Objects;

@JsonPropertyOrder({"includes", "excludes"})
@JsonInclude(Include.NON_NULL)
public class Filter {
    @JsonProperty
    private final String includes;
    @JsonProperty
    private final String excludes;

    @JsonCreator
    public Filter(@JsonProperty("includes") final String includes,
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Filter filter = (Filter) o;
        return Objects.equals(includes, filter.includes) &&
                Objects.equals(excludes, filter.excludes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includes, excludes);
    }

    @Override
    public String toString() {
        final ToStringBuilder builder = new ToStringBuilder();
        builder.append("includes", includes);
        builder.append("excludes", excludes);
        return builder.toString();
    }

    public Filter copy() {
        return new Filter(includes, excludes);
    }
}
