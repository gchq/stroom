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

package stroom.ai.impl.tools.draft;

import stroom.ai.impl.tools.generation.FieldMapping;
import stroom.ai.impl.tools.generation.MappingFacts;
import stroom.ai.impl.tools.generation.TranslationMapping;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/// A mapping's conditions tested with what they need of it worked out once: the unescape each field is compared
/// through and the nil values, as the evaluator otherwise looks them up a test at a time.
class TestMappingConditions extends DraftFixtures {

    private static final String MAPPING = """
            {"input": "json", "json_layout": "lines", "nil_values": ["-"],
             "common": [{"path": "EventTime/TimeCreated", "field": "time"},
                        {"path": "EventSource/User/Id", "field": "user", "transform": "strip_domain"},
                        {"path": "EventDetail/Description", "field": "note", "transform": "unescape_quotes"}],
             "events": [{"name": "quoted", "when": [{"field": "note", "equals": "say \\"hi\\""}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Quoted"},
                                    {"path": "EventDetail/Unknown/Data", "data_name": "n", "field": "note",
                                     "transform": "unescape_backslashes"}]},
                        {"name": "named", "when": [{"field": "user", "present": true}],
                         "fields": [{"path": "EventDetail/TypeId", "value": "Named"}]},
                        {"name": "rest", "allow_unknown": "the rest",
                         "fields": [{"path": "EventDetail/TypeId", "value": "Rest"}]}]}""";

    @Test
    void eachFieldsUnescapeIsTheFirstEntryReadingItGivesIt() {
        final TranslationMapping m = mapping(json(MAPPING));
        final Set<String> fields = new TreeSet<>();
        m.allEntries().stream().map(FieldMapping::field).filter(field -> field != null).forEach(fields::add);

        fields.forEach(field -> assertThat(MappingFacts.unescapes(m).get(field)).as(field)
                .isEqualTo(firstUnescape(m, field)));
        assertThat(MappingFacts.unescapes(m)).containsExactly(Map.entry("note", "unescape_quotes"));
    }

    @Test
    void conditionsAreToldWithTheFieldsUnescapeAndTheNilValues() {
        final TranslationMapping m = mapping(json(MAPPING));
        final List<Object> records = LocalCheck.sampleRecords(m, String.join("\n",
                "{\"time\": \"t1\", \"note\": \"say \\\"\\\"hi\\\"\\\"\"}",
                "{\"time\": \"t2\", \"user\": \"alice\"}",
                "{\"time\": \"t3\", \"user\": \"-\"}",
                "{\"time\": \"t4\", \"user\": \" \"}") + "\n", null).records();
        final MappingConditions conditions = MappingConditions.of(m);

        assertThat(records.stream().map(record -> conditions.ruleOf(record, null)).toList())
                .containsExactly("quoted", "named", "rest", "rest");
        assertThat(records.stream().map(record -> LocalCheck.ruleOf(m, record, null)).toList())
                .containsExactly("quoted", "named", "rest", "rest");
    }

    // The transform the first entry reading the field gives it, among the unescapes, entry by entry.
    private static String firstUnescape(final TranslationMapping m, final String field) {
        return m.allEntries().stream()
                .filter(entry -> field.equals(entry.field()) && entry.transform() != null
                                 && MappingFacts.UNESCAPES.contains(entry.transform()))
                .map(FieldMapping::transform)
                .findFirst()
                .orElse(null);
    }
}
