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

import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolResult;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;

/// get_field_conventions, as the Stroom MCP server's tests/test_indexing.py tests it.
class TestGetFieldConventionsTool {

    private final IndexingWorld world = new IndexingWorld();

    @Test
    @SuppressWarnings("unchecked")
    void elasticsearchAsksForTheUsersExampleFirstOfferingExistingIndexes() {
        world.elasticIndex("k", "Keycloak", "ecs-keycloak-v1", null, false);
        final Map<String, Object> asked = call("{\"backend\": \"elasticsearch\"}", IndexingWorld.noForms());
        final List<Map<String, Object>> options = (List<Map<String, Object>>) asked.get("options");
        assertThat(asked.get("status")).isEqualTo("needs_guidance");
        assertThat(String.valueOf(options.getFirst().get("option"))).startsWith("example index template");
        assertThat(options.get(1).get("option")).isEqualTo("follow an existing index in Stroom");
        assertThat(((List<Map<String, Object>>) options.get(1).get("existing_indexes")).getFirst().get("uuid"))
                .isEqualTo("k");
        // One choice per option, labelled, the template first (an agent offered the rest and dropped the template).
        assertThat(options.stream().map(o -> o.get("choice")).toList()).containsExactly("From an index template",
                "Follow an existing index in Stroom", "ECS (Elastic Common Schema) convention",
                "Stroom flat convention");
        assertThat(String.valueOf(options.get(2).get("how"))).contains("convention=ecs without_example=true");
        assertThat(String.valueOf(asked.get("hint"))).contains("exactly these 4 choices, in this order")
                .contains("From an index template;");
    }

    @Test
    @SuppressWarnings("unchecked")
    void withoutABackendTheOnlyIndexingBackendIsTaken() {
        // Seen: asked without the backend, the agent got the profiles, looked the backend up and asked again.
        final Map<String, Object> elastic = Map.of("uuid", "t1", "name", "Events to Elasticsearch", "path",
                "System", "backend", "elasticsearch");
        Mockito.when(world.pipelineTemplates.find(anyString())).thenReturn(Map.of("candidates", List.of(elastic)));
        Map<String, Object> asked = call("{}", IndexingWorld.noForms());
        assertThat(((List<Map<String, Object>>) asked.get("options")).getFirst().get("choice"))
                .isEqualTo("From an index template");
        assertThat(String.valueOf(asked.get("backend"))).startsWith("elasticsearch");
        assertThat(((List<Map<String, Object>>) asked.get("indexing_templates")).getFirst().get("uuid"))
                .isEqualTo("t1");

        final Map<String, Object> lucene = Map.of("uuid", "t2", "name", "Indexing", "backend", "lucene");
        Mockito.when(world.pipelineTemplates.find(anyString())).thenReturn(Map.of("candidates", List.of(elastic,
                lucene)));
        asked = call("{}", IndexingWorld.noForms());
        // Both backends: the backend alone is asked first. Seen in Ask Stroom AI: given the profiles before the
        // backend, the agent asked the naming in the chat, and draft_index_mapping asked it again in its form.
        assertThat(asked).doesNotContainKey("profiles");
        assertThat(asked.get("question")).isEqualTo("Which backend should the new index use?");
        assertThat(String.valueOf(asked.get("hint"))).contains("Don't ask how fields are named yet");

        // With forms: the backend, then the naming, each asked once, in the tools' forms.
        final ToolCallContext forms = world.forms(question -> Map.of("choice", question.contains("backend")
                ? "Elasticsearch"
                : "ECS (Elastic Common Schema) convention"));
        final Map<String, Object> chosen = call("{}", forms);
        assertThat(chosen.get("status")).isEqualTo("chosen");
        // Remembered: draft_index_mapping convention=ecs doesn't ask again.
        assertThat(world.memory.namingChosen()).containsEntry("convention", "ecs");
        assertThat(world.asked).containsExactly("Which backend should the new index use?",
                "How should the new index's fields be named?");
    }

    @Test
    void followingAnExistingIndexChosenInTheFormIsRemembered() {
        world.elasticIndex("k", "Keycloak", "ecs-keycloak-v1", null, false);
        final Map<String, Object> chosen = call("{\"backend\": \"elasticsearch\"}", world.forms(question ->
                Map.of("choice", "Follow an existing index in Stroom")));
        assertThat(chosen.get("like_index")).isEqualTo("k");
        assertThat(String.valueOf(chosen.get("hint"))).contains("draft_index_mapping like_index=k");
        assertThat(world.memory.namingChosen()).containsEntry("like_index", "k");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aProfileGivesItsFieldMapAndForEcsTheSchema() {
        final Map<String, Object> ecs = call("{\"name\": \"ecs\", \"ecs_fields\": \"process\"}",
                IndexingWorld.noForms());
        assertThat(((Map<String, Object>) ecs.get("profile")).get("field_map")).isNotNull();
        assertThat(((Map<String, Object>) ecs.get("ecs")).get("version")).isNotNull();
        assertThat((Map<String, Object>) ecs.get("ecs_fields")).containsKey("process.command_line");
        final ToolResult unknown = world.conventionsTool().execute(PortedToolCalls.call(
                GetFieldConventionsTool.NAME, "{\"name\": \"nope\"}"), IndexingWorld.noForms());
        assertThat(unknown.isError()).isTrue();
        assertThat(unknown.text()).isEqualTo("No convention profile 'nope'. Profiles: ecs, stroom-flat");
    }

    @Test
    void luceneWithoutFormsListsTheProfiles() {
        final Map<String, Object> asked = call("{\"backend\": \"lucene\"}", IndexingWorld.noForms());
        assertThat(asked.get("status")).isEqualTo("needs_guidance");
        assertThat(asked).containsKey("profiles");
        assertThat(String.valueOf(asked.get("hint"))).startsWith("Ask the user which convention to use");
    }

    private Map<String, Object> call(final String arguments, final ToolCallContext context) {
        final ToolResult result = world.conventionsTool().execute(PortedToolCalls.call(GetFieldConventionsTool.NAME,
                arguments), context);
        assertThat(result.isError()).as(result.text()).isFalse();
        return PortedToolCalls.json(result.text());
    }
}
