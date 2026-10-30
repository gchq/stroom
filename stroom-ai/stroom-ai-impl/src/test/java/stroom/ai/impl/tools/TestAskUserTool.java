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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.workspace.ChatWorkspace;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import io.modelcontextprotocol.spec.McpSchema.ElicitResult.Action;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAskUserTool {

    private static final int CHAT_ID = 7;
    private static final String ARGUMENTS = """
            {"intro": "A few decisions before I save the translation",
             "questions": [
               {"question": "Which action element for tweets?",
                "options": ["Unknown, with the fields as Data", "Create"],
                "recommended": "Unknown, with the fields as Data"},
               {"question": "Which schema version?", "options": ["3.4.2", "3.2.3"], "recommended": "3.4.2",
                "allowCustom": false}]}""";

    private final McpElicitations elicitations = Mockito.mock(McpElicitations.class);
    private final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
    private final AskUserTool tool = new AskUserTool();

    @Test
    void theQuestionsAreAskedAsAFormAndTheAnswersGoToTheModel() {
        Mockito.when(elicitations.askUser(Mockito.eq(CHAT_ID), Mockito.eq("ask_user"), Mockito.any(),
                        Mockito.anyString(), Mockito.any()))
                .thenReturn(new ElicitResult(Action.ACCEPT,
                        Map.of("q1", "A Social element, if there is one", "q2", "3.4.2"), null));

        final String result = run(ARGUMENTS);

        @SuppressWarnings("unchecked") final ArgumentCaptor<Map<String, Object>> form =
                ArgumentCaptor.forClass(Map.class);
        // With the call's id, so the run can stop until the user answers, and carry on from the call.
        Mockito.verify(elicitations).askUser(Mockito.eq(CHAT_ID), Mockito.eq("ask_user"), Mockito.eq("1"),
                Mockito.eq("A few decisions before I save the translation"), form.capture());
        @SuppressWarnings("unchecked") final Map<String, Map<String, Object>> properties =
                (Map<String, Map<String, Object>>) form.getValue().get("properties");
        // Options the user may add to are suggestions; ones they can't are the only answers allowed.
        assertThat(properties.get("q1"))
                .containsEntry("title", "Which action element for tweets?")
                .containsEntry("examples", List.of("Unknown, with the fields as Data", "Create"))
                .containsEntry("default", "Unknown, with the fields as Data")
                .doesNotContainKey("enum");
        assertThat(properties.get("q2"))
                .containsEntry("enum", List.of("3.4.2", "3.2.3"))
                .doesNotContainKey("examples");
        assertThat(form.getValue().get("required")).isEqualTo(List.of("q1", "q2"));

        assertThat(result).isEqualTo("""
                The user answered:
                - Which action element for tweets?
                  A Social element, if there is one (in their own words)
                - Which schema version?
                  3.4.2""");
    }

    @Test
    void declinedOrUnansweredQuestionsSaySo() {
        Mockito.when(elicitations.askUser(Mockito.anyInt(), Mockito.anyString(), Mockito.any(),
                Mockito.anyString(), Mockito.any())).thenReturn(new ElicitResult(Action.DECLINE, null, null));
        assertThat(run(ARGUMENTS)).contains("chose not to answer");

        Mockito.when(elicitations.askUser(Mockito.anyInt(), Mockito.anyString(), Mockito.any(),
                Mockito.anyString(), Mockito.any())).thenReturn(new ElicitResult(Action.CANCEL, null, null));
        assertThat(run(ARGUMENTS)).contains("were not answered").contains("Don't carry on as if they had");
    }

    @Test
    void anAbsentUserIsNotAsked() {
        Mockito.when(runManager.isUnattended(CHAT_ID)).thenReturn(true);

        assertThat(run(ARGUMENTS)).startsWith("Not asked: the user isn't watching");
        Mockito.verifyNoInteractions(elicitations);
    }

    @Test
    void badQuestionsAreRefused() {
        assertThat(run("{\"questions\": []}")).startsWith("Error: Give the questions as a list of at least one.");
        assertThat(run("{\"questions\": [{\"options\": [\"a\"]}]}")).contains("needs its question");
        assertThat(run("{\"questions\": [{\"question\": \"Which?\", \"allowCustom\": false}]}"))
                .contains("needs options");
        assertThatThrownBy(() -> AskUserTool.questions("{\"questions\": [" + "{\"question\": \"Q\"},".repeat(8)
                                                       + "{\"question\": \"Q\"}]}"))
                .hasMessageContaining("at most 8");
        Mockito.verifyNoInteractions(elicitations);
    }

    @Test
    void questionsTooLargeForOneFormAreRefusedWithTheLimitsSoTheyCanBeAskedShorter() {
        // Each question and option within its own limit, but 8 questions of 6 long options, with what each means,
        // come to more than one form can show; and the intro has no limit of its own.
        final String option = "{\"label\": \"%d" + "o".repeat(AskUserTool.MAX_OPTION_CHARS - 1) + "\", "
                              + "\"description\": \"" + "d".repeat(AskUserTool.MAX_OPTION_CHARS) + "\"}";
        final String options = String.join(", ", IntStream.range(0, AskUserTool.MAX_OPTIONS)
                .mapToObj(i -> option.formatted(i)).toList());
        final String question = "{\"question\": \"" + "q".repeat(AskUserTool.MAX_QUESTION_CHARS) + "\", "
                                + "\"options\": [" + options + "]}";
        final String tooMany = "{\"questions\": [" + String.join(", ", Collections.nCopies(
                AskUserTool.MAX_QUESTIONS, question)) + "]}";
        final String longIntro = "{\"intro\": \"" + "i".repeat(3_000) + "\", "
                                 + "\"questions\": [{\"question\": \"Which?\"}]}";

        assertThat(run(tooMany)).startsWith("Error: These questions can't be shown in one form, as its form is too "
                                            + "large")
                .contains("at most 16000 characters together")
                .contains("Ask fewer questions at a time, or make them shorter, and call again");
        assertThat(run(longIntro)).contains("as its message is too long").contains("The intro may be at most 2000");
        Mockito.verifyNoInteractions(elicitations);
    }

    @Test
    void aRecommendationThatIsNotAnOptionIsLeftOut() {
        final List<AskUserTool.Question> questions = AskUserTool.questions(
                "{\"questions\": [{\"question\": \"Which?\", \"options\": [\"a\", \"a\", \" b \"], "
                + "\"recommended\": \"c\"}]}");

        assertThat(questions).singleElement().satisfies(question -> {
            assertThat(question.options()).containsExactly("a", "b");
            assertThat(question.recommended()).isNull();
            assertThat(question.allowCustom()).isTrue();
        });
        assertThat(AskUserTool.form(questions).toString()).doesNotContain("default");
    }

    @Test
    void optionsGivenWithWhatTheyMeanAreShownByTheirLabels() {
        // As a model gave them, though it is asked for text: shown as "{label=4.1.0, description=...}" before.
        final List<AskUserTool.Question> questions = AskUserTool.questions("""
                {"questions": [{"question": "Which event-logging version?",
                  "options": [{"label": "4.1.0", "description": "Newest this Stroom holds."},
                              {"label": "4.0.2"},
                              "3.x"],
                  "recommended": "4.1.0"}]}""");

        assertThat(questions).singleElement().satisfies(question -> {
            assertThat(question.options()).containsExactly("4.1.0", "4.0.2", "3.x");
            assertThat(question.optionDescriptions()).containsExactly("Newest this Stroom holds.", "", "");
            assertThat(question.recommended()).isEqualTo("4.1.0");
        });
        @SuppressWarnings("unchecked") final Map<String, Map<String, Object>> properties =
                (Map<String, Map<String, Object>>) AskUserTool.form(questions).get("properties");
        assertThat(properties.get("q1"))
                .containsEntry("examples", List.of("4.1.0", "4.0.2", "3.x"))
                .containsEntry("optionDescriptions", List.of("Newest this Stroom holds.", "", ""));
    }

    @Test
    void optionsWithoutDescriptionsGiveNone() {
        final List<AskUserTool.Question> questions = AskUserTool.questions(
                "{\"questions\": [{\"question\": \"Which?\", \"options\": [\"a\", {\"value\": \"b\"}, {}]}]}");

        assertThat(questions).singleElement()
                .satisfies(question -> assertThat(question.options()).containsExactly("a", "b"));
        assertThat(AskUserTool.form(questions).toString()).doesNotContain("optionDescriptions");
    }

    private String run(final String arguments) {
        return tool.execute(new ToolCall("1", "ask_user", arguments),
                new ChatToolCallContexts(Mockito.mock(ChatWorkspace.class), () -> elicitations,
                        () -> runManager).forChat(CHAT_ID)).text();
    }
}
