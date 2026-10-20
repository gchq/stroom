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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestErrorMessageNormaliser {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Expected 5 fields but found 3|Expected <n> fields but found <n>",
            "Value '123' is not valid for 'Foo'|Value '<value>' is not valid for '<value>'",
            "Value \"abc def\" is too long|Value \"<value>\" is too long",
            "No user 7c9e6679-7425-40de-944b-e07fc1f90ae7 found|No user <uuid> found",
            "Bad date 2026-10-20T01:02:03.000Z in record|Bad date <time> in record",
            "Bad day 2026-10-20 in record|Bad day <time> in record",
            "Event at 12:34:56 was late|Event at <time> was late",
            "Unknown host 10.1.2.3 seen|Unknown host <ip> seen",
            "Can't read /var/data/in/file.xml now|Can't read <path> now",
            "Can't fetch https://example.com/a?b=1 now|Can't fetch <url> now",
            "Checksum 0x1F2E mismatch, id a3f9c2d17e|Checksum <hex> mismatch, id <hex>",
            "Took -1.5 seconds|Took <n> seconds",
            "  lots   of    space  |lots of space",
    })
    void replacesWhatVariesWithPlaceholders(final String message, final String pattern) {
        assertThat(ErrorMessageNormaliser.normalise(message)).isEqualTo(pattern);
    }

    @Test
    void collapsesTheSameProblemWithDifferentValues() {
        assertThat(ErrorMessageNormaliser.normalise("Element 'Foo' at line 12 isn't allowed"))
                .isEqualTo(ErrorMessageNormaliser.normalise("Element 'Bar' at line 345 isn't allowed"));
    }

    @Test
    void keepsDifferentProblemsApart() {
        assertThat(ErrorMessageNormaliser.normalise("Unknown element 'Foo'"))
                .isNotEqualTo(ErrorMessageNormaliser.normalise("Unknown attribute 'Foo'"));
        // Error codes and names with digits in them say what the problem is, so are kept.
        assertThat(ErrorMessageNormaliser.normalise("XTDE0640: circularity"))
                .isEqualTo("XTDE0640: circularity")
                .isNotEqualTo(ErrorMessageNormaliser.normalise("XTDE0050: circularity"));
        assertThat(ErrorMessageNormaliser.normalise("in xsltFilter2")).isEqualTo("in xsltFilter2");
    }

    @Test
    void keepsApostrophesInWords() {
        assertThat(ErrorMessageNormaliser.normalise("Don't know 'x', isn't valid"))
                .isEqualTo("Don't know '<value>', isn't valid");
    }

    @Test
    void givesAnEmptyPatternForNoMessage() {
        assertThat(ErrorMessageNormaliser.normalise(null)).isEmpty();
        assertThat(ErrorMessageNormaliser.normalise("  ")).isEmpty();
    }

    @Test
    void cutsVeryLongPatternsShort() {
        assertThat(ErrorMessageNormaliser.normalise("x".repeat(1_000)))
                .hasSize(ErrorMessageNormaliser.MAX_PATTERN_CHARS + 1)
                .endsWith("…");
    }
}
