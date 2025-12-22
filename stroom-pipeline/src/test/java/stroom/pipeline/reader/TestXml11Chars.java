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

package stroom.pipeline.reader;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestXml11Chars {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestXml11Chars.class);

    // TODO: 15/09/2022 AT Uncomment this once createDynamicTestStream is available and
    //  remove isValid() method
//    @TestFactory
//    Stream<DynamicTest> isValid() {
//        return TestUtil.createDynamicTestStream(
//                List.of(
//                        TestCase.biOutput('a', true, true),
//                        TestCase.biOutput('1', true, true),
//                        TestCase.biOutput('.', true, true),
//                        TestCase.biOutput('Σ', true, true), // \u03a3
//                        TestCase.biOutput('\u0080', true, false),
//                        TestCase.biOutput('\u0082', true, false)),
//                testCase -> {
//                    final Character chr = testCase.getInput();
//                    return LogUtil.message("char [{}], unicode (hex): {}", chr, Integer.toHexString(chr));
//                },
//                testCase -> {
//                    final boolean isValid = testCase.getExpectedOutput()._1;
//                    final boolean isValidLiteral = testCase.getExpectedOutput()._2;
//                    doTest(testCase.getInput(), isValid, isValidLiteral);
//                });
//    }

    @Test
    @SuppressWarnings("checkstyle:AvoidEscapedUnicodeCharacters")
    void isValid() {
        doTest('a', true, true);
        doTest('1', true, true);
        doTest('.', true, true);
        doTest('Σ', true, true); // \u03a3
        doTest('\u0080', true, false);
        doTest('\u0082', true, false);
        doTest('\ufffe', false, false);
        doTest('\uffff', false, false);
    }

    @Test
    void testRestrictedChars() {
        // Each pair is an inclusive range
        // From https://www.w3.org/TR/xml11/#NT-RestrictedChar
        final int[] restrictedCharRanges = new int[]{
                0x1, 0x8,
                0xb, 0xc,
                0xe, 0x1f,
                0x7f, 0x84,
                0x86, 0x9f};

        for (int i = 0; i < restrictedCharRanges.length; i += 2) {
            final int fromInc = restrictedCharRanges[i];
            final int toInc = restrictedCharRanges[i + 1];

            for (int j = fromInc; j <= toInc; j++) {
                final char chr = (char) j;
                doTest(chr, true, false);
            }
        }
    }

    private void doTest(final char chr,
                        final boolean isValid,
                        final boolean isValidLiteral) {
        LOGGER.info("char [{}], unicode (hex): {}", chr, Integer.toHexString(chr));

        final boolean validCodePoint = Character.isValidCodePoint(chr);

        Assertions.assertThat(validCodePoint)
                .isTrue();

        final Xml11Chars xml11Chars = new Xml11Chars();

        Assertions.assertThat(xml11Chars.isValid(chr))
                .isEqualTo(isValid);

        Assertions.assertThat(xml11Chars.isValidLiteral(chr))
                .isEqualTo(isValidLiteral);
    }
}
