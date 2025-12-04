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

package stroom.util.string;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.string.AceStringMatcher.AceMatchResult;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAceStringMatcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestAceStringMatcher.class);
    public static final String ORANGE = "Orange";
    public static final String PEAR = "Pear";
    public static final String APPLE = "Apple";
    public static final String CRAB_APPLE = "Crab Apple";
    public static final String BANANA = "Banana";
    public static final String FOO = "foo";
    public static final String FOOBAR = "foobar";
    public static final String BAR = "bar";

    @Test
    void filterCompletions() {
        doFiltering(
                List.of(ORANGE, PEAR, APPLE, CRAB_APPLE, BANANA),
                "pp",
                100,
                List.of(APPLE, CRAB_APPLE));
    }

    @Test
    void filterCompletions2() {
        doFiltering(
                List.of(ORANGE, PEAR, APPLE, CRAB_APPLE, BANANA),
                "ae",
                100,
                List.of(APPLE, ORANGE, CRAB_APPLE));
    }

    @Test
    void filterCompletions3() {
        doFiltering(
                List.of(ORANGE, PEAR, APPLE, CRAB_APPLE, BANANA),
                "le",
                100,
                List.of(APPLE, CRAB_APPLE));
    }

    @Test
    void filterCompletions4() {
        final List<AceMatchResult<String>> results = doFiltering(
                List.of(FOO, FOOBAR, BAR),
                "foo",
                100,
                List.of(FOO, FOOBAR));

        assertThat(results)
                .extracting(AceMatchResult::exactMatch)
                .containsExactly(true, true);
    }

    @Test
    void filterCompletions5() {
        final List<AceMatchResult<String>> results = doFiltering(
                List.of(FOO, FOOBAR, BAR),
                "bar",
                100,
                List.of(BAR, FOOBAR));

        assertThat(results)
                .extracting(AceMatchResult::exactMatch)
                .containsExactly(true, false);
    }

    @Test
    void filterCompletions6() {
        final List<AceMatchResult<String>> results = doFiltering(
                List.of("FOO", "foo", "foobar", "FOObar"),
                "foo",
                100,
                List.of("FOO", "foo", "foobar", "FOObar"));

        assertThat(results)
                .extracting(AceMatchResult::exactMatch)
                .containsExactly(true, true, true, true);
    }

    @Test
    void filterCompletions7() {
        final List<AceMatchResult<String>> results = doFiltering(
                List.of("FOO", "foo", "fooBAR", "FOObar"),
                "bar",
                100,
                List.of("fooBAR", "FOObar"));

        assertThat(results)
                .extracting(AceMatchResult::exactMatch)
                .containsExactly(false, false);
    }

    @Test
    void filterCompletions8() {
        final List<AceMatchResult<String>> results = doFiltering(
                List.of("___foo", "_foo_", "foo__", "f_o_o", "f__o__o", "__f__o__o__"),
                "foo",
                100,
                List.of("foo__", "_foo_", "f_o_o", "___foo", "f__o__o", "__f__o__o__"));

        assertThat(results)
                .extracting(AceMatchResult::exactMatch)
                .containsExactly(true, false, false, false, false, false);
    }

    @Test
    void filterCompletions_emptyPattern() {
        doFiltering(
                List.of(ORANGE, PEAR, APPLE, CRAB_APPLE, BANANA),
                "",
                100,
                List.of(APPLE, BANANA, CRAB_APPLE, ORANGE, PEAR));
    }

    @Test
    void filterCompletions_nullPattern() {
        doFiltering(
                List.of(ORANGE, PEAR, APPLE, CRAB_APPLE, BANANA),
                null,
                100,
                List.of(APPLE, BANANA, CRAB_APPLE, ORANGE, PEAR));
    }

    private List<AceMatchResult<String>> doFiltering(final List<String> items,
                                                     final String pattern,
                                                     final int initialScore,
                                                     final List<String> expectedMatches) {
        LOGGER.info("Filtering {} on '{}' with initialScore: {}",
                items, pattern, initialScore);
        List<AceMatchResult<String>> results = AceStringMatcher.filterCompletions(
                items,
                pattern,
                initialScore);

        results = results.stream()
                .sorted(AceStringMatcher.SCORE_DESC_THEN_NAME_COMPARATOR)
                .toList();

        results.stream()
                .map(AceMatchResult::toString)
                .forEach(LOGGER::info);

        assertThat(results)
                .extracting(AceMatchResult::name)
                .containsExactlyElementsOf(expectedMatches);

        return results;
    }
}
