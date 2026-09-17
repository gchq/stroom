/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Criteria for the user access list.
 * <p>
 * Filtering, sorting and paging all happen in memory on the server, because the two sources being merged are a
 * database table and per-node in-memory session state - there is no single query that could span them. See
 * {@code UserAccessService} for the resulting scale ceiling.
 * </p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FindUserAccessCriteria extends BaseCriteria {

    public static final String FIELD_USER = "user";
    public static final String FIELD_SESSIONS = "sessions";
    public static final String FIELD_TOKENS = "tokens";
    public static final String FIELD_LAST_ACCESSED = "lastAccessed";

    /**
     * A free-text filter matched, case-insensitively, against the display name and the subject id.
     * <p>
     * Matching the subject id as well as the name is not incidental: a subject with no stroom user has only an
     * opaque id to show, and those rows still have to be findable.
     * </p>
     */
    @JsonProperty
    private String filter;

    /**
     * When true, only include subjects that currently hold something. Defaults to true, since the screen answers
     * "who has access right now" rather than listing every user.
     */
    @JsonProperty
    private boolean activeOnly = true;

    public FindUserAccessCriteria() {
    }

    @JsonCreator
    public FindUserAccessCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                  @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                  @JsonProperty("filter") final String filter,
                                  @JsonProperty("activeOnly") final boolean activeOnly) {
        super(pageRequest, sortList);
        this.filter = filter;
        this.activeOnly = activeOnly;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(final String filter) {
        this.filter = filter;
    }

    public boolean isActiveOnly() {
        return activeOnly;
    }

    public void setActiveOnly(final boolean activeOnly) {
        this.activeOnly = activeOnly;
    }

    @Override
    public String toString() {
        return "FindUserAccessCriteria{" +
               "filter='" + filter + '\'' +
               ", activeOnly=" + activeOnly +
               '}';
    }
}
