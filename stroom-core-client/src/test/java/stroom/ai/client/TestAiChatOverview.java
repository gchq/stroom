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
        assertThat(AiChatOverview.wrap("onboard feed TWITTER,\nignoring any existing pipelines", 20, 4))
                .containsExactly("onboard feed", "TWITTER, ignoring", "any existing", "pipelines");
        assertThat(AiChatOverview.wrap("onboard feed TWITTER, ignoring any existing pipelines", 20, 2))
                .containsExactly("onboard feed", "TWITTER, ignoring…");
        assertThat(AiChatOverview.wrap("x".repeat(30), 10, 2)).containsExactly("xxxxxxxxx…");
        assertThat(AiChatOverview.wrap(null, 10, 2)).containsExactly("");
    }
}
