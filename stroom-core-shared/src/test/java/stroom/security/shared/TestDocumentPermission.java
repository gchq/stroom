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

package stroom.security.shared;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermission {

    @Test
    void test() {
        final List<DocumentPermission> lowToHighPerms = Arrays.stream(DocumentPermission.values())
                .sorted(Comparator.comparing(DocumentPermission::getPrimitiveValue))
                .toList();

        for (final DocumentPermission heldPerm : lowToHighPerms) {
            for (final DocumentPermission requiredPerm : lowToHighPerms) {
                final byte heldPermByte = heldPerm.getPrimitiveValue();
                final byte requiredPermByte = requiredPerm.getPrimitiveValue();
                if (heldPermByte < requiredPermByte) {
                    assertThat(heldPerm.isHigher(requiredPerm))
                            .isFalse();
                    assertThat(DocumentPermission.highest(heldPerm, requiredPerm))
                            .isEqualTo(requiredPerm);
                } else if (heldPermByte == requiredPermByte) {
                    assertThat(heldPerm)
                            .isEqualTo(requiredPerm);
                    assertThat(DocumentPermission.highest(heldPerm, requiredPerm))
                            .isEqualTo(requiredPerm)
                            .isEqualTo(heldPerm);
                } else {
                    // heldPerm > requiredPerm
                    assertThat(heldPerm.isHigher(requiredPerm))
                            .isTrue();
                    assertThat(DocumentPermission.highest(heldPerm, requiredPerm))
                            .isEqualTo(heldPerm);
                }

                if (heldPermByte >= requiredPermByte) {
                    assertThat(heldPerm.isEqualOrHigher(requiredPerm))
                            .isTrue();
                    assertThat(heldPerm.isEqualOrHigher(requiredPermByte))
                            .isTrue();
                } else {
                    assertThat(heldPerm.isEqualOrHigher(requiredPerm))
                            .isFalse();
                    assertThat(heldPerm.isEqualOrHigher(requiredPermByte))
                            .isFalse();
                }
            }
        }
    }

    /// Assert that permission1 is equal to or higher than permission2.
    private void checkGreaterThanOrEqualTo(final DocumentPermission permission1,
                                           final DocumentPermission permission2) {
        assertThat(permission1.isEqualOrHigher(permission2))
                .isTrue();
        assertThat(permission1.isEqualOrHigher(permission2.getPrimitiveValue()))
                .isTrue();
    }

    @TestFactory
    Stream<DynamicTest> testHighest() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(DocumentPermission.class, DocumentPermission.class)
                .withOutputType(DocumentPermission.class)
                .withTestFunction(testCase -> {
                    final DocumentPermission output1 = DocumentPermission.highest(testCase.getInput()._1,
                            testCase.getInput()._2);
                    final DocumentPermission output2 = DocumentPermission.highest(testCase.getInput()._2,
                            testCase.getInput()._1);
                    Assertions.assertThat(output1)
                            .isEqualTo(output2);
                    return output1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(null, null), null)
                .addCase(Tuple.of(null, DocumentPermission.USE), DocumentPermission.USE)
                .addCase(Tuple.of(DocumentPermission.USE, null), DocumentPermission.USE)
                .addCase(Tuple.of(DocumentPermission.USE, DocumentPermission.USE), DocumentPermission.USE)
                .addCase(Tuple.of(DocumentPermission.EDIT, DocumentPermission.USE), DocumentPermission.EDIT)
                .addCase(Tuple.of(DocumentPermission.USE, DocumentPermission.EDIT), DocumentPermission.EDIT)
                .build();
    }
}
