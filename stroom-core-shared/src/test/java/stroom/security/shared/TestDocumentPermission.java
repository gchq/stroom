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

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestDocumentPermission {

    @Test
    void test() {
        higher(DocumentPermission.OWNER, DocumentPermission.DELETE);
        higher(DocumentPermission.DELETE, DocumentPermission.EDIT);
        higher(DocumentPermission.EDIT, DocumentPermission.VIEW);
        higher(DocumentPermission.VIEW, DocumentPermission.USE);
    }

    private void higher(final DocumentPermission permission1, final DocumentPermission permission2) {
        assertThat(permission1.isEqualOrHigher(permission2)).isTrue();
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
