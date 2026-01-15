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
                .uuid(UUID.randomUUID().toString())
                .rules(List.of(
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
                .fields(List.of(
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

        final DictionaryDoc dict1 = DictionaryDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("dic1")
                .data("""
                        Orange
                        Lemon
                        Apple
                        """)
                .build();
        HasAuditInfoSetters.set(dict1, "user1");

        final DictionaryDoc dict2 = DictionaryDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("dic2")
                .data("""
                        Horse
                        Badger
                        Cow
                        """)
                .build();
        HasAuditInfoSetters.set(dict2, "user2");

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
