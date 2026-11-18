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
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.docref.DocRef;
import stroom.index.shared.IndexResource;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

/// The index's fields as verify_index and write_documentation check them: fields not in the index, fields written but
/// not indexed, and fields that can't be searched.
class TestIndexFields {

    private static final DocRef WEB = new DocRef("ElasticIndex", "i", "web");

    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final KeptMappings keptMappings = Mockito.mock(KeptMappings.class);
    private final IndexingInputs inputs = Mockito.mock(IndexingInputs.class);
    private final IndexFields fields = new IndexFields(() -> queryService, () -> Mockito.mock(IndexResource.class),
            documentAccess, keptMappings, inputs, Mockito.mock(WriteGuard.class), Mockito.mock(IndexPlans.class),
            List.of(Duration.ZERO, Duration.ZERO));

    @Test
    void fieldsAPipelineWritesThatTheIndexLacksAreLookedForAgainThenNamed() {
        // Asked for by the user: an error to put to them (reindex, or check again), not a doc that leaves the field
        // out.
        final Iterator<List<QueryField>> looks = List.of(
                List.of(QueryField.createKeyword("user.name")),
                List.of(QueryField.createKeyword("user.name")),
                List.of(QueryField.createKeyword("user.name"), QueryField.createKeyword("user.role"))).iterator();
        Mockito.when(queryService.findFields(any())).thenAnswer(inv -> new ResultPage<>(looks.next()));
        final Map<String, List<String>> expected = Map.of("Web - Indexing", List.of("user.name", "user.role"));

        // Listed on the third look (Stroom slow to list a field just indexed): nothing missing.
        assertThat(fields.notInIndex(WEB, expected)).isEmpty();
        Mockito.doReturn(new ResultPage<>(List.of(QueryField.createKeyword("user.name")))).when(queryService)
                .findFields(any());
        final Map<String, List<String>> missing = fields.notInIndex(WEB, expected);
        assertThat(missing).isEqualTo(Map.of("Web - Indexing", List.of("user.role")));
        final String said = IndexFields.missingFieldsSaid(missing, "web-v1");
        assertThat(said).contains("has no field user.role (written by 'Web - Indexing')")
                .contains("reindex")
                .contains("check again");
    }

    @Test
    @SuppressWarnings("unchecked")
    void verificationExpectsOnlyTheFieldsTheEventsGiveAValue() {
        // A field no event fills is never mapped where the mapping is dynamic: not a field the index lacks.
        final Map<String, Object> plan = (Map<String, Object>) ToolJson.loads("{\"backend\": \"elasticsearch\", "
                + "\"index_name\": \"web\", \"time_field\": \"@timestamp\", \"fields\": ["
                + "{\"name\": \"StreamId\", \"type\": \"id\", \"source\": \"@StreamId\"},"
                + "{\"name\": \"user.name\", \"type\": \"keyword\", \"source\": \"EventSource/User/Id\"},"
                + "{\"name\": \"user.role\", \"type\": \"keyword\", \"source\": \"EventSource/User/Role\"},"
                + "{\"name\": \"host.name\", \"type\": \"keyword\", \"source\": \"EventSource/Device/HostName\"}]}");
        Mockito.when(keptMappings.keptMapping("p")).thenReturn(Optional.of(new KeptMapping.Kept("index", plan)));
        final Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("path_population", Map.of("EventSource/User/Id", 100.0, "EventSource/Device/HostName", 50.0));
        Mockito.when(inputs.summariseEvents(anyList())).thenReturn(summary);
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(PipelineDoc.class))).thenReturn(Optional.of(
                PipelineDoc.builder().uuid("p").name("Web - Indexing").build()));
        Mockito.when(queryService.findFields(any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createId("StreamId"), QueryField.createKeyword("user.name"))));

        final Map<String, List<String>> lacking = fields.writtenNotIndexed("p", WEB, List.of(9L));

        // user.role: no event has a role, so not expected.
        assertThat(lacking).isEqualTo(Map.of("Web - Indexing", List.of("host.name")));
    }

    @Test
    void aCheckOnAFieldNoSearchFindsIsSaidWhy() {
        final String agreed = KeptMapping.withAgreedTemplate("", Map.of("name", "x", "dev_tools",
                "PUT _index_template/x\n{\"index_patterns\": [\"x*\"], \"template\": {\"mappings\": {\"properties\": "
                + "{\"secret\": {\"type\": \"keyword\", \"index\": false}}}}}"));
        Mockito.when(queryService.findFields(any())).thenReturn(new ResultPage<>(new ArrayList<>(List.of(
                QueryField.createKeyword("secret"), QueryField.createKeyword("hidden", false)))));
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(PipelineDoc.class))).thenReturn(Optional.of(
                PipelineDoc.builder().uuid("p1").name("Web - Indexing").description(agreed).build()));

        final Map<String, String> said = fields.unsearchable(WEB, List.of("secret", "hidden", "user.name"), "p1");

        assertThat(said).isEqualTo(Map.of("hidden", "Stroom lists it as not searchable", "secret",
                "the index template agreed for it doesn't index it (index: false, or an object with enabled: "
                + "false)"));
    }

    @Test
    void columnsNotInTheIndexAreRefusedNamingTheFieldTheyBecame() {
        // Seen in Ask Stroom AI: EventSource.User.Id, the Events path, chosen in place of user.name.
        Mockito.when(queryService.findFields(any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createKeyword("user.name"), QueryField.createId("StreamId"))));
        final Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("backend", "elasticsearch");
        plan.put("index_name", "web");
        plan.put("time_field", "@timestamp");
        plan.put("fields", List.of(Map.of("name", "user.name", "type", "keyword", "source", "EventSource/User/Id")));
        Mockito.when(keptMappings.keptMapping("p")).thenReturn(Optional.of(new KeptMapping.Kept("index", plan)));

        assertThatThrownBy(() -> fields.refuseColumnsNotInIndex(WEB,
                        List.of("user.name", "EventSource.User.Id"), "p"))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("EventSource.User.Id (an Events path: the plan indexes it as user.name)")
                .hasMessageContaining("Its fields: StreamId, user.name");
        fields.refuseColumnsNotInIndex(WEB, List.of("user.name"), "p");
    }
}
