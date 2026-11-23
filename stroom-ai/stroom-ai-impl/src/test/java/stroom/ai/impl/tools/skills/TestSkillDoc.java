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

import stroom.ai.impl.tools.skills.SkillDoc.HistoryEntry;
import stroom.ai.impl.tools.skills.SkillDoc.Parameter;
import stroom.ai.impl.tools.skills.SkillDoc.Skill;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSkillDoc {

    static final String TIMELINE = """
            # User activity timeline

            Builds a timeline of one user's activity
            over a period.

            More about it.

            ## When to use

            - "build a timeline of jsmith's activity"
            - "what was this user doing last week?"

            ## Parameters

            - user (required): the user's account name.
            - `days` (default 7): how many days back.
            - host: the host, if one.

            ## Steps

            1. Find the user's events:
               ```json
               {"tool": "run_stroomql", "arguments": {"query": "from Endpoint where UserId = '{user}' and \
            EventTime > now() - {days}d"}}
               ```
            2. Count them by host with `table`.

            ## Output

            A table in the chat.""";

    static final String DOC = """
            ---
            stroom: skills
            owner: "Security team"
            history:
              - version: 1
                date: "2026-11-20"
                author: "admin"
                how: agent
                skill: "User activity timeline"
                change: "Created"
                body: "%s"
            ---

            %s

            # Who of a group did X

            Finds which members of a group did something.

            ## Steps

            1. Find the members with `run_stroomql`.
            """;

    @Test
    void aSkillIsReadByItsConventions() {
        final Skill skill = SkillDoc.parse(TIMELINE).skills().getFirst();

        assertThat(skill.name()).isEqualTo("User activity timeline");
        assertThat(skill.description()).as("the first paragraph only")
                .isEqualTo("Builds a timeline of one user's activity over a period.");
        assertThat(skill.whenToUse()).containsExactly("\"build a timeline of jsmith's activity\"",
                "\"what was this user doing last week?\"");
        assertThat(skill.parameters()).containsExactly(
                new Parameter("user", true, null, "the user's account name.", true),
                new Parameter("days", false, "7", "how many days back.", true),
                new Parameter("host", true, null, "the host, if one.", false));
        assertThat(skill.steps()).hasSize(2);
        assertThat(skill.steps().getFirst().calls()).singleElement().satisfies(call -> {
            assertThat(call.tool()).isEqualTo("run_stroomql");
            assertThat(call.problem()).isNull();
            assertThat(call.arguments()).containsKey("query");
        });
        assertThat(skill.used()).containsExactlyInAnyOrder("user", "days");
        assertThat(skill.output()).isEqualTo("A table in the chat.");
    }

    @Test
    void aDocsFrontMatterMarksItAndHoldsItsHistory() {
        final SkillDoc doc = SkillDoc.parse(DOC.formatted("x", TIMELINE));

        assertThat(doc.isMarked()).isTrue();
        assertThat(doc.skills()).extracting(Skill::name)
                .containsExactly("User activity timeline", "Who of a group did X");
        assertThat(doc.history()).containsExactly(new HistoryEntry(1, "2026-11-20", "admin", "agent",
                "User activity timeline", "Created", "x"));
        assertThat(doc.nextVersion()).isEqualTo(2);
    }

    @Test
    void aDocWithoutFrontMatterIsNotMarked() {
        final SkillDoc doc = SkillDoc.parse(TIMELINE);

        assertThat(doc.isMarked()).isFalse();
        assertThat(doc.hasFrontMatter()).isFalse();
        assertThat(doc.skills()).hasSize(1);
    }

    @Test
    void frontMatterThatIsntYamlIsSaid() {
        final SkillDoc doc = SkillDoc.parse("---\nstroom: [skills\n---\n\n" + TIMELINE);

        assertThat(doc.isMarked()).isFalse();
        assertThat(doc.frontMatterProblem()).startsWith("its front matter isn't YAML");
    }

    @Test
    void aHeadingInACodeBlockIsntASkill() {
        final SkillDoc doc = SkillDoc.parse("# One\n\nDoes one.\n\n## Steps\n\n1. Write:\n   ```\n# Not a skill\n"
                                            + "   ```\n");

        assertThat(doc.skills()).extracting(Skill::name).containsExactly("One");
    }

    @Test
    void anExampleCallThatIsntACallIsSaid() {
        final Skill skill = SkillDoc.parse("# One\n\nDoes one.\n\n## Steps\n\n1. Run:\n   ```json\n   {\"limit\": "
                                           + "{days}}\n   ```\n2. And:\n   ```json\n   {\"arguments\": {}}\n"
                                           + "   ```\n").skills().getFirst();

        assertThat(skill.calls()).extracting(SkillDoc.ExampleCall::problem)
                .satisfiesExactly(problem -> assertThat(problem).contains("isn't JSON", "\"{days}\""),
                        problem -> assertThat(problem).contains("doesn't name its tool"));
    }

    @Test
    void aSkillIsAddedWithAHistoryEntryHashingTheNewBody() {
        final SkillDoc doc = SkillDoc.parse("");

        final String text = doc.withSkill(TIMELINE, entry(1, "Created"));
        final SkillDoc written = SkillDoc.parse(text);

        assertThat(text).startsWith("---\nstroom: skills\nhistory:\n  - version: 1\n    date: \"2026-11-23\"\n"
                                    + "    author: \"jsmith\"\n    how: agent\n    skill: \"User activity "
                                    + "timeline\"\n    change: \"Created\"\n    body: \"");
        assertThat(written.isMarked()).isTrue();
        assertThat(written.skills()).hasSize(1);
        assertThat(written.isEditedSinceLatestEntry()).isFalse();
        assertThat(written.latestEntry().orElseThrow().body()).isEqualTo(written.bodyHash());
    }

    @Test
    void aSkillOfTheSameNameIsReplacedAndTheOthersAndFrontMatterKept() {
        final SkillDoc doc = SkillDoc.parse(SkillDoc.parse("").withSkill(TIMELINE, entry(1, "Created")));
        final SkillDoc two = SkillDoc.parse(doc.withSkill("# Other\n\nAnother.\n", entry(2, "Added")));

        final String changed = TIMELINE.replace("over a period.", "over a period of days.");
        final SkillDoc written = SkillDoc.parse(two.withSkill(changed, entry(3, "Changed")));

        assertThat(written.skills()).extracting(Skill::name).containsExactly("User activity timeline", "Other");
        assertThat(written.skills().getFirst().description()).endsWith("over a period of days.");
        assertThat(written.history()).extracting(HistoryEntry::version).containsExactly(1, 2, 3);
        assertThat(written.isEditedSinceLatestEntry()).isFalse();
    }

    @Test
    void otherFrontMatterKeysAreKept() {
        final SkillDoc doc = SkillDoc.parse(DOC.formatted("x", TIMELINE));

        final String text = doc.withSkill("# Other\n\nAnother.\n", entry(2, "Added"));

        assertThat(text).contains("owner: \"Security team\"\nhistory:\n  - version: 1");
        assertThat(SkillDoc.parse(text).history()).hasSize(2);
    }

    @Test
    void anEditByHandSinceTheLatestEntryIsSeen() {
        final String text = SkillDoc.parse("").withSkill(TIMELINE, entry(1, "Created"));

        assertThat(SkillDoc.parse(text + "\nAn edit by hand.\n").isEditedSinceLatestEntry()).isTrue();
        assertThat(SkillDoc.parse(text + "\n\n").isEditedSinceLatestEntry()).as("white space at the end").isFalse();
    }

    @Test
    void aSkillIsRemoved() {
        final SkillDoc doc = SkillDoc.parse(DOC.formatted("x", TIMELINE));

        final SkillDoc written = SkillDoc.parse(doc.withoutSkill("user activity timeline", entry(2, "Removed")));

        assertThat(written.skills()).extracting(Skill::name).containsExactly("Who of a group did X");
        assertThatThrownBy(() -> doc.withoutSkill("Nope", entry(2, "Removed")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void onlyOneSkillAtATimeIsAdded() {
        assertThatThrownBy(() -> SkillDoc.parse("").withSkill("# One\n\n# Two\n", entry(1, "x")))
                .hasMessageContaining("one skill at a time");
        assertThatThrownBy(() -> SkillDoc.parse("").withSkill("No heading", entry(1, "x")))
                .hasMessageContaining("'# Name' heading");
    }

    @Test
    void aNameIsMadeACommandsWord() {
        assertThat(SkillDoc.slug("User activity timeline")).isEqualTo("user-activity-timeline");
        assertThat(SkillDoc.slug("  X then Y (by entity)! ")).isEqualTo("x-then-y-by-entity");
    }

    @Test
    void aValueWithQuotesIsWrittenAsYamlThatReadsBack() {
        final String text = SkillDoc.parse("").withSkill(TIMELINE, new HistoryEntry(1, "2026-11-23",
                "a \"quoted\" user", SkillDoc.HOW_AGENT, "User activity timeline", "Said: \"hi\"\nthen", null));

        assertThat(SkillDoc.parse(text).latestEntry().orElseThrow())
                .extracting(HistoryEntry::author, HistoryEntry::change)
                .containsExactly("a \"quoted\" user", "Said: \"hi\" then");
    }

    @Test
    void parametersAreGivenValuesAndDefaultsAndTheMissingSaid() {
        final Skill skill = SkillDoc.parse(TIMELINE).skills().getFirst();

        final SkillParameters.Bound bound = SkillParameters.bind(skill, Map.of("user", "jsmith", "colour", "red"));

        assertThat(bound.values()).containsEntry("user", "jsmith").containsEntry("days", "7");
        assertThat(bound.defaulted()).containsExactly("days");
        assertThat(bound.missing()).extracting(Parameter::name).containsExactly("host");
        assertThat(bound.unknown()).containsExactly("colour");
        assertThat(bound.steps()).contains("UserId = 'jsmith'", "now() - 7d");
        assertThat(bound.describe()).isEqualTo("user=jsmith, days=7 (default)");
    }

    private static HistoryEntry entry(final int version, final String change) {
        return new HistoryEntry(version, "2026-11-23", "jsmith", SkillDoc.HOW_AGENT, "User activity timeline",
                change, null);
    }
}
