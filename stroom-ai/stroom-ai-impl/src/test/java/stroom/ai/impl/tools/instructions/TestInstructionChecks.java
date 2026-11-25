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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.instructions.AgentInstructions.InstructionDoc;
import stroom.ai.impl.tools.instructions.InstructionChecks.Facts;
import stroom.ai.impl.tools.instructions.InstructionChecks.Finding;
import stroom.ai.impl.tools.instructions.InstructionChecks.IndexFacts;
import stroom.ai.impl.tools.instructions.InstructionChecks.Kind;
import stroom.ai.impl.tools.instructions.InstructionChecks.PlanField;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/// The checks on AGENTS docs (`check_instructions`, and `propose_instructions` before it offers a doc): what the
/// tools can't apply as written, from facts, never forcing a form on free text.
class TestInstructionChecks {

    private static final IndexFacts ENDPOINT = new IndexFacts("Endpoint", "/Endpoint", Set.of("actor.login",
            "host.name", "Initiator.Id", "user.name"), Map.of(), Map.of());
    private static final IndexFacts PROXY = new IndexFacts("Proxy", "/Proxy", Set.of("UserId", "Host"), Map.of(),
            Map.of());
    private static final Facts FACTS = new Facts(List.of(), List.of(ENDPOINT, PROXY));

    @Test
    void aWellWrittenDocHasNoFindings() {
        final String text = """
                # Standing instructions

                Times are UTC; say so in every answer.

                ## Fields
                - user: actor.login, UserId
                - host: host.name, Host

                ## Required imports
                - translation: Stream-Meta-V1

                ## Component templates
                - include: org-base

                ## Documentation
                - field mapping: compact

                ## Notes
                - Always: ask before deleting anything.
                - user: whoever asks
                """;

        assertThat(InstructionChecks.check("/", text, FACTS, false)).isEmpty();
        assertThat(InstructionChecks.describe(List.of())).isEqualTo("No problems or suggestions.");
    }

    @Test
    void aKeyCloseToOneTheToolsKnowIsAProblemWithItsFix() {
        final String text = "## Required imports\n- indexng: Common-Index-V1\n- translations: Stream-Meta-V1\n";

        final List<Finding> findings = InstructionChecks.check(null, text, Facts.NONE, false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.PROBLEM);
            assertThat(finding.rule()).isEqualTo("unknown_key");
            assertThat(finding.fix()).isEqualTo("replace it with '- indexing: Common-Index-V1'.");
        });
        assertThat(InstructionChecks.corrected(text, findings))
                .isEqualTo("## Required imports\n- indexing: Common-Index-V1\n- translations: Stream-Meta-V1\n");
    }

    @Test
    void aLineNamingNoKeyNearOneIsANoteNotAProblem() {
        // Free text in a section the tools read only guides the agent: never forced into a form.
        final List<Finding> findings = InstructionChecks.check(null, "## Fields\n- Remember: hosts are lower case\n",
                Facts.NONE, false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.NOTE);
            assertThat(finding.message()).contains("only guides the agent: fine for a note");
        });
        assertThat(InstructionChecks.problems(findings)).isEmpty();
    }

    @Test
    void aFieldNoIndexHasIsAProblemFixedToTheClosestRealField() {
        final String text = "## Fields\n- user: Initiator.ID\n";

        final List<Finding> findings = InstructionChecks.check("/Endpoint", text, FACTS, false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.PROBLEM);
            assertThat(finding.rule()).isEqualTo("unknown_field");
            assertThat(finding.message()).contains("names Initiator.ID, which no index where the doc applies has");
            assertThat(finding.fix()).contains("replace it with '- user: Initiator.Id'");
        });
        assertThat(InstructionChecks.corrected(text, findings)).isEqualTo("## Fields\n- user: Initiator.Id\n");
    }

    @Test
    void oneOfSeveralFieldsMissingIsASuggestionAndNoneCloseIsSaid() {
        final List<Finding> findings = InstructionChecks.check("/Endpoint", "## Fields\n- user: actor.login, "
                                                                            + "zzz\n", FACTS, false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.SUGGESTION);
            assertThat(finding.fix()).startsWith("none is close: zzz");
        });
    }

    @Test
    void fieldLinesUnderAnotherHeadingAreMovedUnderFields() {
        final String text = "# Standing instructions\n\n## Query Defaults\n- user field: actor.login\n- Look back 7 "
                            + "days.\n";

        final List<Finding> findings = InstructionChecks.check("/Endpoint", text, FACTS, false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("misplaced_fields");
            assertThat(finding.message()).contains("'- user field: actor.login' under '## Query Defaults'");
            assertThat(finding.fix()).contains("a new section at the end", "'- user: actor.login'");
        });
        assertThat(InstructionChecks.corrected(text, findings)).isEqualTo("""
                # Standing instructions

                ## Query Defaults
                - Look back 7 days.

                ## Fields

                - user: actor.login
                """);
    }

    @Test
    void aKeyLineNamingNoIndexFieldUnderAnotherHeadingIsLeftAlone() {
        assertThat(InstructionChecks.check("/Endpoint", "## Notes\n- host: the machine, as the user says it\n",
                FACTS, false)).isEmpty();
    }

    @Test
    void aHeadingThatNearlyNamesASectionTheToolsReadIsRenamed() {
        final String fieldNames = "## Field names\n- user: actor.login\n";
        final List<Finding> findings = InstructionChecks.check("/Endpoint", fieldNames, FACTS, false);
        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("heading");
            assertThat(finding.fix()).isEqualTo("rename it '## Fields'.");
        });
        assertThat(InstructionChecks.corrected(fieldNames, findings)).isEqualTo("## Fields\n- user: actor.login\n");

        assertThat(InstructionChecks.check(null, "### Required import\n- translation: Stream-Meta-V1\n",
                Facts.NONE, false)).singleElement()
                .satisfies(finding -> assertThat(finding.fix()).isEqualTo("rename it '### Required imports'."));
        assertThat(InstructionChecks.check(null, "## **Fields:**\n- user: actor.login\n", Facts.NONE, false))
                .singleElement().satisfies(finding -> assertThat(finding.rule()).isEqualTo("heading"));
        // A heading about something else, with nothing the tools read under it, stays.
        assertThat(InstructionChecks.check(null, "## Files\n- Keep exports small.\n", Facts.NONE, false)).isEmpty();
    }

    @Test
    void aNearHeadingBesideTheRealSectionHasItsLinesMovedThere() {
        final String text = "## Fields\n- host: host.name\n\n## Field names\n- user: actor.login\n";

        final List<Finding> findings = InstructionChecks.check("/Endpoint", text, FACTS, false);

        assertThat(findings).singleElement()
                .satisfies(finding -> assertThat(finding.fix()).contains("under the doc's '## Fields'"));
        assertThat(InstructionChecks.corrected(text, findings))
                .isEqualTo("## Fields\n- host: host.name\n- user: actor.login\n\n## Field names\n");
    }

    @Test
    void aSecondFieldsSectionIsLeftOutSoItsLinesAreMovedToTheFirst() {
        final String text = "## Fields\n- host: host.name\n\n## Notes\nUTC.\n\n## Fields\n- user: actor.login\n";

        final List<Finding> findings = InstructionChecks.check("/Endpoint", text, FACTS, false);

        assertThat(findings).singleElement()
                .satisfies(finding -> assertThat(finding.rule()).isEqualTo("repeated_section"));
        assertThat(InstructionChecks.corrected(text, findings))
                .isEqualTo("## Fields\n- host: host.name\n- user: actor.login\n\n## Notes\nUTC.\n\n");
    }

    @Test
    void theSameKeyTwiceSaysWhichLineApplies() {
        final List<Finding> same = InstructionChecks.check(null, "## Fields\n- user: actor.login\n- user: "
                                                                 + "actor.login\n", Facts.NONE, false);
        assertThat(same).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.SUGGESTION);
            assertThat(finding.edits()).hasSize(1);
        });

        final List<Finding> differing = InstructionChecks.check(null, "## Fields\n- user: actor.login\n- person id:"
                                                                      + " a\n- person: b\n", Facts.NONE, false);
        assertThat(differing).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.PROBLEM);
            assertThat(finding.message()).contains("the later line, '- person: b', applies");
            // Which is meant is the user's to say: no fix is applied.
            assertThat(finding.edits()).isEmpty();
        });
    }

    @Test
    void aLineAMoreGeneralDocGivesTooIsRedundantOrOverridesIt() {
        final Facts facts = new Facts(List.of(doc("/", "## Fields\n- user: actor.login\n- host: Host\n")),
                List.of(ENDPOINT, PROXY));

        final List<Finding> findings = InstructionChecks.check("/Endpoint", "## Fields\n- user: actor.login\n- host:"
                                                                            + " host.name\n", facts, false);

        assertThat(findings).extracting(Finding::kind, Finding::rule).containsExactly(
                tuple(Kind.SUGGESTION, "same_as_general"),
                tuple(Kind.NOTE, "overrides_general"));
        assertThat(findings.get(1).message()).contains("In /Endpoint and below, '- host: host.name' applies, not the "
                                                       + "host line of the AGENTS doc in / (Host)");
    }

    @Test
    void aFieldsLineDisagreeingWithTheIndexPlanIsASuggestionNamingBoth() {
        final IndexFacts planned = new IndexFacts("Endpoint", "/Endpoint", ENDPOINT.fields(), Map.of(
                InstructionRules.USER_FIELD, new PlanField("user.name", "Endpoint indexing")), Map.of());
        final String text = "## Fields\n- user: actor.login\n";

        final List<Finding> findings = InstructionChecks.check("/Endpoint", text, new Facts(List.of(),
                List.of(planned)), false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.SUGGESTION);
            assertThat(finding.rule()).isEqualTo("plan_disagrees");
            assertThat(finding.message()).isEqualTo("'- user: actor.login' gives actor.login for index Endpoint, "
                                                    + "but the index plan of 'Endpoint indexing' maps "
                                                    + "EventSource/User/Id to user.name.");
            assertThat(finding.fix()).contains("ask the user", "'- user: user.name, actor.login'");
        });
        // Never applied without the user.
        assertThat(InstructionChecks.corrected(text, findings)).isEqualTo(text);
    }

    @Test
    void anIndexFedByPipelinesWhosePlansDifferIsANoteNamingEachPipelinesFieldNotASuggestionOfOne() {
        final IndexFacts planned = new IndexFacts("Endpoint", "/Endpoint", ENDPOINT.fields(), Map.of(), Map.of(),
                Map.of(InstructionRules.USER_FIELD, List.of(new PlanField("user.name", "A indexing"),
                        new PlanField("actor.login", "B indexing"))));
        final String text = "## Fields\n- user: actor.login\n";

        final List<Finding> findings = InstructionChecks.check("/Endpoint", text, new Facts(List.of(),
                List.of(planned)), false);

        assertThat(findings).singleElement().satisfies(finding -> {
            assertThat(finding.kind()).isEqualTo(Kind.NOTE);
            assertThat(finding.rule()).isEqualTo("plans_differ");
            assertThat(finding.message()).isEqualTo("Index Endpoint is fed by pipelines whose index plans map "
                                                    + "EventSource/User/Id to different fields: user.name ('A "
                                                    + "indexing'), actor.login ('B indexing'); '- user: "
                                                    + "actor.login' gives actor.login for all of them, so events of "
                                                    + "the other pipelines' streams are read by actor.login only "
                                                    + "where they hold it too.");
        });
        assertThat(planned.planned()).containsEntry(InstructionRules.USER_FIELD,
                new PlanField("user.name", "A indexing"));
        assertThat(planned.plannedFields(InstructionRules.USER_FIELD)).containsExactly("user.name", "actor.login");
    }

    @Test
    void eachPipelineThatAgreesIsComparedAndTheIndexNamedOnce() {
        final IndexFacts planned = new IndexFacts("Endpoint", "/Endpoint", ENDPOINT.fields(), Map.of(), Map.of(),
                Map.of(InstructionRules.USER_FIELD, List.of(new PlanField("user.name", "A indexing"),
                        new PlanField("user.name", "B indexing"))));

        assertThat(InstructionChecks.check("/Endpoint", "## Fields\n- user: actor.login\n", new Facts(List.of(),
                List.of(planned)), false)).singleElement().satisfies(finding -> {
                    assertThat(finding.rule()).isEqualTo("plan_disagrees");
                    assertThat(finding.message()).isEqualTo("'- user: actor.login' gives actor.login for index "
                                                            + "Endpoint, but the index plan of 'A indexing' maps "
                                                            + "EventSource/User/Id to user.name.");
                });
    }

    @Test
    void aPlanIsComparedOnlyWithTheLineInForceForTheIndex() {
        final IndexFacts planned = new IndexFacts("Endpoint", "/Logs/Endpoint", ENDPOINT.fields(), Map.of(
                InstructionRules.USER_FIELD, new PlanField("user.name", "Endpoint indexing")), Map.of());
        // A doc below gives the plan's field, so the general doc's line isn't the one in force there.
        final Facts facts = new Facts(List.of(doc("/Logs/Endpoint", "## Fields\n- user: user.name\n")),
                List.of(planned));

        assertThat(InstructionChecks.check("/Logs", "## Fields\n- user: actor.login\n", facts, false)).isEmpty();
    }

    @Test
    void anIndexWhoseUserOnlyAConventionFindsGetsTheLineSuggested() {
        final IndexFacts guessed = new IndexFacts("Proxy", "/Proxy", Set.of("user.name"), Map.of(), Map.of(
                InstructionRules.USER_FIELD, "user.name"));
        final Facts facts = new Facts(List.of(), List.of(guessed));

        assertThat(InstructionChecks.check("/Proxy", "", facts, true)).singleElement().satisfies(finding -> {
            assertThat(finding.rule()).isEqualTo("convention_only");
            assertThat(finding.fix()).startsWith("add '- user: user.name' under '## Fields'");
        });
        assertThat(InstructionChecks.check("/Proxy", "", facts, false)).isEmpty();
        assertThat(InstructionChecks.check("/Proxy", "## Fields\n- user: user.name\n", facts, true)).isEmpty();
    }

    @Test
    void documentationLayoutsAreCheckedAsTheToolsReadThem() {
        assertThat(InstructionChecks.check(null, "## Documentation\n- field mapping: compcat\n", Facts.NONE, false))
                .singleElement()
                .satisfies(finding -> assertThat(finding.fix()).isEqualTo("replace it with '- field mapping: "
                                                                          + "compact'."));
        assertThat(InstructionChecks.check(null, "## Documentation\n- layout: compact\n", Facts.NONE, false))
                .singleElement()
                .satisfies(finding -> assertThat(finding.fix()).isEqualTo("replace it with '- field mapping: "
                                                                          + "compact'."));
        assertThat(InstructionChecks.check(null, "## Documentation\n- Write it in British English.\n", Facts.NONE,
                false)).isEmpty();
    }

    @Test
    void closestIsOnlyAClearNearMatch() {
        assertThat(InstructionChecks.closest("indexng", Set.of("indexing", "translation"))).containsExactly(
                "indexing");
        assertThat(InstructionChecks.closest("zzz", Set.of("indexing"))).isEmpty();
        assertThat(InstructionChecks.closest("hostx", Set.of("host", "hosty"))).containsExactly("host", "hosty");
    }

    private static InstructionDoc doc(final String folder, final String text) {
        return new InstructionDoc(null, folder, text, List.of());
    }
}
