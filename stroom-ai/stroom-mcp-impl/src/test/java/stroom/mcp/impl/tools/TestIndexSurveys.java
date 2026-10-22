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
import stroom.ai.impl.tools.IndexSearches;
import stroom.ai.impl.tools.IndexSearches.Found;
import stroom.ai.impl.tools.IndexSearches.Probe;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.ai.impl.tools.StreamReader;
import stroom.docref.DocRef;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.Column;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.search.elastic.shared.ElasticIndexDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/// What an existing index holds, surveyed through Stroom, ported from the Stroom MCP server's
/// `tests/test_fielddoc.py` (`survey_index`, `index_profile`, `writes_to`).
class TestIndexSurveys {

    private static final DocRef WIDE = new DocRef(ElasticIndexDoc.TYPE, "i", "wide");

    private final IndexSearches indexSearches = Mockito.mock(IndexSearches.class);
    private final IndexFields indexFields = Mockito.mock(IndexFields.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final PipelineIndex pipelineIndex = Mockito.mock(PipelineIndex.class);
    private final IndexSurveys surveys = new IndexSurveys(indexSearches, indexFields, documentAccess,
            Mockito.mock(StreamReader.class), pipelineIndex, Mockito.mock(PipelineShapes.class),
            Mockito.mock(KeptMappings.class), Mockito.mock(FeedNames.class));
    private final List<Probe> searched = new ArrayList<>();
    private final List<Duration> waits = new ArrayList<>();

    @Test
    void aWideIndexIsSurveyedInGroupsOfColumnsJoinedOnTheIds() {
        final List<String> names = new ArrayList<>(List.of("StreamId", "EventId", "@timestamp"));
        for (int n = 0; n < 250; n++) {
            names.add(String.format("f%03d", n));
        }
        final List<Map<String, String>> docs = new ArrayList<>();
        for (final int e : List.of(1, 2)) {
            final Map<String, String> doc = new LinkedHashMap<>();
            doc.put("StreamId", "7");
            doc.put("EventId", String.valueOf(e));
            doc.put("@timestamp", "2026-10-0" + e + "T00:00:00.000Z");
            for (int n = 0; n < 250; n++) {
                doc.put(String.format("f%03d", n), "v" + n + "-" + e);
            }
            docs.add(doc);
        }
        elastic("wide", "wide", "@timestamp");
        fields(names);
        answer(probe -> {
            List<Map<String, String>> held = docs;
            if (probe.expression().toString().contains("day()-30d")) {
                // The recent window: one document, too few to fill the sample.
                held = docs.subList(1, 2);
            } else if (probe.expression().getOp() == ExpressionOperator.Op.OR) {
                final List<Map<String, String>> wanted = new ArrayList<>();
                for (final ExpressionItem pair : probe.expression().getChildren()) {
                    final Map<String, String> ids = new LinkedHashMap<>();
                    ((ExpressionOperator) pair).getChildren().forEach(term -> ids.put(
                            ((ExpressionTerm) term).getField(), ((ExpressionTerm) term).getValue()));
                    wanted.add(ids);
                }
                held = docs.stream().filter(doc -> wanted.contains(Map.of("StreamId", doc.get("StreamId"),
                        "EventId", doc.get("EventId")))).toList();
            }
            final List<String> columns = probe.columns().stream().map(Column::getName).toList();
            final List<Map<String, String>> rows = new ArrayList<>();
            for (final Map<String, String> doc : held.reversed()) {
                final Map<String, String> row = new LinkedHashMap<>();
                columns.forEach(column -> row.put(column, doc.get(column)));
                rows.add(row);
            }
            return new Found(rows, List.of(), true);
        });

        final Map<String, Object> survey = surveys.surveyIndex(PortedToolCalls.context(), WIDE, 100, false);

        assertThat(searched).allSatisfy(probe -> {
            assertThat(probe.columns()).hasSizeLessThanOrEqualTo(100);
            assertThat(probe.columns().subList(0, 3).stream().map(Column::getName))
                    .containsExactly("StreamId", "EventId", "@timestamp");
            // Never sorted: Stroom sorts a table itself, reading every document in the index to do it.
            assertThat(probe.columns()).allSatisfy(column -> assertThat(column.getSort()).isNull());
        });
        // The recent window first; with too few there, any documents; then the other groups, by the first's ids.
        assertThat(searched.subList(0, 2).stream().map(probe -> probe.expression().toString().contains("day()-30d")))
                .containsExactly(true, false);
        assertThat(survey.get("sampled_from")).isEqualTo("any time");
        assertThat(searched).hasSize(4);
        assertThat(searched.subList(2, 4)).allSatisfy(probe -> {
            assertThat(probe.expression().getOp()).isEqualTo(ExpressionOperator.Op.OR);
            assertThat(probe.expression().getChildren()).hasSize(2);
        });
        assertThat((List<?>) survey.get("surveyed_fields")).hasSize(253);
        assertThat(((Map<?, ?>) survey.get("populated")).get("f249")).isEqualTo(100.0);
        assertThat(((Map<?, ?>) survey.get("values")).get("f249")).isEqualTo(List.of("v249-2", "v249-1"));
        assertThat(survey.get("documents_sampled")).isEqualTo(2);

        // A recent window that fills the sample is the sample, and the survey asks for no more.
        searched.clear();
        final Map<String, Object> recent = surveys.surveyIndex(PortedToolCalls.context(), WIDE, 1, false);
        assertThat(recent.get("sampled_from")).isEqualTo("the last 30 days");
        assertThat(recent).doesNotContainKey("profile");
        // The first group, then two by id.
        assertThat(searched.stream().map(probe -> probe.expression().toString().contains("day()-30d")))
                .containsExactly(true, false, false);
    }

    @Test
    void aCountCutShortSaysSo() {
        answer(probe -> new Found(List.of(Map.of("documents", "1200")), List.of(), false));

        final Map<String, Object> profile = surveys.indexProfile(PortedToolCalls.context(), new DocRef(
                ElasticIndexDoc.TYPE, "i", "x"), null, List.of("f"), 20, null);

        assertThat(profile.get("documents")).isEqualTo(1200L);
        assertThat((String) profile.get("partial")).contains("not the whole index");
    }

    @Test
    void aSampleStroomIsStillLookingForIsCutShortAndSaysSo() {
        // Seen: 38 s for any 100 documents of an index written mostly from outside Stroom, whose hits Stroom drops.
        elastic("debug", "debug*", "@timestamp");
        fields(List.of("StreamId", "EventId", "@timestamp"));
        answer(probe -> new Found(List.of(Map.of("StreamId", "7", "EventId", "1", "@timestamp",
                "2026-10-01T00:00:00.000Z")), List.of(), false));

        final Map<String, Object> survey = surveys.surveyIndex(PortedToolCalls.context(), WIDE, 100, false);

        assertThat(waits).hasSize(2).allSatisfy(wait -> assertThat(wait).isLessThanOrEqualTo(
                IndexSurveys.SAMPLE_WAIT));
        assertThat(survey.get("documents_sampled")).isEqualTo(1);
        assertThat((String) survey.get("note")).contains("what Stroom had found after 10 s");

        // None it can show: the streams were deleted, the index kept. No last try with a term once Stroom was cut
        // short.
        waits.clear();
        answer(probe -> new Found(List.of(), List.of(), probe.expression().toString().contains("day()-30d")));
        final Map<String, Object> none = surveys.surveyIndex(PortedToolCalls.context(), WIDE, 100, false);
        assertThat(waits).hasSize(2);
        assertThat(none.get("documents_sampled")).isEqualTo(0);
        assertThat((String) none.get("note")).startsWith("Stroom found no document it could show within 10 s");
    }

    @Test
    void aLuceneIndexWithNothingToShowSaysItsStreamsMayBeGone() {
        // Stroom checks every hit's stream for Lucene as for Elasticsearch: a dev Stroom's streams were deleted in a
        // migration, its indices kept.
        final DocRef lucene = new DocRef(LuceneIndexDoc.TYPE, "i", "LUCENE-IDX");
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(LuceneIndexDoc.class))).thenReturn(
                Optional.of(LuceneIndexDoc.builder().uuid("i").name("LUCENE-IDX").build()));
        fields(List.of("StreamId", "EventId"));
        answer(probe -> new Found(List.of(), List.of(), true));

        final Map<String, Object> survey = surveys.surveyIndex(PortedToolCalls.context(), lucene, 100, false);

        assertThat(survey.get("documents_sampled")).isEqualTo(0);
        assertThat((String) survey.get("note")).contains("or that have been deleted");
    }

    @Test
    void aPipelineWritesToAnIndexByItsEffectivePropertiesNotAMention() {
        final Map<ElementProperty, Object> lucene = Map.of(new ElementProperty("indexingFilter", "index"),
                Map.of("type", "Index", "uuid", "u1"));
        assertThat(IndexDocs.writesTo(lucene, "Index", "u1", null)).isTrue();
        assertThat(IndexDocs.writesTo(lucene, "Index", "u2", null)).isFalse();
        final Function<String, Map<ElementProperty, Object>> named = value -> Map.of(
                new ElementProperty("elasticIndexingFilter", "indexName"), value);
        assertThat(IndexDocs.writesTo(named.apply("foo-v1"), "ElasticIndex", "x", "foo-v1")).isTrue();
        assertThat(IndexDocs.writesTo(named.apply("foo-v10"), "ElasticIndex", "x", "foo-v1")).isFalse();
        // Built from values, and a pattern or alias.
        assertThat(IndexDocs.writesTo(named.apply("ecs-windows{_suffix}v1"), "ElasticIndex", "x",
                "ecs-windows-dc-v1")).isTrue();
        assertThat(IndexDocs.writesTo(named.apply("ecs-windows-v1"), "ElasticIndex", "x", "ecs-windows*")).isTrue();
        assertThat(IndexDocs.writesTo(Map.of(new ElementProperty("xsltFilter", "xslt"), "foo-v1"), "ElasticIndex",
                "x", "foo-v1")).isFalse();
    }

    private void elastic(final String name, final String indexName, final String timeField) {
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(ElasticIndexDoc.class))).thenReturn(
                Optional.of(ElasticIndexDoc.builder().uuid("i").name(name).indexName(indexName).timeField(timeField)
                        .build()));
    }

    private void fields(final List<String> names) {
        Mockito.when(indexFields.indexFields(any())).thenReturn(names.stream()
                .map(name -> (Map<String, Object>) new LinkedHashMap<String, Object>(Map.of("name", name, "type",
                        "keyword")))
                .toList());
    }

    private void answer(final Function<Probe, Found> answer) {
        Mockito.doAnswer(inv -> {
            searched.add(inv.getArgument(1));
            return answer.apply(inv.getArgument(1));
        }).when(indexSearches).search(any(), any(), anyInt(), any());
        Mockito.doAnswer(inv -> {
            searched.add(inv.getArgument(1));
            waits.add(inv.getArgument(4));
            return answer.apply(inv.getArgument(1));
        }).when(indexSearches).search(any(), any(), anyInt(), any(), any());
    }
}
