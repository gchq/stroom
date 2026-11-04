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

import stroom.ai.api.ElasticIndexPresence;
import stroom.ai.api.ElasticIndexPresence.Presence;
import stroom.ai.api.ElasticIndexPresence.State;
import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/// Whether an Elasticsearch indexing pipeline's index already exists on its cluster before it first indexes.
class TestExistingIndexes {

    private static final PipelineDoc PIPELINE = PipelineDoc.builder().uuid("p1").name("Acme").build();

    @Test
    void theIndexIsLookedUpOnThePipelinesClusterDoc() {
        final List<DocRef> clusters = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        final ExistingIndexes existing = new ExistingIndexes(() -> (cluster, indexName) -> {
            clusters.add(cluster);
            names.add(indexName);
            return Presence.absent();
        });

        assertThat(existing.check(destination("ecs-acme-{year}", "c")).state()).isEqualTo(State.ABSENT);

        assertThat(clusters).containsExactly(new DocRef("ElasticCluster", "c", "ES_DEV"));
        // A variable filled per document is any value.
        assertThat(names).containsExactly("ecs-acme-*");
    }

    @Test
    void whatCantBeLookedUpIsUnknownNeverAbsent() {
        final ElasticIndexPresence failing = Mockito.mock(ElasticIndexPresence.class);
        Mockito.when(failing.check(Mockito.any(), Mockito.anyString())).thenThrow(new IllegalStateException("boom"));
        final ExistingIndexes existing = new ExistingIndexes(() -> failing);

        assertThat(existing.check(destination("ecs-acme-v2", "c"))).isEqualTo(Presence.unknown("boom"));
        // No cluster doc named: not looked up.
        assertThat(existing.check(destination("ecs-acme-v2", null)).state()).isEqualTo(State.UNKNOWN);
        assertThat(existing.check(destination(null, "c")).state()).isEqualTo(State.UNKNOWN);
        Mockito.verify(failing, Mockito.times(1)).check(Mockito.any(), Mockito.anyString());
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ecs-acme-v2|ecs-acme-v2",
            "ecs-acme-{year}|ecs-acme-*",
            "ecs-{source}-{year}.{month}|ecs-*-*.*"
    })
    void anIndexNamesVariablesAreLookedUpAsWildcards(final String index, final String expected) {
        assertThat(ExistingIndexes.lookedUp(index)).isEqualTo(expected);
    }

    @Test
    void theNoteSaysWhatWentOnBeingIndexedInto() {
        final Map<String, Object> destination = destination("ecs-acme-v2", "c");

        assertThat(ExistingIndexes.note(Presence.absent(), destination)).isEmpty();
        assertThat(ExistingIndexes.note(present(List.of(), List.of("ecs-acme-v2"), 0L, null), destination))
                .hasValue("'ecs-acme-v2' already exists on cluster ES_DEV (an index), holding no documents: "
                          + "indexing goes into it, with the mapping it was made with");
        assertThat(ExistingIndexes.note(present(List.of(), List.of("ecs-acme-v2"), null, "refused"), destination))
                .hasValueSatisfying(note -> assertThat(note).contains("couldn't be counted (refused)"));
        assertThat(ExistingIndexes.note(Presence.unknown("unreachable"), destination))
                .hasValueSatisfying(note -> assertThat(note).contains("couldn't be checked (unreachable)"));
    }

    @Test
    void everyDataStreamAndIndexToDeleteFirstIsListed() {
        // An alias is emptied by deleting what it points at: those are listed, not the alias.
        final Presence alias = new Presence(State.PRESENT, List.of("logs-app"), List.of("logs-app-old"),
                List.of("ecs-acme"), 3L, null);

        final Map<String, Object> stop = ExistingIndexes.stop(PIPELINE, destination("ecs-acme", "c"), alias,
                false, "create_processor_filter");

        assertThat(stop).containsEntry("delete_first", List.of("DELETE _data_stream/logs-app",
                "DELETE logs-app-old")).doesNotContainKey("delete_first_more");
        assertThat(requests(stop)).containsExactly("DELETE _data_stream/logs-app", "DELETE logs-app-old");
        assertThat(String.valueOf(stop.get("hint"))).contains("list every data stream and index in delete_first")
                .contains("the cluster admin deletes by hand");
    }

    @Test
    void aNameWithVariablesListsEveryIndexItCouldWriteTo() {
        // Each variable is any value: every index the pattern matches is one the pipeline could write to.
        final Presence years = present(List.of(), List.of("ecs-acme-2024", "ecs-acme-2025"), 40L, null);

        final Map<String, Object> stop = ExistingIndexes.stop(PIPELINE, destination("ecs-acme-{year}", "c"),
                years, false, "create_processor_filter");

        assertThat(stop).containsEntry("status", ExistingIndexes.STATUS)
                .containsEntry("looked_up_as", "ecs-acme-*")
                .containsEntry("delete_first", List.of("DELETE ecs-acme-2024", "DELETE ecs-acme-2025"));
        assertThat(requests(stop)).containsExactly("DELETE ecs-acme-2024", "DELETE ecs-acme-2025");
    }

    @Test
    void aLongListIsCutAndCounted() {
        final List<String> many = IntStream.rangeClosed(1, 25).mapToObj(n -> "ecs-acme-" + n).toList();

        final Map<String, Object> stop = ExistingIndexes.stop(PIPELINE, destination("ecs-acme-{n}", "c"),
                present(List.of(), many, 9L, null), false, "create_processor_filter");

        assertThat((List<?>) stop.get("delete_first")).hasSize(20);
        assertThat(stop).containsEntry("delete_first_more", 5);
        assertThat(requests(stop)).hasSize(20);
        assertThat(String.valueOf(stop.get("hint"))).contains("5 more match ecs-acme-*");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requests(final Map<String, Object> stop) {
        return ((List<Map<String, Object>>) stop.get("user_actions")).stream()
                .map(action -> action.get("request"))
                .toList();
    }

    @Test
    void theWarningIsOnlyForAnIndexHoldingDocuments() {
        final Map<String, Object> destination = destination("ecs-acme-v2", "c");

        assertThat(ExistingIndexes.warning(present(List.of("ecs-acme-v2"), List.of(), 5L, null), destination))
                .hasValueSatisfying(warning -> assertThat(warning).contains("holding 5 document(s)"));
        assertThat(ExistingIndexes.warning(present(List.of("ecs-acme-v2"), List.of(), 0L, null), destination))
                .isEmpty();
        assertThat(ExistingIndexes.warning(present(List.of("ecs-acme-v2"), List.of(), null, "refused"),
                destination)).isEmpty();
        assertThat(ExistingIndexes.warning(Presence.unknown("refused"), destination)).isEmpty();
    }

    private static Presence present(final List<String> dataStreams,
                                    final List<String> indices,
                                    final Long documents,
                                    final String message) {
        return new Presence(State.PRESENT, dataStreams, indices, List.of(), documents, message);
    }

    private static Map<String, Object> destination(final String index, final String clusterUuid) {
        final Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("index name", index);
        destination.put("cluster", "ES_DEV");
        destination.put(IndexTemplateAgreements.CLUSTER_UUID, clusterUuid);
        return destination;
    }
}
