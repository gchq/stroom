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

package stroom.statistics.impl.sql;


import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSQLSafe {
    @Test
    void testEscapeChars_backslash() {
        final String dirtyString = "ab\\c";
        doEscapeCharsTest(dirtyString, "ab\\\\c");
    }

    @Test
    void testEscapeChars_doubelQuote() {
        final String dirtyString = "a\"bc";
        doEscapeCharsTest(dirtyString, "a\\\"bc");
    }

    @Test
    void testEscapeChars_singleQuote() {
        final String dirtyString = "abc'";
        doEscapeCharsTest(dirtyString, "abc\\'");
    }

    @Test
    void testEscapeChars_lotsOfChars() {
        final String dirtyString = "a\"b\\c'";
        doEscapeCharsTest(dirtyString, "a\\\"b\\\\c\\'");
    }

    private void doEscapeCharsTest(final String dirtyString, final String expectedOutput) {
        final String cleanString = SQLSafe.escapeChars(dirtyString);
        System.out.println(String.format("Dirty string [%s], clean string [%s], expectedOutput [%s]", dirtyString,
                cleanString, expectedOutput));
        assertThat(cleanString).isEqualTo(expectedOutput);
    }

    @Test
    void testCleanWhiteSpace() {
        final String dirty = "a\tb c\nd\fe";
        final String cleanString = SQLSafe.cleanWhiteSpace(dirty);
        assertThat(cleanString).isEqualTo("a b c d e");
    }

    @Test
    void testRegexTerm() {
        final String dirty = "abc\\^$.|?*+()[{def";
        final String expected = "abc\\\\\\^\\$\\.\\|\\?\\*\\+\\(\\)\\[\\{def";
        final String cleaned = SQLSafe.cleanRegexpTerm(dirty);
        System.out.println(dirty);
        System.out.println(cleaned);
        assertThat(cleaned).isEqualTo(expected);
    }
}
