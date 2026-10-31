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
import stroom.ai.impl.tools.generation.MappingError;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.sample.ToolJson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// A field plan read and validated: `tools/indexing/model_cases.json` holds plans (crafted to reach each field's
/// checks), with the plan read from each or the errors it must give (place, type, message and input), in the same
/// order. They pin the established wording the agent is shown.
class TestFieldPlanValidation {

    @TestFactory
    Stream<DynamicTest> aPlanIsValidatedAsPydanticValidatesIt() {
        final List<Object> cases = JsonValues.listOrEmpty(ToolJson.loads(IndexingFixtures.resource(
                "/stroom/ai/impl/tools/indexing/model_cases.json")));
        assertThat(cases).hasSizeGreaterThan(8);
        return cases.stream().map(item -> DynamicTest.dynamicTest(ToolJson.dumps(JsonValues.map(item).get("input")),
                () -> check(JsonValues.map(item))));
    }

    private static void check(final Map<String, Object> given) {
        if (given.containsKey("plan")) {
            assertThat(ToolJson.dumps(FieldPlan.parse(given.get("input")).toJson()))
                    .isEqualTo(ToolJson.dumps(given.get("plan")));
            return;
        }
        try {
            FieldPlan.parse(given.get("input"));
            throw new AssertionError("validated, but pydantic refused it");
        } catch (final MappingValidationException e) {
            final List<Object> errors = new ArrayList<>();
            for (final MappingError error : e.errors()) {
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", error.type());
                entry.put("loc", error.loc().stream().map(part -> part instanceof final Integer n
                        ? (Object) n.longValue()
                        : part).toList());
                entry.put("msg", error.message());
                entry.put("input", error.input());
                errors.add(entry);
            }
            final List<Object> expected = new ArrayList<>();
            for (final Object item : JsonValues.listOrEmpty(given.get("errors"))) {
                final Map<String, Object> error = JsonValues.map(item);
                final Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("type", error.get("type"));
                entry.put("loc", error.get("loc"));
                entry.put("msg", error.get("msg"));
                entry.put("input", error.get("input"));
                expected.add(entry);
            }
            assertThat(ToolJson.dumps(errors, 1)).isEqualTo(ToolJson.dumps(expected, 1));
            assertThat(e.getMessage()).startsWith(((String) given.get("message")).lines().findFirst().orElseThrow());
        }
    }

    @Test
    void aPlanIsReadAndWrittenByJacksonInTheServersShape() throws Exception {
        final FieldPlan plan = FieldPlan.forDiscovery("d", Discovery.of("xml", "@at"));
        final ObjectMapper mapper = new ObjectMapper();
        final String json = mapper.writeValueAsString(plan);
        assertThat(ToolJson.dumps(ToolJson.loads(json))).isEqualTo(ToolJson.dumps(plan.toJson()));
        assertThat(mapper.readValue(json, FieldPlan.class)).isEqualTo(plan);
    }

    @Test
    void aDiscoveryPlanPassedBackWithoutItsFieldsKeepsThem() {
        // Seen (records_output eval, Haiku): the plan saved with "fields": [], and the template then mapped
        // @timestamp as a keyword, so the time range found nothing.
        final FieldPlan plan = FieldPlan.fromJson(IndexingFixtures.json("""
                {"backend": "elasticsearch", "index_name": "assets", "time_field": "last_seen", "fields": [],
                 "discovery": {"input": "delimited", "timestamp_field": "last_seen"}}"""));
        assertThat(plan.fields()).extracting(PlannedField::name, PlannedField::type, PlannedField::source)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("StreamId", "id", "stroom:stream-id()"),
                        org.assertj.core.groups.Tuple.tuple("EventId", "id", "stroom:record-no()"),
                        org.assertj.core.groups.Tuple.tuple("@timestamp", "date", "last_seen"));
        assertThat(plan.timeField()).isEqualTo("@timestamp");
        assertThat(plan.required()).isEmpty();
    }

    @Test
    void aPlanThatIsntOneIsRefusedWithPydanticsWording() {
        assertThatThrownBy(() -> FieldPlan.parse(List.of()))
                .isInstanceOf(MappingValidationException.class)
                .hasMessageContaining("Input should be a valid dictionary or instance of FieldPlan");
    }
}
