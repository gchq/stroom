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

package stroom.search.elastic.template;

import stroom.search.elastic.template.ElasticIndexTemplatesResponse.ComponentTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.IndexTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.OverlappingTemplate;
import stroom.search.elastic.template.ElasticIndexTemplatesResponse.Status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestElasticIndexTemplateService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private static final String FORBIDDEN_REASON = "action [indices:admin/index_template/get] is unauthorized "
                                                   + "for user [stroom] with effective roles [reader], this "
                                                   + "action is granted by the cluster privileges "
                                                   + "[manage_index_templates,manage,all]";

    private static final String KEYCLOAK_TEMPLATE = """
            {"index_patterns": ["ecs-keycloak-v1*"], "priority": 300,
             "composed_of": ["ecs@mappings", "org-ilm"],
             "template": {"settings": {"number_of_shards": 1}, "mappings": {"dynamic": false}}}""";
    private static final String DEFAULT_TEMPLATE = """
            {"index_patterns": ["ecs-*"], "priority": 100, "composed_of": []}""";
    private static final String LOGS_TEMPLATE = """
            {"index_patterns": ["logs-app*"], "data_stream": {}, "composed_of": []}""";

    private final ElasticIndexTemplateService service = new ElasticIndexTemplateService();

    @Test
    void testConcreteIndex() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1-000003", """
                        {"indices": [{"name": "ecs-keycloak-v1-000003", "attributes": ["open"]}],
                         "aliases": [], "data_streams": []}""")
                .ok("/_index_template", indexTemplates(
                        "ecs-keycloak-v1", KEYCLOAK_TEMPLATE,
                        "logs", LOGS_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .ok("/_component_template/org-ilm", componentTemplate("org-ilm"));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000003", elastic);

        assertThat(response.getIndexName()).isEqualTo("ecs-keycloak-v1-000003");
        assertThat(response.getResolvedName()).isEqualTo("ecs-keycloak-v1-000003");
        assertThat(response.getResolveMessage()).isNull();
        final IndexTemplate indexTemplate = response.getIndexTemplate();
        assertThat(indexTemplate.getStatus()).isEqualTo(Status.OK);
        assertThat(indexTemplate.getName()).isEqualTo("ecs-keycloak-v1");
        assertThat(json(indexTemplate.getBody())).isEqualTo(json(KEYCLOAK_TEMPLATE));
        assertThat(indexTemplate.getOverlapping()).isEmpty();
        assertThat(response.getComponentTemplates())
                .extracting(ComponentTemplate::getName, ComponentTemplate::getStatus)
                .containsExactly(
                        tuple("ecs@mappings", Status.OK),
                        tuple("org-ilm", Status.OK));
        assertThat(json(response.getComponentTemplates().getFirst().getBody()))
                .isEqualTo(json(componentTemplateBody("ecs@mappings")));
    }

    @Test
    void testPattern_newestByCreationDate() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1-*", """
                        {"indices": [{"name": "ecs-keycloak-v1-000001"},
                                     {"name": "ecs-keycloak-v1-000002"},
                                     {"name": "ecs-keycloak-v1-000003"}],
                         "aliases": [], "data_streams": []}""")
                .ok("/ecs-keycloak-v1-*/_settings/index.creation_date", """
                        {"ecs-keycloak-v1-000001": {"settings": {"index": {"creation_date": "1000"}}},
                         "ecs-keycloak-v1-000002": {"settings": {"index": {"creation_date": "3000"}}},
                         "ecs-keycloak-v1-000003": {"settings": {"index": {"creation_date": "2000"}}}}""")
                .ok("/_index_template", indexTemplates("ecs-default", DEFAULT_TEMPLATE));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-*", elastic);

        assertThat(response.getResolvedName()).isEqualTo("ecs-keycloak-v1-000002");
        assertThat(response.getIndexTemplate().getName()).isEqualTo("ecs-default");
    }

    @Test
    void testPattern_newestByNameWhenCreationDatesRefused() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1-*", """
                        {"indices": [{"name": "ecs-keycloak-v1-000003"},
                                     {"name": "ecs-keycloak-v1-000001"},
                                     {"name": "ecs-keycloak-v1-000002"}]}""")
                .forbidden("/ecs-keycloak-v1-*/_settings/index.creation_date", FORBIDDEN_REASON)
                .ok("/_index_template", indexTemplates("ecs-default", DEFAULT_TEMPLATE));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-*", elastic);

        assertThat(response.getResolvedName()).isEqualTo("ecs-keycloak-v1-000003");
        assertThat(response.getResolveMessage()).isNull();
        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.OK);
    }

    @Test
    void testAlias() throws IOException {
        // The alias's indices are listed under the alias, not under indices, as they need not match the name
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak", """
                        {"indices": [],
                         "aliases": [{"name": "ecs-keycloak",
                                      "indices": ["ecs-keycloak-v1-000001", "ecs-keycloak-v1-000002"]}],
                         "data_streams": []}""")
                .ok("/ecs-keycloak/_settings/index.creation_date", """
                        {"ecs-keycloak-v1-000001": {"settings": {"index": {"creation_date": "2000"}}},
                         "ecs-keycloak-v1-000002": {"settings": {"index": {"creation_date": "1000"}}}}""")
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", KEYCLOAK_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .ok("/_component_template/org-ilm", componentTemplate("org-ilm"));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak", elastic);

        assertThat(response.getResolvedName()).isEqualTo("ecs-keycloak-v1-000001");
        assertThat(response.getIndexTemplate().getName()).isEqualTo("ecs-keycloak-v1");
    }

    @Test
    void testDataStream() throws IOException {
        // Templates match the data stream's name, not its backing indices'
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/logs-app", """
                        {"indices": [],
                         "aliases": [],
                         "data_streams": [{"name": "logs-app",
                                           "backing_indices": [".ds-logs-app-2026.10.01-000001",
                                                               ".ds-logs-app-2026.10.08-000002"],
                                           "timestamp_field": "@timestamp"}]}""")
                .ok("/_index_template", indexTemplates(
                        "ecs-keycloak-v1", KEYCLOAK_TEMPLATE,
                        "logs", LOGS_TEMPLATE));

        final ElasticIndexTemplatesResponse response = service.read("logs-app", elastic);

        assertThat(response.getResolvedName()).isEqualTo("logs-app");
        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.OK);
        assertThat(response.getIndexTemplate().getName()).isEqualTo("logs");
        assertThat(response.getComponentTemplates()).isEmpty();
    }

    @Test
    void testDataStream_backingIndexListedUnderIndices() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/.ds-logs-app-*", """
                        {"indices": [{"name": ".ds-logs-app-2026.10.01-000001", "data_stream": "logs-app"}]}""")
                .ok("/_index_template", indexTemplates("logs", LOGS_TEMPLATE));

        final ElasticIndexTemplatesResponse response = service.read(".ds-logs-app-*", elastic);

        assertThat(response.getResolvedName()).isEqualTo("logs-app");
        assertThat(response.getIndexTemplate().getName()).isEqualTo("logs");
    }

    @Test
    void testNothingResolves_templateFoundByPattern() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .respond("/_resolve/index/ecs-keycloak-v1", 404, """
                        {"error": {"type": "index_not_found_exception", "reason": "no such index [ecs-keycloak-v1]"},
                         "status": 404}""")
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", KEYCLOAK_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .ok("/_component_template/org-ilm", componentTemplate("org-ilm"));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1", elastic);

        assertThat(response.getResolvedName()).isNull();
        assertThat(response.getResolveMessage()).isNull();
        assertThat(response.getIndexTemplate().getName()).isEqualTo("ecs-keycloak-v1");
    }

    @Test
    void testNothingResolves_emptyResolution() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1*", """
                        {"indices": [], "aliases": [], "data_streams": []}""")
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", KEYCLOAK_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .ok("/_component_template/org-ilm", componentTemplate("org-ilm"));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1*", elastic);

        assertThat(response.getResolvedName()).isNull();
        assertThat(response.getIndexTemplate().getName()).isEqualTo("ecs-keycloak-v1");
        assertThat(elastic.calls()).doesNotContain("/ecs-keycloak-v1*/_settings/index.creation_date");
    }

    @Test
    void testResolveRefused() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .forbidden("/_resolve/index/ecs-keycloak-v1", FORBIDDEN_REASON)
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", KEYCLOAK_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .ok("/_component_template/org-ilm", componentTemplate("org-ilm"));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1", elastic);

        assertThat(response.getResolvedName()).isNull();
        assertThat(response.getResolveMessage()).isEqualTo(FORBIDDEN_REASON);
        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.OK);
        assertThat(response.getIndexTemplate().getName()).isEqualTo("ecs-keycloak-v1");
    }

    @Test
    void testHighestPriorityApplies_othersOverlapping() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1-000001", """
                        {"indices": [{"name": "ecs-keycloak-v1-000001"}]}""")
                // Listed lowest priority first, so the order Elasticsearch gives does not decide
                .ok("/_index_template", indexTemplates(
                        "catch-all", """
                                {"index_patterns": ["*"]}""",
                        "ecs-default", DEFAULT_TEMPLATE,
                        "ecs-keycloak-v1", KEYCLOAK_TEMPLATE,
                        "logs", LOGS_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .ok("/_component_template/org-ilm", componentTemplate("org-ilm"));

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000001", elastic);

        assertThat(response.getIndexTemplate().getName()).isEqualTo("ecs-keycloak-v1");
        assertThat(response.getIndexTemplate().getOverlapping()).containsExactly(
                new OverlappingTemplate("ecs-default", List.of("ecs-*"), 100L),
                new OverlappingTemplate("catch-all", List.of("*"), 0L));
    }

    @Test
    void testNoTemplateMatches() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/other-index", """
                        {"indices": [{"name": "other-index"}]}""")
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", KEYCLOAK_TEMPLATE));

        final ElasticIndexTemplatesResponse response = service.read("other-index", elastic);

        assertThat(response.getResolvedName()).isEqualTo("other-index");
        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.NOT_FOUND);
        assertThat(response.getIndexTemplate().getName()).isNull();
        assertThat(response.getIndexTemplate().getBody()).isNull();
        assertThat(response.getIndexTemplate().getMessage()).contains("other-index");
        assertThat(response.getComponentTemplates()).isEmpty();
    }

    @Test
    void testTemplatesRefused() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1-000001", """
                        {"indices": [{"name": "ecs-keycloak-v1-000001"}]}""")
                .forbidden("/_index_template", FORBIDDEN_REASON);

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000001", elastic);

        assertThat(response.getResolvedName()).isEqualTo("ecs-keycloak-v1-000001");
        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.FORBIDDEN);
        assertThat(response.getIndexTemplate().getMessage()).isEqualTo(FORBIDDEN_REASON);
        assertThat(response.getComponentTemplates()).isEmpty();
        assertThat(elastic.calls()).noneMatch(call -> call.startsWith("/_component_template"));
    }

    @Test
    void testTemplatesError_nonJsonBodyPassedOn() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .respond("/_index_template", 502, "Bad Gateway from proxy");

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000001", elastic);

        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.ERROR);
        assertThat(response.getIndexTemplate().getMessage()).isEqualTo("Bad Gateway from proxy");
    }

    @Test
    void testTemplatesError_emptyBody() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .respond("/_index_template", 401, "");

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000001", elastic);

        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.ERROR);
        assertThat(response.getIndexTemplate().getMessage()).contains("401");
    }

    @Test
    void testTemplatesUnparseable() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_index_template", "not json");

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000001", elastic);

        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.ERROR);
        assertThat(response.getIndexTemplate().getMessage()).contains("_index_template");
    }

    @Test
    void testComponentTemplatesReportedEach() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_resolve/index/ecs-keycloak-v1-000001", """
                        {"indices": [{"name": "ecs-keycloak-v1-000001"}]}""")
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", """
                        {"index_patterns": ["ecs-keycloak-v1*"],
                         "composed_of": ["ecs@mappings", "org-ilm", "deleted", "broken"]}"""))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .forbidden("/_component_template/org-ilm", FORBIDDEN_REASON)
                .respond("/_component_template/deleted", 404, """
                        {"error": {"type": "resource_not_found_exception",
                                   "reason": "component template matching [deleted] not found"},
                         "status": 404}""")
                .respond("/_component_template/broken", 500, """
                        {"error": {"type": "exception", "reason": "something broke"}, "status": 500}""");

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1-000001", elastic);

        assertThat(response.getIndexTemplate().getStatus()).isEqualTo(Status.OK);
        assertThat(response.getComponentTemplates()).containsExactly(
                new ComponentTemplate("ecs@mappings",
                        Status.OK,
                        MAPPER.writeValueAsString(json(componentTemplateBody("ecs@mappings"))),
                        null),
                new ComponentTemplate("org-ilm", Status.FORBIDDEN, null, FORBIDDEN_REASON),
                new ComponentTemplate("deleted",
                        Status.NOT_FOUND,
                        null,
                        "component template matching [deleted] not found"),
                new ComponentTemplate("broken", Status.ERROR, null, "something broke"));
    }

    @Test
    void testComponentTemplateMissingFromSuccessfulResponse() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", """
                        {"index_patterns": ["ecs-keycloak-v1*"], "composed_of": ["org-ilm"]}"""))
                .ok("/_component_template/org-ilm", """
                        {"component_templates": []}""");

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1", elastic);

        assertThat(response.getComponentTemplates())
                .singleElement()
                .extracting(ComponentTemplate::getStatus)
                .isEqualTo(Status.NOT_FOUND);
    }

    @Test
    void testClusterUnreachable() {
        final ElasticGet unreachable = endpoint -> {
            throw new ConnectException("Connection refused");
        };

        assertThatThrownBy(() -> service.read("ecs-keycloak-v1", unreachable))
                .isInstanceOf(ConnectException.class)
                .hasMessage("Connection refused");
    }

    @Test
    void testBodiesWrittenAsJson() throws IOException {
        final FakeElastic elastic = new FakeElastic()
                .forbidden("/_resolve/index/ecs-keycloak-v1", FORBIDDEN_REASON)
                .ok("/_index_template", indexTemplates("ecs-keycloak-v1", KEYCLOAK_TEMPLATE))
                .ok("/_component_template/ecs%40mappings", componentTemplate("ecs@mappings"))
                .forbidden("/_component_template/org-ilm", FORBIDDEN_REASON);

        final ElasticIndexTemplatesResponse response = service.read("ecs-keycloak-v1", elastic);
        final JsonNode written = MAPPER.readTree(MAPPER.writeValueAsString(response));

        assertThat(written.path("indexTemplate").path("body")).isEqualTo(json(KEYCLOAK_TEMPLATE));
        assertThat(written.path("componentTemplates").path(0).path("body"))
                .isEqualTo(json(componentTemplateBody("ecs@mappings")));
        assertThat(written.path("componentTemplates").path(1).has("body")).isFalse();
        // Written as null rather than left out, so it is always there to read
        assertThat(written.has("resolvedName")).isTrue();
        assertThat(written.path("resolvedName").isNull()).isTrue();
        assertThat(written.path("resolveMessage").asString()).isEqualTo(FORBIDDEN_REASON);
        assertThat(written.path("indexTemplate").path("overlapping").isArray()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "ecs-*, ecs-keycloak-v1, true",
            "ecs-keycloak-v1*, ecs-keycloak-v1, true",
            "ecs-keycloak-v1*, ecs-keycloak-v1-000001, true",
            "ecs-keycloak-v1*, ecs-keycloak-v2, false",
            "*, anything, true",
            "logs-*-prod, logs-app-prod, true",
            "logs-*-prod, logs-app-dev, false",
            "exact, exact, true",
            "exact, exactly, false",
            "a.b, axb, false",
            "a+b*, a+bc, true"})
    void testMatchesIndexPattern(final String pattern, final String name, final boolean expected) {
        assertThat(ElasticIndexTemplateService.matchesIndexPattern(pattern, name)).isEqualTo(expected);
    }

    @Test
    void testMatchesIndexPattern_nulls() {
        assertThat(ElasticIndexTemplateService.matchesIndexPattern(null, "a")).isFalse();
        assertThat(ElasticIndexTemplateService.matchesIndexPattern("a", null)).isFalse();
    }

    private static JsonNode json(final String json) {
        return MAPPER.readTree(json);
    }

    // A GET _index_template response, from alternating names and template bodies
    private static String indexTemplates(final String... namesAndBodies) {
        final List<String> entries = new ArrayList<>();
        for (int i = 0; i < namesAndBodies.length; i += 2) {
            entries.add("{\"name\": \"" + namesAndBodies[i] + "\", \"index_template\": "
                        + namesAndBodies[i + 1] + "}");
        }
        return "{\"index_templates\": [" + String.join(", ", entries) + "]}";
    }

    private static String componentTemplate(final String name) {
        return "{\"component_templates\": [{\"name\": \"" + name + "\", \"component_template\": "
               + componentTemplateBody(name) + "}]}";
    }

    private static String componentTemplateBody(final String name) {
        return "{\"template\": {\"mappings\": {\"_meta\": {\"from\": \"" + name + "\"}}}, \"version\": 1}";
    }

    private static String errorBody(final int status, final String reason) {
        return "{\"error\": {\"type\": \"security_exception\", \"reason\": \"" + reason + "\"}, \"status\": "
               + status + "}";
    }

    // Answers each endpoint as set up, and anything else as Elasticsearch answers an unknown one, 404
    private static final class FakeElastic implements ElasticGet {

        private final Map<String, ElasticGetResponse> responses = new HashMap<>();
        private final List<String> calls = new ArrayList<>();

        FakeElastic ok(final String endpoint, final String body) {
            return respond(endpoint, 200, body);
        }

        FakeElastic forbidden(final String endpoint, final String reason) {
            return respond(endpoint, 403, errorBody(403, reason));
        }

        FakeElastic respond(final String endpoint, final int statusCode, final String body) {
            responses.put(endpoint, new ElasticGetResponse(statusCode, body));
            return this;
        }

        List<String> calls() {
            return calls;
        }

        @Override
        public ElasticGetResponse get(final String endpoint) {
            calls.add(endpoint);
            return responses.getOrDefault(endpoint, new ElasticGetResponse(404, errorBody(404, "not set up")));
        }
    }
}
