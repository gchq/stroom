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

package stroom.contentindex;

import stroom.explorer.shared.StringMatch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRegexPatternValidator {

    @ParameterizedTest
    @ValueSource(strings = {
            "XML\\w*",
            "abc",
            "a.c",
            "(foo|bar)",
            "[a-z]+",
            "",
            "tag:xml",
            "tag:xml XML\\w*"})
    void testValidate_valid(final String regex) {
        assertThatCode(() -> RegexPatternValidator.validate(StringMatch.regex(regex)))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(abc",
            "abc)",
            "[abc",
            "a{2",
            "tag:xml (abc"})
    void testValidate_invalid(final String regex) {
        assertThatThrownBy(() -> RegexPatternValidator.validate(StringMatch.regex(regex)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(RegexPatternValidator.INVALID_REGEX_MESSAGE_PREFIX);
    }

    @Test
    void testValidate_invalidForJavaOnly() {
        // Lucene takes a dangling '*' as a literal, but Java doesn't, so it can't be highlighted
        assertThatThrownBy(() -> RegexPatternValidator.validate(StringMatch.regex("*abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(RegexPatternValidator.INVALID_REGEX_MESSAGE_PREFIX);
    }

    @Test
    void testValidate_notRegex() {
        // Not a regex, so isn't checked as one
        assertThatCode(() -> RegexPatternValidator.validate(StringMatch.contains("(abc")))
                .doesNotThrowAnyException();
    }

    @Test
    void testValidate_null() {
        assertThatCode(() -> RegexPatternValidator.validate(null))
                .doesNotThrowAnyException();
    }
}
