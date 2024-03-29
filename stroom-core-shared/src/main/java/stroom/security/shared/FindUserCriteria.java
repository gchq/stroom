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

package stroom.security.shared;

import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.List;

/**
 * Criteria class.
 */
@JsonInclude(Include.NON_NULL)
public class FindUserCriteria extends BaseCriteria {

    public static final String FIELD_NAME = "Unique Identity";
    public static final String FIELD_DISPLAY_NAME = "Display Name";
    public static final String FIELD_FULL_NAME = "Full Name";

    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldDefinition FIELD_DEF_DISPLAY_NAME = FilterFieldDefinition.defaultField(
            FIELD_DISPLAY_NAME);
    public static final FilterFieldDefinition FIELD_DEF_FULL_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_FULL_NAME);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NAME,
            FIELD_DEF_DISPLAY_NAME,
            FIELD_DEF_FULL_NAME);

    /**
     * Find user groups
     */
    @JsonProperty
    private boolean group;
    @JsonProperty
    private User relatedUser;
    @JsonProperty
    private String quickFilterInput;

    public FindUserCriteria() {
    }

    public FindUserCriteria(final String quickFilterInput, final boolean group) {
        this.quickFilterInput = quickFilterInput;
        this.group = group;
    }

    @JsonCreator
    public FindUserCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                            @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                            @JsonProperty("quickFilterInput") final String quickFilterInput,
//                            @JsonProperty("requiredPermission") final String requiredPermission,
                            @JsonProperty("group") final boolean group,
                            @JsonProperty("relatedUser") final User relatedUser) {
        super(pageRequest, sortList);
        this.group = group;
        this.relatedUser = relatedUser;
        this.quickFilterInput = quickFilterInput;
    }

    public FindUserCriteria(final Boolean group) {
        this.group = group;
    }

    public FindUserCriteria(final User relatedUser) {
        this.relatedUser = relatedUser;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(final boolean group) {
        this.group = group;
    }

    public User getRelatedUser() {
        return relatedUser;
    }

    public void setRelatedUser(User relatedUser) {
        this.relatedUser = relatedUser;
    }

    public String getQuickFilterInput() {
        return quickFilterInput;
    }

    public void setQuickFilterInput(final String quickFilterInput) {
        this.quickFilterInput = quickFilterInput;
    }
}
