package stroom.receive.rules.shared;

import stroom.dictionary.shared.DictionaryDoc;
import stroom.docstore.shared.DocRefUtil;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.security.shared.HashAlgorithm;
import stroom.test.common.TestUtil;
import stroom.util.shared.HasAuditInfoSetters;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

class TestHashedReceiveDataRules {

    @Test
    void testSerDeser() {

        final HashedReceiveDataRules hashedReceiveDataRules1 = createHashedReceiveDataRules();

        TestUtil.testSerialisation(hashedReceiveDataRules1, HashedReceiveDataRules.class);
    }

    private HashedReceiveDataRules createHashedReceiveDataRules() {
        final ReceiveDataRules receiveDataRules = ReceiveDataRules.builder()
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

        final DictionaryDoc dict1 = new DictionaryDoc();
        HasAuditInfoSetters.set(dict1, "user1");
        dict1.setData("""
                Orange
                Lemon
                Apple
                """);
        dict1.setName("dic1");
        dict1.setUuid(UUID.randomUUID().toString());
        dict1.setType(DictionaryDoc.TYPE);

        final DictionaryDoc dict2 = new DictionaryDoc();
        HasAuditInfoSetters.set(dict2, "user2");
        dict2.setData("""
                Horse
                Badger
                Cow
                """);
        dict2.setName("dic2");
        dict2.setUuid(UUID.randomUUID().toString());
        dict2.setType(DictionaryDoc.TYPE);

        final Map<String, DictionaryDoc> uuidToDictMap = DocRefUtil.toMapByUuid(dict1, dict2);

        final Map<String, String> fieldNameToSaltMap = Map.of(
                "field1", "salt1",
                "field2", "salt2",
                "field3", "salt3");

        return new HashedReceiveDataRules(
                receiveDataRules,
                uuidToDictMap,
                fieldNameToSaltMap,
                HashAlgorithm.SHA3_256);
    }
}
