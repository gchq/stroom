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

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ProcessorFilterAccess;
import stroom.ai.impl.tools.StreamReader;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.cef.CefEvents;
import stroom.ai.impl.tools.generation.XmlElement;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.validation.EventStreams;
import stroom.docref.DocRef;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaRow;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

/// The Stroom MCP server's `tests/test_coverage.py`: whole-feed coverage of an events pipeline, and the pipelines
/// that follow it.
class TestReviewCoverageTool {

    static final String EVENTS = """
            <Events xmlns="event-logging:3">
            <Event><EventSource><User><Id>carol</Id></User></EventSource><EventDetail><TypeId>View</TypeId>
             <View><Resource><Type>Secret</Type><Name>Payroll</Name></Resource></View></EventDetail></Event>
            </Events>""";

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    private final CoverageReads reads = Mockito.mock(CoverageReads.class);
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final PipelineShapes pipelineShapes = Mockito.mock(PipelineShapes.class);
    private final IndexTemplateAgreements agreements = Mockito.mock(IndexTemplateAgreements.class);
    private final PipelineStepping stepping = Mockito.mock(PipelineStepping.class);
    private final EventStreams eventStreams = Mockito.mock(EventStreams.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final ReviewCoverageTool tool = new ReviewCoverageTool(documentAccess, keptMappings, reads,
            processorFilters, pipelineShapes, agreements, stepping, eventStreams, guard);

    @BeforeEach
    void setUp() {
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(PipelineDoc.class))).thenReturn(Optional.of(
                PipelineDoc.builder().uuid("ev").name("Acme-Events").build()));
        Mockito.when(keptMappings.keptXslt(anyString())).thenReturn(Optional.empty());
        Mockito.when(keptMappings.keptMapping(anyString())).thenReturn(Optional.empty());
        Mockito.when(reads.anyEvents(any())).thenReturn(List.of());
        Mockito.when(reads.logged(any(), any())).thenAnswer(inv -> new CoverageReads.LoggedRecords(List.of(),
                ((List<?>) inv.getArgument(1)).size()));
        Mockito.when(reads.sampledUnknown(any(), anyInt(), anyInt())).thenReturn(new CoverageReads.SampledUnknown(
                Map.of(), Map.of(), new TreeSet<>()));
        Mockito.when(guard.tags(any())).thenReturn(Set.of());
        Mockito.when(processorFilters.followOnPipelines(anyString(), any())).thenReturn(List.of());
    }

    @Test
    void followOnPipelinesAreThoseWhoseFiltersReadItsEvents() {
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)).thenReturn(true);
        final DocumentAccess access = Mockito.mock(DocumentAccess.class);
        Mockito.when(access.canView(any())).thenReturn(true);
        final ProcessorFilterService service = Mockito.mock(ProcessorFilterService.class);
        final List<ProcessorListRow> rows = new ArrayList<>();
        rows.add(row(filter(1, "idx", "Acme-Index", false, ExpressionOperator.builder().op(Op.AND)
                .addOperator(ExpressionOperator.builder().op(Op.AND)
                        .addTerm(term("Feed", "ACME"))
                        .addTerm(term("Type", "Events"))
                        .build())
                .addTerm(ExpressionTerm.builder().field("Pipeline").condition(Condition.IS_DOC_REF)
                        .docRef(new DocRef(PipelineDoc.TYPE, "ev")).build()))));
        // Its Events feed, whatever its case.
        rows.add(row(filter(2, "cef", "Acme-CEF", false, ExpressionOperator.builder().op(Op.AND)
                .addTerm(term("Feed", "acme"))
                .addTerm(term("Type", "Events")))));
        // Reads the raw data, not the Events.
        rows.add(row(filter(3, "raw", null, false, ExpressionOperator.builder().op(Op.AND)
                .addTerm(term("Feed", "ACME"))
                .addTerm(term("Type", "Raw Events")))));
        rows.add(row(filter(4, "gone", null, true, ExpressionOperator.builder().op(Op.AND)
                .addTerm(term("Feed", "ACME"))
                .addTerm(term("Type", "Events")))));
        rows.add(row(filter(5, "ev", null, false, ExpressionOperator.builder().op(Op.AND)
                .addTerm(term("Feed", "ACME")))));
        Mockito.when(service.find(any(FetchProcessorRequest.class))).thenReturn(new ResultPage<>(rows));
        final ProcessorFilters filters = new ProcessorFilters(securityContext, () -> service, access,
                new PipelineFeeds(Mockito.mock(ProcessorFilterAccess.class), access,
                        Mockito.mock(StreamReader.class)));

        final List<Map<String, Object>> found = filters.followOnPipelines("ev", Set.of("ACME"));

        assertThat(found).extracting(f -> f.get("uuid"), f -> f.get("filters"))
                .containsExactly(tuple("idx", List.of(1)), tuple("cef", List.of(2)));
    }

    @Test
    void anIndexPlanListsTheEventPathsNoFieldTakes() {
        final FieldPlan plan = FieldPlan.fromJson(indexPlan("x", List.of(
                field("UserId", "keyword", "EventSource/User/Id"),
                field("Resource", "keyword", "EventDetail/*/Resource/Name"))));

        assertThat(ReviewCoverageTool.indexGaps(plan, CefEvents.eventsOf(EVENTS))).containsExactly(
                "EventDetail/TypeId (1 of 1 events)", "EventDetail/View/Resource/Type (1 of 1 events)");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anElasticsearchFollowOnGetsTheAdminsDeleteBeforeReindexing() {
        // Asked for by the user: a reindex is handled carefully. The cluster's admin deletes what the old Events
        // streams put in the index before the affected streams are processed again, or those events are indexed
        // twice.
        final Map<String, Object> plan = indexPlan("ecs-vault-v1", List.of(
                field("StreamId", "long", "@StreamId"),
                field("UserId", "keyword", "EventSource/User/Id")));
        Mockito.when(keptMappings.keptMapping("p-ix")).thenReturn(Optional.of(new KeptMapping.Kept("index", plan)));
        Mockito.when(pipelineShapes.shape("p-ix")).thenReturn(shape("indexing", "elasticsearch"));
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("index name", "ecs-vault-v1");
        destination.put("cluster", "prod-es");
        Mockito.when(agreements.elasticDestination("p-ix")).thenReturn(Optional.of(destination));
        final Map<String, Object> entry = Map.of("uuid", "p-ix", "name", "Vault - Indexing");
        final List<XmlElement> events = CefEvents.eventsOf(EVENTS);

        final Map<String, Object> few = tool.reviewFollowOn(entry, events, Map.of(), List.of(101L, 102L));
        final Map<String, Object> many = tool.reviewFollowOn(entry, events,
                Map.of("from", "2026-10-01T00:00:00Z", "to", "2026-10-02T00:00:00Z"), null);
        final Map<String, Object> none = tool.reviewFollowOn(entry, events, Map.of(), List.of());

        assertThat(few.get("kind")).isEqualTo("index");
        assertThat((List<Object>) few.get("not_indexed"))
                .contains((Object) "EventDetail/View/Resource/Type (1 of 1 events)");
        final Map<?, ?> es = (Map<?, ?>) few.get("elasticsearch");
        assertThat(es.get("index")).isEqualTo("ecs-vault-v1");
        assertThat(es.get("cluster")).isEqualTo("prod-es");
        assertThat((String) es.get("template")).contains("check_index_template");
        assertThat((String) es.get("admin")).contains(
                "POST ecs-vault-v1/_delete_by_query {\"query\": {\"terms\": {\"StreamId\": [101, 102]}}}");
        assertThat((String) ((Map<?, ?>) many.get("elasticsearch")).get("admin"))
                .contains("created from 2026-10-01T00:00:00Z to 2026-10-02T00:00:00Z");
        assertThat(((Map<?, ?>) none.get("elasticsearch")).get("admin"))
                .isEqualTo("No Events streams of the affected raw streams were indexed yet.");
    }

    @Test
    void aCefFollowOnIsGivenWhatItsPlanLacksAndWarnedOfResending() {
        Mockito.when(keptMappings.keptMapping("p-cef")).thenReturn(Optional.of(new KeptMapping.Kept("cef",
                Map.of("output", "text", "vendor", Map.of("value", "Acme"), "product", Map.of("value", "P"),
                        "version", Map.of("value", "1"), "signature", Map.of("source", "EventDetail/TypeId"),
                        "name", Map.of("value", "n"),
                        "common", List.of(Map.of("path", "EventSource/User/Id", "key", "suser"))))));
        Mockito.when(pipelineShapes.shape("p-cef")).thenReturn(shape("forwarding", "text"));

        final Map<String, Object> reviewed = tool.reviewFollowOn(Map.of("uuid", "p-cef"),
                CefEvents.eventsOf(EVENTS), Map.of(), List.of());

        assertThat(reviewed.get("kind")).isEqualTo("cef");
        assertThat((List<?>) reviewed.get("changes")).isNotEmpty();
        assertThat((String) reviewed.get("to_do")).startsWith("draft_cef_mapping uuid=<its XSLT>");
        assertThat((String) reviewed.get("reprocessing")).contains("a second time");
    }

    @Test
    void aFollowOnWithNoPlanIsSteppedByHand() {
        Mockito.when(pipelineShapes.shape("p-x")).thenReturn(shape("forwarding", "kafka"));

        final Map<String, Object> reviewed = tool.reviewFollowOn(Map.of("uuid", "p-x"), List.of(), Map.of(),
                List.of());

        assertThat(reviewed.get("kind")).isEqualTo("hand-written");
        assertThat((String) reviewed.get("to_do")).startsWith("Its XSLT keeps no plan");
    }

    @Test
    void aPipelineThatHasProcessedNothingIsRefused() {
        Mockito.when(reads.errorBatch(any(), anyLong(), anyInt())).thenReturn(new CoverageReads.ErrorBatch(
                List.of(), null));

        final String said = text(tool.execute(call("{\"pipeline_uuid\": \"ev\"}"), new SteppingWorld().context()));

        assertThat(said).contains("Pipeline 'Acme-Events' has processed nothing yet");
    }

    @Test
    void missedRecordsAreGroupedByKindWithTheAffectedStreamsAndWhatToDo() {
        Mockito.when(reads.errorBatch(any(), anyLong(), anyInt())).thenReturn(new CoverageReads.ErrorBatch(
                List.of(new CoverageReads.ErrorStream(10, 3L, 1_790_000_000_000L, Map.of()),
                        new CoverageReads.ErrorStream(11, 4L, 1_790_000_100_000L, Map.of())), null));
        Mockito.doReturn(new CoverageReads.LoggedRecords(List.of(
                new CoverageReads.Logged(3, 1_790_000_000_000L, 1, null, Map.of("action", "view")),
                new CoverageReads.Logged(4, 1_790_000_100_000L, 0, null, Map.of("action", "view")),
                new CoverageReads.Logged(4, 1_790_000_100_000L, 5, "other", Map.of("action", "export"))), 2))
                .when(reads).logged(any(), any());
        // Its XSLT logs Unknown records: none are sampled.
        Mockito.when(keptMappings.keptXslt("ev")).thenReturn(Optional.of(new KeptMappings.KeptXslt(
                new KeptMapping.Kept("index", Map.of()), "translationFilter",
                XsltDoc.builder().uuid("x").name("X").data("Kept as Unknown by rule").build())));

        final Map<String, Object> result = tool.review(new SteppingWorld().context(), new ReviewCoverageTool.Request(
                "ev", null, 500, List.of(), false, null, List.of()));

        final List<?> missed = (List<?>) result.get("missed");
        assertThat(missed).hasSize(2);
        final Map<?, ?> first = (Map<?, ?>) missed.getFirst();
        assertThat(first.get("found_as")).isEqualTo("no rule matched");
        assertThat(first.get("records")).isEqualTo(2);
        assertThat(first.get("raw_streams")).isEqualTo(List.of(3L, 4L));
        assertThat(((Map<?, ?>) missed.get(1)).get("found_as")).isEqualTo("kept as Unknown by rule 'other'");
        final Map<?, ?> affected = (Map<?, ?>) result.get("affected_raw_streams");
        assertThat(affected.get("count")).isEqualTo(2);
        assertThat(affected.get("from")).isEqualTo("2026-09-21T14:13:20Z");
        assertThat(result.get("affected_raw_stream_ids")).isEqualTo(List.of(3L, 4L));
        assertThat((String) result.get("tell_user")).startsWith("Found untranslated events: 3 record(s) of 2 "
                                                                + "kind(s)");
        // In production: a working copy first, and reprocessing is the user's.
        final List<?> next = (List<?>) result.get("next");
        assertThat((String) next.get(1)).contains("copy_pipeline working_copy=true");
        assertThat((String) next.get(3)).startsWith("Processing the affected raw streams again is the user's");
        assertThat(((Map<?, ?>) result.get("progress")).get("done")).isEqualTo(true);
    }

    @Test
    void aBatchCutShortCarriesOnFromTheLastErrorStreamRead() {
        Mockito.when(reads.errorBatch(any(), anyLong(), anyInt())).thenReturn(new CoverageReads.ErrorBatch(
                List.of(new CoverageReads.ErrorStream(10, 3L, null, Map.of()),
                        new CoverageReads.ErrorStream(11, 4L, null, Map.of())), null));
        // The call's time ran out after the first.
        Mockito.doReturn(new CoverageReads.LoggedRecords(List.of(), 1)).when(reads).logged(any(), any());

        final Map<String, Object> result = tool.review(new SteppingWorld().context(), new ReviewCoverageTool.Request(
                "ev", null, 500, List.of(), false, null, List.of()));

        final Map<?, ?> progress = (Map<?, ?>) result.get("progress");
        assertThat(progress.get("error_streams_read")).isEqualTo(1);
        assertThat(progress.get("continue_from")).isEqualTo(10L);
        assertThat(progress.get("done")).isEqualTo(false);
        assertThat((String) result.get("tell_user")).startsWith("No untranslated events (in the 1 Error streams "
                                                                + "this call read; there are more)");
    }

    @Test
    void maxStreamsOutOfRangeIsRefused() {
        final String said = text(tool.execute(call("{\"pipeline_uuid\": \"ev\", \"max_streams\": 9000}"),
                new SteppingWorld().context()));

        assertThat(said).contains("max_streams must be a whole number from 1 to 5000");
    }

    static Map<String, Object> indexPlan(final String index, final List<Map<String, Object>> fields) {
        final Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("backend", "elasticsearch");
        plan.put("index_name", index);
        plan.put("time_field", "@timestamp");
        plan.put("fields", fields);
        return plan;
    }

    static Map<String, Object> field(final String name, final String type, final String source) {
        return Map.of("name", name, "type", type, "source", source);
    }

    private static PipelineShapes.Shape shape(final String stage, final String backend) {
        return new PipelineShapes.Shape(stage, backend, List.of(), null, List.of(), List.of(), List.of(), Map.of());
    }

    private static ProcessorListRow row(final ProcessorFilter filter) {
        return new ProcessorFilterRow(filter);
    }

    private static ProcessorFilter filter(final int id,
                                          final String pipelineUuid,
                                          final String pipelineName,
                                          final boolean deleted,
                                          final ExpressionOperator.Builder expression) {
        return ProcessorFilter.builder()
                .id(id)
                .pipelineUuid(pipelineUuid)
                .pipelineName(pipelineName)
                .deleted(deleted)
                .queryData(QueryData.builder().expression(expression.build()).build())
                .build();
    }

    private static ExpressionTerm term(final String field, final String value) {
        return ExpressionTerm.builder().field(field).condition(Condition.EQUALS).value(value).build();
    }

    static ToolCall call(final String arguments) {
        return new ToolCall("1", ReviewCoverageTool.NAME, arguments);
    }

    static String text(final stroom.ai.impl.tools.ToolResult result) {
        return result.text();
    }

    static MetaRow events(final long id, final String feed) {
        return new MetaRow(Meta.builder().id(id).feedName(feed).build(), null, Map.of());
    }
}
