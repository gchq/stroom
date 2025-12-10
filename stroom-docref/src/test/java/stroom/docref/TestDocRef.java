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

package stroom.docref;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestDocRef {

    @TestFactory
    Stream<DynamicTest> test() {
        final String uuid1 = "1";
        final String uuid2 = "2";
        final String type1 = "TYPE1";
        final String type2 = "TYPE2";
        final String name1 = "one";
        final String name2 = "two";
        final DocRef docRef = new DocRef(type1, uuid1, name1);

        return TestUtil.buildDynamicTestStream()
                .withInputTypes(DocRef.class, DocRef.class)
                .withOutputType(boolean.class)
                .withTestFunction(testCase -> {
                    final boolean isEqual1 = DocRef.equalsIncludingName(
                            testCase.getInput()._1,
                            testCase.getInput()._2);
                    // Check the reverse
                    final boolean isEqual2 = DocRef.equalsIncludingName(
                            testCase.getInput()._2,
                            testCase.getInput()._1);
                    Assertions.assertThat(isEqual1)
                            .isEqualTo(isEqual2);
                    return isEqual1;
                })
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(docRef, docRef), true)
                .addCase(Tuple.of(null, null), true)
                .addCase(Tuple.of(null, docRef), false)
                .addCase(Tuple.of(docRef, null), false)
                .addCase(Tuple.of(
                                new DocRef(type1, uuid1, name1),
                                new DocRef(type1, uuid1, name1)),
                        true)
                .addCase(Tuple.of(
                                new DocRef(type1, uuid1, name1),
                                new DocRef(type1, uuid1, name2)),
                        false)
                .addCase(Tuple.of(
                                new DocRef(type1, uuid1, name1),
                                new DocRef(type1, uuid2, name1)),
                        false)
                .addCase(Tuple.of(
                                new DocRef(type1, uuid1, name1),
                                new DocRef(type2, uuid1, name1)),
                        false)
                .addCase(Tuple.of(
                                new DocRef(type1, uuid1, name1),
                                new DocRef(type2, uuid2, name1)),
                        false)
                .addCase(Tuple.of(
                                new DocRef(type1, uuid1, name1),
                                new DocRef(type2, uuid2, name2)),
                        false)
                .build();
    }
}
