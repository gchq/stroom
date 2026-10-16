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

package stroom.ai.impl;

import stroom.ai.api.AiService;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestChatTitler {

    private final AiService aiService = Mockito.mock(AiService.class);
    private final AiDao aiDao = Mockito.mock(AiDao.class);
    private AiConfig aiConfig = new AiConfig();
    private final ChatTitler titler = new ChatTitler(aiService, aiDao, () -> aiConfig);
    private final List<ChatRequest> requests = new ArrayList<>();

    @Test
    void titlesAreDueAfterTheFirstThirdSixthAndEveryTenthQuestion() {
        assertThat(IntStream.rangeClosed(0, 31).filter(ChatTitler::isDue).boxed().toList())
                .containsExactly(1, 3, 6, 10, 20, 30);
    }

    @Test
    void aNewChatIsNamedAfterItsFirstQuestion() {
        questions("  How many firewall errors were there yesterday?\nper host");

        titler.nameFromQuestion(1, "How many firewall errors were there yesterday?\nper host");

        Mockito.verify(aiDao).setAutoTitle(1, "How many firewall errors were there yesterday?");
    }

    @Test
    void laterQuestionsDontRenameItStraightAway() {
        questions("first", "second");

        titler.nameFromQuestion(1, "second");

        Mockito.verifyNoInteractions(aiDao);
    }

    @Test
    void theModelIsAskedForATitleWhenOneIsDue() {
        questions("How many firewall errors yesterday?", "Which hosts?", "Show me fw1's errors");

        titler.retitleIfDue(1, model("Title: \"Firewall errors by host.\"\nmore text"));

        Mockito.verify(aiDao).setAutoTitle(1, "Firewall errors by host");
        // Only the user's questions are sent.
        assertThat(((UserMessage) requests.getFirst().messages().getLast()).singleText())
                .isEqualTo("The user's questions in this chat, oldest first:\n"
                           + "- How many firewall errors yesterday?\n- Which hosts?\n- Show me fw1's errors\n");
    }

    @Test
    void aChatTheUserNamedIsNotRetitled() {
        questions("one");
        Mockito.when(aiDao.isTitleSetByUser(1)).thenReturn(true);

        titler.retitleIfDue(1, model("anything"));

        // Not even asked, as the title would be ignored.
        assertThat(requests).isEmpty();
        Mockito.verify(aiDao, Mockito.never()).setAutoTitle(Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    void theModelIsAskedForUpToTheConfiguredNumberOfWords() {
        questions("one");
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, 4, null);

        titler.retitleIfDue(1, model("Firewall errors on hosts fw1 and fw2"));

        assertThat(((SystemMessage) requests.getFirst().messages().getFirst()).text()).contains("at most 4 words");
        // A longer reply is cut to that many words.
        Mockito.verify(aiDao).setAutoTitle(1, "Firewall errors on hosts");
    }

    @Test
    void byDefaultTitlesAreUpToTwelveWords() {
        questions("one");
        final String title = "Why the firewall on host fw1 dropped packets from the backup network overnight";

        titler.retitleIfDue(1, model(title));

        assertThat(((SystemMessage) requests.getFirst().messages().getFirst()).text()).contains("at most 12 words");
        Mockito.verify(aiDao).setAutoTitle(1, "Why the firewall on host fw1 dropped packets from the backup network");
    }

    @Test
    void noTitleIsAskedForWhenNoneIsDue() {
        questions("one", "two");

        titler.retitleIfDue(1, model("anything"));

        assertThat(requests).isEmpty();
        Mockito.verify(aiDao, Mockito.never()).setAutoTitle(Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    void aModelThatFailsLeavesTheTitleAsItIs() {
        questions("one");
        final ChatModel failing = new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                throw new IllegalStateException("model down");
            }
        };

        titler.retitleIfDue(1, failing);
        titler.retitleIfDue(1, model("   "));

        Mockito.verify(aiDao, Mockito.never()).setAutoTitle(Mockito.anyInt(), Mockito.anyString());
    }

    @Test
    void longChatsSendTheFirstAndLatestQuestionsShortened() {
        final List<String> questions = IntStream.rangeClosed(1, 25).mapToObj(i -> "question " + i).toList();
        final String prompt = ChatTitler.prompt(questions);

        assertThat(prompt.lines().skip(1)).hasSize(ChatTitler.MAX_QUESTIONS)
                .first().isEqualTo("- question 1");
        assertThat(prompt).contains("- question 25").doesNotContain("- question 2\n");
        assertThat(ChatTitler.prompt(List.of("x".repeat(500)))).contains("x".repeat(ChatTitler.MAX_QUESTION_CHARS)
                                                                         + "…");
    }

    @Test
    void titlesAreCleanedUp() {
        assertThat(ChatTitler.clean("**Disk space on fw1.**")).isEqualTo("Disk space on fw1");
        assertThat(ChatTitler.clean("'Quoted'")).isEqualTo("Quoted");
        // A label inside quotes, as models sometimes give.
        assertThat(ChatTitler.clean("\"Title: Firewall errors.\"")).isEqualTo("Firewall errors");
        assertThat(ChatTitler.clean("y".repeat(300))).hasSize(ChatTitler.MAX_TITLE_CHARS).endsWith("…");
        assertThat(ChatTitler.clean(null)).isEmpty();
    }

    private void questions(final String... questions) {
        final List<AiChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < questions.length; i++) {
            messages.add(new AiChatMessage(i * 2 + 1, 1, 0L, AiMessageType.USER_MESSAGE, null, questions[i],
                    i * 2 + 1, null));
            messages.add(new AiChatMessage(i * 2 + 2, 1, 0L, AiMessageType.AI_RESPONSE, null, "an answer",
                    i * 2 + 1, null));
        }
        Mockito.when(aiService.getMessages(1)).thenReturn(messages);
    }

    private ChatModel model(final String reply) {
        return new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                requests.add(chatRequest);
                return ChatResponse.builder().aiMessage(AiMessage.from(reply)).build();
            }
        };
    }
}
