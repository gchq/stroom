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

import stroom.util.shared.EqualsBuilder;
import stroom.util.shared.HashCodeBuilder;
import stroom.util.shared.SharedObject;

import java.util.Set;

public class ExplorerTreeFilter implements SharedObject {
    private static final long serialVersionUID = 6474393620178001033L;

    private Set<String> includedTypes;
    private Set<String> tags;
    private Set<String> requiredPermissions;
    private String nameFilter;

    public ExplorerTreeFilter() {
        // Default constructor necessary for GWT serialisation.
    }

    public ExplorerTreeFilter(final Set<String> includedTypes, final Set<String> tags, final Set<String> requiredPermissions, final String nameFilter) {
        this.includedTypes = includedTypes;
        this.tags = tags;
        this.requiredPermissions = requiredPermissions;
        this.nameFilter = nameFilter;
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

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        } else if (!(o instanceof ExplorerTreeFilter)) {
            return false;
        }

        final ExplorerTreeFilter filter = (ExplorerTreeFilter) o;
        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(includedTypes, filter.includedTypes);
        builder.append(tags, filter.tags);
        builder.append(requiredPermissions, filter.requiredPermissions);
        builder.append(nameFilter, filter.nameFilter);
        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        final HashCodeBuilder builder = new HashCodeBuilder();
        builder.append(includedTypes);
        builder.append(tags);
        builder.append(requiredPermissions);
        builder.append(nameFilter);
        return builder.toHashCode();
    }
}
