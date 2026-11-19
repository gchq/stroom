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

package stroom.query.client.result;

import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextHighlights {

    @Test
    void testNoInputOrTerms() {
        assertThat(TextHighlights.find(null, Set.of("a"))).isEmpty();
        assertThat(TextHighlights.find("abc", null)).isEmpty();
        assertThat(TextHighlights.find("abc", Collections.emptySet())).isEmpty();
    }

    @Test
    void testFindsTermIgnoringCase() {
        final List<TextRange> highlights = TextHighlights.find("one Two three", Set.of("two"));

        assertThat(highlights).containsExactly(range(1, 5, 1, 7));
    }

    @Test
    void testFindsTermOnLaterLine() {
        final List<TextRange> highlights = TextHighlights.find("first\nsecond word", Set.of("word"));

        assertThat(highlights).containsExactly(range(2, 8, 2, 11));
    }

    @Test
    void testSkipsMarkup() {
        // The term inside the element tag and the escaped element isn't highlighted
        final List<TextRange> highlights = TextHighlights.find(
                "<foo attr=\"x\">foo </foo> &lt;foo&gt; foo",
                Set.of("foo"));

        assertThat(highlights).containsExactly(range(1, 15, 1, 17), range(1, 38, 1, 40));
    }

    @Test
    void testSortsHighlightsOfSeveralTerms() {
        final Set<String> terms = new LinkedHashSet<>();
        terms.add("c");
        terms.add(null);
        terms.add("a");

        final List<TextRange> highlights = TextHighlights.find("a b c", terms);

        assertThat(highlights).containsExactly(range(1, 1, 1, 1), range(1, 5, 1, 5));
    }

    private static TextRange range(final int fromLine, final int fromCol, final int toLine, final int toCol) {
        return new TextRange(new DefaultLocation(fromLine, fromCol), new DefaultLocation(toLine, toCol));
    }
}
