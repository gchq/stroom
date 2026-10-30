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

package stroom.query.language.functions;

import org.assertj.core.data.Offset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestShannonEntropyCalculator {

    private static final Offset<Double> OFFSET = Offset.offset(0.0001);

    @ParameterizedTest
    @MethodSource("referenceValues")
    void testCalculate(final String input, final double expected) {
        assertThat(ShannonEntropyCalculator.calculate(input))
                .isCloseTo(expected, OFFSET);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void testCalculate_nullOrEmpty(final String input) {
        assertThat(ShannonEntropyCalculator.calculate(input))
                .isEqualTo(0.0);
    }

    // Some arrangements of the formula give -0.0 for one distinct character, which displays as -0.
    // Boxed equality is used because it treats 0.0 and -0.0 as different, unlike isCloseTo().
    @ParameterizedTest
    @ValueSource(strings = {"a", "aaaaaaaa", "é", "😀😀"})
    void testCalculate_singleCharacterIsPositiveZero(final String input) {
        assertThat(Double.valueOf(ShannonEntropyCalculator.calculate(input)))
                .isEqualTo(Double.valueOf(0.0));
    }

    // Each case differs only in case, so the scores must differ.
    @ParameterizedTest
    @ValueSource(strings = {"aA", "Google.COM"})
    void testCalculate_caseSensitive(final String input) {
        assertThat(ShannonEntropyCalculator.calculate(input))
                .isNotCloseTo(ShannonEntropyCalculator.calculate(input.toLowerCase()), OFFSET);
    }

    static Stream<Arguments> referenceValues() {
        return Stream.of(
                Arguments.of("abababab", 1.0),
                Arguments.of("abcd", 2.0),
                Arguments.of("google", 1.9183),
                Arguments.of("password", 2.75),
                Arguments.of("Hello, World!", 3.1808),
                Arguments.of("xkq7zp2vbnwr", 3.5850),
                Arguments.of("aGVsbG8gd29ybGQgdGhpcyBpcyBiYXNlNjQ=", 4.3712),
                Arguments.of("12345", 2.3219),
                Arguments.of("aA", 1.0),
                // Counted by code point, not by UTF-8 byte (which would give 2.8402)
                Arguments.of("日本語日本", 1.5219),
                // A surrogate pair is one character, not two halves
                Arguments.of("😀a", 1.0));
    }
}
