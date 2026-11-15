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

package stroom.ai.impl.tools.indexing;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.generation.MappingChanges;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.indexing.ExampleTemplates.Built;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A planned field holding one value in every document (type `constant`), asked for by the user: read and
/// validated, mapped `constant_keyword` with its value by Elasticsearch (not written by the indexing XSLT), written in
/// every record by Lucene's indexing XSLT, and held by every event as verify_index counts them.
class TestPlannedField {

    private static final String NS = "event-logging:3";

    @Test
    void aConstantIsReadWithItsValueAndNoSource() {
        final PlannedField field = PlannedField.fromJson(Map.of("name", "organisation", "type", "constant",
                "value", "Acme Corp"));

        assertThat(field.isConstant()).isTrue();
        assertThat(field.constant()).isEqualTo("Acme Corp");
        assertThat(field.source()).isEmpty();
        assertThat(field.toJson()).containsEntry("type", "constant").containsEntry("value", "Acme Corp");
        // Kept and read back as it was.
        assertThat(PlannedField.fromJson(field.toJson())).isEqualTo(field);
        // Any other field is kept as it always has been: no value key.
        assertThat(PlannedField.of("host.name", "keyword", "EventSource/Device/HostName").toJson())
                .doesNotContainKey("value");
    }

    @Test
    void aConstantWithoutItsValueOrWithASourceIsRefused() {
        assertThatThrownBy(() -> PlannedField.fromJson(Map.of("name", "organisation", "type", "constant")))
                .isInstanceOf(MappingValidationException.class).hasMessageContaining("value");
        assertThatThrownBy(() -> PlannedField.fromJson(Map.of("name", "organisation", "type", "constant",
                "value", "")))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("a field of type constant needs its value");
        assertThatThrownBy(() -> PlannedField.fromJson(Map.of("name", "organisation", "type", "constant",
                "value", "Acme", "source", "EventSource/System/Name")))
                .isInstanceOf(MappingValidationException.class).hasMessageContaining("leave out its source");
        assertThatThrownBy(() -> PlannedField.fromJson(Map.of("name", "organisation", "type", "constant",
                "value", "Acme", "multiple", true)))
                .isInstanceOf(MappingValidationException.class).hasMessageContaining("is one value");
        // A value is only for a constant.
        assertThatThrownBy(() -> PlannedField.fromJson(Map.of("name", "host.name", "type", "keyword",
                "source", "EventSource/Device/HostName", "value", "web01")))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("value is for a field of type constant");
    }

    @Test
    void aConstantIsAddedToAKeptPlanByChanges() {
        final Map<String, Object> kept = new LinkedHashMap<>(plan("elasticsearch").toJson());
        final MappingChanges.Applied applied = MappingChanges.applyPlanChanges(kept, Map.of("fields", List.of(
                Map.of("name", "site", "type", "constant", "value", "London"))));

        final FieldPlan read = FieldPlan.parse(applied.merged());

        assertThat(read.fields()).anyMatch(field -> field.isConstant() && "Acme Corp".equals(field.constant()))
                .anyMatch(field -> field.isConstant() && "London".equals(field.constant()));
        assertThat(read.required()).isEmpty();
    }

    @Test
    void elasticsearchMapsAConstantAsConstantKeywordWithItsValueAndTheXsltDoesNotWriteIt() {
        final FieldPlan plan = plan("elasticsearch");

        final Map<String, Object> properties = properties(plan.elasticTemplate("acme", null));

        assertThat(properties).containsEntry("organisation",
                Map.of("type", "constant_keyword", "value", "Acme Corp"));
        assertThat(plan.xslt()).doesNotContain("organisation").doesNotContain("Acme Corp")
                .contains("key=\"host\"");
    }

    @Test
    void anEcsPlansConstantIsMappedHereNotLeftToTheComponent() {
        // organization.name is ECS's keyword: left to ecs@mappings, it would hold no value.
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme", "@timestamp", List.of(
                        PlannedField.of("StreamId", "id", "@StreamId"),
                        PlannedField.of("EventId", "id", "@EventId"),
                        PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                        PlannedField.constant("organization.name", "Acme Corp", "")))
                .withConvention("ecs", List.of());

        final Map<String, Object> properties = properties(plan.elasticTemplate("acme", IndexingFixtures.ECS));

        assertThat(JsonValues.map(JsonValues.map(properties.get("organization")).get("properties")))
                .containsEntry("name", Map.of("type", "constant_keyword", "value", "Acme Corp"));
        assertThat(plan.conventionProblems(IndexingFixtures.ECS)).isEmpty();
    }

    @Test
    void luceneWritesAConstantAsAKeywordInEveryRecordWithItsValueEscaped() {
        final FieldPlan plan = FieldPlan.of("lucene", "acme", "EventTime", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("EventTime", "date", "EventTime/TimeCreated"),
                PlannedField.constant("Organisation", "Acme & Sons {UK} <Ltd>", "")));

        assertThat(plan.luceneFields()).anySatisfy(field -> assertThat(field)
                .containsEntry("fldName", "Organisation")
                .containsEntry("fldType", "TEXT")
                .containsEntry("analyzerType", "KEYWORD"));
        // Escaped for XML, its braces doubled so the attribute value template reads them as text.
        assertThat(plan.xslt()).contains("<data name=\"Organisation\" value=\"Acme &amp; Sons {{UK}} &lt;Ltd&gt;\"");
    }

    @Test
    void everyIndexedEventHoldsAConstant() {
        final String events = "<Events xmlns=\"" + NS + "\"><Event><EventSource><Device><HostName>web01</HostName>"
                              + "</Device></EventSource></Event><Event><EventSource><Device><HostName>web02"
                              + "</HostName></Device></EventSource></Event></Events>";

        final PlannedValues values = PlannedValues.of(plan("elasticsearch"), List.of(events));

        final PlannedValues.Written written = values.written("organisation");
        assertThat(written.known()).isTrue();
        assertThat(written.perEvent()).containsExactly(List.of("Acme Corp"), List.of("Acme Corp"));
        assertThat(written.source()).contains("the constant 'Acme Corp'");
        assertThat(values.present("organisation").perEvent()).hasSize(2).allMatch(held -> !held.isEmpty());
    }

    @Test
    void anExamplesConstantKeywordFieldIsKeptThoughThePipelineDoesNotWriteIt() {
        final FieldPlan plan = FieldPlan.of("elasticsearch", "acme-v2", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName")));
        final Map<String, Object> planned = JsonValues.map(plan.elasticTemplate("acme-v2", null, false, null)
                .get("body"));
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["acme-v1*"],
                 "template": {"mappings": {"properties": {
                   "host": {"properties": {"name": {"type": "keyword"}}},
                   "organisation": {"type": "constant_keyword", "value": "Acme Corp"},
                   "unset": {"type": "constant_keyword"},
                   "legacy": {"type": "keyword"}}}}}""");

        final Built built = ExampleTemplates.fromExample(planned, example, Map.of(), false, List.of(), List.of(),
                false, IndexingFixtures.ECS);

        final Map<String, Map<String, Object>> fields = TemplateMappings.readMappingFields(built.body());
        assertThat(fields.get("organisation")).containsEntry("type", "constant_keyword")
                .containsEntry("value", "Acme Corp");
        assertThat(fields).doesNotContainKeys("legacy", "unset");
        assertThat(built.notes())
                .anyMatch(note -> note.startsWith("constant_keyword fields of the example, kept")
                                  && note.contains("organisation"))
                .anyMatch(note -> note.startsWith("in the example but not written by this pipeline")
                                  && note.contains("legacy") && !note.contains("organisation"));
    }

    @Test
    void aPlannedConstantKeepsItsOwnValueOverTheExamples() {
        final Map<String, Object> planned = JsonValues.map(plan("elasticsearch")
                .elasticTemplate("acme-v2", null, false, null).get("body"));
        final Map<String, Object> example = IndexingFixtures.json("""
                {"index_patterns": ["acme-v1*"],
                 "template": {"mappings": {"properties": {
                   "organisation": {"type": "constant_keyword", "value": "Old Name"}}}}}""");

        final Built built = ExampleTemplates.fromExample(planned, example, Map.of(), false, List.of(), List.of(),
                false, IndexingFixtures.ECS);

        assertThat(TemplateMappings.readMappingFields(built.body()).get("organisation"))
                .containsEntry("value", "Acme Corp");
        assertThat(built.notes()).anyMatch(note -> note.contains("the plan's value over the example's mapping"));
    }

    private static FieldPlan plan(final String backend) {
        return FieldPlan.of(backend, "acme", "@timestamp", List.of(
                PlannedField.of("StreamId", "id", "@StreamId"),
                PlannedField.of("EventId", "id", "@EventId"),
                PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
                PlannedField.of("host.name", "keyword", "EventSource/Device/HostName"),
                PlannedField.constant("organisation", "Acme Corp", "The organisation the events are from")));
    }

    private static Map<String, Object> properties(final Map<String, Object> template) {
        return JsonValues.map(JsonValues.map(JsonValues.map(JsonValues.map(JsonValues.map(template.get("body"))
                .get("template")).get("mappings")).get("properties")));
    }
}
