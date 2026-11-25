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
import stroom.search.elastic.ElasticIndexStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// `sequence_query`'s role=target steps with logonForms (decided by the user, 2026-11-25: on the actor scope's
/// terms), over `/mcp` on a real Stroom and Elasticsearch: a person logs on at a workstation (written `CORP\jsmith`),
/// then within 10 minutes another account resets their password there (the target written `jsmith@corp.example`);
/// their own reset of their own password is left out. Matched exactly by default, neither written form is the
/// logon id given, so nothing is found; with logonForms [strip_domain, lowercase] the pattern finds the one
/// sequence, the target field and the actor's own acts left out alike. The fields come from the AGENTS doc beside
/// the index (`user:` and `target user:`).
///
/// Skipped without an Elasticsearch node unless `STROOM_TEST_ELASTIC_REQUIRED=true`.
@SuppressWarnings("unchecked")
class TestE2eSequenceTargetLogonForms extends AbstractE2eTest {

    private static final String DAY = "2026-03-02T";

    @Inject
    private ElasticIndexStore elasticIndexStore;

    private ElasticTestNode node;
    private String indexName;

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
        indexName = ElasticTestNode.createIndexName("target-logon-forms");
    }

    @AfterEach
    void removeTheIndex() {
        if (node != null && indexName != null) {
            node.deleteIndices(indexName);
        }
    }

    @Test
    void aTargetStepMatchesTheLogonsOtherWrittenFormsOnlyWithLogonForms() {
        final ExplorerNode folder = folder("System/E2E Target Logon Forms " + Long.toHexString(System.nanoTime()));
        final DocRef index = elasticIndex(folder);
        documentationDoc(folder, "AGENTS", """
                # Directory and logons

                ## Fields
                - user: Login
                - target user: TargetLogin
                """);

        // Exact by default: neither CORP\jsmith nor jsmith@corp.example is the logon id given.
        final Map<String, Object> exact = json("sequence_query", sequenceArgs(index, false));
        dump("exact", exact);
        assertThat(exact).containsEntry("sequence_count", 0);
        assertThat((List<Map<String, Object>>) exact.get("scope")).allSatisfy(scope ->
                assertThat(scope).doesNotContainKey("matched"));

        // With logonForms: the logon and the reset done to them by another account; their own reset left out.
        final Map<String, Object> forms = json("sequence_query", sequenceArgs(index, true));
        dump("logonForms", forms);
        assertThat(forms).containsEntry("sequence_count", 1);
        assertThat((String) forms.get("query")).contains("TargetLogin regex~", "not (Login regex~");
        assertThat(String.valueOf(forms.get("rows"))).contains("ws-1").doesNotContain("ws-2");
        assertThat((List<Map<String, Object>>) forms.get("scope")).anySatisfy(scope -> {
            assertThat(scope).containsEntry("role", "target").containsEntry("field", "TargetLogin");
            assertThat(String.valueOf(scope.get("matched"))).startsWith("matched with strip_domain, lowercase: ")
                    .contains("slower");
        });
    }

    private Map<String, Object> sequenceArgs(final DocRef index, final boolean logonForms) {
        final Map<String, Object> args = args("logonIds", List.of("jsmith"), "steps", List.of(
                Map.of("index", index.getName(), "by", List.of("Workstation"), "where", List.of(Map.of("field",
                        "Action", "value", "Logon"))),
                Map.of("index", index.getName(), "by", List.of("Workstation"), "role", "target", "where",
                        List.of(Map.of("field", "Action", "value", "ResetPassword")))), "maxspan", "10m",
                "timeFrom", DAY + "00:00:00Z", "timeTo", DAY + "23:59:59Z");
        if (logonForms) {
            args.put("logonForms", List.of("strip_domain", "lowercase"));
        }
        return args;
    }

    // A logon then a reset of the person's password by another account at ws-1; at ws-2, a logon then their own
    // reset of their own password (left out: done by them).
    private DocRef elasticIndex(final ExplorerNode folder) {
        node.createIndex(indexName, """
                {"mappings": {"properties": {
                  "EventTime": {"type": "date"},
                  "Login": {"type": "keyword"},
                  "TargetLogin": {"type": "keyword"},
                  "Action": {"type": "keyword"},
                  "Workstation": {"type": "keyword"}}}}""");
        final ArrayNode batch = JsonUtil.getMapper().createArrayNode();
        batch.add(event(1, "09:00:00Z", "CORP\\jsmith", null, "Logon", "ws-1"));
        batch.add(event(2, "09:04:00Z", "CORP\\helpdesk", "jsmith@corp.example", "ResetPassword", "ws-1"));
        batch.add(event(3, "11:00:00Z", "CORP\\JSmith", null, "Logon", "ws-2"));
        batch.add(event(4, "11:03:00Z", "jsmith@corp.example", "CORP\\jsmith", "ResetPassword", "ws-2"));
        node.bulkIndex(indexName, batch);
        final DocRef cluster = liveElasticCluster(node, "E2E target logon cluster " + indexName);
        final ExplorerNode created = create(ElasticIndexDoc.TYPE, "Directory " + indexName, folder);
        asUser(admin, () -> elasticIndexStore.writeDocument(elasticIndexStore.readDocument(created.getDocRef())
                .copy()
                .clusterRef(cluster)
                .indexName(indexName)
                .timeField("EventTime")
                .build()));
        return created.getDocRef();
    }

    private static ObjectNode event(final int id,
                                    final String time,
                                    final String login,
                                    final String target,
                                    final String action,
                                    final String workstation) {
        final ObjectNode doc = JsonUtil.getMapper().createObjectNode();
        doc.put("_id", String.valueOf(id));
        doc.put("EventTime", DAY + time);
        doc.put("Login", login);
        if (target != null) {
            doc.put("TargetLogin", target);
        }
        doc.put("Action", action);
        doc.put("Workstation", workstation);
        return doc;
    }
}
