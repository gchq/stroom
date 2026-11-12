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
import stroom.ai.impl.tools.generation.DispatcherXslt;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    void aDispatchersLinesGiveThePassedOverCountsAndTheNewSourceTypes() throws Exception {
        // As an aggregated feed's dispatcher logs them: counts at the end of each document, a new value once a
        // stream.
        Mockito.when(streamSummaries.errorMarkers(Mockito.eq(10L), anyInt())).thenReturn(List.of(
                info(DispatcherXslt.PASSED_OVER + "40" + DispatcherXslt.PASSED_OVER_OF + "PegaDB"),
                info(DispatcherXslt.PASSED_OVER + "7" + DispatcherXslt.PASSED_OVER_OF + "PegaIntegration"
                     + DispatcherXslt.HANDLED_BY + "pipeline 'PEGA-Integration-Events'"),
                info(DispatcherXslt.PASSED_OVER + "3" + DispatcherXslt.PASSED_OVER_OF
                     + DispatcherXslt.PASSED_OVER_NONE),
                new ErrorTriage.Marker("WARNING", "translationFilter", DispatcherXslt.NEW_SOURCE_TYPE
                                                                       + "sourcetype 'PegaBatch'"
                                                                       + DispatcherXslt.NEW_SOURCE_TYPE_END,
                        null, null, null),
                new ErrorTriage.Marker("WARNING", "translationFilter", DispatcherXslt.NEW_SOURCE_TYPE
                                                                       + "sourcetype (no value)"
                                                                       + DispatcherXslt.NEW_SOURCE_TYPE_END,
                        null, null, null),
                new ErrorTriage.Marker("WARNING", "x", "No event mapping matched record 2 (a=b)", null, null,
                        null)));
        Mockito.when(streamSummaries.errorMarkers(Mockito.eq(11L), anyInt())).thenReturn(List.of(
                info(DispatcherXslt.PASSED_OVER + "2" + DispatcherXslt.PASSED_OVER_OF + "PegaDB"),
                new ErrorTriage.Marker("WARNING", "translationFilter", DispatcherXslt.NEW_SOURCE_TYPE
                                                                       + "sourcetype 'PegaBatch'"
                                                                       + DispatcherXslt.NEW_SOURCE_TYPE_END,
                        null, null, null)));

        final CoverageReads.LoggedRecords found = reads.logged(progress, List.of(
                new CoverageReads.ErrorStream(10, 1L, null, Map.of()),
                new CoverageReads.ErrorStream(11, 2L, null, Map.of())));

        // The records the rules missed are read as before; the dispatcher's lines apart.
        assertThat(found.records()).extracting(CoverageReads.Logged::raw).containsExactly(1L);
        final CoverageReads.Dispatched dispatched = found.dispatched();
        assertThat(dispatched.passedOver()).isEqualTo(Map.of(DispatcherXslt.PASSED_OVER_NONE, 3L, "PegaDB", 42L,
                "PegaIntegration", 7L));
        assertThat(dispatched.handledBy()).containsExactly(Map.entry("PegaIntegration",
                "pipeline 'PEGA-Integration-Events'"));
        assertThat(dispatched.newSourceTypes()).containsExactly(Map.entry("PegaBatch", Set.of(1L, 2L)),
                Map.entry("", Set.of(1L)));
        assertThat(dispatched.field()).isEqualTo("sourcetype");
        assertThat(dispatched.rawStreams()).containsExactly(1L, 2L);
    }

    @Test
    void otherLinesAreNotADispatchers() {
        final CoverageReads.Dispatched dispatched = new CoverageReads.Dispatched();

        assertThat(dispatched.add(1L, "Passed over the record")).isFalse();
        assertThat(dispatched.add(1L, null)).isFalse();
        assertThat(dispatched.add(1L, "New source type: no registry row matches")).isFalse();
        assertThat(dispatched.isEmpty()).isTrue();
    }

    @Test
    void anErrorStreamThatReachedStroomsLimitOfLogLinesIsSaidToBeCapped() throws Exception {
        // From the chat 163 review: 1,000 "Kept as Unknown" lines for 2,003 records, the rest never written.
        final List<ErrorTriage.Marker> full = new ArrayList<>();
        for (int n = 0; n < CoverageReads.MAX_WRITTEN_MARKERS; n++) {
            full.add(new ErrorTriage.Marker("INFO", "x", "Kept as Unknown by rule 'other': record " + n + " (a=b)",
                    null, null, null));
        }
        Mockito.when(streamSummaries.errorMarkers(Mockito.eq(10L), anyInt())).thenReturn(full);
        // As many lines of another severity, none of them logged records: not this check's.
        final List<ErrorTriage.Marker> warnings = new ArrayList<>(Collections.nCopies(
                CoverageReads.MAX_WRITTEN_MARKERS, new ErrorTriage.Marker("WARNING", "x", "Some warning", null, null,
                        null)));
        warnings.add(new ErrorTriage.Marker("INFO", "x", "No event mapping matched record 7 (a=b)", null, null,
                null));
        Mockito.when(streamSummaries.errorMarkers(Mockito.eq(11L), anyInt())).thenReturn(warnings);

        final CoverageReads.LoggedRecords found = reads.logged(progress, List.of(
                new CoverageReads.ErrorStream(10, 1L, null, Map.of()),
                new CoverageReads.ErrorStream(11, 2L, null, Map.of())));

        assertThat(found.capped()).containsExactly(1L);
        assertThat(found.records()).hasSize(CoverageReads.MAX_WRITTEN_MARKERS + 1);
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
        assertThat(kept.caught()).isFalse();
        // A catch-all rule's record: a kind no rule names.
        final CoverageReads.Logged caught = CoverageReads.parseLogged(
                "Caught by rule 'cef_other': record 19642 (variant=cef | cef_name=APPFW_CSRF)");
        assertThat(caught.record()).isEqualTo(19_642);
        assertThat(caught.rule()).isEqualTo("cef_other");
        assertThat(caught.caught()).isTrue();
        assertThat(caught.values()).containsEntry("cef_name", "APPFW_CSRF");
        assertThat(CoverageReads.parseLogged("No event mapping matched record 3 (action=x)").rule()).isNull();
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

    private static ErrorTriage.Marker info(final String message) {
        return new ErrorTriage.Marker("INFO", "translationFilter", message, null, null, null);
    }
}
