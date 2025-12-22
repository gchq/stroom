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

package stroom.meta.shared;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaExpressionUtil {

    @Test
    void createDataIdSetExpression() {

        final ExpressionOperator expr = MetaExpressionUtil.createDataIdSetExpression(Set.of(1L, 2L, 3L));

        assertThat(expr.getChildren())
                .hasSize(1);

        final ExpressionItem expressionItem = expr.getChildren().get(0);
        assertThat(expressionItem)
                .isInstanceOf(ExpressionTerm.class);
        final ExpressionTerm term = (ExpressionTerm) expressionItem;
        assertThat(term)
                .extracting(ExpressionTerm::getField)
                .isEqualTo(MetaFields.ID.getFldName());
        assertThat(term)
                .extracting(ExpressionTerm::getCondition)
                .isEqualTo(Condition.IN);
        // Can't be sure of order as it is a set
        assertThat(term)
                .extracting(ExpressionTerm::getValue, Assertions.as(InstanceOfAssertFactories.STRING))
                .contains("1")
                .contains("2")
                .contains("3")
                .contains(",");
    }

    @Test
    void createDataIdSetExpression_oneItem() {

        final ExpressionOperator expr = MetaExpressionUtil.createDataIdSetExpression(Set.of(2L));

        assertThat(expr.getChildren())
                .hasSize(1);

        final ExpressionItem expressionItem = expr.getChildren().get(0);
        assertThat(expressionItem)
                .isInstanceOf(ExpressionTerm.class);
        final ExpressionTerm term = (ExpressionTerm) expressionItem;
        assertThat(term)
                .extracting(ExpressionTerm::getField)
                .isEqualTo(MetaFields.ID.getFldName());
        assertThat(term)
                .extracting(ExpressionTerm::getCondition)
                .isEqualTo(Condition.EQUALS);
        // Can't be sure of order as it is a set
        assertThat(term)
                .extracting(ExpressionTerm::getValue)
                .isEqualTo("2");
    }
}
