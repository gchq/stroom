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

package stroom.processor.shared;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.util.shared.HasIsConstrained;
import stroom.util.shared.TreeAction;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class FetchProcessorRequest implements TreeAction<ProcessorListRow>, HasIsConstrained {

    @JsonProperty
    private ExpressionOperator expression;
    @JsonProperty
    private Set<ProcessorListRow> expandedRows;

    public FetchProcessorRequest() {
    }

    public FetchProcessorRequest(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @JsonCreator
    public FetchProcessorRequest(@JsonProperty("expression") final ExpressionOperator expression,
                                 @JsonProperty("expandedRows") final Set<ProcessorListRow> expandedRows) {
        this.expression = expression;
        this.expandedRows = expandedRows;
    }

    @JsonIgnore
    @Override
    public boolean isConstrained() {
        return ExpressionUtil.hasTerms(expression);
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @Override
    public void setRowExpanded(final ProcessorListRow row, final boolean open) {
        if (open) {
            if (expandedRows == null) {
                expandedRows = new HashSet<>();
            }
            expandedRows.add(row);
        } else {
            if (expandedRows != null) {
                expandedRows.remove(row);
            }
        }
    }

    @Override
    public boolean isRowExpanded(final ProcessorListRow row) {
        if (expandedRows == null) {
            return false;
        }
        return expandedRows.contains(row);
    }

    @Override
    public Set<ProcessorListRow> getExpandedRows() {
        return expandedRows;
    }

    public void setExpandedRows(final Set<ProcessorListRow> expandedRows) {
        this.expandedRows = expandedRows;
    }
}
