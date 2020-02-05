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

import java.util.Objects;
import java.util.Set;

public class ExplorerTreeFilter {
    private Set<String> includedTypes;
    private Set<String> tags;
    private Set<String> requiredPermissions;
    private String nameFilter;
    private boolean nameFilterChange;

    public ExplorerTreeFilter() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerTreeFilter(final Set<String> includedTypes, final Set<String> tags, final Set<String> requiredPermissions, final String nameFilter, final boolean nameFilterChange) {
        this.includedTypes = includedTypes;
        this.tags = tags;
        this.requiredPermissions = requiredPermissions;
        this.nameFilter = nameFilter;
        this.nameFilterChange = nameFilterChange;
    }

    public Set<String> getIncludedTypes() {
        return includedTypes;
    }

    public void setIncludedTypes(final Set<String> includedTypes) {
        this.includedTypes = includedTypes;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(final Set<String> tags) {
        this.tags = tags;
    }

    public Set<String> getRequiredPermissions() {
        return requiredPermissions;
    }

    public void setRequiredPermissions(final Set<String> requiredPermissions) {
        this.requiredPermissions = requiredPermissions;
    }

    public String getNameFilter() {
        return nameFilter;
    }

    public void setNameFilter(final String nameFilter) {
        this.nameFilter = nameFilter;
    }

    public boolean isNameFilterChange() {
        return nameFilterChange;
    }

    public void setNameFilterChange(final boolean nameFilterChange) {
        this.nameFilterChange = nameFilterChange;
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
