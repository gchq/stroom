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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// The events of a text that is one JSON array, read a character at a time: whole events only, one too long to
/// sample left out (seen in user testing: an OpenWebUI audit stream's newline-free arrays cut inside a `\\u`
/// escape, and refused as "isn't valid JSON").
class TestJsonArrayStream {

    private static final int MAX = 100;

    @Test
    void eachTopLevelItemIsAnEvent() {
        final JsonArrayStream.Read read = read("[{\"a\":1},{\"b\":[1,2,{\"c\":\"]\"}]},\"s\",42,true,null]");

        assertThat(read.events()).containsExactly("{\"a\":1}", "{\"b\":[1,2,{\"c\":\"]\"}]}", "\"s\"", "42", "true",
                "null");
        assertThat(read.closed()).isTrue();
        assertThat(read.ended()).isTrue();
        assertThat(read.skipped()).isZero();
        assertThat(read.text()).isEqualTo("[{\"a\":1},{\"b\":[1,2,{\"c\":\"]\"}]},\"s\",42,true,null]");
    }

    @Test
    void stringsHoldingBracketsCommasAndEscapedQuotesDontEndAnEvent() {
        final String event = "{\"m\":\"a \\\"quoted\\\" ], {x}, [y] \\\\\",\"n\":\"\\\\\"}";

        final JsonArrayStream.Read read = read("[" + event + "," + event + "]");

        assertThat(read.events()).containsExactly(event, event);
        assertThat(ToolJson.loads(read.text())).asList().hasSize(2);
    }

    @Test
    void whiteSpaceAndNewLinesBetweenEventsAndABomBeforeTheArrayArePassedOver() {
        final JsonArrayStream.Read read = read("﻿ \n [\n  {\"a\": 1} ,\n\n  {\"a\": 2}\r\n]\n");

        assertThat(read.events()).containsExactly("{\"a\": 1}", "{\"a\": 2}");
        assertThat(read.trailing()).isFalse();
    }

    @Test
    void anEmptyArrayHasNoEvents() {
        final JsonArrayStream.Read read = read(" [ ] ");

        assertThat(read.events()).isEmpty();
        assertThat(read.closed()).isTrue();
        assertThat(read.text()).isEqualTo("[]");
    }

    @Test
    void anEventLongerThanTheMostIsLeftOutAndReadingGoesOn() {
        final String big = "{\"message\":\"" + "x".repeat(300) + "\",\"n\":1}";

        final JsonArrayStream.Read read = read("[{\"a\":1}," + big + ",{\"a\":2}]");

        assertThat(read.events()).containsExactly("{\"a\":1}", "{\"a\":2}");
        assertThat(read.skipped()).isEqualTo(1);
        assertThat(read.largestSkipped()).isEqualTo(big.length());
        assertThat(read.longestSkippedText()).isEqualTo(300);
        assertThat(read.longestSkippedPath()).isEqualTo("message");
        assertThat(read.seen()).isEqualTo(3);
    }

    @Test
    void theLongestStringOfALeftOutEventIsMeasuredDecodedWithItsDottedField() {
        // \\u00e9 is one character, \\" one: 200 x's, an é and a quote.
        final String big = "{\"a\":\"short\",\"request\":{\"body\":\"" + "x".repeat(200) + "\\u00e9\\\"\"},"
                           + "\"tags\":[\"" + "y".repeat(150) + "\"]}";

        final JsonArrayStream.Read read = read("[" + big + "]");

        assertThat(read.skipped()).isEqualTo(1);
        assertThat(read.longestSkippedText()).isEqualTo(202);
        assertThat(read.longestSkippedPath()).isEqualTo("request.body");
    }

    @Test
    void anArrayItemsStringIsNamedAsTheProfileNamesIt() {
        final String big = "{\"tags\":[\"" + "y".repeat(150) + "\"]}";

        final JsonArrayStream.Read read = read("[" + big + "]");

        assertThat(read.longestSkippedPath()).isEqualTo("tags[]");
    }

    @Test
    void thirtyEventsWithTheSecondAndSeventhTooLongGiveTheOtherTwentyEightWhole() throws IOException {
        final List<String> events = IntStream.range(0, 30)
                .mapToObj(i -> i == 1 || i == 6
                        ? "{\"n\":" + i + ",\"m\":\"" + "z".repeat(500) + "\"}"
                        : "{\"n\":" + i + "}")
                .toList();

        final JsonArrayStream.Read read = JsonArrayStream.read(new StringReader("[" + String.join(",", events)
                                                                                + "]"), MAX, 1_000_000, () -> false);

        assertThat(read.events()).hasSize(28).doesNotContain(events.get(1), events.get(6))
                .containsExactlyElementsOf(events.stream().filter(event -> event.length() <= MAX).toList());
        assertThat(read.skipped()).isEqualTo(2);
        assertThat(read.full()).isFalse();
        assertThat(read.ended()).isTrue();
        assertThat(ToolJson.loads(read.text())).asList().hasSize(28);
    }

    @Test
    void charactersNotACountDecideHowManyEventsAreKept() throws IOException {
        final String events = IntStream.range(0, 100).mapToObj(i -> "{\"n\":" + (100 + i) + "}")
                .collect(Collectors.joining(",", "[", "]"));

        // Each event is 9 characters: 50 fit in 450.
        final JsonArrayStream.Read read = JsonArrayStream.read(new StringReader(events), MAX, 450, () -> false);

        assertThat(read.events()).hasSize(50);
        assertThat(read.full()).isTrue();
        assertThat(read.ended()).isFalse();
    }

    @Test
    void aPartThatEndsFirstGivesWhatItHas() throws IOException {
        final JsonArrayStream.Read read = JsonArrayStream.read(new StringReader("[{\"a\":1},{\"a\":2}]"), MAX,
                1_000_000, () -> false);

        assertThat(read.events()).hasSize(2);
        assertThat(read.full()).isFalse();
        assertThat(read.ended()).isTrue();
    }

    @Test
    void anEventThatDoesntFitTheBudgetIsLeftForALaterReadNotCut() throws IOException {
        final JsonArrayStream.Read read = JsonArrayStream.read(new StringReader("[{\"a\":11111},{\"a\":22222}]"),
                MAX, 15, () -> false);

        assertThat(read.events()).containsExactly("{\"a\":11111}");
        assertThat(read.full()).isTrue();
    }

    @Test
    void aPartEndingInsideAnEventDropsItAndSaysSo() {
        final JsonArrayStream.Read read = read("[{\"a\":1},{\"a\":\"cut here \\u00");

        assertThat(read.events()).containsExactly("{\"a\":1}");
        assertThat(read.unterminated()).isTrue();
        assertThat(read.unterminatedChars()).isEqualTo("{\"a\":\"cut here \\u00".length());
        assertThat(read.closed()).isFalse();
        assertThat(read.ended()).isTrue();
    }

    @Test
    void aPartEndingInsideAnEventTooLongToKeepDropsItToo() {
        final JsonArrayStream.Read read = read("[{\"a\":1},{\"a\":\"" + "q".repeat(500));

        assertThat(read.events()).containsExactly("{\"a\":1}");
        assertThat(read.unterminated()).isTrue();
        assertThat(read.skipped()).isZero();
    }

    @Test
    void anArrayNeverClosedKeepsItsWholeEvents() {
        final JsonArrayStream.Read read = read("[{\"a\":1},{\"a\":2}");

        assertThat(read.events()).hasSize(2);
        assertThat(read.unterminated()).isFalse();
        assertThat(read.closed()).isFalse();
    }

    @Test
    void textAfterTheArrayIsNotOneArray() {
        // JSON lines of arrays.
        final JsonArrayStream.Read read = read("[{\"a\":1}]\n[{\"a\":2}]\n");

        assertThat(read.trailing()).isTrue();
    }

    @Test
    void textThatIsntAnArrayIsNotRead() {
        final JsonArrayStream.Read read = read("{\"a\":1}");

        assertThat(read.notArray()).isTrue();
        assertThat(read.events()).isEmpty();
    }

    @Test
    void aBareNumberAtTheEndOfAnUnclosedArrayIsAnEvent() {
        assertThat(read("[1, 2").events()).containsExactly("1", "2");
    }

    @Test
    void readingStopsWhenTimeIsUp() throws IOException {
        final String text = IntStream.range(0, 20_000).mapToObj(i -> "{\"n\":" + i + "}")
                .collect(Collectors.joining(",", "[", "]"));

        final JsonArrayStream.Read read = JsonArrayStream.read(new StringReader(text), MAX, 10_000_000,
                () -> true);

        assertThat(read.timedOut()).isTrue();
        assertThat(read.events()).isNotEmpty().allMatch(event -> event.startsWith("{\"n\":") && event.endsWith("}"));
    }

    private static JsonArrayStream.Read read(final String text) {
        try {
            return JsonArrayStream.read(new StringReader(text), MAX, 1_000_000, () -> false);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
