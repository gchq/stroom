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
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class FindUserDependenciesCriteria extends ExpressionCriteria {

    public static final String FIELD_DOC_NAME = "docname";
    //    public static final String FIELD_DOC_UUID = "docuuid";
    public static final String FIELD_DETAILS = "details";

    public static final QueryField DOC_NAME = QueryField.createText(FIELD_DOC_NAME);
    //    public static final QueryField DOC_UUID = QueryField.createText(FIELD_DOC_UUID);
    public static final QueryField DETAILS = QueryField.createText(FIELD_DETAILS);

    public static final Set<QueryField> DEFAULT_FIELDS = Set.of(DOC_NAME);

    public static final Map<String, QueryField> ALL_FIELDs_MAP = QueryField.buildFieldMap(
            DOC_NAME,
//            DOC_UUID,
            DETAILS);


    public static final FilterFieldDefinition FIELD_DEF_DOC_NAME = FilterFieldDefinition.defaultField(
            FIELD_DOC_NAME);
    //    public static final FilterFieldDefinition FIELD_DEF_DOC_UUID = FilterFieldDefinition.qualifiedField(
//            FIELD_DOC_UUID);
    public static final FilterFieldDefinition FIELD_DEF_DETAILS = FilterFieldDefinition.qualifiedField(FIELD_DETAILS);

    public static final List<FilterFieldDefinition> FILTER_FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_DOC_NAME,
//            FIELD_DEF_DOC_UUID,
            FIELD_DEF_DETAILS);

    @JsonProperty
    private UserRef userRef;

    @JsonCreator
    public FindUserDependenciesCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                        @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                        @JsonProperty("expression") final ExpressionOperator expression,
                                        @JsonProperty("userRef") final UserRef userRef) {
        super(pageRequest, sortList, expression);
        this.userRef = userRef;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String toString() {
        return "FindApiKeyCriteria{" +
               "owner=" + userRef +
               '}';
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractBuilder<FindUserDependenciesCriteria, Builder> {

        private UserRef userRef = null;

        public Builder() {
        }

        public Builder(final FindUserDependenciesCriteria expressionCriteria) {
            super(expressionCriteria);
        }

        public Builder userRef(final UserRef userRef) {
            this.userRef = Objects.requireNonNull(userRef);
            return this;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FindUserDependenciesCriteria build() {
            Objects.requireNonNull(userRef);
            return new FindUserDependenciesCriteria(
                    pageRequest,
                    sortList,
                    expression,
                    userRef);
        }
    }
}
