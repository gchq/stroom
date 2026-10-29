/*
 * Copyright 2026 Crown Copyright
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

import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestShannonEntropy extends AbstractXsltFunctionTest<ShannonEntropy> {

    private ShannonEntropy shannonEntropy;

    @BeforeEach
    void setUp() {
        shannonEntropy = new ShannonEntropy();
    }

    @Test
    void call() {
        final Sequence sequence = callFunctionWithSimpleArgs("password");

        final Double entropy = getAsDoubleValue(sequence)
                .orElseThrow();

        assertThat(entropy)
                .isCloseTo(2.75, Offset.offset(0.0001));
    }

    @Test
    void call_emptyString() {
        final Sequence sequence = callFunctionWithSimpleArgs("");

        final Double entropy = getAsDoubleValue(sequence)
                .orElseThrow();

        assertThat(entropy)
                .isEqualTo(0.0);
    }

    @Test
    void call_emptySequence() {
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSequenceArgs(new Sequence[]{EmptyAtomicSequence.getInstance()});

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        // Same warning as other functions that read their argument with getSafeString
        final LogArgs logArgs = verifySingleLogCall();
        assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        assertThat(logArgs.getMessage())
                .containsIgnoringCase("Illegal non string argument");
    }

    @Override
    ShannonEntropy getXsltFunction() {
        return shannonEntropy;
    }

    @Override
    String getFunctionName() {
        return ShannonEntropy.FUNCTION_NAME;
    }
}
