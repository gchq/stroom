/*
 * Copyright 2023 Crown Copyright
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

import stroom.query.api.datasource.QueryField;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.QuickFilterCriteria;
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
public class FindApiKeyCriteria extends QuickFilterCriteria {

    public static final String FIELD_NAME = "name";
    public static final String FIELD_PREFIX = "prefix";
    public static final String FIELD_OWNER = "owner";
    public static final String FIELD_COMMENTS = "comments";
    public static final String FIELD_STATE = "enabled";
    public static final String FIELD_EXPIRE_TIME = "expiretime";
    public static final String FIELD_HASH_ALGORITHM = "hashalgo";

    // Identity-mapped onto text columns in ApiKeyDaoImpl, so these can honour SQL_TEXT - which
    // matters now the quick filter is parsed server-side, because a bare term is CONTAINS.
    public static final QueryField NAME = QueryField.createSqlText(FIELD_NAME);
    public static final QueryField PREFIX = QueryField.createSqlText(FIELD_PREFIX);
    public static final QueryField OWNER = QueryField.createSqlText(FIELD_OWNER);
    public static final QueryField COMMENTS = QueryField.createSqlText(FIELD_COMMENTS);
    public static final QueryField HASH_ALGORITHM = QueryField.createSqlText(FIELD_HASH_ALGORITHM);
    // Converted by StringUtil::asBoolean before it reaches SQL.
    public static final QueryField STATE = QueryField.createBoolean(FIELD_STATE);
    // Not mapped by ApiKeyDaoImpl at all, so it is not offered to the quick filter.
    public static final QueryField EXPIRE_TIME = QueryField.createText(FIELD_EXPIRE_TIME);

    public static final Set<QueryField> DEFAULT_FIELDS = Set.of(NAME, PREFIX);

    public static final List<QueryField> QUICK_FILTER_DEFAULT_FIELDS = Arrays.asList(NAME, PREFIX);

    public static final List<QueryField> QUICK_FILTER_FIELDS = Arrays.asList(
            NAME,
            PREFIX,
            OWNER,
            COMMENTS,
            STATE,
            HASH_ALGORITHM);

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

    public FindApiKeyCriteria(final PageRequest pageRequest,
                              final List<CriteriaFieldSort> sortList,
                              final UserRef owner) {
        this(pageRequest, sortList, owner, null);
    }

    @JsonCreator
    public FindApiKeyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("owner") final UserRef owner,
                              @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList, quickFilter);
        this.owner = owner;
    }

    public static FindApiKeyCriteria create(final UserRef owner) {
        final FindApiKeyCriteria findApiKeyCriteria = new FindApiKeyCriteria();
        findApiKeyCriteria.setOwner(owner);
        return findApiKeyCriteria;
    }


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


    public static class Builder extends QuickFilterCriteriaBuilder<FindApiKeyCriteria, Builder> {

        private UserRef owner = null;

        public Builder() {
        }

        public Builder(final FindApiKeyCriteria criteria) {
            super(criteria);
            this.owner = criteria.owner;
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
                    owner,
                    quickFilter);
        }
    }
}
