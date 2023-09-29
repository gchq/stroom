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

import stroom.util.shared.GwtNullSafe;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@JsonInclude(Include.NON_NULL)
public class ExplorerTreeFilter {

    public static FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField("Name");
    public static FilterFieldDefinition FIELD_DEF_TYPE = FilterFieldDefinition.qualifiedField("Type");
    public static FilterFieldDefinition FIELD_DEF_UUID = FilterFieldDefinition.qualifiedField("UUID");
    public static FilterFieldDefinition FIELD_DEF_TAG = FilterFieldDefinition.qualifiedField("Tag");

    public static List<FilterFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NAME, FIELD_DEF_TYPE, FIELD_DEF_UUID, FIELD_DEF_TAG);

    @JsonProperty
    private final Set<String> includedTypes;
    @JsonProperty
    private final Set<String> includedRootTypes;
    @JsonProperty
    private final Set<String> tags;
    @JsonProperty
    private final Set<NodeFlag> nodeFlags;
    @JsonProperty
    private final Set<String> requiredPermissions;
    @JsonProperty
    private final String nameFilter;
    @JsonProperty
    private final boolean nameFilterChange;

    @JsonCreator
    public ExplorerTreeFilter(@JsonProperty("includedTypes") final Set<String> includedTypes,
                              @JsonProperty("includedRootTypes") final Set<String> includedRootTypes,
                              @JsonProperty("tags") final Set<String> tags,
                              @JsonProperty("nodeFlags") final Set<NodeFlag> nodeFlags,
                              @JsonProperty("requiredPermissions") final Set<String> requiredPermissions,
                              @JsonProperty("nameFilter") final String nameFilter,
                              @JsonProperty("nameFilterChange") final boolean nameFilterChange) {
        this.includedTypes = includedTypes;
        this.includedRootTypes = includedRootTypes;
        this.tags = tags;
        this.nodeFlags = nodeFlags;
        this.requiredPermissions = requiredPermissions;
        this.nameFilter = nameFilter;
        this.nameFilterChange = nameFilterChange;
    }

    public Set<String> getIncludedTypes() {
        return includedTypes;
    }

    public Set<String> getIncludedRootTypes() {
        return includedRootTypes;
    }

    public Set<String> getTags() {
        return tags;
    }

    public Set<NodeFlag> getNodeFlags() {
        return nodeFlags;
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

    /**
     * @return A copy of this {@link ExplorerTreeFilter} with the supplied nameFilter
     */
    public ExplorerTreeFilter withNameFilter(final String nameFilter) {
        return new ExplorerTreeFilter(
                includedTypes,
                includedRootTypes,
                tags,
                nodeFlags,
                requiredPermissions,
                nameFilter,
                nameFilterChange);
    }

    @Override
    public String toString() {
        return "ExplorerTreeFilter{" +
                "includedTypes=" + includedTypes +
                ", includedRootTypes=" + includedRootTypes +
                ", tags=" + tags +
                ", nodeFlags=" + nodeFlags +
                ", requiredPermissions=" + requiredPermissions +
                ", nameFilter='" + nameFilter + '\'' +
                ", nameFilterChange=" + nameFilterChange +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ExplorerTreeFilter that = (ExplorerTreeFilter) o;
        return nameFilterChange == that.nameFilterChange &&
                Objects.equals(includedTypes, that.includedTypes) &&
                Objects.equals(includedRootTypes, that.includedRootTypes) &&
                Objects.equals(tags, that.tags) &&
                Objects.equals(nodeFlags, that.nodeFlags) &&
                Objects.equals(requiredPermissions, that.requiredPermissions) &&
                Objects.equals(nameFilter, that.nameFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                includedTypes,
                includedRootTypes,
                tags,
                nodeFlags,
                requiredPermissions,
                nameFilter,
                nameFilterChange);
    }

    /**
     * Turns 'cat dog' into 'tag:cat tag:dog '
     * @return A qualified quick filter input string or null if tags is empty/null
     */
    public static String createTagQuickFilterInput(final Set<String> tags) {
        final String quickFilterInput;
        if (GwtNullSafe.hasItems(tags)) {
            // Add a space on the end so the user is ready to type any extra terms
            quickFilterInput = tags.stream()
                    .map(tag ->
                            ExplorerTreeFilter.FIELD_DEF_TAG.getFilterQualifier() + ":" + tag)
                    .collect(Collectors.joining(" ")) + " ";
        } else {
            quickFilterInput = null;
        }
        return quickFilterInput;
    }
}
