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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.tools.AbstractBuiltInTool;
import stroom.ai.impl.tools.ToolDefinition;
import stroom.ai.impl.tools.ToolSchema;
import stroom.ai.impl.tools.ToolSchema.ArraySchema;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.impl.tools.skills.SkillChecks.DataSource;
import stroom.ai.impl.tools.skills.SkillChecks.Finding;
import stroom.ai.impl.tools.skills.SkillChecks.Kind;
import stroom.ai.impl.tools.skills.SkillChecks.OtherSkill;
import stroom.ai.impl.tools.skills.SkillDoc.Skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestSkillChecks {

    private static final ToolDefinition SEARCH = ToolDefinition.builder()
            .name("search_events")
            .description("Searches the events in an index for conditions on its fields.")
            .parameters(ObjectSchema.builder()
                    .addStringProperty("index", "The index.")
                    .addIntegerProperty("limit", "Hits.")
                    .addBooleanProperty("allStreams", "All.")
                    .addProperty("where", ArraySchema.builder().items(ObjectSchema.builder()
                            .addStringProperty("field", "The field.")
                            .addStringProperty("value", "The value.")
                            .addEnumProperty("condition", List.of("EQUALS", "CONTAINS"), "How.")
                            .required("field")
                            .build()).build())
                    .addProperty("fields", AbstractBuiltInTool.stringArraySchema("Fields."))
                    .required("index")
                    .build())
            .build();
    private static final ToolDefinition RUN = ToolDefinition.builder()
            .name("run_stroomql")
            .description("Runs a StroomQL query.")
            .parameters(ObjectSchema.builder().addStringProperty("query", "The query.").required("query").build())
            .build();
    private static final ToolDefinition CONFIG = ToolDefinition.builder()
            .name("config_properties")
            .description("Reads configuration properties.")
            .parameters(ObjectSchema.builder().build())
            .build();

    private final List<OtherSkill> others = new ArrayList<>();
    private final Map<String, SkillUsage.Counts> usage = new LinkedHashMap<>();

    @Test
    void aGoodSkillHasNoProblemsOrSuggestions() {
        final List<Finding> findings = check(skill("""
                ## When to use
                - "build a timeline of a user's activity"
                - "what did this user do last week?"

                ## Parameters
                - user (required): the account.
                - days (default 7): how far back.

                ## Steps
                1. Find the events:
                   ```json
                   {"tool": "search_events", "arguments": {"index": "Endpoint", "where": [{"field": "UserId", \
                "value": "{user}"}], "limit": "{days}"}}
                   ```

                ## Output
                A table in the chat."""));

        assertThat(findings).isEmpty();
        assertThat(SkillChecks.describe(findings)).isEqualTo("No problems or suggestions.");
    }

    @Test
    void structureAndParameterProblemsAreFound() {
        final List<Finding> findings = check(SkillDoc.parse("""
                # Broken

                ## Parameters
                - host (required): the host.

                ## Output
                {user}'s table."""
        ).skills().getFirst());

        assertThat(rules(findings, Kind.PROBLEM)).containsExactlyInAnyOrder("no_description", "no_steps",
                "undeclared_parameter", "unused_parameter");
        assertThat(findings).filteredOn(finding -> finding.rule().equals("undeclared_parameter")).singleElement()
                .satisfies(finding -> assertThat(finding.fix()).contains("- user (required)"));
    }

    @Test
    void callsAreCheckedAgainstTheToolsAndTheirSchemas() {
        final List<Finding> findings = check(skill("""
                ## Steps
                1. A tool that isn't one:
                   ```json
                   {"tool": "search_event", "arguments": {}}
                   ```
                2. A tool the policies deny:
                   ```json
                   {"tool": "config_properties", "arguments": {}}
                   ```
                3. Off the schema:
                   ```json
                   {"tool": "search_events", "arguments": {"limt": 3, "limit": "ten", "allStreams": "yes", \
                "where": [{"value": "x", "condition": "LIKE"}]}}
                   ```"""));

        assertThat(rules(findings, Kind.PROBLEM)).contains("unknown_tool", "tool_not_allowed", "call_off_schema");
        assertThat(findings).filteredOn(finding -> finding.rule().equals("unknown_tool")).singleElement()
                .satisfies(finding -> assertThat(finding.fix()).isEqualTo("Did you mean 'search_events'?"));
        assertThat(findings).filteredOn(finding -> finding.rule().equals("call_off_schema")).singleElement()
                .satisfies(finding -> assertThat(finding.message()).contains("'limt' isn't a parameter",
                        "'limit' should be a whole number", "'allStreams' should be true or false",
                        "'where[0].condition' should be one of EQUALS, CONTAINS", "'where[0].field' is required",
                        "'index' is required"));
    }

    @Test
    void aParameterOrPlaceholderFitsAnyType() {
        final List<String> out = new ArrayList<>();

        SkillChecks.schema(Map.of("index", "[[endpoint index]]", "limit", "{days}", "allStreams", "{all}"),
                SEARCH.parameters(), "", out);

        assertThat(out).isEmpty();
    }

    @Test
    void missingDataSourcesAndFieldsAreFound() {
        final List<Finding> findings = check(skill("""
                ## Steps
                1. Search:
                   ```json
                   {"tool": "search_events", "arguments": {"index": "Endpoint", "where": [{"field": "UserNme", \
                "value": "x"}], "fields": ["Host"]}}
                   ```
                2. Query:
                   ```json
                   {"tool": "run_stroomql", "arguments": {"query": "from \\"Gone Index\\" select a"}}
                   ```
                3. A placeholder isn't looked up:
                   ```json
                   {"tool": "run_stroomql", "arguments": {"query": "from \\"[[identity index]]\\" select a"}}
                   ```"""));

        assertThat(findings).filteredOn(finding -> finding.rule().equals("missing_field"))
                .extracting(Finding::message)
                .singleElement().asString().contains("'UserNme'");
        assertThat(findings).filteredOn(finding -> finding.rule().equals("missing_data_source"))
                .extracting(Finding::message)
                .singleElement().asString().contains("'Gone Index'");
        assertThat(rules(findings, Kind.PROBLEM)).contains("unfilled_placeholder");
    }

    @Test
    void anEditByHandIsAProblemOfferedToRecord() {
        final String text = SkillDoc.parse("").withSkill(TestSkillDoc.TIMELINE, new SkillDoc.HistoryEntry(1,
                "2026-11-23", "jsmith", SkillDoc.HOW_AGENT, "User activity timeline", "Created", null));
        final SkillDoc edited = SkillDoc.parse(text + "\nChanged by hand.\n");

        final List<Finding> findings = SkillChecks.check(edited.skills().getFirst(), edited, facts());

        assertThat(findings).filteredOn(finding -> finding.rule().equals("hand_edit")).singleElement()
                .satisfies(finding -> {
                    assertThat(finding.message()).contains("version 1");
                    assertThat(finding.fix()).contains("record");
                });
    }

    @Test
    void suggestionsAreMadeWithFixes() {
        final List<Finding> findings = check(SkillDoc.parse("""
                # Timeline

                Builds a timeline of jsmith's activity.

                ## When to use
                - "timeline of jsmith"

                ## Steps
                1. Search the events:
                   ```json
                   {"tool": "search_events", "arguments": {"index": "Endpoint", "where": [{"field": "UserId", \
                "value": "jsmith"}], "timeFrom": "2026-11-01"}}
                   ```
                2. Look at what was found in the index's events, and search them again.

                ## Output
                A pie chart in the chat.
                ```mermaid
                pie
                ```"""
        ).skills().getFirst());

        assertThat(rules(findings, Kind.SUGGESTION)).contains("hard_coded_value", "vague_step",
                "chart_outside_subset", "thin_when_to_use");
        assertThat(findings).filteredOn(finding -> finding.rule().equals("vague_step")).singleElement()
                .satisfies(finding -> assertThat(finding.fix()).contains("\"tool\":\"search_events\"",
                        "\"index\":\"...\""));
        assertThat(findings).filteredOn(finding -> finding.rule().equals("hard_coded_value"))
                .extracting(Finding::message)
                .anySatisfy(message -> assertThat(message).contains("'2026-11-01'"))
                .anySatisfy(message -> assertThat(message).contains("'jsmith'"));
        assertThat(findings).filteredOn(finding -> finding.rule().equals("chart_outside_subset"))
                .extracting(Finding::message)
                .anySatisfy(message -> assertThat(message).contains("pie"));
        assertThat(findings.getFirst().kind()).as("problems first").isEqualTo(Kind.PROBLEM);
    }

    @Test
    void aTimelineInTheChatOrAChartOnADashboardIsFine() {
        assertThat(rules(check(skill("""
                ## Steps
                1. Draw it with `run_stroomql`.

                ## Output
                A timeline in the chat (```mermaid gantt), and a pie chart on a dashboard.""")), Kind.SUGGESTION))
                .doesNotContain("chart_outside_subset");
    }

    @Test
    void aNearDuplicateIsPointedOut() {
        others.add(new OtherSkill("User activity timeline", "Builds a timeline of one user's activity over a "
                                                            + "period.", "/Security", false));

        assertThat(rules(check(skill("")), Kind.SUGGESTION)).contains("near_duplicate");
    }

    @Test
    void howItWasUsedIsReported() {
        usage.put("user activity timeline", new SkillUsage.Counts(6, 0, List.of("run the user activity timeline "
                                                                                + "for bob")));

        assertThat(rules(check(skill("")), Kind.SUGGESTION)).contains("offered_never_chosen", "asked_not_offered");
    }

    // A skill named "User activity timeline", with a description, and the sections given.
    private static Skill skill(final String sections) {
        return SkillDoc.parse("# User activity timeline\n\nBuilds a timeline of one user's activity.\n\n" + sections)
                .skills().getFirst();
    }

    private List<Finding> check(final Skill skill) {
        return SkillChecks.check(skill, null, facts());
    }

    private static List<String> rules(final List<Finding> findings, final Kind kind) {
        return findings.stream().filter(finding -> finding.kind() == kind).map(Finding::rule).toList();
    }

    private SkillChecks.Facts facts() {
        final Map<String, ToolDefinition> tools = Map.of(SEARCH.name(), SEARCH, RUN.name(), RUN, CONFIG.name(),
                CONFIG);
        return new SkillChecks.Facts() {
            @Override
            public Optional<ToolDefinition> tool(final String name) {
                return Optional.ofNullable(tools.get(name));
            }

            @Override
            public Set<String> toolNames() {
                return tools.keySet();
            }

            @Override
            public boolean allowed(final String name) {
                return !CONFIG.name().equals(name);
            }

            @Override
            public DataSource dataSource(final String dataSource) {
                return "Endpoint".equals(dataSource)
                        ? new DataSource(true, Set.of("UserId", "Host", "EventTime"))
                        : new DataSource(false, null);
            }

            @Override
            public Optional<String> closestByPurpose(final Set<String> words) {
                return words.contains("search") || words.contains("index")
                        ? Optional.of(SEARCH.name())
                        : Optional.empty();
            }

            @Override
            public List<OtherSkill> otherSkills() {
                return others;
            }

            @Override
            public SkillUsage.Counts usage(final String skill) {
                return usage.get(skill.toLowerCase(Locale.ROOT));
            }
        };
    }
}
