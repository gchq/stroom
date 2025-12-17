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

package stroom.importexport.shared;

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
