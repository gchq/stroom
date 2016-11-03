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

package stroom.explorer.client.presenter;

import stroom.explorer.shared.ExplorerTreeFilter;

import java.util.HashSet;
import java.util.Set;

public class ExplorerTreeFilterBuilder {
    private Set<String> includedTypes;
    private Set<String> tags;
    private Set<String> requiredPermissions;
    private String nameFilter;

    public void setIncludedTypeSet(final Set<String> types) {
        if (types == null) {
            includedTypes = null;
        } else {
            includedTypes = new HashSet<String>(types);
        }
    }

    /**
     * Convenience method.
     *
     * @param types
     */
    public void setIncludedTypes(final String... types) {
        this.includedTypes = SetUtil.toSet(types);
    }

    public void setTags(final String... tags) {
        this.tags = SetUtil.toSet(tags);
    }

    public void setRequiredPermissions(final String... requiredPermissions) {
        this.requiredPermissions = SetUtil.toSet(requiredPermissions);
    }

    /**
     * This sets the name filter to be used when fetching items. This method
     * returns false is the filter is set to the same value that is already set.
     *
     * @param nameFilter
     * @return
     */
    public boolean setNameFilter(final String nameFilter) {
        String filter = nameFilter;
        if (filter != null) {
            filter = filter.trim();
            if (filter.length() == 0) {
                filter = null;
            }
        }

        if ((filter == null && this.nameFilter == null) || (filter != null && filter.equals(this.nameFilter))) {
            return false;
        }

        this.nameFilter = filter;

        return true;
    }

    public ExplorerTreeFilter build() {
        return new ExplorerTreeFilter(SetUtil.copySet(includedTypes), SetUtil.copySet(tags), SetUtil.copySet(requiredPermissions), nameFilter);
    }
}
