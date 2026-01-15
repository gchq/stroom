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

package stroom.security.shared;

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.datasource.QueryField;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.UserRef;
import stroom.util.shared.filter.FilterFieldDefinition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FindApiKeyCriteria extends ExpressionCriteria {

    public static final String FIELD_NAME = "name";
    public static final String FIELD_PREFIX = "prefix";
    public static final String FIELD_OWNER = "owner";
    public static final String FIELD_COMMENTS = "comments";
    public static final String FIELD_STATE = "enabled";
    public static final String FIELD_EXPIRE_TIME = "expiretime";
    public static final String FIELD_HASH_ALGORITHM = "hashalgo";

    public static final QueryField NAME = QueryField.createText(FIELD_NAME);
    public static final QueryField PREFIX = QueryField.createText(FIELD_PREFIX);
    public static final QueryField OWNER = QueryField.createText(FIELD_OWNER);
    public static final QueryField COMMENTS = QueryField.createText(FIELD_COMMENTS);
    public static final QueryField STATE = QueryField.createBoolean(FIELD_STATE);
    public static final QueryField EXPIRE_TIME = QueryField.createText(FIELD_EXPIRE_TIME);
    public static final QueryField HASH_ALGORITHM = QueryField.createText(FIELD_HASH_ALGORITHM);

    public static final Set<QueryField> DEFAULT_FIELDS = Set.of(NAME, PREFIX);

    public static final Map<String, QueryField> ALL_FIELDs_MAP = QueryField.buildFieldMap(
            NAME,
            PREFIX,
            OWNER,
            COMMENTS,
            STATE,
            EXPIRE_TIME,
            HASH_ALGORITHM);


    public static final FilterFieldDefinition FIELD_DEF_NAME = FilterFieldDefinition.defaultField(FIELD_NAME);
    public static final FilterFieldDefinition FIELD_DEF_PREFIX = FilterFieldDefinition.defaultField(FIELD_PREFIX);
    public static final FilterFieldDefinition FIELD_DEF_OWNER_DISPLAY_NAME = FilterFieldDefinition.qualifiedField(
            FIELD_OWNER);
    public static final FilterFieldDefinition FIELD_DEF_COMMENTS = FilterFieldDefinition.qualifiedField(
            FIELD_COMMENTS);
    public static final FilterFieldDefinition FIELD_DEF_ENABLED = FilterFieldDefinition.qualifiedField(
            FIELD_STATE);
    public static final FilterFieldDefinition FIELD_DEF_HASH_ALGORITHM = FilterFieldDefinition.qualifiedField(
            FIELD_HASH_ALGORITHM);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_NAME,
            FIELD_DEF_PREFIX,
            FIELD_DEF_OWNER_DISPLAY_NAME,
            FIELD_DEF_COMMENTS,
            FIELD_DEF_ENABLED,
            FIELD_DEF_HASH_ALGORITHM);

    @JsonProperty
    private UserRef owner;

    public FindApiKeyCriteria() {
    }

    @JsonCreator
    public FindApiKeyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("expression") final ExpressionOperator expression,
                              @JsonProperty("owner") final UserRef owner) {
        super(pageRequest, sortList, expression);
        this.owner = owner;
    }

    //    public static FindApiKeyCriteria create(final String quickFilterInput) {
//        FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
//        findApiKeyCriteria.setQuickFilterInput(quickFilterInput);
//        return findApiKeyCriteria;
//    }
//
    public static FindApiKeyCriteria create(final UserRef owner) {
        final FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
        findApiKeyCriteria.setOwner(owner);
        return findApiKeyCriteria;
    }
//
//    public static FindApiKeyCriteria create(final String quickFilterInput, final UserRef owner) {
//        FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
//        findApiKeyCriteria.setQuickFilterInput(quickFilterInput);
//        findApiKeyCriteria.setOwner(owner);
//        return findApiKeyCriteria;
//    }

    public UserRef getOwner() {
        return owner;
    }

    public void setOwner(final UserRef owner) {
        this.owner = owner;
    }

    @Override
    public String toString() {
        return "FindApiKeyCriteria{" +
               "owner=" + owner +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends ExpressionCriteriaBuilder<FindApiKeyCriteria, Builder> {

        private UserRef owner = null;

        public Builder() {
        }

        public Builder(final FindApiKeyCriteria expressionCriteria) {
            super(expressionCriteria);
        }

        public Builder owner(final UserRef owner) {
            this.owner = owner;
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FindApiKeyCriteria build() {
            return new FindApiKeyCriteria(
                    pageRequest,
                    sortList,
                    expression,
                    owner);
        }
    }
}
