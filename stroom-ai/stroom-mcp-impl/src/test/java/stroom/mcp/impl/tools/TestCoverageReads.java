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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.CallProgress;
import stroom.ai.impl.tools.ErrorTriage;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.StreamSummaries;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;

/// What review_coverage reads of the Error streams and the streams a pipeline wrote.
class TestCoverageReads {

    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "p", "P");

    private final StreamReader streamReader = Mockito.mock(StreamReader.class);
    private final StreamSummaries streamSummaries = Mockito.mock(StreamSummaries.class);
    private final SampleStreamTexts sampleStreamTexts = Mockito.mock(SampleStreamTexts.class);
    private final CoverageReads reads = new CoverageReads(streamReader, streamSummaries, sampleStreamTexts);
    private final CallProgress progress = CallProgress.of(new SteppingWorld().context());

    @Test
    void missedRecordsComeFromTheErrorStreamsOfTheRawStreamsTheyWereIn() throws Exception {
        final List<CoverageReads.ErrorStream> errors = List.of(
                new CoverageReads.ErrorStream(10, 3L, 1L, Map.of("Warning Count", "2")),
                new CoverageReads.ErrorStream(11, 4L, null, Map.of("Warning Count", "0", "Error Count", "0",
                        "Info Count", "0")),
                // Counts not given: read.
                new CoverageReads.ErrorStream(12, 5L, null, Map.of("Rule", "None")));
        final Map<Long, List<String>> markers = Map.of(
                10L, List.of("Log - No event mapping matched record 1 (action=view | type=)",
                        // An older XSLT's line.
                        "Log - No event mapping matched record 2"),
                12L, List.of("Log - Kept as Unknown by rule 'other': record 0 (action=export)", "some other warning"));
        Mockito.when(streamSummaries.errorMarkers(anyLong(), anyInt())).thenAnswer(inv -> markers
                .getOrDefault((long) inv.getArgument(0), List.of()).stream()
                .map(message -> new ErrorTriage.Marker("WARNING", "translationFilter", message, null, null, null))
                .toList());

        final CoverageReads.LoggedRecords found = reads.logged(progress, errors);

        assertThat(found.records()).extracting(CoverageReads.Logged::raw, CoverageReads.Logged::record,
                        CoverageReads.Logged::rule, CoverageReads.Logged::values)
                .containsExactly(
                        tuple(3L, 1, null, Map.of("action", "view", "type", "")),
                        tuple(3L, 2, null, Map.of()),
                        tuple(5L, 0, "other", Map.of("action", "export")));
        // The counted-clean one isn't read.
        final ArgumentCaptor<Long> read = ArgumentCaptor.forClass(Long.class);
        Mockito.verify(streamSummaries, Mockito.times(2)).errorMarkers(read.capture(), anyInt());
        assertThat(read.getAllValues()).containsExactly(10L, 12L);
        assertThat(found.read()).isEqualTo(3);
    }

    @Test
    void anErrorStreamStroomCantReadLeavesTheOthersCounted() throws Exception {
        Mockito.when(streamSummaries.errorMarkers(Mockito.eq(10L), anyInt()))
                .thenThrow(new java.io.IOException("volume gone"));
        Mockito.when(streamSummaries.errorMarkers(Mockito.eq(11L), anyInt())).thenReturn(List.of(
                new ErrorTriage.Marker("WARNING", "x", "No event mapping matched record 7 (a=b)", null, null, null)));

        final CoverageReads.LoggedRecords found = reads.logged(progress, List.of(
                new CoverageReads.ErrorStream(10, 1L, null, Map.of()),
                new CoverageReads.ErrorStream(11, 2L, null, Map.of()),
                // Not a raw stream's: nothing to reprocess.
                new CoverageReads.ErrorStream(12, null, null, Map.of())));

        assertThat(found.records()).extracting(CoverageReads.Logged::raw).containsExactly(2L);
    }

    @Test
    void aLargeFeedIsReadInBoundedBatchesThatCarryOn() {
        // Seen as a concern: a feed of hundreds of thousands of streams. Pages by Id, at most the budget a call.
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenAnswer(inv -> {
            final long after = Long.parseLong(idTerm(inv.getArgument(0)).getValue());
            final int limit = inv.getArgument(1);
            return new ResultPage<>(LongStream.rangeClosed(1, 1000)
                    .filter(i -> i > after)
                    .limit(limit)
                    .mapToObj(i -> new MetaRow(Meta.builder().id(i).status(Status.UNLOCKED).build(), null, Map.of()))
                    .toList());
        });

        final CoverageReads.ErrorBatch first = reads.errorBatch(PIPELINE, 0, 450);
        assertThat(first.errors()).hasSize(450);
        assertThat(first.more()).isEqualTo(450L);
        // Pages of 200 at most, oldest first.
        Mockito.verify(streamReader, Mockito.times(3)).find(any(), anyInt(), Mockito.eq(false));

        final CoverageReads.ErrorBatch rest = reads.errorBatch(PIPELINE, 450, 1000);
        assertThat(rest.errors()).hasSize(550);
        assertThat(rest.more()).isNull();
        assertThat(rest.errors().getFirst().id()).isEqualTo(451);
    }

    @Test
    void aChangedCopyIsSteppedOverTheFeedsNewestRawStreamsByDefault() {
        // A working copy can't process production streams: the follow-on pipelines are checked against what it
        // gives.
        final List<MetaRow> rows = new ArrayList<>();
        for (final long id : List.of(4L, 9L, 7L, 2L)) {
            rows.add(new MetaRow(Meta.builder().id(id).createMs(id * 1000).build(), null, Map.of()));
        }
        Mockito.when(streamReader.find(any(), anyInt(), anyBoolean())).thenReturn(new ResultPage<>(rows));

        assertThat(reads.newestRaw(List.of(new MetaRow(Meta.builder().feedName("ACME").build(), null, Map.of()))))
                .containsExactly(9L, 7L, 4L);
        final ArgumentCaptor<ExpressionOperator> asked = ArgumentCaptor.forClass(ExpressionOperator.class);
        Mockito.verify(streamReader).find(asked.capture(), anyInt(), anyBoolean());
        assertThat(asked.getValue().getChildren()).extracting(item -> ((ExpressionTerm) item).getField(),
                        item -> ((ExpressionTerm) item).getValue())
                .containsExactly(tuple("Feed", "ACME"),
                        tuple("Type", "Raw Events"));
        assertThat(reads.newestRaw(List.of())).isEmpty();
    }

    @Test
    void aLoggedLineGivesItsRecordRuleAndValues() {
        final CoverageReads.Logged kept = CoverageReads.parseLogged(
                "Kept as Unknown by rule 'other': record 12 (action=view | user=a=b)");
        assertThat(kept.record()).isEqualTo(12);
        assertThat(kept.rule()).isEqualTo("other");
        assertThat(kept.values()).containsExactly(Map.entry("action", "view"), Map.entry("user", "a=b"));
        assertThat(CoverageReads.parseLogged("Some other warning")).isNull();
        assertThat(CoverageReads.parseLogged(null)).isNull();
    }

    @Test
    void aRecordIsShownBriefly() {
        assertThat(CoverageReads.shown(Map.of("a", 1))).isEqualTo("a=1");
        assertThat(CoverageReads.shown("x".repeat(500))).hasSize(400);
    }

    private static ExpressionTerm idTerm(final ExpressionOperator expression) {
        for (final ExpressionItem item : expression.getChildren()) {
            if (item instanceof final ExpressionTerm term && "Id".equals(term.getField())) {
                return term;
            }
        }
        throw new IllegalStateException("No Id term");
    }
}
