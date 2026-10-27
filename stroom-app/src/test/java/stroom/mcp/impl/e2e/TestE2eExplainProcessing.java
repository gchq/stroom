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

package stroom.mcp.impl.e2e;

import stroom.docref.DocRef;
import stroom.explorer.shared.ExplorerNode;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.CreateProcessFilterRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorType;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// explain_processing against a real Stroom: its filters, trackers and tasks, the meta query that says whether a
/// filter selects a stream, and the jobs, nodes and cluster state it reads, for stalls made on purpose (asked for by
/// the user: why is this pipeline not processing?).
class TestE2eExplainProcessing extends AbstractE2eTest {

    private static final String SAMPLE = "time,user,action\n2026-10-28T10:00:00Z,alice,login\n";

    @Inject
    private ProcessorFilterService filterService;

    @Test
    void aDisabledFilterIsFoundFromThePipelineTheFeedAndTheStream() {
        final String feed = "EXPLAIN-DISABLED";
        createFeed("explain-disabled", feed);
        final long stream = upload(feed, SAMPLE);
        final DocRef pipeline = pipeline("Explain Disabled");
        final ProcessorFilter filter = filter(pipeline, feedIs(feed), false);

        final Map<String, Object> byPipeline = explain(Map.of("pipeline_uuid", pipeline.getUuid()));
        assertThat(byPipeline).doesNotContainKey("not_checked");
        final Map<String, Object> state = check(byPipeline, "filter " + filter.getId() + ": filter");
        assertThat(state).containsEntry("state", "blocking");
        assertThat(String.valueOf(state.get("fix"))).contains("Processors tab")
                .contains("set_processor_filter_enabled filter_uuid=" + filter.getUuid());
        assertThat(checks(byPipeline)).extracting(check -> check.get("link"))
                .as("the cluster's links, read from Stroom")
                .contains("nodes", "settings", "job: Data Processor", "job: Processor Task Creator");

        // The stream has no task: the real meta query finds the filter that selects it.
        final Map<String, Object> byStream = explain(Map.of("stream_id", stream));
        assertThat(map(byStream.get("stream"))).containsEntry("feed", feed);
        assertThat(state(byStream, "filter " + filter.getId() + ": filter")).isEqualTo("blocking");

        final Map<String, Object> byFeed = explain(Map.of("feed", feed));
        assertThat(map(byFeed.get("subject"))).containsEntry("explained_through", "its newest stream, " + stream);
    }

    @Test
    void aStreamNoFilterSelectsSaysSo() {
        final String feed = "EXPLAIN-UNSELECTED";
        createFeed("explain-unselected", feed);
        final long stream = upload(feed, SAMPLE);
        // A filter for another feed only.
        filter(pipeline("Explain Other"), feedIs("EXPLAIN-SOMETHING-ELSE"), true);

        final Map<String, Object> selection = check(explain(Map.of("stream_id", stream)), "selection");

        // (The jobs come first in the chain, and integration tests run without Stroom's job bootstrap.)
        assertThat(selection).containsEntry("state", "blocking");
        assertThat(String.valueOf(selection.get("fix"))).contains(feed);
    }

    @Test
    void aFilterInErrorSaysWhyAndThatItIsNeverRetried() {
        // A selection with no terms: Stroom refuses to make tasks from it and puts the tracker in Error.
        final DocRef pipeline = pipeline("Explain Error");
        final ProcessorFilter filter = filter(pipeline, ExpressionOperator.builder().build(), true);
        processAll();

        final Map<String, Object> tracker = check(explain(Map.of("pipeline_uuid", pipeline.getUuid())),
                "filter " + filter.getId() + ": tracker");

        assertThat(tracker).containsEntry("state", "blocking");
        assertThat(String.valueOf(tracker.get("finding"))).contains("Error")
                .contains("Stroom never retries a filter in Error");
    }

    @Test
    void theClusterViewGivesThePrioritiesRule() {
        filter(pipeline("Explain Cluster"), feedIs("EXPLAIN-CLUSTER"), true);

        final Map<String, Object> result = explain(Map.of());

        assertThat(map(result.get("subject"))).containsEntry("kind", "cluster");
        assertThat(String.valueOf(map(result.get("priorities")).get("rule"))).contains("highest priority first");
        assertThat(result.get("mode")).asString().startsWith("master queue");
    }

    // --------------------------------------------------------------------------------

    private Map<String, Object> explain(final Map<String, Object> arguments) {
        final Map<String, Object> result = json("explain_processing", new LinkedHashMap<>(arguments));
        dump("explain_processing " + arguments, result);
        return result;
    }

    private DocRef pipeline(final String name) {
        final ExplorerNode node = create(PipelineDoc.TYPE, name, templateFolder());
        return node.getDocRef();
    }

    private ProcessorFilter filter(final DocRef pipeline, final ExpressionOperator expression, final boolean enabled) {
        return asUser(admin, () -> filterService.create(CreateProcessFilterRequest.builder()
                .processorType(ProcessorType.PIPELINE)
                .pipeline(pipeline)
                .queryData(QueryData.builder()
                        .dataSource(MetaFields.STREAM_STORE_DOC_REF)
                        .expression(expression)
                        .build())
                .priority(10)
                .enabled(enabled)
                .minMetaCreateTimeMs(0L)
                .build()));
    }

    private static ExpressionOperator feedIs(final String feed) {
        return ExpressionOperator.builder()
                .addTextTerm(MetaFields.FEED, Condition.EQUALS, feed)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Object value) {
        return (Map<String, Object>) value;
    }

    // Every check: the cluster's, and each filter's named "filter N: link".
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> checks(final Map<String, Object> result) {
        final List<Map<String, Object>> all = new ArrayList<>((List<Map<String, Object>>) result.get("checks"));
        for (final Map<String, Object> filter : (List<Map<String, Object>>) result.getOrDefault("filters",
                List.of())) {
            for (final Map<String, Object> check : (List<Map<String, Object>>) filter.get("checks")) {
                final Map<String, Object> named = new LinkedHashMap<>(check);
                named.put("link", "filter " + filter.get("id") + ": " + check.get("link"));
                all.add(named);
            }
        }
        return all;
    }

    private static Map<String, Object> check(final Map<String, Object> result, final String link) {
        return checks(result).stream()
                .filter(check -> link.equals(check.get("link")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No check " + link + " in " + checks(result)));
    }

    private static String state(final Map<String, Object> result, final String link) {
        return String.valueOf(check(result, link).get("state"));
    }
}
