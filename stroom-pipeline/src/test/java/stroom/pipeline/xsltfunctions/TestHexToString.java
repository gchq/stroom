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

import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestHexToString extends AbstractXsltFunctionTest<HexToString> {

    private HexToString hexToString;

    @BeforeEach
    void setUp() {
        hexToString = new HexToString();
    }

    @Test
    void decodeAscii() {
        final Sequence sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6e 67 20 31 32 33", "ASCII");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("ASCII encoded text should be decoded correctly");
    }

    @Test
    void decodeUtf8() {
        final Sequence sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6e 67 20 31 32 33", "UTF-8");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("UTF-8 encoded text should be decoded correctly");
    }

    @Test
    void decodeUtf8UpperCase() {
        final Sequence sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6E 67 20 31 32 33", "UTF-8");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("UTF-8 encoded text should be decoded correctly, regardless of input case");
    }

    @Test
    void decodeUtf16Be() {
        final Sequence sequence = callFunctionWithSimpleArgs(
                "00 74 00 65 00 73 00 74 00 69 00 6e 00 67 00 20 00 31 00 32 00 33",
                "UTF-16BE");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("testing 123")
                .describedAs("UTF-16BE encoded text should be decoded correctly");
    }

    @Test
    void decodeEmptyHex() {
        final Sequence sequence = callFunctionWithSimpleArgs("", "UTF-8");
        assertThat(getAsStringValue(sequence).orElseThrow())
                .isEqualTo("")
                .describedAs("Empty hex value should return an empty string");
    }

    @Test
    void invalidCharset() {
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("74 65 73 74 69 6E 67 20 31 32 33", "invalid charset");
        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("invalid charset");
    }

    @Test
    void invalidHexLength() {
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("7 ", "UTF-8");
        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.WARNING);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("invalid string length");
    }

    @Override
    HexToString getXsltFunction() {
        return hexToString;
    }

    @Override
    String getFunctionName() {
        return HexToString.FUNCTION_NAME;
    }
}
