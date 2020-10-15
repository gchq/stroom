/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api.v2;

import org.junit.jupiter.api.Test;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionToString {
    @Test
    void TestSingleLine() {
        ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND).enabled(false);
        single("", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        single("AND {}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addTerm("field", Condition.EQUALS, "value");
        single("AND {field = value}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addTerm("field1", Condition.EQUALS, "value1");
        builder.addTerm("field2", Condition.EQUALS, "value2");
        single("AND {field1 = value1, field2 = value2}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).build());
        single("AND {AND {}}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).enabled(false).build());
        single("AND {}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addTerm("field", Condition.EQUALS, "value");
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).build());
        single("AND {field = value, AND {}}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).build());
        builder.addTerm("field", Condition.EQUALS, "value");
        single("AND {AND {}, field = value}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND)
                .addTerm("nestedField", Condition.EQUALS, "nestedValue")
                .build());
        builder.addTerm("field", Condition.EQUALS, "value");
        single("AND {AND {nestedField = nestedValue}, field = value}", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true)
                .addOperator(new ExpressionOperator.Builder(Op.AND)
                        .addTerm("nestedField1", Condition.EQUALS, "nestedValue1")
                        .addTerm("nestedField2", Condition.EQUALS, "nestedValue2")
                        .build())
                .addTerm("field", Condition.EQUALS, "value");
        single("AND {AND {nestedField1 = nestedValue1, nestedField2 = nestedValue2}, field = value}", builder);
    }

    @Test
    void TestMultiLine() {
        ExpressionOperator.Builder builder = new ExpressionOperator.Builder(Op.AND).enabled(false);
        multi("", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        multi("AND", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addTerm("field", Condition.EQUALS, "value");
        multi("AND\n  field = value", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addTerm("field1", Condition.EQUALS, "value1");
        builder.addTerm("field2", Condition.EQUALS, "value2");
        multi("AND\n  field1 = value1\n  field2 = value2", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).build());
        multi("AND\n  AND", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).enabled(false).build());
        multi("AND", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addTerm("field", Condition.EQUALS, "value");
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).build());
        multi("AND\n  field = value\n  AND", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND).build());
        builder.addTerm("field", Condition.EQUALS, "value");
        multi("AND\n  AND\n  field = value", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true);
        builder.addOperator(new ExpressionOperator.Builder(Op.AND)
                .addTerm("nestedField", Condition.EQUALS, "nestedValue")
                .build());
        builder.addTerm("field", Condition.EQUALS, "value");
        multi("AND\n  AND\n    nestedField = nestedValue\n  field = value", builder);

        builder = new ExpressionOperator.Builder(Op.AND).enabled(true)
                .addOperator(new ExpressionOperator.Builder(Op.AND)
                        .addTerm("nestedField1", Condition.EQUALS, "nestedValue1")
                        .addTerm("nestedField2", Condition.EQUALS, "nestedValue2")
                        .build())
                .addTerm("field", Condition.EQUALS, "value");
        multi("AND\n  AND\n    nestedField1 = nestedValue1\n    nestedField2 = nestedValue2\n  field = value", builder);
    }

    private void single(final String expected, final ExpressionItem.Builder builder) {
        final String actual = builder.build().toString();
        System.out.println(actual);
        assertThat(actual).isEqualTo(expected);
    }

    private void multi(final String expected, final ExpressionItem.Builder builder) {
        final String actual = builder.build().toMultiLineString();
        System.out.println(actual);
        assertThat(actual).isEqualTo(expected);
    }
}
