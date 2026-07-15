/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.data.store.impl.fs.s3v1;

import stroom.test.common.TestUtil;

import io.vavr.Tuple;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestFilePadStyle {

    @TestFactory
    Stream<DynamicTest> test() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(FilePadStyle.class, long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        testCase.getInput()._1().padId(testCase.getInput()._2()))
                .withSimpleEqualityAssertion()
                .addCase(Tuple.of(FilePadStyle.TEN_DIGITS, 1L), "0000000001")
                .addCase(Tuple.of(FilePadStyle.TEN_DIGITS, 1234L), "0000001234")
                .addCase(Tuple.of(FilePadStyle.TEN_DIGITS, 1234567L), "0001234567")
                .addCase(Tuple.of(FilePadStyle.TEN_DIGITS, 9_999_999_999L), "9999999999")
                .addCase(Tuple.of(FilePadStyle.MULTIPLE_OF_THREE_DIGITS, 1L), "001")
                .addCase(Tuple.of(FilePadStyle.MULTIPLE_OF_THREE_DIGITS, 1234L), "001234")
                .addCase(Tuple.of(FilePadStyle.MULTIPLE_OF_THREE_DIGITS, 1234567L), "001234567")
                .addCase(Tuple.of(FilePadStyle.MULTIPLE_OF_THREE_DIGITS, 9_999_999_999L), "009999999999")
                .build();
    }
}
