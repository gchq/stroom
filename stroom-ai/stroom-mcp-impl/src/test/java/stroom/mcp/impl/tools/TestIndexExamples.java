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

import stroom.ai.api.ElasticIndexTemplates.ComponentTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplate;
import stroom.ai.api.ElasticIndexTemplates.IndexTemplates;
import stroom.ai.api.ElasticIndexTemplates.Status;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.sample.ToolJson;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

/// The user's example index template, read through Stroom from an existing index.
class TestIndexExamples {

    private final IndexingWorld world = new IndexingWorld();

    @Test
    @SuppressWarnings("unchecked")
    void anExistingIndexInStroomBecomesTheExampleTemplate() {
        // The doc's own field list carries Elasticsearch's types (live FortiOS-V1: nativeType keyword, long, ip...).
        world.elasticIndex("k", "Keycloak", "ecs-keycloak-v1", null, false);
        world.world.docs.put("k", ((ElasticIndexDoc) world.world.docs.get("k")).copy().fields(List.of(
                field("user.name", "keyword"), field("source.ip", "ip"), field("@timestamp", "date"),
                field("message", "match_only_text"))).build());
        IndexExamples.FromFields read = world.examples.exampleFromIndex("k");
        final String[] parts = read.template().split("\n", 2);
        assertThat(parts[0]).isEqualTo("PUT _index_template/ecs-keycloak-v1");
        final Map<String, Object> template = (Map<String, Object>) ToolJson.loads(parts[1]);
        assertThat(template.get("index_patterns")).isEqualTo(List.of("ecs-keycloak-v1*"));
        final Map<String, Object> props = (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>)
                template.get("template")).get("mappings")).get("properties");
        assertThat(((Map<String, Object>) ((Map<String, Object>) props.get("user")).get("properties")).get("name"))
                .isEqualTo(Map.of("type", "keyword"));
        // The exact type, kept.
        assertThat(props.get("message")).isEqualTo(Map.of("type", "match_only_text"));
        Mockito.verify(world.queryService, Mockito.never()).findFields(any());
        assertThat(read.read()).isEqualTo(Map.of("doc", "Keycloak", "index", "ecs-keycloak-v1", "fields", 4,
                "source", "the index doc's field list, with Elasticsearch's own types", "examples",
                List.of("user.name (keyword)", "source.ip (ip)", "@timestamp (date)", "message (match_only_text)")));
        assertThat(read.note()).contains("Keycloak").contains("index template itself");

        // A doc without its field list: Stroom's own, with Stroom's types mapped.
        world.world.docs.put("k", ((ElasticIndexDoc) world.world.docs.get("k")).copy().fields(List.of()).build());
        Mockito.when(world.queryService.findFields(any())).thenReturn(new ResultPage<>(List.of(
                QueryField.builder().fldName("source.ip").fldType(FieldType.IPV4_ADDRESS).build())));
        read = world.examples.exampleFromIndex("k");
        assertThat(read.template()).contains("{\"ip\": {\"type\": \"ip\"}}");
        assertThat(read.source()).startsWith("Stroom's field list");

        // Nothing listed (an unreachable cluster): no form, the user is to paste the template.
        Mockito.when(world.queryService.findFields(any())).thenReturn(ResultPage.empty());
        assertThatThrownBy(() -> world.examples.exampleFromIndex("k")).isInstanceOf(ToolRefusal.class)
                .hasMessageContaining("paste its index template");
    }

    @Test
    void anIndexTemplateIsReadThroughStroomWithItsComponents() {
        world.elasticIndex("k", "Keycloak", "ecs-keycloak-v1", null, false);
        Mockito.when(world.templates.read(any())).thenReturn(new IndexTemplates("ecs-keycloak-v1", null, null,
                new IndexTemplate(Status.OK, "keycloak", "{\"index_patterns\":[\"ecs-keycloak-v1*\"],"
                                                         + "\"composed_of\":[\"ecs@mappings\",\"org-base\","
                                                         + "\"org-gone\",\"org-secret\"]}", List.of(), null),
                List.of(new ComponentTemplate("ecs@mappings", Status.OK, "{}", null),
                        new ComponentTemplate("org-base", Status.OK, "{\"template\":{}}", null),
                        new ComponentTemplate("org-gone", Status.NOT_FOUND, null, "missing"),
                        new ComponentTemplate("org-secret", Status.FORBIDDEN, null, "no privilege"))));
        final IndexExamples.FromIndex read = world.examples.templateFromIndex("Keycloak").orElseThrow();
        assertThat(read.template()).isEqualTo("PUT _index_template/keycloak\n{\"index_patterns\": "
                                              + "[\"ecs-keycloak-v1*\"], \"composed_of\": [\"ecs@mappings\", "
                                              + "\"org-base\", \"org-gone\", \"org-secret\"]}");
        // ecs@mappings is built in, and known here: not given.
        assertThat(read.components()).containsExactly("PUT _component_template/org-base\n{\"template\": {}}");
        assertThat(read.missing()).containsExactly("org-gone");
        assertThat(read.unread()).isEqualTo(Map.of("org-secret", "no privilege"));

        Mockito.when(world.templates.read(any())).thenReturn(new IndexTemplates("ecs-keycloak-v1", null, null,
                new IndexTemplate(Status.FORBIDDEN, null, null, List.of(), "action [indices:admin/index_template/get] "
                                                                         + "is unauthorized"), List.of()));
        assertThat(world.examples.templateFromIndex("k").orElseThrow().why()).isEqualTo(
                "Stroom couldn't read its index template (the cluster's credentials in Stroom may not read index "
                + "templates: action [indices:admin/index_template/get] is unauthorized)");
    }

    @Test
    void componentTemplatesGivenOnceAreKeptForTheSession() {
        final Map<String, Map<String, Object>> first = world.examples.givenComponents(
                List.of("PUT _component_template/ecs-base {\"template\": {}}"), List.of("ecs-base"), null);
        final Map<String, Map<String, Object>> again = world.examples.givenComponents(List.of(),
                List.of("ecs-base", "other"), null);
        assertThat(first).isEqualTo(again).isEqualTo(Map.of("ecs-base", Map.of("template", Map.of())));
        // Only those the template composes.
        assertThat(world.examples.givenComponents(List.of(), List.of("other"), null)).isEmpty();
    }

    private static ElasticIndexField field(final String name, final String type) {
        return ElasticIndexField.builder().fldName(name).nativeType(type).build();
    }
}
