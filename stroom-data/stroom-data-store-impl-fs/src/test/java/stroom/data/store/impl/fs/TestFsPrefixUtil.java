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

package stroom.data.store.impl.fs;

import stroom.test.common.TestUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

class TestFsPrefixUtil {

    @TestFactory
    Stream<DynamicTest> testPadId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(Long.class)
                .withOutputType(String.class)
                .withTestFunction(testCase ->
                        FsPrefixUtil.padId(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, "000")
                .addCase(0L, "000")
                .addCase(1L, "001")
                .addCase(999L, "999")
                .addCase(1_000L, "001000")
                .addCase(1_001L, "001001")
                .addCase(999_999L, "999999")
                .addCase(1_000_000L, "001000000")
                .addCase(999_999_999L, "999999999")
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testDePadId() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(long.class)
                .withTestFunction(testCase ->
                        FsPrefixUtil.dePadId(testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(null, -1L)
                .addCase("", -1L)
                .addCase("0", 0L)
                .addCase("000", 0L)
                .addCase("1", 1L)
                .addCase("001", 1L)
                .addCase("999", 999L)
                .addCase("001000", 1_000L)
                .addCase("999999", 999_999L)
                .addCase("001000000", 1_000_000L)
                .addCase("999999999", 999_999_999L)
                .addCase("000ABC", -1L)
                .addCase("ABC", -1L)
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAppendIdPath_long() {
        final Path root = Paths.get("");
        return TestUtil.buildDynamicTestStream()
                .withInputType(long.class)
                .withOutputType(Path.class)
                .withTestFunction(testCase ->
                        FsPrefixUtil.appendIdPath(root, testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase(0L, root)
                .addCase(1L, root)
                .addCase(1000L, root.resolve("001"))
                .addCase(9999L, root.resolve("009"))
                .addCase(1000000L, root.resolve("001").resolve("000"))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> testAppendIdPath_string() {
        final Path root = Paths.get("");
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(Path.class)
                .withTestFunction(testCase ->
                        FsPrefixUtil.appendIdPath(root, testCase.getInput()))
                .withSimpleEqualityAssertion()
                .addCase("000000", root.resolve("000"))
                .addCase(FsPrefixUtil.padId(1L), root)
                .addCase(FsPrefixUtil.padId(1000L), root.resolve("001"))
                .addCase(FsPrefixUtil.padId(9999L), root.resolve("009"))
                .addCase(FsPrefixUtil.padId(1000000L), root.resolve("001").resolve("000"))
                .build();
    }
}
