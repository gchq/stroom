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

package stroom.ai.impl.tools.generation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// An extraction's regex is checked as the generated XSLT runs it: XPath's dialect, compiled by Saxon.
class TestXPathRegex {

    @ParameterizedTest
    @CsvSource(delimiterString = " => ", value = {
            "^(\\S+) (\\S+)$ => 2",
            "(?:a)(b) => 1",
            // A letter in XPath (it was refused when checked in another dialect).
            "(\\p{L}+) => 1",
            "[\\p{L}\\d]+ => 0",
            "(a)|(b)|(c) => 3",
            "Action: \\[([^\\]]+)\\] => 1",
            "(?:^|[\\s,;])srcip=([^\\s,;\"]*) => 1",
            // Parentheses in a character class, or escaped, are not groups.
            "[()]+(x) => 1",
            "\\((\\d+)\\) => 1",
            "[a-z-[aeiou]](x) => 1",
            "(a)\\1 => 1"})
    void aRegexXPathCompilesHasItsCaptureGroupsCounted(final String regex, final int groups)
            throws XPathRegex.XPathRegexException {
        assertThat(XPathRegex.groups(regex)).isEqualTo(groups);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "(",
            ")",
            "[a-",
            "[z-a]",
            "*a",
            // Not in XPath's dialect: named groups, lookaround, inline flags.
            "(?P<n>x)",
            "(?=a)",
            "(?<!a)",
            "(?i)a"})
    void aRegexXPathDoesNotCompileIsRefusedWithSaxonsReason(final String regex) {
        assertThatThrownBy(() -> XPathRegex.groups(regex))
                .isInstanceOf(XPathRegex.XPathRegexException.class)
                .hasMessageNotContaining("null");
    }
}
