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

package stroom.ai.impl;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionDescriber {

    @Test
    void oneTerm() {
        assertThat(describe(and(term("Feed", Condition.EQUALS, "ACME")))).isEqualTo("Feed is 'ACME'");
    }

    @Test
    void severalTermsAndNestedOperators() {
        final ExpressionOperator expression = and(
                term("Feed", Condition.EQUALS, "ACME"),
                ExpressionOperator.builder()
                        .op(Op.OR)
                        .addTerm(term("Type", Condition.EQUALS, "Raw Events"))
                        .addTerm(term("Type", Condition.EQUALS, "Events"))
                        .build());

        assertThat(describe(expression))
                .isEqualTo("Feed is 'ACME' and (Type is 'Raw Events' or Type is 'Events')");
    }

    @Test
    void notAndLists() {
        final ExpressionOperator expression = and(
                term("Feed", Condition.IN, "A, B,C"),
                ExpressionOperator.builder()
                        .op(Op.NOT)
                        .addTerm(term("Status", Condition.EQUALS, "Deleted"))
                        .build());

        assertThat(describe(expression)).isEqualTo("Feed is one of 'A', 'B' or 'C' and not (Status is 'Deleted')");
    }

    @Test
    void timesAndNumbers() {
        assertThat(describe(and(
                term("Create Time", Condition.GREATER_THAN, "2026-01-01T00:00:00.000Z"),
                term("Raw Size", Condition.LESS_THAN_OR_EQUAL_TO, "1000"),
                term("Effective Time", Condition.BETWEEN, "2026-01-01,2026-02-01"))))
                .isEqualTo("Create Time is after '2026-01-01T00:00:00.000Z' and Raw Size is at most '1000' and "
                           + "Effective Time is between '2026-01-01' and '2026-02-01'");
    }

    @Test
    void documentsAreNamed() {
        assertThat(describe(and(ExpressionTerm.builder()
                .field("Pipeline")
                .condition(Condition.IS_DOC_REF)
                .docRef("Pipeline", "1e48bdca-4d46-4ab3-b3e8-ca4f4f81feea", "Acme-Events")
                .build())))
                .isEqualTo("Pipeline is 'Acme-Events'");
    }

    @Test
    void disabledItemsAreLeftOut() {
        final ExpressionOperator expression = and(
                term("Feed", Condition.EQUALS, "ACME"),
                ExpressionTerm.builder().field("Type").condition(Condition.EQUALS).value("Events").enabled(false)
                        .build());

        assertThat(describe(expression)).isEqualTo("Feed is 'ACME'");
    }

    @Test
    void anExpressionThatSelectsEverything() {
        assertThat(describe(null)).isNull();
        assertThat(describe(ExpressionOperator.builder().build())).isNull();
        assertThat(describe(and(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS).value("A")
                .enabled(false).build()))).isNull();
    }

    private static String describe(final ExpressionOperator expression) {
        return ExpressionDescriber.describe(expression);
    }

    private static ExpressionOperator and(final stroom.query.api.ExpressionItem... items) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        for (final stroom.query.api.ExpressionItem item : items) {
            builder.addItem(item);
        }
        return builder.build();
    }

    private static ExpressionTerm term(final String field, final Condition condition, final String value) {
        return ExpressionTerm.builder().field(field).condition(condition).value(value).build();
    }
}
