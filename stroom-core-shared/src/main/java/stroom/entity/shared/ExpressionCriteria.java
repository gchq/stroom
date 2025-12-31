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

package stroom.entity.shared;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionUtil;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExpressionCriteria extends BaseCriteria {

    @JsonProperty
    private ExpressionOperator expression;

    public ExpressionCriteria() {
    }

    public ExpressionCriteria(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @JsonCreator
    public ExpressionCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                              @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                              @JsonProperty("expression") final ExpressionOperator expression) {
        super(pageRequest, sortList);
        this.expression = expression;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ExpressionCriteria)) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ExpressionCriteria that = (ExpressionCriteria) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }

    @Override
    public String toString() {
        return "BaseCriteria{" +
               "pageRequest=" + getPageRequest() +
               ", sortList=" + getSortList() +
               ", expression=" + expression +
               '}';
    }

    public static Builder criteriaBuilder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends ExpressionCriteriaBuilder<ExpressionCriteria, Builder> {

        public Builder() {

        }

        public Builder(final ExpressionCriteria expressionCriteria) {
            super(expressionCriteria);
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public ExpressionCriteria build() {
            return new ExpressionCriteria(pageRequest, sortList, expression);
        }
    }


    // --------------------------------------------------------------------------------


    public abstract static class ExpressionCriteriaBuilder
            <T extends ExpressionCriteria, B extends ExpressionCriteriaBuilder<T, B>>
            extends BaseCriteriaBuilder<T, B> {

        protected ExpressionOperator expression;

        protected ExpressionCriteriaBuilder() {

        }

        protected ExpressionCriteriaBuilder(final T expressionCriteria) {
            super(expressionCriteria);
            if (expressionCriteria.getExpression() != null) {
                expression = ExpressionUtil.copyOperator(expressionCriteria.getExpression());
            }
        }

        public B expression(final ExpressionOperator expression) {
            this.expression = expression;
            return self();
        }

        public ExpressionOperator getExpression() {
            return expression;
        }
    }
}
