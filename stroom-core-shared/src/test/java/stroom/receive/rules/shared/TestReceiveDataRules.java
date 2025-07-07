package stroom.receive.rules.shared;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.test.common.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

class TestReceiveDataRules {

    @Test
    void testSerDeser() {

        final ReceiveDataRules hashedReceiveDataRules1 = createReceiveDataRules();

        TestUtil.testSerialisation(hashedReceiveDataRules1, ReceiveDataRules.class);
    }

    private ReceiveDataRules createReceiveDataRules() {

        return ReceiveDataRules.builder()
                .withName("foo")
                .withDescription("Some words")
                .withCreateTimeMs(123L)
                .withUpdateTimeMs(123L)
                .withCreateUser("Bob")
                .withUpdateUser("Dave")
                .withRules(List.of(
                        ReceiveDataRule.builder()
                                .withRuleNumber(1)
                                .withAction(ReceiveAction.RECEIVE)
                                .withExpression(ExpressionOperator.builder()
                                        .addTerm("field1", Condition.EQUALS, "bar")
                                        .build())
                                .build(),
                        ReceiveDataRule.builder()
                                .withRuleNumber(2)
                                .withAction(ReceiveAction.REJECT)
                                .withExpression(ExpressionOperator.builder()
                                        .addTerm("field2", Condition.EQUALS, "foo")
                                        .build())
                                .build()))
                .withFields(List.of(
                        QueryField.builder()
                                .fldName("field1")
                                .fldType(FieldType.TEXT)
                                .build(),
                        QueryField.builder()
                                .fldName("field2")
                                .fldType(FieldType.TEXT)
                                .build(),
                        QueryField.builder()
                                .fldName("field3")
                                .fldType(FieldType.TEXT)
                                .build()))
                .build();
    }
}
