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

package stroom.processor.impl;

import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.common.v2.ExpressionValidationException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestProcessorTaskCreatorImpl {

    @Test
    void testSanitise_removeTerms() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .enabled(true)
                .addTerm(ExpressionTerm.builder()
                        .field(MetaFields.FEED)
                        .enabled(true)
                        .condition(Condition.EQUALS)
                        .value("FEED1")
                        .build())
                .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                .addTerm(ExpressionTerm.builder()
                        .enabled(true)
                        .field(MetaFields.TYPE)
                        .condition(Condition.NOT_EQUALS)
                        .value("Events")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .enabled(true)
                        .addTerm(ExpressionTerm.builder()
                                .field(MetaFields.PIPELINE_NAME)
                                .enabled(true)
                                .condition(Condition.EQUALS)
                                .value("foo")
                                .build())
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .enabled(true)
                        .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.UNLOCKED.getDisplayValue())
                        .addTextTerm(MetaFields.STATUS, Condition.EQUALS, Status.LOCKED.getDisplayValue())
                        .build())
                .build();
        final ExpressionOperator operator2 = ProcessorTaskCreatorImpl.sanitiseAndValidateExpression(operator);
        assertThat(operator2)
                .isNotEqualTo(operator);

        assertThat(operator.containsField(
                MetaFields.FEED.getFldName(),
                MetaFields.TYPE.getFldName(),
                MetaFields.PIPELINE_NAME.getFldName(),
                MetaFields.STATUS.getFldName()))
                .isTrue();
        assertThat(operator2.containsField(MetaFields.STATUS.getFldName()))
                .isFalse();
        assertThat(operator.containsField(
                MetaFields.FEED.getFldName(),
                MetaFields.TYPE.getFldName(),
                MetaFields.PIPELINE_NAME.getFldName()))
                .isTrue();

        assertThat(operator2)
                .isEqualTo(ExpressionOperator.builder()
                        .enabled(true)
                        .addTerm(ExpressionTerm.builder()
                                .field(MetaFields.FEED)
                                .enabled(true)
                                .condition(Condition.EQUALS)
                                .value("FEED1")
                                .build())
                        .addTerm(ExpressionTerm.builder()
                                .enabled(true)
                                .field(MetaFields.TYPE)
                                .condition(Condition.NOT_EQUALS)
                                .value("Events")
                                .build())
                        .addOperator(ExpressionOperator.builder()
                                .op(Op.OR)
                                .enabled(true)
                                .addTerm(ExpressionTerm.builder()
                                        .field(MetaFields.PIPELINE_NAME)
                                        .enabled(true)
                                        .condition(Condition.EQUALS)
                                        .value("foo")
                                        .build())
                                .build())
                        .addOperator(ExpressionOperator.builder()
                                .op(Op.OR)
                                .enabled(true)
                                .build())
                        .build());
    }

    @Test
    void testSanitise_noChange() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .enabled(true)
                .addTerm(ExpressionTerm.builder()
                        .field(MetaFields.FEED)
                        .enabled(true)
                        .condition(Condition.EQUALS)
                        .value("FEED1")
                        .build())
                .addTerm(ExpressionTerm.builder()
                        .enabled(true)
                        .field(MetaFields.TYPE)
                        .condition(Condition.NOT_EQUALS)
                        .value("Events")
                        .build())
                .addOperator(ExpressionOperator.builder()
                        .op(Op.OR)
                        .enabled(true)
                        .addTerm(ExpressionTerm.builder()
                                .field(MetaFields.PIPELINE_NAME)
                                .enabled(true)
                                .condition(Condition.EQUALS)
                                .value("foo")
                                .build())
                        .build())
                .build();
        final ExpressionOperator operator2 = ProcessorTaskCreatorImpl.sanitiseAndValidateExpression(operator);
        assertThat(operator2)
                .isSameAs(operator);
    }

    @Test
    void testSanitise_unknownField() {
        final ExpressionOperator operator = ExpressionOperator.builder()
                .enabled(true)
                .addTerm(ExpressionTerm.builder()
                        .field("foo")
                        .enabled(true)
                        .condition(Condition.EQUALS)
                        .value("bar")
                        .build())
                .build();

        Assertions.assertThatThrownBy(() ->
                        ProcessorTaskCreatorImpl.sanitiseAndValidateExpression(operator))
                .isInstanceOf(ExpressionValidationException.class)
                .hasMessageContaining("field")
                .hasMessageContaining("foo");
    }
}
