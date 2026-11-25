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
import stroom.ai.impl.tools.instructions.InstructionChecks.IndexFacts;
import stroom.ai.impl.tools.instructions.InstructionChecks.PlanField;
import stroom.ai.impl.tools.instructions.InstructionDraft.Draft;
import stroom.ai.impl.tools.instructions.InstructionDraft.Plan;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Drafting a folder's `## Fields` lines from facts, asking only what the author alone knows.
class TestInstructionDraft {

    @Test
    void aKeyTheDocsInForceGiveAlreadyNeedsNoLine() {
        final Facts facts = new Facts(List.of(doc("/", "## Fields\n- host: Host\n")), List.of(new IndexFacts("Proxy",
                "/Proxy", Set.of("Host"), Map.of(InstructionRules.HOST_FIELD, new PlanField("Host", "Proxy "
                                                                                              + "indexing")),
                Map.of())));

        final Plan plan = InstructionDraft.plan("/Proxy", Map.of(), List.of(), facts);

        assertThat(plan.keys()).isEmpty();
        assertThat(plan.questions()).isEmpty();
    }

    @Test
    void eachIndexsChoiceComesBeforeAnyOtherFieldItHas() {
        // Both have UserId; Endpoint's user is actor.login, so it must come first.
        final Facts facts = new Facts(List.of(), List.of(
                new IndexFacts("Proxy", "/Logs/Proxy", Set.of("UserId"), Map.of(InstructionRules.USER_FIELD,
                        new PlanField("UserId", "Proxy indexing")), Map.of()),
                new IndexFacts("Endpoint", "/Logs/Endpoint", Set.of("UserId", "actor.login"), Map.of(
                        InstructionRules.USER_FIELD, new PlanField("actor.login", "Endpoint indexing")), Map.of())));

        final Draft draft = InstructionDraft.draft(InstructionDraft.plan("/Logs", Map.of(), List.of(), facts),
                Map.of(), "");

        assertThat(draft.lines()).containsExactly(Map.entry(InstructionRules.USER_FIELD,
                "- user: actor.login, UserId"));
        assertThat(draft.notes()).isEmpty();
    }

    @Test
    void choicesOneLineCantGiveAreSaid() {
        // Each index has both fields but uses a different one: no order of one line gives both.
        final Facts facts = new Facts(List.of(), List.of(
                new IndexFacts("A", "/Logs/A", Set.of("x", "y"), Map.of(InstructionRules.USER_FIELD,
                        new PlanField("x", "A indexing")), Map.of()),
                new IndexFacts("B", "/Logs/B", Set.of("x", "y"), Map.of(InstructionRules.USER_FIELD,
                        new PlanField("y", "B indexing")), Map.of())));

        final Draft draft = InstructionDraft.draft(InstructionDraft.plan("/Logs", Map.of(), List.of(), facts),
                Map.of(), "");

        assertThat(draft.notes()).singleElement().satisfies(note -> assertThat(note)
                .contains("one line can't give", "give that index's folder a doc of its own"));
    }

    @Test
    void anAnswerLeavingTheKeyOutOrBlankAddsNothing() {
        final Facts facts = new Facts(List.of(), List.of(new IndexFacts("Proxy", "/Proxy", Set.of("UserId",
                "user.name"), Map.of(InstructionRules.USER_FIELD, new PlanField("UserId", "Proxy indexing")), Map.of(
                InstructionRules.USER_FIELD, "user.name"))));
        final Plan plan = InstructionDraft.plan("/Proxy", Map.of(), List.of(), facts);
        assertThat(plan.questions()).singleElement().satisfies(question -> {
            assertThat(question.text()).isEqualTo("Index Proxy: which field holds the user?");
            assertThat(question.found()).isEqualTo("UserId (the index plan of 'Proxy indexing'); user.name (a "
                                                   + "field convention)");
        });

        assertThat(InstructionDraft.draft(plan, Map.of("q1", InstructionDraft.LEAVE_OUT), "").lines()).isEmpty();
        assertThat(InstructionDraft.draft(plan, Map.of("q1", "user.name"), "").lines())
                .containsEntry(InstructionRules.USER_FIELD, "- user: user.name");
    }

    @Test
    void anIndexFedByPipelinesWhosePlansDifferOffersEachPipelinesField() {
        final Facts facts = new Facts(List.of(), List.of(new IndexFacts("Proxy", "/Proxy", Set.of("UserId",
                "user.name"), Map.of(), Map.of(), Map.of(InstructionRules.USER_FIELD, List.of(
                new PlanField("UserId", "A indexing"), new PlanField("user.name", "B indexing"))))));

        assertThat(InstructionDraft.plan("/Proxy", Map.of(), List.of(), facts).questions()).singleElement()
                .satisfies(question -> assertThat(question.found()).isEqualTo("UserId (the index plan of 'A "
                                                                              + "indexing'); user.name (the index "
                                                                              + "plan of 'B indexing')"));
    }

    @Test
    void theAuthorsLinesAreReadAsTheToolsReadThem() {
        assertThat(InstructionDraft.stated(List.of("- Person ID: `pid`", "host: a, b"))).containsExactly(
                Map.entry(InstructionRules.PERSON_FIELD, List.of("pid")),
                Map.entry(InstructionRules.HOST_FIELD, List.of("a", "b")));
        assertThatThrownBy(() -> InstructionDraft.stated(List.of("actor.login")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void linesReplaceTheDocsOwnOrAreAddedToItsFields() {
        final Map<String, String> lines = new LinkedHashMap<>();
        lines.put(InstructionRules.USER_FIELD, "- user: actor.login");
        lines.put(InstructionRules.HOST_FIELD, "- host: host.name");

        assertThat(InstructionChecks.withFieldLines("# Rules\n\n## Fields\n- user: UserId\n\n## Notes\nUTC.\n",
                lines)).isEqualTo("# Rules\n\n## Fields\n- user: actor.login\n- host: host.name\n\n## Notes\nUTC.\n");
        assertThat(InstructionChecks.withFieldLines("# Rules\n\nUTC.", Map.of(InstructionRules.USER_FIELD,
                "- user: actor.login"))).isEqualTo("# Rules\n\nUTC.\n\n## Fields\n\n- user: actor.login\n");
    }

    private static InstructionDoc doc(final String folder, final String text) {
        return new InstructionDoc(null, folder, text, List.of());
    }
}
