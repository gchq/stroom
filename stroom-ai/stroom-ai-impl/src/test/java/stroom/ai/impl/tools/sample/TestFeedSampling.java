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

import stroom.meta.shared.Meta;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestFeedSampling {

    private static Meta meta(final long id, final long createMs, final Status status) {
        return Meta.builder().id(id).createMs(createMs).status(status).feedName("F").typeName("Raw Events").build();
    }

    private static boolean leavesOutDeleted(final ExpressionOperator expression) {
        if (expression.getChildren() == null) {
            return false;
        }
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof final ExpressionOperator operator
                && (operator.equals(FeedSampling.notDeleted()) || leavesOutDeleted(operator))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void spreadOrderCoversTheRangeEarly() {
        assertThat(FeedSampling.spreadOrder(5)).containsExactly(4, 0, 2, 1, 3);
        assertThat(FeedSampling.spreadOrder(10)).containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 10).boxed().toList());
        assertThat(FeedSampling.spreadOrder(10).subList(0, 3)).containsExactly(9, 0, 4);
        assertThat(FeedSampling.spreadOrder(0)).isEmpty();
        assertThat(FeedSampling.spreadOrder(1)).containsExactly(0);
    }

    @Test
    void aFeedsDeletedStreamsAreLeftOutByStroomNotAfter() {
        // Seen in Ask Stroom AI: TWITTER's oldest stream was deleted; the one row asked for was dropped as deleted,
        // and survey_feed failed with "list index out of range".
        final Meta live = meta(73146, 1_700_000_000_000L, Status.UNLOCKED);
        final Meta gone = meta(73100, 1_600_000_000_000L, Status.DELETED);
        final FeedSampling.StreamFinder finder = (expression, limit, newestFirst) -> {
            final List<Meta> kept = leavesOutDeleted(expression)
                    ? List.of(live)
                    : List.of(live, gone);
            final List<Meta> ordered = new ArrayList<>(kept);
            if (!newestFirst) {
                Collections.reverse(ordered);
            }
            return ordered.subList(0, Math.min(limit, ordered.size()));
        };

        final FeedSampling.Spread spread = FeedSampling.pickSpread(finder, ExpressionOperator.builder().build(), 3,
                Set.of(), message -> {
                });

        assertThat(spread.picked()).containsExactly(live);
        assertThat(spread.span().get("oldest")).isEqualTo(spread.span().get("newest"))
                .isEqualTo("2023-11-14T22:13:20.000Z");
    }

    @Test
    void findingAFeedsStreamsSaysSo() {
        // Seen: 28 stream searches, 2.4 s each, most of a 35 s survey_feed call.
        final Meta stream = meta(6, 1_700_000_000_000L, Status.UNLOCKED);
        final List<String> said = new ArrayList<>();

        final FeedSampling.Spread spread = FeedSampling.pickSpread((expression, limit, newestFirst) -> List.of(stream),
                ExpressionOperator.builder().build(), 3, Set.of(), said::add);

        assertThat(spread.picked()).containsExactly(stream);
        assertThat(said.getFirst()).isEqualTo("Finding up to 3 streams spread over the feed's time range");
    }

    @Test
    void streamsArePickedSpreadOverTheFeedsLifetimeAndUnreadOnes() {
        final List<Meta> metas = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            metas.add(meta(100 + i, 1_000_000L * i, Status.UNLOCKED));
        }
        // A finder that honours neither the time slice nor the order: whatever it gives, read streams are skipped
        // and none is picked twice.
        final FeedSampling.StreamFinder finder = (expression, limit, newestFirst) ->
                metas.subList(0, Math.min(limit, metas.size()));

        final FeedSampling.Spread spread = FeedSampling.pickSpread(finder, ExpressionOperator.builder().build(), 4,
                Set.of(100L), message -> {
                });

        assertThat(spread.picked()).hasSize(4).doesNotHaveDuplicates().noneMatch(m -> m.getId() == 100L);
    }

    @Test
    void aStreamReadThroughItsBytesSaysWhatWasReplaced() {
        final Map<String, Object> report = SampleProfiler.map("encoding", "UTF-8", "undecodable_bytes", 3,
                "byte_values", List.of("93"), "as_windows_1252", Map.of("93", "“"), "examples", List.of(),
                "advice", "Tell the user");

        assertThat(FeedSampling.charsetEntry(report)).isEqualTo(Map.of("undecodable_bytes", SampleProfiler.map(
                "encoding", "UTF-8", "undecodable_bytes", 3, "as_windows_1252", Map.of("93", "“"), "advice",
                "Tell the user")));
        assertThat(FeedSampling.charsetEntry(null)).isEmpty();
    }

    @Test
    void theHintSaysWhatToDoNext() {
        assertThat(FeedSampling.hint(true, false, true)).isEqualTo("No new kinds of event in these streams. The feed "
                                                                    + "looks covered: run the broad check (step_sample "
                                                                    + "over a few whole streams with "
                                                                    + "records_per_stream).");
        assertThat(FeedSampling.hint(false, false, true)).endsWith("Read more with skip_stream_ids=every stream read "
                                                                    + "so far.");
        assertThat(FeedSampling.hint(true, true, true)).contains("so the feed looks covered.")
                .startsWith("Translate every shape");
        assertThat(FeedSampling.hint(false, true, false)).endsWith("until saturated.");
        assertThat(FeedSampling.iso(1_760_000_000_123L)).isEqualTo("2025-10-09T08:53:20.123Z");
    }

    @Test
    void aSurveyDocMadeByTheCallJustBeforeIsMergedIntoNotMadeAgain() {
        // Seen: two survey calls back to back; the explorer had not yet listed the first call's doc when the second
        // began, and the second's creating it again was refused as a twin.
        final FeedSampling.SurveyRecords records = Mockito.mock(FeedSampling.SurveyRecords.class);
        final Map<String, Object> earlierState = SurveyDoc.merge(null, SampleProfiler.map("feed", "F",
                "streams_read", List.of(1L)), Map.of("a", SampleProfiler.map("count", 1, "streams", List.of(1L),
                "examples", List.of())), 3, "then");
        final FeedSampling.SurveyRecord earlier = new FeedSampling.SurveyRecord("doc", "F - Survey",
                SurveyDoc.render(earlierState));
        Mockito.when(records.load("b", "F")).thenReturn(Optional.of(earlier));
        Mockito.when(records.write(Mockito.eq(earlier), Mockito.anyString())).thenAnswer(invocation ->
                new FeedSampling.SurveyRecord("doc", "F - Survey", invocation.getArgument(1)));

        final Map<String, Object> out = FeedSampling.keep(records, "b", null, null,
                new LinkedHashMap<>(SampleProfiler.map("feed", "F", "streams_read", List.of(2L))), Map.of(), 3);

        Mockito.verify(records, Mockito.never()).create(Mockito.any(), Mockito.any(), Mockito.any());
        assertThat(out.get("survey_doc")).isEqualTo(SampleProfiler.map("type", "Documentation", "uuid", "doc",
                "name", "F - Survey", "kinds_of_event", 1, "streams_read_in_total", 2));
    }

    @Test
    void aFirstSurveyMakesTheBuildsSurveyDoc() {
        final FeedSampling.SurveyRecords records = Mockito.mock(FeedSampling.SurveyRecords.class);
        Mockito.when(records.load("b", "F")).thenReturn(Optional.empty());
        Mockito.when(records.create(Mockito.eq("b"), Mockito.eq("F - Survey"), Mockito.anyString())).thenAnswer(
                invocation -> new FeedSampling.SurveyRecord("new", "F - Survey", invocation.getArgument(2)));
        final Map<String, Map<String, Object>> shapes = Map.of("fields:a", SampleProfiler.map("count", 2,
                "streams", List.of(5L), "examples", List.of()));

        final Map<String, Object> out = FeedSampling.keep(records, "b", null, null,
                new LinkedHashMap<>(SampleProfiler.map("feed", "F", "streams_read", List.of(5L))), shapes, 2);

        assertThat(((Map<?, ?>) out.get("survey_doc")).get("uuid")).isEqualTo("new");
        assertThat(FeedSampling.keep(records, null, null, null, new LinkedHashMap<>(Map.of("feed", "F")), shapes, 2))
                .doesNotContainKey("survey_doc");
    }
}
