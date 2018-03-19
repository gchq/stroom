/*
 * Copyright 2016 Crown Copyright
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

package stroom.statistics.sql;

import org.junit.Assert;
import org.junit.Test;

public class TestSQLSafe {
    @Test
    public void testEscapeChars_backslash() throws Exception {
        final String dirtyString = "ab\\c";
        doEscapeCharsTest(dirtyString, "ab\\\\c");
    }

    @Test
    public void testEscapeChars_doubelQuote() throws Exception {
        final String dirtyString = "a\"bc";
        doEscapeCharsTest(dirtyString, "a\\\"bc");
    }

    @Test
    public void testEscapeChars_singleQuote() throws Exception {
        final String dirtyString = "abc'";
        doEscapeCharsTest(dirtyString, "abc\\'");
    }

    @Test
    public void testEscapeChars_lotsOfChars() throws Exception {
        final String dirtyString = "a\"b\\c'";
        doEscapeCharsTest(dirtyString, "a\\\"b\\\\c\\'");
    }

    private void doEscapeCharsTest(final String dirtyString, final String expectedOutput) {
        final String cleanString = SQLSafe.escapeChars(dirtyString);
        System.out.println(String.format("Dirty string [%s], clean string [%s], expectedOutput [%s]", dirtyString,
                cleanString, expectedOutput));
        Assert.assertEquals(expectedOutput, cleanString);
    }

    @Test
    public void testCleanWhiteSpace() throws Exception {
        final String dirty = "a\tb c\nd\fe";
        final String cleanString = SQLSafe.cleanWhiteSpace(dirty);
        Assert.assertEquals("a b c d e", cleanString);
    }

    @Test
    public void testRegexTerm() throws Exception {
        final String dirty = "abc\\^$.|?*+()[{def";
        final String expected = "abc\\\\\\^\\$\\.\\|\\?\\*\\+\\(\\)\\[\\{def";
        final String cleaned = SQLSafe.cleanRegexpTerm(dirty);
        System.out.println(dirty);
        System.out.println(cleaned);
        Assert.assertEquals(expected, cleaned);
    }
}
