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

package stroom.pipeline.xsltfunctions;


import stroom.test.common.TestUtil;
import stroom.util.shared.Severity;

import io.vavr.Tuple;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

class TestHash extends AbstractXsltFunctionTest<Hash> {

    private final Hash hash = new Hash();


    @TestFactory
    Stream<DynamicTest> testCall_defaultAlgo() {
        // Uses Sha-256
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final Sequence sequence = callFunctionWithSimpleArgs(testCase.getInput());
                    verifyNoLogCalls();
                    return getAsStringValue(sequence)
                            .orElseThrow();
                })
                .withSimpleEqualityAssertion()
                .addCase("test", "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
                .addCase("foobar", "c3ab8ff13720e8ad9047dd39466b3c8974e592c2fa383d4a3960714caef0c4f2")
                .build();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @TestFactory
    Stream<DynamicTest> testCall_customAlgo_noSalt() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final Sequence sequence = callFunctionWithSimpleArgs(
                            testCase.getInput()._1,
                            testCase.getInput()._2);
                    verifyNoLogCalls();
                    return getAsStringValue(sequence)
                            .orElseThrow();
                })
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of("test", "MD5"),
                        "098f6bcd4621d373cade4e832627b4f6")
                .addCase(
                        Tuple.of("test", "SHA-1"),
                        "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3")
                .addCase(
                        Tuple.of("test", "SHA-256"),
                        "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08")
                .addCase(
                        Tuple.of("test", "SHA-512"),
                        "ee26b0dd4af7e749aa1a8ee3c10ae9923f618980772e473f8819a5d4940e0db27ac185f8a0e1d5f84f88bc887fd67b143732c304cc5fa9ad8e6f57f50028a8ff")
                .build();
    }

    @SuppressWarnings("checkstyle:LineLength")
    @TestFactory
    Stream<DynamicTest> testCall_customAlgo_withSalt() {
        return TestUtil.buildDynamicTestStream()
                .withInputTypes(String.class, String.class, String.class)
                .withOutputType(String.class)
                .withTestFunction(testCase -> {
                    final Sequence sequence = callFunctionWithSimpleArgs(
                            testCase.getInput()._1,
                            testCase.getInput()._2,
                            testCase.getInput()._3);

                    verifyNoLogCalls();

                    return getAsStringValue(sequence)
                            .orElseThrow();
                })
                .withSimpleEqualityAssertion()
                .addCase(
                        Tuple.of("test", "MD5", "haveSomeSalt"),
                        "14953be9f11b60a3decd4a40c6eee67a")
                .addCase(
                        Tuple.of("test", "SHA-1", "haveSomeSalt"),
                        "154d1a9610558250d1c2907e25b52f12cf1ee1bc")
                .addCase(
                        Tuple.of("test", "SHA-256", "haveSomeSalt"),
                        "074eb41bbffbd87315fc4095a9f082a646efa1c51e146ebd616e5d47fc3ff9d7")
                .addCase(
                        Tuple.of("test", "SHA-512", "haveSomeSalt"),
                        "7559c1cac090b47296f8d8ab5835202821a0024d113b3c4636648d5dce22213ebc3464bb4003890bc763ad2b4c14a5cf4f84241441aefe140d30d8600e5e2520")
                .build();
    }

    @Test
    void testCall_badAlgo() {
        final Sequence sequence = callFunctionWithSimpleArgs("test", "my-bad-algo");
        Assertions.assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);
        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(
                logArgs,
                Severity.ERROR,
                "my-bad-algo",
                "messagedigest not available");
    }

    @Override
    Hash getXsltFunction() {
        return hash;
    }

    @Override
    String getFunctionName() {
        return Hash.FUNCTION_NAME;
    }
}
