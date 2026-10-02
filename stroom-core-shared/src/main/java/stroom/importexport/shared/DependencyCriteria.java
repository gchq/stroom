/*
 * Copyright 2018 Crown Copyright
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

package stroom.importexport.shared;

import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
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

@JsonInclude(Include.NON_NULL)
public class DependencyCriteria extends BaseCriteria {

    // Display names. These are the column headings, and the sort ids sent in
    // CriteriaFieldSort - they are NOT what a user types in the quick filter.
    //
    // The quick filter qualifier is derived from each of these by
    // FilterFieldDefinition.toQualifiedName(), which strips non-alphanumerics and lowercases,
    // so "To (UUID)" is typed as "touuid:<value>". The qualifiers never appear as literals in
    // this class; see the QF_* QueryFields below, which the server resolves filter terms
    // against, and the "Field qualifier" column of the quick filter help tooltip, which is how
    // a user discovers them.
    public static final String FIELD_FROM_TYPE = "From (Type)";
    public static final String FIELD_FROM_NAME = "From (Name)";
    public static final String FIELD_FROM_UUID = "From (UUID)";
    public static final String FIELD_TO_TYPE = "To (Type)";
    public static final String FIELD_TO_NAME = "To (Name)";
    public static final String FIELD_TO_UUID = "To (UUID)";
    public static final String FIELD_STATUS = "Status";

    // Default fields
    public static final FilterFieldDefinition FIELD_DEF_FROM_NAME =
            FilterFieldDefinition.defaultField(FIELD_FROM_NAME);
    public static final FilterFieldDefinition FIELD_DEF_TO_NAME =
            FilterFieldDefinition.defaultField(FIELD_TO_NAME);
    // Qualified Fields
    public static final FilterFieldDefinition FIELD_DEF_FROM_TYPE =
            FilterFieldDefinition.qualifiedField(FIELD_FROM_TYPE);
    public static final FilterFieldDefinition FIELD_DEF_FROM_UUID =
            FilterFieldDefinition.qualifiedField(FIELD_FROM_UUID);
    public static final FilterFieldDefinition FIELD_DEF_TO_TYPE =
            FilterFieldDefinition.qualifiedField(FIELD_TO_TYPE);
    public static final FilterFieldDefinition FIELD_DEF_TO_UUID =
            FilterFieldDefinition.qualifiedField(FIELD_TO_UUID);
    public static final FilterFieldDefinition FIELD_DEF_STATUS =
            FilterFieldDefinition.qualifiedField(FIELD_STATUS);

    public static final List<FilterFieldDefinition> FIELD_DEFINITIONS = Arrays.asList(
            FIELD_DEF_FROM_TYPE,
            FIELD_DEF_FROM_NAME,
            FIELD_DEF_FROM_UUID,
            FIELD_DEF_TO_TYPE,
            FIELD_DEF_TO_NAME,
            FIELD_DEF_TO_UUID,
            FIELD_DEF_STATUS);

    // The server resolves a quick filter term to a field by its filter qualifier, so the
    // QueryField names below are the qualifiers ("fromtype"), not the display names
    // ("From (Type)"). Deriving each from its FilterFieldDefinition rather than repeating the
    // literal means the two can never drift apart.
    public static final QueryField QF_FROM_TYPE = sqlTextField(FIELD_DEF_FROM_TYPE);
    public static final QueryField QF_FROM_NAME = sqlTextField(FIELD_DEF_FROM_NAME);
    public static final QueryField QF_FROM_UUID = sqlTextField(FIELD_DEF_FROM_UUID);
    public static final QueryField QF_TO_TYPE = sqlTextField(FIELD_DEF_TO_TYPE);
    public static final QueryField QF_TO_NAME = sqlTextField(FIELD_DEF_TO_NAME);
    public static final QueryField QF_TO_UUID = sqlTextField(FIELD_DEF_TO_UUID);
    /**
     * Status holds one of two literal values, "OK" or "Missing", so only exact matching makes
     * sense on it. Declaring the narrower set also means an expression tree editor offers just
     * the two sensible operators.
     */
    public static final QueryField QF_STATUS = queryField(FIELD_DEF_STATUS, ConditionSet.SQL_ENUM_TEXT);

    /**
     * Not {@link QueryField#createText(String)} - that defaults to
     * {@link ConditionSet#DEFAULT_TEXT}, which omits CONTAINS, the condition a quick filter
     * uses for every unqualified term.
     */
    private static QueryField sqlTextField(final FilterFieldDefinition fieldDefinition) {
        return queryField(fieldDefinition, ConditionSet.SQL_TEXT);
    }

    private static QueryField queryField(final FilterFieldDefinition fieldDefinition,
                                         final ConditionSet conditionSet) {
        return QueryField
                .builder()
                .fldName(fieldDefinition.getFilterQualifier())
                .fldType(FieldType.TEXT)
                .conditionSet(conditionSet)
                .build();
    }

    @JsonProperty
    private String partialName;

    public DependencyCriteria() {
    }

//    @JsonCreator
//    public DependencyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
//                              @JsonProperty("sortList") final List<Sort> sortList,
//                              @JsonProperty("expression") final ExpressionOperator expressionOperator) {
//        super(pageRequest, sortList, expressionOperator);
//    }

    @JsonCreator
    public DependencyCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("partialName") final String partialName) {
        super(pageRequest, sortList);
        this.partialName = partialName;
    }

    public String getPartialName() {
        return partialName;
    }

    public void setPartialName(final String partialName) {
        this.partialName = partialName;
    }

    @Override
    public String toString() {
        return "DependencyCriteria{" +
                "partialName='" + partialName + '\'' +
                '}';
    }
}
