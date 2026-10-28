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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.PyText;
import stroom.ai.impl.tools.generation.SharedTemplate;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.indexing.FieldPlan;
import stroom.ai.impl.tools.indexing.PlannedField;
import stroom.ai.impl.tools.sample.PyJson;
import stroom.ai.impl.tools.validation.EventSchema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The Field mapping section of an indexing pipeline's documentation and of an existing index's, from the index
/// plan, the documents written and the survey. Ported from the index cases of the Stroom MCP server's
/// `tests/test_fielddoc.py`, `test_sharedxslt.py` and `test_validation.py`, and checked against what its
/// `utils/fielddoc.py` writes for the same plans, documents and surveys (golden files). Here, beside the bundled
/// ECS conventions the descriptions fall back on (they are stroom-mcp-impl's resources).
class TestIndexFieldDocs {

    private static final EventSchema SCHEMA = EventSchema.parse(resource(
            "/stroom/mcp/impl/tools/event-logging-v4.1.0.xsd"));

    @Test
    void indexFieldMappingTable() {
        final FieldPlan planned = FieldPlan.of("lucene", "ACME-INDEX", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventTime", "date", "EventTime/TimeCreated"),
                PlannedField.of("UserId", "keyword", "EventSource/User/Id")));
        final FieldPlan plan = new FieldPlan(planned.backend(), planned.indexName(), planned.timeField(),
                planned.fields(), List.of("EventDetail/TypeId = 'hb'"), planned.discovery(), planned.imports(),
                planned.shared(), planned.subobjects(), planned.convention(), planned.customNamespaces(),
                planned.style(), planned.standIns());
        final Map<String, Double> population = new LinkedHashMap<>();
        population.put("EventTime/TimeCreated", 100.0);
        population.put("EventSource/User/Id", 66.7);
        final String text = section(plan, population, null, null);
        assertThat(text).contains("- `EventDetail/TypeId = 'hb'`",
                // Common, so without the In sample column: every event has it.
                "| `StreamId` | The id of the Events stream the event came from. | id | `@StreamId` |",
                // No schema, no sample.
                "| `UserId` |  | keyword | `EventSource/User/Id` | 66.7% of events |");
        assertThat(section(plan, null, null, null)).doesNotContain("| In sample |");
    }

    @Test
    void elasticsearchRowsShowLongIdsAndGroupFieldsByObject() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "people-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"),
                PlannedField.of("user.id", "keyword", "EventSource/User/Id"),
                PlannedField.of("message", "text", "EventDetail/Description"),
                PlannedField.of("host.ip", "ip", "EventSource/Device/IPAddress"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Name")));
        final String text = section(plan, null, null, null);
        assertThat(rowNames(text)).containsExactly("StreamId", "@timestamp", "host.name", "host.ip", "user.id",
                "user.name", "message");
        // ECS's words, the schema's absent.
        assertThat(text).contains("| `StreamId` | The id of the Events stream the event came from. | long | "
                                  + "`@StreamId` |", "| `host.ip` | Host ip addresses. | ip |");
    }

    @Test
    void eachIndexFieldIsDescribedFromTheSchemaAndTheSample() {
        final List<PlannedField> fields = new ArrayList<>();
        for (final String[] f : new String[][]{{"StreamId", "id", "@StreamId"},
                {"host.ip", "ip", "EventSource/Device/IPAddress"}, {"user.id", "keyword", "EventSource/User/Id"},
                {"user.email", "keyword", "EventSource/User/EmailAddress"},
                {"product", "keyword", "EventSource/System/Name"},
                {"agent", "keyword", "EventDetail/Authenticate/Data[@Name='agent']/@Value"},
                {"outcome", "keyword", "EventDetail/Authenticate/Outcome/Success"}}) {
            fields.add(new PlannedField(f[0], f[1], f[2], "outcome".equals(f[0])
                    ? "Whether the logon succeeded."
                    : "", null, false));
        }
        final FieldPlan plan = FieldPlan.of("elasticsearch", "people-v1", "@timestamp", fields);
        final List<Map<String, List<String>>> documents = json("""
                        [{"StreamId": ["7"], "host.ip": ["10.0.0.1"], "user.id": ["alice"], "user.email": ["a@x.org"],
                          "product": ["E2E"], "agent": ["curl"], "outcome": ["true"]},
                         {"StreamId": ["7"], "host.ip": ["10.0.0.2"], "user.id": ["bob"], "user.email": ["b@x.org"],
                          "product": ["E2E"], "outcome": ["false"]}]""");
        final Map<String, String> described = descriptions(section(plan, null, documents, SCHEMA));
        // What each is; how often and which values are the In sample and Sample values columns' to say.
        assertThat(described.get("StreamId")).isEqualTo("The id of the Events stream the event came from.");
        assertThat(described.get("host.ip")).isEqualTo("The network IP address of the device, e.g. 192.168.0.3.");
        assertThat(described.get("user.email")).isEqualTo("The email address of the user.");
        assertThat(described.get("product")).isEqualTo("The name of the system.");
        assertThat(described.get("agent")).startsWith("The `agent` value recorded in a Data element")
                .doesNotContain("sample");
        // The plan's own.
        assertThat(described.get("outcome")).isEqualTo("Whether the logon succeeded.");
        // The schema's words for the user's id (its base type's 'the object' named for the user), then the sample.
        assertThat(described.get("user.id")).isEqualTo("An identifier for the user.");
    }

    @Test
    void theIndexSectionShowsWhatEachIndexFieldGotFromTheSample() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "ecs-acme-v2", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress")));
        final String elastic = "<array xmlns=\"http://www.w3.org/2005/xpath-functions\"><map><string "
                               + "key=\"@timestamp\">2026-10-01T10:00:00.000Z</string><map key=\"user\"><string "
                               + "key=\"name\">alice</string></map></map></array>";
        final String lucene = "<records xmlns=\"records:2\"><record><data name=\"UserId\" value=\"bob\"/><data "
                              + "name=\"UserId\" value=\"carol\"/></record></records>";
        final List<Map<String, List<String>>> documents = IndexFieldDocs.indexDocuments(List.of(elastic));
        assertThat(PyJson.dumps(documents)).isEqualTo("[{\"@timestamp\": [\"2026-10-01T10:00:00.000Z\"], \"user\": "
                                                      + "[], \"user.name\": [\"alice\"]}]");
        assertThat(PyJson.dumps(IndexFieldDocs.indexDocuments(List.of(lucene))))
                .isEqualTo("[{\"UserId\": [\"bob\", \"carol\"]}]");
        final String section = section(plan, Map.of("EventSource/User/Id", 100.0), documents, null);
        assertThat(section).contains("Sample values are what the 1 documents written from the sample got.",
                // Without the event-logging schema, ECS's words for the field's name (asked for by the user: a
                // fallback).
                "| `user.name` | Short name or login of the user. | keyword | `EventSource/User/Id` | 100% of events | "
                + "`alice` |",
                "| `source.ip` | IP address of the source. | ip | `EventSource/Client/IPAddress` | not in the sample | "
                + "(none in the sample) |");
    }

    @Test
    void anExistingIndexIsDocumentedFromItsSurvey() {
        final String text = IndexFieldDocs.existingIndexMarkdown(survey(List.of(), true), Map.of(), null,
                FieldDocs.BY_EVENT_TYPE, null);
        final Map<String, List<String>> rows = rows(text);
        // Time first, then as Stroom lists them.
        assertThat(rows.keySet()).containsExactly("@timestamp", "message", "user.name", "http.status", "source.ip");
        // The overall facts are the summary's; the field table says only what explains its columns.
        assertThat(text).doesNotContain("No pipeline was found", "newest documents", "come from elsewhere")
                .contains("Where each field comes from is not recorded: no pipeline that writes to it keeps an index "
                          + "plan.");
        assertThat(rows.get("http.status")).containsExactly("Numbers.", "long", "(not recorded)", "50% of documents",
                "`200` |");
        // ECS's words.
        assertThat(rows.get("source.ip").getFirst()).isEqualTo("IP address of the source.");
        final String unstored = IndexFieldDocs.existingIndexMarkdown(survey(List.of(), false), Map.of(), null,
                FieldDocs.BY_EVENT_TYPE, null);
        assertThat(unstored).contains("| `http.status` | Indexed but not stored: searchable, its values cannot be "
                                      + "shown. | long | (not recorded) | not stored | - |");
    }

    @Test
    void anExistingIndexFedByAPlanSaysWhereEachFieldComesFrom() {
        final Map<String, PlannedField> planned = Map.of("user.name", PlannedField.of("user.name", "keyword",
                "EventSource/User/Id"));
        final String text = IndexFieldDocs.existingIndexMarkdown(survey(fedByPlan(), true), planned, SCHEMA,
                FieldDocs.BY_EVENT_TYPE, null);
        final String row = text.lines().filter(l -> l.startsWith("| `user.name`")).findFirst().orElseThrow();
        assertThat(row).contains("| `user.name` | An identifier for the user. | keyword | `EventSource/User/Id` |");
        assertThat(text).doesNotContain("Fed by");
        assertThat(text.split("\\|")[0]).doesNotContain("not recorded");
    }

    @Test
    void anIndexStroomReadsNoDocumentsFromIsDocumentedFromItsMappingSayingSo() {
        final Map<String, Object> survey = survey(List.of(), true);
        survey.put("documents", List.of());
        survey.put("earliest", null);
        survey.put("latest", null);
        survey.put("note", "No documents came back through Stroom. Stroom returns a hit only when its StreamId is a "
                           + "stream in this Stroom");
        final String text = IndexFieldDocs.existingIndexMarkdown(survey, Map.of(), null, FieldDocs.BY_EVENT_TYPE,
                null);
        assertThat(text).contains("No documents came back through Stroom, so no field below was read (see Data "
                                  + "surveyed).",
                "| `user.name` | Not read: no documents came back through Stroom. | keyword | (not recorded) | not "
                + "read | - |");
    }

    @Test
    void fieldsPastWhatTheSurveyReadsAreListedAsNotSurveyed() {
        final Map<String, Object> survey = survey(List.of(), true);
        survey.put("surveyed_fields", List.of("@timestamp", "user.name", "message"));
        final String text = IndexFieldDocs.existingIndexMarkdown(survey, Map.of(), null, FieldDocs.BY_EVENT_TYPE,
                null);
        assertThat(text).contains("The survey read 3 of the 5 fields; the other 2 are listed as not surveyed.",
                "| `source.ip` | Not surveyed: past the fields the survey reads. | ip | (not recorded) | not surveyed "
                + "| - |", "| `user.name` | Short name or login of the user. |");
    }

    @Test
    void anExistingIndexDescribesItsIdsAndTimeFieldWithoutRepeatingTheSample() {
        final Map<String, Object> survey = survey(List.of(), true);
        final List<Object> fields = new ArrayList<>(List.of(Map.of("name", "StreamId", "type", "long"),
                Map.of("name", "EventId", "type", "long")));
        fields.addAll((List<?>) survey.get("fields"));
        survey.put("fields", fields);
        final List<Object> documents = new ArrayList<>();
        int n = 0;
        for (final Object document : (List<?>) survey.get("documents")) {
            @SuppressWarnings("unchecked") final Map<String, Object> withIds = new LinkedHashMap<>(
                    (Map<String, Object>) document);
            withIds.put("StreamId", List.of("7"));
            withIds.put("EventId", List.of(String.valueOf(++n)));
            documents.add(withIds);
        }
        survey.put("documents", documents);
        final Map<String, String> described = descriptions(IndexFieldDocs.existingIndexMarkdown(survey, Map.of(),
                null, FieldDocs.BY_EVENT_TYPE, null));
        assertThat(described.get("StreamId")).startsWith("The id of the stream the document came from.")
                .doesNotContain("Events");
        assertThat(described.get("EventId")).startsWith("The record's number in that stream");
        assertThat(described.get("@timestamp")).startsWith("The index's time field: searches and dashboards filter "
                                                           + "on it.");
    }

    @Test
    void theDataSurveyedSummarySaysWhatTheIndexHoldsAndWhereItComesFrom() {
        final Map<String, Object> survey = survey(fedByPlan(), true);
        survey.put("documents_sampled", 2);
        survey.put("profile", json("""
                {"documents": 1200, "earliest": "2026-01-01T00:00:00.000Z", "latest": "2026-09-23T11:45:00.000Z",
                 "streams": 40, "streams_examined": 20,
                 "sources": [{"feed": "WEB-ACCESS", "type": "Events", "produced_by": "WEB-ACCESS-Events",
                              "streams": 20, "documents": 900, "description": "Proxy access logs."}]}"""));
        final String text = IndexFieldDocs.existingIndexSummary(survey);
        assertThat(text).startsWith("### Data surveyed").contains(
                "`legacy-web` holds 1200 documents, from 2026-01-01T00:00:00.000Z to 2026-09-23T11:45:00.000Z, in 40 "
                + "streams.",
                "From feed `WEB-ACCESS` (Events streams, produced by pipeline `WEB-ACCESS-Events`): 20 streams, 900 "
                + "documents. The feed's description: Proxy access logs.",
                "(Sources from the 20 streams with the most documents.)",
                "Indexed by pipeline `Web - Indexing`, from the index plan kept with its XSLT.",
                "The field table is drawn from 2 documents (", "In sample and Sample values");
        final Map<String, Object> recent = new LinkedHashMap<>(survey);
        recent.put("sampled_from", "the last 30 days");
        assertThat(IndexFieldDocs.existingIndexSummary(recent)).contains(
                "The field table is drawn from 2 documents of the last 30 days (");
        // A count Stroom hadn't finished is a floor, and says so.
        final Map<String, Object> partial = new LinkedHashMap<>(survey);
        @SuppressWarnings("unchecked") final Map<String, Object> profile = new LinkedHashMap<>(
                (Map<String, Object>) survey.get("profile"));
        profile.put("partial", "Stroom had not finished");
        partial.put("profile", profile);
        assertThat(IndexFieldDocs.existingIndexSummary(partial)).contains("`legacy-web` holds at least 1200 documents",
                "(Stroom had not finished.)");
    }

    @Test
    void aFieldThePlanWritesThatTheIndexLacksIsSaidWhenTheUserChoseToDocumentIt() {
        // Seen in production: Stroom didn't list a field the pipeline writes, and the doc left it out, unsaid.
        // Refused by default (write_documentation); when the user chooses to document anyway, it is listed as not in
        // the index.
        final Map<String, PlannedField> planned = new LinkedHashMap<>();
        planned.put("user.name", PlannedField.of("user.name", "keyword", "EventSource/User/Id"));
        planned.put("user.role", new PlannedField("user.role", "keyword", "EventSource/User/Role",
                "The role the user acted in.", null, false));
        final Map<String, Object> survey = survey(fedByPlan(), true);
        assertThat(IndexFieldDocs.notListed(survey, planned)).containsExactly("user.role");
        assertThat(IndexFieldDocs.existingIndexMarkdown(survey, planned, null, FieldDocs.BY_EVENT_TYPE, null))
                .contains("| `user.role` | The role the user acted in. | keyword | `EventSource/User/Role` | not in "
                          + "the index | - |");
    }

    @Test
    void indexDocumentationHasCommonFieldsThenASectionPerAction() {
        // Asked for by the user: as the Events documentation is, by event type; actions with the same fields
        // together.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("event.code", "keyword", "EventDetail/TypeId"),
                PlannedField.of("email.from", "keyword", "EventDetail/Send/Source/User/Id"),
                PlannedField.of("email.to", "keyword", "EventDetail/Send/Destination/User/Id"),
                PlannedField.of("inbox.from", "keyword", "EventDetail/Receive/Source/User/Id"),
                PlannedField.of("inbox.to", "keyword", "EventDetail/Receive/Destination/User/Id"),
                PlannedField.of("file.name", "keyword", "EventDetail/View/Document/Title")));
        final Map<String, Double> population = new LinkedHashMap<>();
        population.put("EventDetail/Send/Source/User/Id", 30.0);
        population.put("EventDetail/Receive/Source/User/Id", 20.0);
        final String[] parts = section(plan, population, null, null).split("### Event types");
        assertThat(parts[0]).contains("### Common fields", "`event.code`").doesNotContain("`email.from`");
        assertThat(parts[1]).contains("#### Send, Receive\n\nIn 30% (Send), 20% (Receive) of the sample's events.");
        assertThat(parts[1].indexOf("`email.from`")).isLessThan(parts[1].indexOf("`inbox.to`"));
        assertThat(parts[1].indexOf("`inbox.to`")).isLessThan(parts[1].indexOf("#### View"));
        final String compact = IndexFieldDocs.indexFieldMappingMarkdown(plan, population, null, null,
                FieldDocs.COMPACT, null);
        assertThat(compact).doesNotContain("###");
        assertThat(compact.split("\\| Index field \\|", -1)).hasSize(2);
    }

    @Test
    void theCommonFieldsAreOnlyThoseEveryEventHas() {
        // Asked for by the user: seen, source.ip (EventSource/Client/IPAddress, in 40% of the events) among them.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("user.name", "keyword", "EventSource/User/Id"),
                PlannedField.of("event.code", "keyword", "EventDetail/TypeId"),
                PlannedField.of("message", "text", "EventDetail/Description"),
                PlannedField.of("event.outcome", "keyword", "EventDetail/*/Outcome/Success")));
        final Map<String, Double> population = new LinkedHashMap<>();
        population.put("EventTime/TimeCreated", 100.0);
        population.put("EventSource/Device/HostName", 100.0);
        population.put("EventSource/Client/IPAddress", 40.0);
        population.put("EventSource/User/Id", 99.5);
        population.put("EventDetail/TypeId", 100.0);
        population.put("EventDetail/Authenticate/Outcome/Success", 55.0);
        // The same by the action element of the events: 60% are logons, 40% views.
        final Map<String, Map<String, Double>> byAction = new LinkedHashMap<>();
        byAction.put("Authenticate", Map.of("EventTime/TimeCreated", 60.0, "EventSource/Device/HostName", 60.0,
                "EventSource/Client/IPAddress", 30.0, "EventSource/User/Id", 59.5, "EventDetail/TypeId", 60.0,
                "EventDetail/Authenticate/Outcome/Success", 55.0));
        byAction.put("View", Map.of("EventTime/TimeCreated", 40.0, "EventSource/Device/HostName", 40.0,
                "EventSource/Client/IPAddress", 10.0, "EventSource/User/Id", 40.0, "EventDetail/TypeId", 40.0));

        final Map<String, Map<String, Double>> typeIds = new LinkedHashMap<>();
        typeIds.put("Authenticate", Map.of("User-Login", 40.0, "User-Logout", 20.0));
        typeIds.put("View", Map.of("Secret-View", 40.0));
        final String text = IndexFieldDocs.indexFieldMappingMarkdown(plan, population,
                new IndexFieldDocs.ByAction(byAction, typeIds), null, null, FieldDocs.BY_EVENT_TYPE, null);

        assertThat(commonFields(text)).containsExactly("StreamId", "@timestamp", "host.name", "event.code");
        // Which kinds of event each section is, by TypeId, most first (seen: nothing said a section was
        // Secret-View's).
        assertThat(text.split("#### Authenticate")[1].split("\n\n\\| Index field")[0])
                .contains("TypeIds: `User-Login` (40%), `User-Logout` (20%).");
        assertThat(text).contains("#### View\n\nTypeIds: `Secret-View` (40%).");
        // Each one's source in every event, or the server's own id.
        final Map<String, String> sources = new LinkedHashMap<>();
        plan.fields().forEach(field -> sources.put(field.name(), field.source()));
        assertThat(commonFields(text)).allSatisfy(name -> assertThat(sources.get(name).startsWith("@")
                                                                     || Double.valueOf(100.0).equals(
                population.get(sources.get(name)))).as(name).isTrue());
        // Each of the rest under each event type whose events have it, with that share; none in a section of
        // fields of no event type.
        assertThat(text).doesNotContain("Fields of any event type");
        final String logons = text.split("#### Authenticate")[1].split("####")[0];
        assertThat(logons).contains("`source.ip`", "30% of events", "`user.name`", "59.5% of events",
                "`event.outcome`", "55% of events");
        final String views = text.split("#### View")[1].split("###")[0];
        assertThat(views).contains("`source.ip`", "10% of events", "`user.name`", "40% of events")
                .doesNotContain("event.outcome");
        // What no event has, saying so.
        assertThat(text.split("### Not in the sample")[1]).contains("No event of the sample has these",
                "`message`");
        // Without the shares by action element (the draft's preview), only some events' fields stay common, with
        // their share shown.
        final String preview = section(plan, population, null, null);
        assertThat(commonFields(preview)).contains("source.ip", "user.name");
        assertThat(preview).contains("| In sample |", "40% of events");
        // Without a sample, nothing can be told: what any kind of event can have is common.
        assertThat(commonFields(section(plan, null, null, null))).containsExactly("StreamId", "@timestamp",
                "host.name", "source.ip", "user.name", "event.code", "message");
    }

    @Test
    void aFieldTheSampleHasNoneOfShowsTheIndexsValues() {
        // Asked for by the user: what kind of data it holds, on either backend.
        for (final String backend : List.of(FieldPlan.LUCENE, FieldPlan.ELASTICSEARCH)) {
            final FieldPlan plan = FieldPlan.of(backend, "acme-v1", "@timestamp", List.of(
                    PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                    PlannedField.of("user.target.name", "keyword", "EventDetail/*/User/Name")));
            final List<Map<String, List<String>>> documents = List.of(Map.of("@timestamp",
                    List.of("2026-10-01T10:00:00.000Z")));

            final String text = IndexFieldDocs.indexFieldMappingMarkdown(plan, Map.of("EventTime/TimeCreated", 100.0),
                    new IndexFieldDocs.ByAction(Map.of(), Map.of()), documents, Map.of("user.target.name",
                            List.of("Patel, Priya", "Lee, Sam")), null,
                    FieldDocs.BY_EVENT_TYPE, null);

            assertThat(text.split("### Not in the sample")[1]).as(backend)
                    .contains("| `EventDetail/*/User/Name` | not in the sample | `Patel, Priya`, `Lee, Sam` (from the "
                              + "index) |");
        }
    }

    @Test
    void fieldsFromAnyActionAreUnderTheActionsTheSampleHasThemIn() {
        // Seen: SecretServer's user.target.*, object and folder fields (EventDetail/*/...) among the common fields,
        // and its Data fields (EventDetail//Data[@Name='...']/@Value) under a heading of no name.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", List.of(
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("event.code", "keyword", "EventDetail/TypeId"),
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress"),
                PlannedField.of("user.target.id", "keyword", "EventDetail/*/User/Id"),
                PlannedField.of("user.target.name", "keyword", "EventDetail/*/User/Name"),
                PlannedField.of("secret.id", "keyword", "EventDetail//Data[@Name='secret_id']/@Value"),
                PlannedField.of("copy.to", "keyword", "EventDetail/Copy/Destination/Object/Name")));
        final Map<String, Double> population = new LinkedHashMap<>();
        population.put("EventTime/TimeCreated", 100.0);
        population.put("EventDetail/TypeId", 100.0);
        population.put("EventSource/Client/IPAddress", 40.0);
        population.put("EventDetail/Authenticate/User/Id", 18.0);
        population.put("EventDetail/Authorise/User/Id", 4.0);
        population.put("EventDetail/Copy/Destination/Object/Name", 3.0);
        // Each named Data entry's share, as the documentation's population gives it.
        population.put("EventDetail/View/Data[@Name='secret_id']/@Value", 40.0);
        population.put("EventDetail/Copy/Data[@Name='secret_id']/@Value", 3.0);
        final Map<String, Map<String, Double>> byAction = new LinkedHashMap<>();
        byAction.put("Authenticate", Map.of("EventDetail/Authenticate/User/Id", 18.0,
                "EventSource/Client/IPAddress", 18.0));
        byAction.put("Authorise", Map.of("EventDetail/Authorise/User/Id", 4.0, "EventSource/Client/IPAddress", 4.0));
        byAction.put("Copy", Map.of("EventDetail/Copy/Destination/Object/Name", 3.0,
                "EventDetail/Copy/Data[@Name='secret_id']/@Value", 3.0, "EventSource/Client/IPAddress", 3.0));
        byAction.put("View", Map.of("EventDetail/View/Data[@Name='secret_id']/@Value", 40.0,
                "EventSource/Client/IPAddress", 15.0));

        final String text = IndexFieldDocs.indexFieldMappingMarkdown(plan, population,
                new IndexFieldDocs.ByAction(byAction, Map.of()), null, null, FieldDocs.BY_EVENT_TYPE, null);

        assertThat(text.lines().filter(line -> line.strip().equals("####"))).isEmpty();
        // Only what every event has is common.
        final String common = text.split("### Common fields")[1].split("###")[0];
        assertThat(common).contains("`@timestamp`", "`event.code`")
                .doesNotContain("user.target", "secret.id", "source.ip")
                // Each in every event: no In sample column.
                .doesNotContain("In sample", "100% of events");
        // What no event of the sample has: its own section, saying so.
        assertThat(text.split("### Not in the sample")[1]).contains("`user.target.name`")
                .doesNotContain("`user.target.id`", "`secret.id`", "`source.ip`");
        // What only some events have, though not an action element's (seen: source.ip, in 40%), under each.
        assertThat(text.split("#### View")[1]).contains("`source.ip`", "15% of events");
        // The rest under each action the sample has it in, with that action's share; once in a section of
        // several.
        final String types = text.split("### Event types")[1];
        assertThat(types).contains("`user.target.id`", "22% of events", "`secret.id`", "40% of events");
        final String copy = types.substring(types.indexOf("Copy"));
        assertThat(copy).contains("`copy.to`", "`secret.id`");
        assertThat(types.split("`user.target.id`", -1)).hasSize(2);
    }

    @Test
    void eachFieldIsDescribedFromItsMeaningNotTheSchemasGenericWords() {
        // Asked for by the user: 'The subject value recorded in a Data element of Send' says nothing a reader can't
        // see. The agent writes what each field means, from its name, where it comes from and its sample values.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "mail-v1", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("email.subject", "keyword", "EventDetail/Send/Data[@Name='subject']/@Value"),
                new PlannedField("host.name", "keyword", "EventSource/Device/HostName", "Mail relay.", null,
                        false)));
        final List<Map<String, List<String>>> documents = json("""
                [{"StreamId": ["7"], "email.subject": ["Invoice 4411"]},
                 {"StreamId": ["7"], "email.subject": ["Re: lunch"]}]""");
        final Descriptions asking = Descriptions.asking(true);
        IndexFieldDocs.indexFieldMappingMarkdown(plan, null, documents, null, FieldDocs.BY_EVENT_TYPE, asking);
        // Not StreamId (the server's own), nor host.name (the plan says): the subject, with what it was seen
        // holding.
        assertThat(asking.wanted().keySet()).containsExactly("email.subject");
        assertThat(asking.wanted().get("email.subject").get("values")).isEqualTo(List.of("Invoice 4411", "Re: lunch"));
        assertThat(asking.wanted().get("email.subject").get("from"))
                .isEqualTo("EventDetail/Send/Data[@Name='subject']/@Value");
        final Descriptions given = new Descriptions(Map.of("email.subject", "Subject line of the email sent."), true);
        final String text = IndexFieldDocs.indexFieldMappingMarkdown(plan, null, documents, null,
                FieldDocs.BY_EVENT_TYPE, given);
        assertThat(given.wanted()).isEmpty();
        assertThat(text).contains("| `email.subject` | Subject line of the email sent.");
    }

    @Test
    void theAgentIsShownWhatEachSchemaSaysToBuildOn() {
        // Asked for by the user: ECS and event-logging descriptions supplement the agent's, and are the fallback.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", List.of(
                PlannedField.of("source.ip", "ip", "EventSource/Client/IPAddress")));
        final Descriptions asking = Descriptions.asking(true);
        final String text = IndexFieldDocs.indexFieldMappingMarkdown(plan, null, json("[{\"source.ip\": "
                                                                                      + "[\"10.1.2.3\"]}]"), SCHEMA,
                FieldDocs.BY_EVENT_TYPE, asking);
        assertThat(asking.wanted().get("source.ip").get("schemas_say")).isEqualTo(Map.of(
                "event-logging", "The network IP address of the client.", "ECS", "IP address of the source."));
        // event-logging first: what was written.
        assertThat(text).contains("| `source.ip` | The network IP address of the client. |");
    }

    @Test
    void anIndexFieldKnowsTheVendorsWordsByTheElementTheNotesPutItIn() {
        // From test_the_vendors_documentation_comes_first: an index field knows only its path, matched by the
        // element the notes put the source field in.
        final List<Map<String, Object>> notes = json("""
                [{"field": "user", "meaning": "Login of the account that signed in",
                  "event_logging_path": "EventSource/User/Id"},
                 {"field": "sid", "meaning": "VPN session", "values": {"s1": "first session"}}]""");
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme-v1", "@timestamp", List.of(
                PlannedField.of("user.name", "keyword", "EventSource/User/Id")));
        final Descriptions asking = new Descriptions(null, true, notes);
        IndexFieldDocs.indexFieldMappingMarkdown(plan, null, null, SCHEMA, FieldDocs.BY_EVENT_TYPE, asking);
        @SuppressWarnings("unchecked") final Map<String, String> said =
                (Map<String, String>) asking.wanted().get("user.name").get("schemas_say");
        assertThat(said.get("vendor documentation")).isEqualTo("Login of the account that signed in.");
    }

    @Test
    void aSharedTemplatesFieldSaysWhichWritesIt() {
        // From test_sharedxslt.py: the index still maps the field; the documentation says where it comes from.
        final FieldPlan plan = new FieldPlan("elasticsearch", "x", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"), PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("user.id", "keyword", "EventSource/User/Id"),
                PlannedField.of("guid", "keyword", "shared:guid")), List.of(), null, List.of(),
                List.of(new SharedTemplate("Common-Elastic-V1", "guid", "guid", Map.of())), true, null, List.of(),
                XsltStyle.DEFAULT, Map.of());
        assertThat(plan.writtenBy("guid").template()).isEqualTo("guid");
        assertThat(section(plan, Map.of("EventSource/User/Id", 100.0), null, null)).contains(
                // Common, so without the In sample column: every event has it.
                "| `guid` | Written by the shared template `guid`. | keyword | shared template `guid` of "
                + "`Common-Elastic-V1` |");
    }

    @Test
    void anIndexingXsltWithoutAPlanIsDocumentedFromTheDocumentsItWrites() {
        // From test_validation.py.
        final String section = IndexFieldDocs.writtenFieldsMarkdown(json("""
                [{"UserId": ["alice"], "Host": ["ws01"], "Empty": [""]}, {"UserId": ["bob"], "Host": [""]}]"""));
        assertThat(section).contains("| `UserId` | Not described: no plan or schema covers it. | 100% of documents | "
                                     + "`alice`, `bob` |",
                "| `Host` | Not described: no plan or schema covers it. | 50% of documents | `ws01` |",
                "keeps no index plan").doesNotContain("`Empty`");
    }

    @Test
    void theIndexSectionsAreThePythonText() {
        final FieldPlan plan = plan(golden("plan.json"));
        final Map<String, Object> sample = golden("plan-docs.json");
        @SuppressWarnings("unchecked") final Map<String, Object> rawPopulation =
                (Map<String, Object>) sample.get("population");
        final Map<String, Double> population = new LinkedHashMap<>();
        rawPopulation.forEach((k, v) -> population.put(k, ((Number) v).doubleValue()));
        final List<Map<String, List<String>>> documents = documents(sample.get("documents"));
        assertThat(IndexFieldDocs.indexFieldMappingMarkdown(plan, population, documents, SCHEMA,
                FieldDocs.BY_EVENT_TYPE, null)).isEqualTo(inPortLayout(goldenText("plan.md"), population));
        assertThat(IndexFieldDocs.indexFieldMappingMarkdown(plan, population, documents, SCHEMA, FieldDocs.COMPACT,
                null)).isEqualTo(goldenText("plan-compact.md"));
        assertThat(section(plan, null, null, null)).isEqualTo(goldenText("plan-bare.md"));
        final Descriptions asking = Descriptions.asking(true);
        IndexFieldDocs.indexFieldMappingMarkdown(plan, population, documents, SCHEMA, FieldDocs.BY_EVENT_TYPE, asking);
        assertThat(PyJson.dumps(asking.wanted())).isEqualTo(PyJson.dumps(golden("plan-wanted.json")));
    }

    @Test
    void theExistingIndexSectionsAreThePythonText() {
        final Map<String, Object> given = golden("survey.json");
        @SuppressWarnings("unchecked") final Map<String, Object> survey = (Map<String, Object>) given.get("survey");
        final Map<String, PlannedField> planned = new LinkedHashMap<>();
        @SuppressWarnings("unchecked") final Map<String, Map<String, Object>> fields =
                (Map<String, Map<String, Object>>) given.get("planned");
        fields.forEach((name, f) -> planned.put(name, PlannedField.fromJson(f)));
        assertThat(IndexFieldDocs.existingIndexMarkdown(survey, planned, SCHEMA, FieldDocs.BY_EVENT_TYPE, null))
                .isEqualTo(goldenText("existing.md"));
        assertThat(IndexFieldDocs.existingIndexMarkdown(survey, planned, SCHEMA, FieldDocs.COMPACT, null))
                .isEqualTo(goldenText("existing-compact.md"));
        assertThat(IndexFieldDocs.existingIndexSummary(survey)).isEqualTo(goldenText("existing-summary.md"));
        final Map<String, Object> empty = new LinkedHashMap<>(survey);
        empty.put("documents", List.of());
        empty.put("documents_sampled", 0);
        empty.put("profile", null);
        empty.put("fed_by", List.of());
        empty.put("note", "No documents came back.");
        assertThat(IndexFieldDocs.existingIndexMarkdown(empty, Map.of(), null, FieldDocs.BY_EVENT_TYPE, null))
                .isEqualTo(goldenText("existing-empty.md"));
        assertThat(IndexFieldDocs.existingIndexSummary(empty)).isEqualTo(goldenText("existing-empty-summary.md"));
    }

    @Test
    void theWrittenAndDiscoverySectionsAreThePythonText() {
        assertThat(IndexFieldDocs.writtenFieldsMarkdown(json("""
                [{"UserId": ["alice"], "Host": ["ws01"], "Empty": [""], "EventId": ["3"]},
                 {"UserId": ["bob"], "Host": [""], "n": ["1", "2"]}, {"n": ["3"]}]""")))
                .isEqualTo(goldenText("written.md"));
        final List<String> outputs = List.of(goldenText("elastic.xml"), goldenText("lucene.xml"), "<broken");
        final List<Map<String, List<String>>> documents = IndexFieldDocs.indexDocuments(outputs);
        final Map<String, Object> golden = golden("documents.json");
        assertThat(PyJson.dumps(documents)).isEqualTo(PyJson.dumps(golden.get("documents")));
        final List<String> arrays = IndexFieldDocs.objectArrays(List.of(goldenText("elastic.xml")));
        assertThat(arrays).isEqualTo(golden.get("arrays"));
        final FieldPlan plan = plan(golden("discovery-plan.json"));
        assertThat(IndexFieldDocs.discoveryFieldMarkdown(plan, documents, arrays))
                .isEqualTo(goldenText("discovery.md"));
        assertThat(IndexFieldDocs.discoveryFieldMarkdown(plan, null, null)).isEqualTo(goldenText("discovery-bare.md"));
    }

    private static String section(final FieldPlan plan, final Map<String, Double> population,
                                  final List<Map<String, List<String>>> documents, final EventSchema schema) {
        return IndexFieldDocs.indexFieldMappingMarkdown(plan, population, documents, schema, FieldDocs.BY_EVENT_TYPE,
                null);
    }

    // _survey(fed_by, stored) of the Python tests.
    private static Map<String, Object> survey(final List<Object> fedBy, final boolean stored) {
        final Map<String, Object> survey = json("""
                {"index": {"type": "ElasticIndex", "uuid": "i", "name": "legacy-web", "path": "System/Prod/legacy-web"},
                 "backend": "elasticsearch", "index_name": "legacy-web", "time_field": "@timestamp",
                 "fields": [{"name": "message", "type": "text"}, {"name": "@timestamp", "type": "date"},
                            {"name": "user.name", "type": "keyword"}, {"name": "http.status", "type": "long"},
                            {"name": "source.ip", "type": "ip"}],
                 "documents": [{"@timestamp": ["2026-09-23T11:45:00.000Z"], "user.name": ["alice"],
                                "source.ip": ["10.3.0.1"], "http.status": [], "message": ["health check"]},
                               {"@timestamp": ["2026-09-20T08:00:00.000Z"], "user.name": ["bob"],
                                "source.ip": ["10.3.0.2"], "http.status": ["200"], "message": ["GET /"]}],
                 "earliest": "2026-09-20T08:00:00.000Z", "latest": "2026-09-23T11:45:00.000Z"}""");
        survey.put("fed_by", fedBy);
        if (!stored) {
            @SuppressWarnings("unchecked") final List<Map<String, Object>> fields =
                    (List<Map<String, Object>>) survey.get("fields");
            fields.get(3).put("stored", false);
        }
        return survey;
    }

    private static List<Object> fedByPlan() {
        return json("[{\"name\": \"Web - Indexing\", \"uuid\": \"p\", \"plan\": {\"fields\": []}}]");
    }

    private static Map<String, List<String>> rows(final String text) {
        final Map<String, List<String>> rows = new LinkedHashMap<>();
        text.lines().filter(l -> l.startsWith("| `")).forEach(l -> {
            final List<String> parts = List.of(l.split(" \\| ", -1));
            rows.put(strip(parts.getFirst()), parts.subList(1, parts.size()));
        });
        return rows;
    }

    private static List<String> rowNames(final String text) {
        return new ArrayList<>(rows(text).keySet());
    }

    private static Map<String, String> descriptions(final String text) {
        final Map<String, String> out = new LinkedHashMap<>();
        rows(text).forEach((name, cells) -> out.put(name, cells.getFirst()));
        return out;
    }

    // Python's strip('| `').
    private static String strip(final String text) {
        return PyText.strip(text, "| `");
    }

    // A plan as the MCP server dumped it, read as the MCP server reads one.
    private static FieldPlan plan(final Map<String, Object> json) {
        return FieldPlan.fromJson(json);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, List<String>>> documents(final Object json) {
        return (List<Map<String, List<String>>>) json;
    }

    @SuppressWarnings("unchecked")
    private static <T> T json(final String text) {
        return (T) PyJson.loads(text);
    }

    private static <T> T golden(final String name) {
        return json(goldenText(name));
    }

    // Python's text with its rows laid out as the port lays them out (not in the Stroom MCP server: only what every
    // event has is common, without the In sample column; what only some events have, and what is read from
    // whichever action element, elsewhere): the text before the tables, and each row, as Python wrote them.
    private static String inPortLayout(final String python, final Map<String, Double> population) {
        final List<String> lines = python.lines().toList();
        final int start = lines.indexOf("### Common fields");
        List<String> head = null;
        final List<String> seen = new ArrayList<>();
        final List<IndexFieldDocs.IndexRow> rows = new ArrayList<>();
        for (final String line : lines.subList(start, lines.size())) {
            if (!line.startsWith("| ") || line.startsWith("| ---")) {
                continue;
            }
            final List<String> cells = cells(line);
            if (head == null) {
                head = cells;
            } else if (!cells.equals(head) && !seen.contains(line)) {
                seen.add(line);
                final String from = cells.get(3);
                rows.add(new IndexFieldDocs.IndexRow(from.startsWith("`")
                        ? from.substring(1, from.length() - 1)
                        : "", cells));
            }
        }
        final List<String> out = new ArrayList<>(lines.subList(0, start));
        out.addAll(IndexFieldDocs.indexTables(rows, head, FieldDocs.BY_EVENT_TYPE, population));
        return String.join("\n", out) + "\n";
    }

    // The index fields of the Common fields table, in order.
    private static List<String> commonFields(final String text) {
        final String table = text.split("### Common fields")[1].split("\n### ", 2)[0];
        return table.lines()
                .filter(line -> line.startsWith("| `"))
                .map(line -> line.substring(3, line.indexOf('`', 3)))
                .toList();
    }

    // A table row's cells, as FieldDocs.row wrote them.
    private static List<String> cells(final String line) {
        final String inner = line.substring(2, line.length() - 2);
        final List<String> out = new ArrayList<>();
        for (final String cell : inner.split("(?<!\\\\) \\| ", -1)) {
            out.add(cell.replace("\\|", "|").replace("<br>", "\n"));
        }
        return out;
    }

    private static String goldenText(final String name) {
        return resource("/stroom/ai/impl/golden/index/" + name);
    }

    private static String resource(final String name) {
        try (final InputStream in = TestIndexFieldDocs.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("No resource " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
