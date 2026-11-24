/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.extraction;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionCopier {

    @Test
    void testCopy_keepsOptions() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("user")
                        .condition(Condition.EQUALS)
                        .value("admin*")
                        .options(Map.of("optionA", "true"))
                        .build())
                .build();

        final ExpressionOperator copy = new ExpressionCopier().copy(operator);

        assertThat(copy)
                .isEqualTo(operator);
        assertThat(((ExpressionTerm) copy.getChildren().getFirst()).getOptions())
                .containsExactly(Map.entry("optionA", "true"));
    }
}
