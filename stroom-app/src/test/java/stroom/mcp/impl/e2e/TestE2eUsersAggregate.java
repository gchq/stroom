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
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineData;
import stroom.search.elastic.ElasticClusterStore;
import stroom.search.elastic.ElasticTestNode;
import stroom.search.elastic.shared.ElasticClusterDoc;
import stroom.search.elastic.shared.ElasticConnectionConfig;
import stroom.util.json.JsonUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Every user an event names, indexed once each by login name (asked for by the user): Events holding several users
/// (the sender as the event's user and the Send's source, recipients and copies), each with a login name, an email
/// address and an organisation, indexed into Elasticsearch through an indexing pipeline whose plan has a `users`
/// field of objects made distinct by `Id`. Driving the REAL tools on a real Stroom and Elasticsearch: the plan, the
/// documents stepped, the template built from it and applied, indexing, and the searches through Stroom and in
/// Elasticsearch.
///
/// The node is [ElasticTestNode]'s (`STROOM_TEST_ELASTIC_URL`): skipped without one unless
/// `STROOM_TEST_ELASTIC_REQUIRED=true`. What the test made there is removed after it.
@SuppressWarnings("unchecked")
class TestE2eUsersAggregate extends AbstractE2eTest {

    private static final String FIXTURE_TEMPLATE = "E2E Events to Elasticsearch (users)";
    private static final String FIXTURE_CLUSTER = "E2E Live Elasticsearch (users)";
    private static final String STAMP = Long.toString(System.currentTimeMillis(), 36);
    // Emails sent: the sender, the recipient and a copy, each a login, an email address and an organisation.
    private static final String SAMPLE = """
            time,from,from_email,from_org,to,to_email,to_org,cc,cc_email,cc_org
            2026-10-01T09:00:00,alice,alice@ex.org,Finance,bob,bob@ex.org,Sales,alice,alice@ex.org,Finance
            2026-10-01T09:05:00,bob,bob@ex.org,Sales,carol,carol@ex.org,Legal,dave,dave@ex.org,Ops
            2026-10-01T09:10:00,carol,carol@ex.org,Legal,carol,carol@ex.org,Legal,,,
            """;
    // The translation: the sender as the event's user and the Send's source, the recipient and any copy as its
    // destinations. The same user is named more than once in an event.
    private static final String XSLT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <xsl:stylesheet xpath-default-namespace="records:2" xmlns="event-logging:3" xmlns:stroom="stroom"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                version="3.0">
              <xsl:template match="records">
                <Events xsi:schemaLocation="event-logging:3 file://event-logging-v4.1.0.xsd" Version="4.1.0">
                  <xsl:apply-templates />
                </Events>
              </xsl:template>
              <xsl:template match="record">
                <Event>
                  <EventTime><TimeCreated><xsl:value-of select="stroom:format-date(data[@name='time']/@value,
                      'yyyy-MM-dd''T''HH:mm:ss')" /></TimeCreated></EventTime>
                  <EventSource>
                    <System><Name>E2E</Name><Environment>Dev</Environment></System>
                    <Generator>e2e-mail</Generator>
                    <Device><HostName>mail01</HostName></Device>
                    <xsl:call-template name="user"><xsl:with-param name="who" select="'from'" /></xsl:call-template>
                  </EventSource>
                  <EventDetail>
                    <TypeId>Email-Send</TypeId>
                    <Description>Email sent</Description>
                    <Send>
                      <Source>
                        <xsl:call-template name="user"><xsl:with-param name="who" select="'from'" /></xsl:call-template>
                      </Source>
                      <Destination>
                        <xsl:call-template name="user"><xsl:with-param name="who" select="'to'" /></xsl:call-template>
                        <xsl:if test="normalize-space(data[@name='cc']/@value)">
                          <xsl:call-template name="user"><xsl:with-param name="who" select="'cc'" /></xsl:call-template>
                        </xsl:if>
                      </Destination>
                    </Send>
                  </EventDetail>
                </Event>
              </xsl:template>
              <xsl:template name="user">
                <xsl:param name="who" />
                <User>
                  <Id><xsl:value-of select="data[@name=$who]/@value" /></Id>
                  <EmailAddress><xsl:value-of select="data[@name=concat($who, '_email')]/@value" /></EmailAddress>
                  <UserDetails><Organisation><xsl:value-of select="data[@name=concat($who, '_org')]/@value" />
                  </Organisation></UserDetails>
                </User>
              </xsl:template>
            </xsl:stylesheet>
            """;
    // The users field: every user the event names, once each by login name.
    private static final Map<String, Object> USERS = obj("name", "users", "type", "object",
            "source", "EventSource/User | EventDetail//User", "distinct_by", "Id",
            "description", "Every user the event names, once each by login name",
            "items", List.of(obj("name", "login", "type", "keyword", "source", "Id"),
                    obj("name", "email", "type", "keyword", "source", "EmailAddress"),
                    obj("name", "organisation", "type", "keyword", "source", "UserDetails/Organisation")));

    @Inject
    private PipelineStore pipelineStore;
    @Inject
    private ElasticClusterStore elasticClusterStore;

    private ElasticTestNode node;
    private final List<String> indices = new ArrayList<>();
    private final List<String> indexTemplates = new ArrayList<>();

    @BeforeEach
    void setUpNode() {
        node = ElasticTestNode.getOrSkip();
    }

    @AfterEach
    void removeWhatWasIndexed() {
        if (node == null) {
            return;
        }
        for (final String index : indices) {
            node.request("DELETE", "/_data_stream/" + index, null);
            node.deleteIndices(index + "*");
        }
        indexTemplates.forEach(node::deleteIndexTemplate);
    }

    @Test
    void everyUserAnEventNamesIsIndexedOnceByLoginName() {
        final Onboarded mail = onboard(new Case("users-" + STAMP, "Event Data (Text)", SAMPLE, CSV_SPLITTER, XSLT));
        final String index = "e2e-users-" + STAMP + "-v1";
        indices.add(index);
        final DocRef cluster = liveCluster();
        final String template = eventsTemplate();

        // The plan: the ids, the time, and the users field of objects.
        final Map<String, Object> plan = obj("backend", "elasticsearch", "index_name", index,
                "time_field", "@timestamp", "fields", List.of(
                        obj("name", "StreamId", "type", "id", "source", "@StreamId"),
                        obj("name", "EventId", "type", "id", "source", "@EventId"),
                        obj("name", "@timestamp", "type", "date", "source", "EventTime/TimeCreated"),
                        obj("name", "event.code", "type", "keyword", "source", "EventDetail/TypeId"),
                        USERS));
        final String xslt = uuidOf(client.json("save_xslt", args("build", mail.build(), "name", index + "-XSLT",
                "index_plan", plan)));
        assertThat(readXslt(xslt).getData()).as("the users written by grouping each event's users by login")
                .contains("<xsl:for-each-group", "group-by=\"Id\"");
        final String pipeline = uuidOf(agreed("create_indexing_pipeline", args("build", mail.build(),
                "name", index + " - Indexing", "template_uuid", template, "xslt_uuid", xslt, "index_name", index,
                "cluster_uuid", cluster.getUuid())));
        final Map<String, Object> stepped = stepSample(pipeline, List.of(mail.events()));
        assertThat(stepped).as("stepped clean: %s", stepped.get("groups")).containsEntry("verdict", "clean");

        // The documents: each user once, by login, in the order first named, with their email and organisation.
        final String first = compactXml(stepOutput(pipeline, mail.events(), 0, "xsltFilter"));
        assertThat(first).contains("<array key=\"users\"><map><string key=\"login\">alice</string>"
                                   + "<string key=\"email\">alice@ex.org</string>"
                                   + "<string key=\"organisation\">Finance</string></map>"
                                   + "<map><string key=\"login\">bob</string>"
                                   + "<string key=\"email\">bob@ex.org</string>"
                                   + "<string key=\"organisation\">Sales</string></map></array>");
        assertThat(first.split("<string key=\"login\">alice</string>", -1)).as("alice named three times, once")
                .hasSize(2);
        assertThat(compactXml(stepOutput(pipeline, mail.events(), 2, "xsltFilter")))
                .as("carol sending to herself: one user").contains("<array key=\"users\"><map><string key=\"login\">"
                                                                  + "carol</string>")
                .doesNotContain("</map><map><string key=\"login\">carol");

        // The template built from the plan: the users an object of its items; agreed, then applied by the admin.
        final Map<String, Object> agreedTemplate = agreed("propose_index_template", args("pipeline_uuid", pipeline,
                "plan", plan, "events_stream_ids", List.of(mail.events()), "without_example", true));
        assertThat(agreedTemplate).containsEntry("agreed", true);
        final Map<String, Object> properties = map(map(map(map(agreedTemplate, "template"), "template"),
                "mappings"), "properties");
        assertThat(map(properties, "users")).as("users mapped as an object of its items")
                .isEqualTo(obj("properties", obj("login", obj("type", "keyword"), "email", obj("type", "keyword"),
                        "organisation", obj("type", "keyword"))));
        apply((String) agreedTemplate.get("dev_tools"));

        // Indexed, and searched in Elasticsearch: each event once per user it names.
        final Map<String, Object> started = agreed("create_processor_filter", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(mail.events()), "source_pipeline_uuid", mail.pipelineUuid()));
        processAll();
        final Map<String, Object> done = client.json("wait_for_processing", args("pipeline_uuid", pipeline,
                "stream_ids", List.of(mail.events()), "expect_events", false, "filter_id", started.get("filter_id"),
                "timeout_seconds", 10));
        assertThat(done).as("indexed with no Error stream: %s", done.get("streams")).containsEntry("gate", "pass");
        node.refresh(index);
        assertThat(node.count(index, null)).as("the index holds the 3 events").isEqualTo(3);
        assertThat(List.of(term("alice"), term("bob"), term("carol"), term("dave"))).as("events naming each user")
                .containsExactly(1L, 2L, 2L, 1L);
        assertThat(node.count(index, "{\"term\": {\"users.organisation\": \"Ops\"}}")).isEqualTo(1);
        final Map<String, Object> source = map(JsonUtil.readValue(node.search(index,
                "{\"query\": {\"term\": {\"users.login\": \"dave\"}}}").toString(), Map.class), "hits");
        final Map<String, Object> hit = map(((List<Map<String, Object>>) source.get("hits")).getFirst(), "_source");
        assertThat((List<Map<String, Object>>) hit.get("users")).as("the document's users, as indexed")
                .extracting(user -> user.get("login")).containsExactly("bob", "carol", "dave");

        // Searched through Stroom, each hit traced to its record; every field the pipeline writes in the index.
        final String indexDoc = uuidOf(agreed("create_index_doc", args("build", mail.build(), "backend",
                "elasticsearch", "name", index, "time_field", "@timestamp", "index_name", index, "cluster_uuid",
                cluster.getUuid())));
        final Map<String, Object> verified = agreed("verify_index", args("build", mail.build(), "index_uuid",
                indexDoc, "backend", "elasticsearch", "stream_ids", List.of(mail.events()), "expected_documents", 3,
                "fields", List.of("StreamId", "EventId", "@timestamp", "users.login"), "pipeline_uuid", pipeline,
                "searches", List.of(
                        obj("field", "users.login", "condition", "EQUALS", "value", "bob", "expected", 2),
                        obj("field", "users.email", "condition", "EQUALS", "value", "dave@ex.org",
                                "expected", 1),
                        obj("field", "users.organisation", "condition", "EQUALS", "value", "Legal",
                                "expected", 2))));
        final List<Map<String, Object>> checks = list(verified, "checks");
        assertThat(checks).as("every check passes: %s", checks).allSatisfy(check ->
                assertThat(check).containsEntry("pass", true));

        // Documented: the users' items as fields of the index.
        final Map<String, Object> written = agreed("write_documentation", args("build", mail.build(),
                "pipeline_uuid", pipeline, "markdown", "## Purpose and data\n\nEmails sent, by the users each names."
                                                      + "\n", "change", "Created", "stream_ids",
                List.of(mail.events())));
        assertThat(String.valueOf(written.get("field_mapping"))).contains("users.login", "users.email",
                "users.organisation");
    }

    private long term(final String login) {
        return node.count("e2e-users-" + STAMP + "-v1", "{\"term\": {\"users.login\": \"" + login + "\"}}");
    }

    // An indexing template reading Events (the content pack's Indexing, its Lucene filter swapped for Elasticsearch's,
    // with a JSON schema filter before it, as the live one has).
    private String eventsTemplate() {
        final String lucene = templateUuid("indexing", "Indexing");
        final ExplorerNode created = create(PipelineDoc.TYPE, FIXTURE_TEMPLATE, templateFolder());
        asUser(admin, () -> {
            final PipelineDoc source = pipelineStore.readDocument(new DocRef(PipelineDoc.TYPE, lucene));
            final String json = JsonUtil.writeValueAsString(source.getPipelineData(), false)
                    .replace("\"indexingFilter\"", "\"elasticIndexingFilter\"")
                    .replace("\"IndexingFilter\"", "\"ElasticIndexingFilter\"");
            final Map<String, Object> data = JsonUtil.readValue(json, Map.class);
            final List<Map<String, Object>> elements = (List<Map<String, Object>>) ((Map<String, Object>)
                    data.computeIfAbsent("elements", key -> new LinkedHashMap<>()))
                    .computeIfAbsent("add", key -> new ArrayList<>());
            elements.add(Map.of("id", "schemaFilter", "type", "SchemaFilter"));
            ((List<Map<String, Object>>) ((Map<String, Object>) data.computeIfAbsent("properties",
                    key -> new LinkedHashMap<>())).computeIfAbsent("add", key -> new ArrayList<>()))
                    .add(Map.of("element", "schemaFilter", "name", "schemaGroup", "value", Map.of("string", "JSON")));
            final List<Map<String, Object>> links = (List<Map<String, Object>>) ((Map<String, Object>)
                    data.computeIfAbsent("links", key -> new LinkedHashMap<>()))
                    .computeIfAbsent("add", key -> new ArrayList<>());
            for (final Map<String, Object> link : links) {
                if ("xsltFilter".equals(link.get("from")) && "elasticIndexingFilter".equals(link.get("to"))) {
                    link.put("to", "schemaFilter");
                }
            }
            links.add(Map.of("from", "schemaFilter", "to", "elasticIndexingFilter"));
            pipelineStore.writeDocument(pipelineStore.readDocument(created.getDocRef()).copy()
                    .description("Fixture for TestE2eUsersAggregate")
                    .pipelineData(JsonUtil.readValue(JsonUtil.writeValueAsString(data, false), PipelineData.class))
                    .build());
        });
        return created.getUuid();
    }

    // The Elastic Cluster doc for the test node.
    private DocRef liveCluster() {
        final ExplorerNode created = create(ElasticClusterDoc.TYPE, FIXTURE_CLUSTER, templateFolder());
        final ElasticConnectionConfig connection = new ElasticConnectionConfig();
        connection.setConnectionUrls(List.of(node.getUrl()));
        asUser(admin, () -> elasticClusterStore.writeDocument(elasticClusterStore.readDocument(created.getDocRef())
                .copy()
                .connection(connection)
                .build()));
        return created.getDocRef();
    }

    // The cluster admin applies a Dev Tools request the user was given.
    private void apply(final String devTools) {
        final int newline = devTools.indexOf('\n');
        final String[] line = devTools.substring(0, newline).strip().split(" ", 2);
        final String path = line[1].strip();
        if (path.startsWith("_index_template/")) {
            indexTemplates.add(path.substring("_index_template/".length()));
        }
        final ElasticTestNode.Response response = node.request(line[0], "/" + path,
                devTools.substring(newline + 1));
        assertThat(response.isSuccess()).as("%s %s: %s %s", line[0], path, response.status(), response.body())
                .isTrue();
    }

    private static String compactXml(final String xml) {
        return xml.replaceAll(">\\s+<", "><").strip();
    }

    private static Map<String, Object> obj(final Object... keysAndValues) {
        final Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            out.put((String) keysAndValues[i], keysAndValues[i + 1]);
        }
        return out;
    }
}
