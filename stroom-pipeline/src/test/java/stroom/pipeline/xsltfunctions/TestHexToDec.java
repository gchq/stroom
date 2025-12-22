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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestHexToDec extends AbstractXsltFunctionTest<HexToDec> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestHexToDec.class);

    private HexToDec hexToDec;

    @BeforeEach
    void setUp() {
        hexToDec = new HexToDec();
    }

    @Test
    void call() {

        final Sequence sequence = callFunctionWithSimpleArgs("2A");

        // Not sure why it is outputting a StringValue instead of an IntegerValue
        final Long decimalVal = getAsLongValue(sequence)
                .orElseThrow();

        assertThat(decimalVal)
                .isEqualTo(42L);
    }

    @Test
    void call_invalid() {

        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("foobar");

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("error converting")
                .containsIgnoringCase("foobar")
                .containsIgnoringCase("decimal");
    }

    @Override
    HexToDec getXsltFunction() {
        return hexToDec;
    }

    @Override
    String getFunctionName() {
        return HexToDec.FUNCTION_NAME;
    }
}
