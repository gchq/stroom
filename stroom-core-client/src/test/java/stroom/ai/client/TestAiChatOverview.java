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

package stroom.ai.client;

import stroom.ai.client.AiChatOverview.Item;
import stroom.ai.client.AiChatOverview.Kind;
import stroom.ai.client.AiChatOverview.Overview;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiChatOverview {

    @Test
    void eachStepIsFollowedByTheToolsCalledAndTheTimeSpentUntilTheNext() {
        final Overview overview = AiChatOverview.of(List.of(
                // Before the first question: nothing to join it to.
                Item.work(1, 0),
                Item.step(2, 10_000, Kind.QUESTION, "Onboard feed TWITTER"),
                Item.tool(3, 11_000, "activate_onboarding"),
                Item.tool(4, 12_000, "find_documents"),
                Item.work(5, 13_000),
                Item.tool(6, 20_000, "find_documents"),
                Item.tool(7, 30_000, "build_status"),
                Item.work(8, 112_000),
                Item.step(9, 120_000, Kind.ANSWER, "4.1.0"),
                Item.step(10, 125_000, Kind.APPROVAL, "create_pipeline")));

        assertThat(overview.steps()).extracting(AiChatOverview.Step::messageId).containsExactly(2, 9, 10);
        assertThat(overview.links()).hasSize(3);
        // Turning groups of tools on says nothing of what was done, so is left out.
        assertThat(overview.links().get(0).tools()).containsExactly("find_documents", "find_documents",
                "build_status");
        assertThat(overview.links().get(0).durationMs()).isEqualTo(102_000);
        assertThat(overview.links().get(1).isEmpty()).isTrue();
        assertThat(overview.links().get(2).isEmpty()).isTrue();
    }

    @Test
    void aChatWithNothingAskedHasNoSteps() {
        assertThat(AiChatOverview.of(List.of(Item.work(1, 0))).steps()).isEmpty();
        assertThat(AiChatOverview.svg(AiChatOverview.of(List.of()), false)).contains("Nothing asked yet");
    }

    @Test
    void stepsAreDrawnToBeClickedAndTheirTextIsMadeSafe() {
        final String svg = AiChatOverview.svg(AiChatOverview.of(List.of(
                Item.step(2, 0, Kind.QUESTION, "Is <script> & \"this\" safe?"),
                Item.tool(3, 1_000, "find_documents"),
                Item.tool(4, 2_000, "find_documents"),
                Item.work(5, 65_000),
                Item.step(6, 70_000, Kind.REJECTION, "delete_feed"))), false);

        assertThat(svg)
                .startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\"")
                .contains(AiChatOverview.MESSAGE_ID_ATTRIBUTE + "=\"2\"")
                .contains(AiChatOverview.MESSAGE_ID_ATTRIBUTE + "=\"6\"")
                .contains("ai-overview-node--question")
                .contains("ai-overview-node--rejection")
                .contains("You asked")
                .contains("You rejected")
                .contains("Is &lt;script&gt; &amp; &quot;this&quot; safe?")
                .doesNotContain("<script>")
                .contains(">find_documents ×2</text>")
                .contains(">1m 5s</text>")
                .doesNotContain("ai-overview-link--live");
    }

    @Test
    void theLastStepShowsTheAgentStillWorking() {
        final Overview overview = AiChatOverview.of(List.of(Item.step(2, 0, Kind.QUESTION, "status?")));

        assertThat(AiChatOverview.svg(overview, true))
                .contains("ai-overview-link--live")
                .contains(">Working…</text>")
                .contains("ai-overview-link-end");
        // Finished with nothing after it: no line at all.
        assertThat(AiChatOverview.svg(overview, false)).doesNotContain("<line");
    }

    @Test
    void theHeightIsGivenForZooming() {
        final String svg = AiChatOverview.svg(AiChatOverview.of(List.of(
                Item.step(2, 0, Kind.QUESTION, "one"),
                Item.step(3, 0, Kind.STEER, "two"))), false);

        // Two one-line steps (51 each) joined by a short line (24), within the top and bottom margins (16 each).
        assertThat(svg).contains("viewBox=\"0 0 360 158\"").contains("data-height=\"158\"");
    }

    @Test
    void toolsAreSummedUpInTheOrderFirstCalled() {
        final List<String> tools = List.of("find_documents", "build_status", "find_documents", "jq", "grep");

        assertThat(AiChatOverview.toolSummary(tools, Integer.MAX_VALUE))
                .isEqualTo("find_documents ×2, build_status, jq, grep");
        assertThat(AiChatOverview.toolSummary(tools, 24)).isEqualTo("find_documents ×2, +3 more");
        // The first is shown, however long.
        assertThat(AiChatOverview.toolSummary(List.of("a_very_long_tool_name_indeed"), 10))
                .isEqualTo("a_very_long_tool_name_indeed");
        assertThat(AiChatOverview.toolSummary(List.of(), 24)).isEmpty();
    }

    @Test
    void textIsWrappedInWholeWordsAndCutShortIfTooLong() {
        assertThat(AiChatOverview.wrap("onboard feed TWITTER, ignoring any existing pipelines", 20, 4, 4))
                .containsExactly("onboard feed", "TWITTER, ignoring", "any existing", "pipelines");
        assertThat(AiChatOverview.wrap("onboard feed TWITTER, ignoring any existing pipelines", 20, 2, 2))
                .containsExactly("onboard feed", "TWITTER, ignoring…");
        assertThat(AiChatOverview.wrap("x".repeat(30), 10, 2, 2)).containsExactly("xxxxxxxxx…");
        assertThat(AiChatOverview.wrap(null, 10, 2, 2)).containsExactly("");
    }

    @Test
    void eachLineOfTextStartsALineOfItsOwn() {
        // As an answer gives its question and answer, each cut short to its own share of the lines.
        assertThat(AiChatOverview.wrap("Question: Which event-logging schema version should it write?\n"
                                       + "Answer: 4.1.0", 24, 2, 6))
                .containsExactly("Question: Which", "event-logging schema…", "Answer: 4.1.0");
        // Past the most lines in all, the last says there is more.
        assertThat(AiChatOverview.wrap("Question: one\nAnswer: a\nQuestion: two\nAnswer: b", 24, 2, 3))
                .containsExactly("Question: one", "Answer: a", "Question: two…");
        assertThat(AiChatOverview.wrap("first\n\nsecond", 24, 2, 3)).containsExactly("first", "second");
    }

    @Test
    void anAnswerShowsEachQuestionThenItsAnswerUnderHeadings() {
        final AiChatOverview.Step answer = new AiChatOverview.Step(2, Kind.ANSWER,
                "Question: Which event-logging schema version should the translation write?\nAnswer: 4.1.0\n"
                + "Question: Index the events?\nAnswer: Yes");

        assertThat(AiChatOverview.rows(answer)).containsExactly(
                AiChatOverview.Row.heading("Question"),
                AiChatOverview.Row.line("Which event-logging schema version"),
                AiChatOverview.Row.line("should the translation write?"),
                AiChatOverview.Row.heading("You answered"),
                AiChatOverview.Row.line("4.1.0"),
                AiChatOverview.Row.heading("Question"),
                AiChatOverview.Row.line("Index the events?"),
                AiChatOverview.Row.heading("You answered"),
                AiChatOverview.Row.line("Yes"));
        // Other steps have their kind as their heading.
        assertThat(AiChatOverview.rows(new AiChatOverview.Step(3, Kind.QUESTION, "status?")))
                .containsExactly(AiChatOverview.Row.heading("You asked"), AiChatOverview.Row.line("status?"));

        final String svg = AiChatOverview.svg(AiChatOverview.of(List.of(Item.step(2, 0, Kind.ANSWER,
                "Question: Which?\nAnswer: 4.1.0 <latest>"))), false);
        assertThat(svg)
                .contains(">Question</text>")
                .contains(">Which?</text>")
                .contains(">You answered</text>")
                .contains(">4.1.0 &lt;latest&gt;</text>")
                // Two headings, two lines, room above the second heading: 2 x 10 + 2 x 14 + 4 + 2 x 17.
                .contains("height=\"86\"");
    }

    @Test
    void anAnswerWithMoreThanFitsSaysSo() {
        final StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 5; i++) {
            text.append("Question: Question ").append(i).append("\nAnswer: Answer ").append(i).append('\n');
        }

        final List<AiChatOverview.Row> rows = AiChatOverview.rows(new AiChatOverview.Step(2, Kind.ANSWER,
                text.toString()));

        assertThat(rows).filteredOn(row -> !row.heading()).hasSize(6);
        assertThat(rows.get(rows.size() - 1)).isEqualTo(AiChatOverview.Row.line("Answer 3…"));
    }

    @Test
    void aStepMatchesASearchIfItHoldsEachWord() {
        final AiChatOverview.Step step = new AiChatOverview.Step(2, Kind.QUESTION,
                "Onboard feed TWITTER, ignoring any existing pipelines");

        assertThat(AiChatOverview.matches(step, "twitter")).isTrue();
        assertThat(AiChatOverview.matches(step, "  pipelines   ONBOARD ")).isTrue();
        assertThat(AiChatOverview.matches(step, "twitter index")).isFalse();
        assertThat(AiChatOverview.matches(step, " ")).isFalse();
        assertThat(AiChatOverview.matches(step, null)).isFalse();
    }

    @Test
    void aSearchPicksOutTheStepsThatMatchAndDimsTheRest() {
        final Overview overview = AiChatOverview.of(List.of(
                Item.step(2, 0, Kind.QUESTION, "onboard TWITTER"),
                Item.step(3, 0, Kind.ANSWER, "Question: Which version?\nAnswer: 4.1.0"),
                Item.step(4, 0, Kind.STEER, "and index twitter too")));

        final String svg = AiChatOverview.svg(overview, false, "Twitter");

        assertThat(svg)
                .contains("ai-overview-node--question " + AiChatOverview.MATCH_CLASS)
                .contains("ai-overview-node--answer " + AiChatOverview.DIMMED_CLASS)
                .contains("ai-overview-node--steer " + AiChatOverview.MATCH_CLASS)
                // Each place it is found is marked, as written.
                .contains("onboard <tspan class=\"ai-overview-hit\">TWITTER</tspan>")
                .contains("index <tspan class=\"ai-overview-hit\">twitter</tspan> too");
        // Without a search, nothing is picked out, dimmed or marked.
        assertThat(AiChatOverview.svg(overview, false, " "))
                .doesNotContain(AiChatOverview.MATCH_CLASS)
                .doesNotContain(AiChatOverview.DIMMED_CLASS)
                .doesNotContain(AiChatOverview.HIT_CLASS);
    }

    @Test
    void theToolsAndTimesOnTheLinesAreSearchedToo() {
        final Overview overview = AiChatOverview.of(List.of(
                Item.step(2, 0, Kind.QUESTION, "status?"),
                Item.tool(3, 1_000, "find_documents"),
                Item.tool(4, 2_000, "build_status"),
                Item.tool(5, 3_000, "jq"),
                Item.tool(6, 4_000, "grep"),
                Item.work(7, 65_000),
                Item.step(8, 70_000, Kind.QUESTION, "and the index?")));

        // Its label has room for only one tool; a tool it would leave out still matches, and is shown first.
        assertThat(AiChatOverview.svg(overview, false, "grep"))
                .contains("ai-overview-link " + AiChatOverview.MATCH_CLASS)
                .contains("ai-overview-node--question " + AiChatOverview.DIMMED_CLASS)
                .contains("><tspan class=\"ai-overview-hit\">grep</tspan>, find_documents, +2 more</text>");
        assertThat(AiChatOverview.svg(overview, false, null)).contains(">find_documents, +3 more</text>");
        assertThat(AiChatOverview.svg(overview, false, "status"))
                .contains("ai-overview-link " + AiChatOverview.MATCH_CLASS)
                .contains("build_<tspan class=\"ai-overview-hit\">status</tspan>")
                .contains("<tspan class=\"ai-overview-hit\">status</tspan>?");
        assertThat(AiChatOverview.svg(overview, false, "1m"))
                .contains("<tspan class=\"ai-overview-hit\">1m</tspan> 5s");
        // Headings are searched and marked as well.
        assertThat(AiChatOverview.svg(overview, false, "asked")).contains("You <tspan class=\"ai-overview-hit\">asked");
    }

    @Test
    void hitsAreMarkedOnceAndTheirTextIsMadeSafe() {
        assertThat(AiChatOverview.highlight("a<b> index indexes", AiChatOverview.words("INDEX ind <b>")))
                .isEqualTo("a<tspan class=\"ai-overview-hit\">&lt;b&gt;</tspan> <tspan class=\"ai-overview-hit\">index"
                           + "</tspan> <tspan class=\"ai-overview-hit\">index</tspan>es");
        assertThat(AiChatOverview.highlight("a & b", List.of())).isEqualTo("a &amp; b");
    }

    @Test
    void aStepsColouredEdgeIsCutToItsRoundedCorners() {
        final String svg = AiChatOverview.svg(AiChatOverview.of(List.of(Item.step(7, 0, Kind.QUESTION, "q"))),
                false);

        assertThat(svg)
                .contains("<clipPath id=\"ai-overview-clip-7\"><rect x=\"36\" y=\"16\" width=\"288\" height=\"51\" "
                          + "rx=\"10\"/></clipPath>")
                .contains("class=\"ai-overview-node-accent\"")
                .contains("clip-path=\"url(#ai-overview-clip-7)\"");
    }
}
