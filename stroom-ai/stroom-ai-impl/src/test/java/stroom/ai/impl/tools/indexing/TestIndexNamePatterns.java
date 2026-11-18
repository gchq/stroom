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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The index templates the tools build cover the pipeline's exact index name, a variable's part a wildcard (asked
/// for by the user: `acme-events-v1*` also covered `acme-events-v10`); check_index_template warns of a pattern
/// ending in a digit then `*`.
class TestIndexNamePatterns {

    private static final List<PlannedField> FIELDS = List.of(
            PlannedField.of("@timestamp", "date", "EventTime/TimeCreated"),
            PlannedField.of("user.name", "keyword", "EventSource/User/Id"));

    @Test
    void aPlainNameIsExactAndEachVariableAWildcard() {
        assertThat(IndexNamePatterns.templatePatterns("acme-events-v1")).containsExactly("acme-events-v1");
        assertThat(IndexNamePatterns.templatePatterns("index-name-v1{_suffix}")).containsExactly("index-name-v1*");
        assertThat(IndexNamePatterns.templatePatterns("acme-{feed}-v1-{_year}")).containsExactly("acme-*-v1-*");
        assertThat(IndexNamePatterns.variables("acme-{feed}-{_year}-{feed}")).containsExactly("feed", "_year");
    }

    @Test
    void aPlansTemplateCoversTheExactNameOrItsVariablesAsWildcards() {
        // A data stream's template with an exact pattern: Elasticsearch matches the data stream's own name.
        final Map<String, Object> plain = body(FieldPlan.of("elasticsearch", "acme-events-v1", "@timestamp", FIELDS));
        assertThat(plain.get("index_patterns")).isEqualTo(List.of("acme-events-v1"));
        assertThat(plain).containsKey("data_stream");
        final Map<String, Object> varied = body(FieldPlan.of("elasticsearch", "index-name-v1{_suffix}", "@timestamp",
                FIELDS));
        assertThat(varied.get("index_patterns")).isEqualTo(List.of("index-name-v1*"));
        // The name propose_index_template gives the plan for a name with variables: already a pattern, kept.
        final Map<String, Object> target = body(FieldPlan.of("elasticsearch", IndexingHelpers.templateTarget(
                "index-name-v1{_suffix}").indexName(), "@timestamp", FIELDS));
        assertThat(target.get("index_patterns")).isEqualTo(List.of("index-name-v1*"));
        final Map<String, Object> discovery = body(FieldPlan.forDiscovery("stroom-discovery-acme-v1",
                Discovery.of("json", "ts")));
        assertThat(discovery.get("index_patterns")).isEqualTo(List.of("stroom-discovery-acme-v1"));
    }

    @Test
    void aPatternEndingInADigitThenAWildcardIsWarnedOfButNotBlocking() {
        final Map<String, Object> result = TemplateCheck.compare(template("acme-events-v1*"), List.of(),
                "acme-events-v1", IndexingFixtures.ECS);
        assertThat(result.get("compatible")).isEqualTo(true);
        assertThat(JsonValues.strings(result.get("warnings"))).singleElement().asString()
                .contains("index pattern 'acme-events-v1*' ends in a digit then *")
                .contains("'acme-events-v10' too")
                .contains("use the exact index name ('acme-events-v1')");
    }

    @Test
    void anExactOrNonDigitPatternOrAVariablesWildcardIsNotWarnedOf() {
        for (final String[] given : List.of(new String[]{"acme-events-v1", "acme-events-v1"},
                new String[]{"acme-events*", "acme-events-v1"},
                new String[]{"index-name-v1*", "index-name-v1{_suffix}"})) {
            final Map<String, Object> result = TemplateCheck.compare(template(given[0]), List.of(), given[1],
                    IndexingFixtures.ECS);
            assertThat(JsonValues.strings(result.get("warnings"))).as(given[0]).isEmpty();
        }
    }

    @Test
    void aTemplateNotCoveringTheIndexSuggestsItsExactName() {
        final Map<String, Object> result = TemplateCheck.compare(template("other"), List.of(), "acme-events-v1",
                IndexingFixtures.ECS);
        assertThat(String.valueOf(result.get("pipeline_changes"))).contains(
                "change index_patterns to include 'acme-events-v1', or set");
    }

    private static Map<String, Object> body(final FieldPlan plan) {
        return JsonValues.map(plan.elasticTemplate("t", IndexingFixtures.ECS).get("body"));
    }

    private static Map<String, Object> template(final String pattern) {
        return IndexingFixtures.json("{\"index_patterns\": [\"" + pattern + "\"], \"template\": {\"mappings\": "
                                     + "{\"dynamic\": true, \"properties\": {}}}}");
    }
}
