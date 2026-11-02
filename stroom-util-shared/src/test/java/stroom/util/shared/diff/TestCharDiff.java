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

package stroom.util.shared.diff;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestCharDiff {

    @Test
    void testIdentical() {
        final CharDiff charDiff = CharDiff.of("abc", "abc");
        assertThat(charDiff.getOldRanges()).isEmpty();
        assertThat(charDiff.getNewRanges()).isEmpty();
    }

    @Test
    void testSingleCharacterChange() {
        // In a short word, the word is shown whole.
        final CharDiff charDiff = CharDiff.of("abc", "axc");
        assertThat(charDiff.getOldRanges()).containsExactly(new CharRange(0, 3));
        assertThat(charDiff.getNewRanges()).containsExactly(new CharRange(0, 3));
        // In a longer one, only the changed letter.
        final CharDiff longer = CharDiff.of("abcdefg", "abcXefg");
        assertThat(longer.getOldRanges()).containsExactly(new CharRange(3, 4));
    }

    @Test
    void testInsertionOnly() {
        final CharDiff charDiff = CharDiff.of("ac", "abbbc");
        assertThat(charDiff.getOldRanges()).isEmpty();
        assertThat(charDiff.getNewRanges()).containsExactly(new CharRange(0, 5));
    }

    @Test
    void testDeletionOnly() {
        final CharDiff charDiff = CharDiff.of("hello world", "hello");
        assertThat(charDiff.getOldRanges()).containsExactly(new CharRange(5, 11));
        assertThat(charDiff.getNewRanges()).isEmpty();
    }

    @Test
    void testFromAndToEmpty() {
        assertThat(CharDiff.of("", "abc").getNewRanges()).containsExactly(new CharRange(0, 3));
        assertThat(CharDiff.of("abc", "").getOldRanges()).containsExactly(new CharRange(0, 3));
    }

    @Test
    void testSeparateChanges() {
        final CharDiff charDiff = CharDiff.of("one fish two fish", "One fish two dish");
        // "one" is shown whole; "fish" leaves three letters out, so only its first is.
        assertThat(charDiff.getOldRanges()).containsExactly(new CharRange(0, 3), new CharRange(13, 14));
        assertThat(charDiff.getNewRanges()).containsExactly(new CharRange(0, 3), new CharRange(13, 14));
    }

    @Test
    void testShortGapsAreJoined() {
        // The 'a' between the changes is a gap of one, so the two changes become one.
        final CharDiff charDiff = CharDiff.of("xay", "XaY");
        assertThat(charDiff.getOldRanges()).containsExactly(new CharRange(0, 3));
        assertThat(charDiff.getNewRanges()).containsExactly(new CharRange(0, 3));
    }

    @Test
    void aChangedWordIsShownWholeThoughItSharesLettersAtItsEdges() {
        // "Test" and "Development" share their last "t", which alone would leave "Tes" and "Developmen".
        final String oldLine = "<Environment>Test</Environment>";
        final String newLine = "<Environment>Development</Environment>";
        final CharDiff charDiff = CharDiff.of(oldLine, newLine);

        assertThat(charDiff.getOldRanges()).singleElement().satisfies(range ->
                assertThat(oldLine.substring(range.getStart(), range.getEnd())).isEqualTo("Test"));
        assertThat(charDiff.getNewRanges()).singleElement().satisfies(range ->
                assertThat(newLine.substring(range.getStart(), range.getEnd())).isEqualTo("Development"));
    }

    @Test
    void testLongLinesOnlyTrimPrefixAndSuffix() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CharDiff.MAX_DIFF_LENGTH; i++) {
            sb.append('a');
        }
        final String common = sb.toString();
        final CharDiff charDiff = CharDiff.of(common + "XbX" + common, common + "YbY" + common);
        final int start = common.length();
        assertThat(charDiff.getOldRanges()).containsExactly(new CharRange(start, start + 3));
        assertThat(charDiff.getNewRanges()).containsExactly(new CharRange(start, start + 3));
    }
}
