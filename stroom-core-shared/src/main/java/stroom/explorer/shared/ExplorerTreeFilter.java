/*
 * Copyright 2016 Crown Copyright
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

package stroom.explorer.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class ExplorerTreeFilter {
    @JsonProperty
    private final Set<String> includedTypes;
    @JsonProperty
    private final Set<String> tags;
    @JsonProperty
    private final Set<String> requiredPermissions;
    @JsonProperty
    private final String nameFilter;
    @JsonProperty
    private final boolean nameFilterChange;

    @JsonCreator
    public ExplorerTreeFilter(@JsonProperty("includedTypes") final Set<String> includedTypes,
                              @JsonProperty("tags") final Set<String> tags,
                              @JsonProperty("requiredPermissions") final Set<String> requiredPermissions,
                              @JsonProperty("nameFilter") final String nameFilter,
                              @JsonProperty("nameFilterChange") final boolean nameFilterChange) {
        this.includedTypes = includedTypes;
        this.tags = tags;
        this.requiredPermissions = requiredPermissions;
        this.nameFilter = nameFilter;
        this.nameFilterChange = nameFilterChange;
    }

    public Set<String> getIncludedTypes() {
        return includedTypes;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Set<String> getRequiredPermissions() {
        return requiredPermissions;
    }

    public String getNameFilter() {
        return nameFilter;
    }

    public boolean isNameFilterChange() {
        return nameFilterChange;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ExplorerTreeFilter that = (ExplorerTreeFilter) o;
        return nameFilterChange == that.nameFilterChange &&
                Objects.equals(includedTypes, that.includedTypes) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(requiredPermissions, that.requiredPermissions) &&
                Objects.equals(nameFilter, that.nameFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(includedTypes, tags, requiredPermissions, nameFilter, nameFilterChange);
    }
}
