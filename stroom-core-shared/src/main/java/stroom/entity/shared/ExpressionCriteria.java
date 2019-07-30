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

package stroom.entity.shared;

import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.BaseCriteria;

import java.util.Objects;

public class ExpressionCriteria extends BaseCriteria {
    private ExpressionOperator expression;

    public ExpressionCriteria() {
    }

    public ExpressionCriteria(final ExpressionOperator expression) {
        this.expression = expression;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public void setExpression(final ExpressionOperator expression) {
        this.expression = expression;
    }

//    @Override
//    public boolean isConstrained() {
//        return ExpressionUtil.termCount(expression) > 0;
//    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpressionCriteria)) return false;
        if (!super.equals(o)) return false;
        final ExpressionCriteria that = (ExpressionCriteria) o;
        return Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), expression);
    }
}
