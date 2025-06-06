package stroom.processor.impl;

import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
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
