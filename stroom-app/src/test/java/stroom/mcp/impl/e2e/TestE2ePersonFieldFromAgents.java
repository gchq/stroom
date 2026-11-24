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
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.mcp.impl.McpTestClient;
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The person field of a flat Elastic Index that follows no field convention (`EmployeeRef`), named by the site's
/// AGENTS doc beside it (`## Fields`, `- person: EmployeeRef`), over `/mcp` on a real Stroom and Elasticsearch:
/// activity_timeline, sequence_query and compare_activity, each given a person and no personField, read the person's
/// events by EmployeeRef and say the field came from the AGENTS doc. With the doc removed, each says why it can't
/// read the person's events: no field holding the person id was found, and the AGENTS doc's `person:` line would
/// name it.
@SuppressWarnings("unchecked")
class TestE2ePersonFieldFromAgents extends AbstractE2eTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestE2ePersonFieldFromAgents.class);
    private static final ZoneId ZONE = ZoneId.of("Australia/Sydney");
    private static final LocalDate FROM = LocalDate.of(2026, 1, 5);
    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 5, 4);
    private static final LocalDate LAST_DAY = LocalDate.of(2026, 5, 29);
    private static final String PERSON = "E1001";
    private static final String FROM_AGENTS = "the AGENTS doc's ## Fields";
    private static final String NO_PERSON_FIELD = "no field holding the person id was found (a ## Fields 'person:' "
                                                  + "line in the AGENTS doc names it)";

    @Inject
    private ElasticIndexStore elasticIndexStore;

    private ElasticTestNode node;
    private String indexName;

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("person-from-agents");
    }

    @AfterEach
    void removeTheIndex() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void eachToolReadsThePersonFieldTheAgentsDocNames() {
        final ExplorerNode folder = folder("System/E2E Person From AGENTS " + Long.toHexString(System.nanoTime()));
        final DocRef index = elasticIndex(folder);
        final String agents = documentationDoc(folder, "AGENTS", """
                # Staff activity

                ## Fields
                - person: EmployeeRef
                - user: Login
                """);

        // The timeline: by person id, EmployeeRef from the AGENTS doc.
        final Map<String, Object> timeline = json("activity_timeline", timelineArgs(index));
        LOGGER.info(() -> "activity_timeline: " + JsonUtil.writeValueAsString(timeline, true));
        assertThat((List<Map<String, Object>>) timeline.get("sources")).singleElement().satisfies(source -> {
            assertThat(source).containsEntry("user_field", "EmployeeRef").containsEntry("by", "by person id");
            assertThat(String.valueOf(source.get("user_field_from"))).contains(FROM_AGENTS);
            assertThat((Number) source.get("events")).satisfies(events -> assertThat(events.longValue())
                    .isPositive());
        });

        // The sequence: each step restricted to the person by EmployeeRef, from the AGENTS doc.
        final Map<String, Object> sequence = json("sequence_query", sequenceArgs(index));
        LOGGER.info(() -> "sequence_query: " + JsonUtil.writeValueAsString(sequence, true));
        assertThat((List<Map<String, Object>>) sequence.get("scope")).singleElement().satisfies(scope -> {
            assertThat(scope).containsEntry("field", "EmployeeRef").containsEntry("by", "by person id")
                    .containsEntry("values", List.of(PERSON));
            assertThat(String.valueOf(scope.get("field_from"))).contains(FROM_AGENTS);
        });
        assertThat(String.valueOf(sequence.get("rows"))).contains(PERSON).doesNotContain("E1002");

        // The comparison: by person id, EmployeeRef from the AGENTS doc.
        final Map<String, Object> compare = json("compare_activity", compareArgs(index));
        LOGGER.info(() -> "compare_activity: " + JsonUtil.writeValueAsString(compare, true));
        assertThat((List<Map<String, Object>>) compare.get("sources")).singleElement().satisfies(source ->
                assertThat(String.valueOf(source.get("by"))).startsWith("by person id (EmployeeRef from "
                                                                         + FROM_AGENTS));

        // Without the doc: no convention names EmployeeRef, so each says why it can't read the person's events.
        delete(new DocRef(DocumentationDoc.TYPE, agents));
        final CallToolResult timelineWithout = client.call("activity_timeline", timelineArgs(index));
        assertThat(timelineWithout.isError()).isTrue();
        assertThat(McpTestClient.text(timelineWithout)).contains(NO_PERSON_FIELD);
        final CallToolResult sequenceWithout = client.call("sequence_query", sequenceArgs(index));
        assertThat(sequenceWithout.isError()).isTrue();
        assertThat(McpTestClient.text(sequenceWithout)).contains("can't be restricted to the person: "
                                                                 + NO_PERSON_FIELD);
        final CallToolResult compareWithout = client.call("compare_activity", compareArgs(index));
        assertThat(compareWithout.isError()).isTrue();
        assertThat(McpTestClient.text(compareWithout)).contains("No source can be compared: ")
                .contains(NO_PERSON_FIELD);
    }

    private Map<String, Object> timelineArgs(final DocRef index) {
        return args("person", PERSON, "sources", List.of(index.getName()), "timeFrom",
                LAST_DAY.atStartOfDay(ZONE).toOffsetDateTime().toString(), "timeTo",
                LAST_DAY.plusDays(1).atStartOfDay(ZONE).toOffsetDateTime().toString(), "timeZone", ZONE.getId());
    }

    private Map<String, Object> sequenceArgs(final DocRef index) {
        return args("person", PERSON, "steps", List.of(
                Map.of("index", index.getName(), "by", List.of("EmployeeRef"), "where", List.of(Map.of("field",
                        "Action", "value", "Logon"))),
                Map.of("index", index.getName(), "by", List.of("EmployeeRef"), "where", List.of(Map.of("field",
                        "Action", "value", "Export")))), "maxspan", "1h", "timeFrom",
                LAST_DAY.atStartOfDay(ZONE).toInstant().toString(), "timeTo",
                LAST_DAY.plusDays(1).atStartOfDay(ZONE).toInstant().toString());
    }

    private Map<String, Object> compareArgs(final DocRef index) {
        return args("person", PERSON, "sources", List.of(index.getUuid()), "windowFrom", WINDOW_FROM.toString(),
                "windowTo", LAST_DAY.toString(), "baselineFrom", FROM.toString(), "timeZone", ZONE.getId(), "peers",
                false);
    }

    // Two people's working days, each a logon then an export an hour later and a page an hour from 10:00 to 16:00,
    // in an index whose fields follow no convention, in its own folder.
    private DocRef elasticIndex(final ExplorerNode folder) {
        node.createIndex(indexName, """
                {"mappings": {"properties": {
                  "EventTime": {"type": "date"},
                  "EmployeeRef": {"type": "keyword"},
                  "Login": {"type": "keyword"},
                  "Action": {"type": "keyword"},
                  "Workstation": {"type": "keyword"}}}}""");
        final ArrayNode batch = JsonUtil.getMapper().createArrayNode();
        int id = 0;
        for (LocalDate date = FROM; !date.isAfter(LAST_DAY); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (final String person : List.of(PERSON, "E1002")) {
                final List<Object[]> day = new ArrayList<>();
                day.add(new Object[]{9, "Logon"});
                day.add(new Object[]{10, "Export"});
                for (int hour = 10; hour < 17; hour++) {
                    day.add(new Object[]{hour, "View"});
                }
                for (final Object[] event : day) {
                    final ObjectNode doc = JsonUtil.getMapper().createObjectNode();
                    doc.put("_id", String.valueOf(id++));
                    doc.put("EventTime", at(date, (Integer) event[0]).toString());
                    doc.put("EmployeeRef", person);
                    doc.put("Login", person.toLowerCase(Locale.ROOT) + "@corp.example");
                    doc.put("Action", (String) event[1]);
                    doc.put("Workstation", "ws-" + person);
                    batch.add(doc);
                }
            }
        }
        node.bulkIndex(indexName, batch);
        final DocRef cluster = liveElasticCluster(node, "E2E person cluster " + indexName);
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, "Staff activity " + indexName, folder);
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("EventTime")
                .build()));
        return created.getDocRef();
    }

    private static Instant at(final LocalDate date, final int hour) {
        return date.atStartOfDay(ZONE).plusHours(hour).plusMinutes(5).toInstant();
    }
}
