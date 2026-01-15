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

package stroom.task.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class FindTaskProgressCriteria extends BaseCriteria {

    public static final String FIELD_NODE = "Node";
    public static final String FIELD_NAME = "Name";
    public static final String FIELD_USER = "User";
    public static final String FIELD_SUBMIT_TIME = "Submit Time";
    public static final String FIELD_AGE = "Age";
    public static final String FIELD_INFO = "Info";

    public static final FilterFieldDefinition FIELD_DEF_NODE = FilterFieldDefinition.qualifiedField(FIELD_NODE);
    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldDefinition FIELD_DEF_USER = FilterFieldDefinition.qualifiedField(FIELD_USER);
    public static final FilterFieldDefinition FIELD_DEF_SUBMIT_TIME = FilterFieldDefinition.qualifiedField(
            FIELD_SUBMIT_TIME,
            "time");
    public static final FilterFieldDefinition FIELD_DEF_INFO = FilterFieldDefinition.qualifiedField(FIELD_INFO);

    public static final List<FilterFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NODE,
            FIELD_DEF_NAME,
            FIELD_DEF_USER,
            FIELD_DEF_SUBMIT_TIME,
            FIELD_DEF_INFO);

    @JsonProperty
    private Set<TaskProgress> expandedTasks;
    @JsonProperty
    private String nameFilter;
    @JsonProperty
    private String sessionId;

    public FindTaskProgressCriteria() {
    }

    @JsonCreator
    public FindTaskProgressCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                    @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                    @JsonProperty("expandedTasks") final Set<TaskProgress> expandedTasks,
                                    @JsonProperty("nameFilter") final String nameFilter,
                                    @JsonProperty("sessionId") final String sessionId) {
        super(pageRequest, sortList);
        this.expandedTasks = expandedTasks;
        this.nameFilter = nameFilter;
        this.sessionId = sessionId;
    }

    public Set<TaskProgress> getExpandedTasks() {
        return expandedTasks;
    }

    public void setExpandedTasks(final Set<TaskProgress> expandedTasks) {
        this.expandedTasks = expandedTasks;
    }

    public String getNameFilter() {
        return nameFilter;
    }

    public void setNameFilter(final String nameFilter) {
        this.nameFilter = nameFilter;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public void setExpanded(final TaskProgress taskProgress, final boolean expanded) {
        if (expanded) {
            if (expandedTasks == null) {
                expandedTasks = new HashSet<>();
            }
            expandedTasks.add(taskProgress);
        } else {
            if (expandedTasks != null) {
                expandedTasks.remove(taskProgress);
                if (expandedTasks.size() == 0) {
                    expandedTasks = null;
                }
            }
        }
    }

    public boolean isExpanded(final TaskProgress taskProgress) {
        if (expandedTasks != null) {
            return expandedTasks.contains(taskProgress);
        }
        return false;
    }

    public void validateSortField() {
        if (this.getSortList().isEmpty()) {
            final CriteriaFieldSort defaultSort = new CriteriaFieldSort(FindTaskProgressCriteria.FIELD_SUBMIT_TIME,
                    false,
                    true);
            this.getSortList().add(defaultSort);
        } else {
            for (final CriteriaFieldSort sort : this.getSortList()) {
                if (!Arrays.asList(
                        FindTaskProgressCriteria.FIELD_AGE,
                        FindTaskProgressCriteria.FIELD_INFO,
                        FindTaskProgressCriteria.FIELD_NAME,
                        FindTaskProgressCriteria.FIELD_NODE,
                        FindTaskProgressCriteria.FIELD_SUBMIT_TIME,
                        FindTaskProgressCriteria.FIELD_USER).contains(sort.getId())) {
                    throw new IllegalArgumentException("A sort field of " + sort.getId() +
                            " is not valid! It must be one of FindTaskProgressCriteria.FIELD_xxx");
                }
            }
        }
    }

    public boolean isMatch(final String sessionId) {
        return true;
    }
}
