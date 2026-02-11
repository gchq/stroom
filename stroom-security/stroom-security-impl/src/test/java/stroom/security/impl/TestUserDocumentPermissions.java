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

package stroom.security.impl;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("checkstyle:AvoidStaticImport")
class TestUserDocumentPermissions {

    private static final DocRef DOC_UUID_1 = new DocRef("test", "123");
    private static final DocRef DOC_UUID_2 = new DocRef("test", "456");
    private static final DocRef DOC_UUID_3 = new DocRef("test", "789");

    private UserDocumentPermissions userDocPerms;

    @BeforeEach
    void setUp() {
        userDocPerms = new UserDocumentPermissions();
        userDocPerms.setPermission(DOC_UUID_1, DocumentPermission.OWNER);
        userDocPerms.setPermission(DOC_UUID_2, DocumentPermission.DELETE);
    }

    @TestFactory
    Stream<DynamicTest> testHasDocumentPermission() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(DocRef.class, DocumentPermission.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> userDocPerms.hasDocumentPermission(
                        testCase.getInput()._1(),
                        testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.VIEW), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.EDIT), true) // Direct
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.DELETE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_1, DocumentPermission.OWNER), true) // Direct

                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.USE), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.VIEW), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.EDIT), true) // Inferred
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.DELETE), true) // Direct
                .addCase(Tuple.of(DOC_UUID_2, DocumentPermission.OWNER), false)

                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.USE), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.VIEW), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.EDIT), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.DELETE), false)
                .addCase(Tuple.of(DOC_UUID_3, DocumentPermission.OWNER), false)

                .build();
    }


    @Test
    void addPermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.EDIT))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.VIEW))
                .isFalse();

        userDocPerms.setPermission(DOC_UUID_3, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.EDIT))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.VIEW))
                .isTrue();

        // Add same again
        userDocPerms.setPermission(DOC_UUID_3, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.EDIT))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_3, DocumentPermission.VIEW))
                .isTrue();
    }

    @Test
    void removePermission() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();

        userDocPerms.setPermission(DOC_UUID_1, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();

        // Remove same again
        userDocPerms.setPermission(DOC_UUID_1, DocumentPermission.EDIT);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();
    }

    @Test
    void clearDocumentPermissions() {
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isTrue();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isTrue();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isTrue();

        userDocPerms.clearPermission(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isTrue();

        // Same again
        userDocPerms.clearPermission(DOC_UUID_1);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.OWNER))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.DELETE))
                .isFalse();
        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_1, DocumentPermission.EDIT))
                .isFalse();

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isTrue();

        userDocPerms.clearPermission(DOC_UUID_2);

        assertThat(userDocPerms.hasDocumentPermission(DOC_UUID_2, DocumentPermission.VIEW))
                .isFalse();
    }
}
