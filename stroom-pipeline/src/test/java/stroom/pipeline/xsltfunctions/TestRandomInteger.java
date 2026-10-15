/*
 * Copyright 2023 Crown Copyright
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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestRandomInteger extends AbstractXsltFunctionTest<RandomInteger> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestRandomInteger.class);

    private RandomInteger randomInteger = new RandomInteger();

    @Test
    void testNoArgs() {
        final Sequence sequence1 = callFunctionWithSimpleArgs();

        verifyEmptySequence(sequence1);
        final LogArgs logArgs = verifySingleLogCall();
        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
    }

    @Test
    void testZeroUpperBound() {
        final Sequence sequence1 = callFunctionWithSimpleArgs(0);

        verifyEmptySequence(sequence1);
        final LogArgs logArgs = verifySingleLogCall();
        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
    }

    @Test
    void testNegativeUpperBound() {
        final Sequence sequence1 = callFunctionWithSimpleArgs(-1);

        verifyEmptySequence(sequence1);
        final LogArgs logArgs = verifySingleLogCall();
        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
    }

    @Test
    void testValidArg() {
        for (int i = 0; i < 100; i++) {
            final Sequence sequence1 = callFunctionWithSimpleArgs(10);

            verifyNoLogCalls();

            final Optional<Integer> optInt = getAsIntegerValue(sequence1);
            assertThat(optInt)
                    .isNotEmpty();
            assertThat(optInt.get())
                    .isBetween(0, 9);
        }
    }

    @Override
    RandomInteger getXsltFunction() {
        return randomInteger;
    }

    @Override
    String getFunctionName() {
        return RandomInteger.FUNCTION_NAME;
    }
}
