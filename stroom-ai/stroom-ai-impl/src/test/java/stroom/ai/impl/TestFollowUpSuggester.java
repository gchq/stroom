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
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatAttachment;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.ai.shared.AiSuggestions;
import stroom.docref.DocRef;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestFollowUpSuggester {

    private final AiService aiService = Mockito.mock(AiService.class);
    private AiConfig aiConfig = new AiConfig();
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final FollowUpSuggester suggester = new FollowUpSuggester(aiService, () -> aiConfig, tabSnapshots,
            documentAccess);
    private final List<ChatRequest> requests = new ArrayList<>();
    private final List<AiChatMessage> messages = new ArrayList<>();

    @Test
    void upToThreeSuggestionsAreStoredAfterAnAnswer() {
        turn(AiMessageType.USER_MESSAGE, "How many failed logons were there today?");
        turn(AiMessageType.TOOL_CALL, "{}");
        turn(AiMessageType.AI_RESPONSE, "There were 42 failed logons, mostly on fw1.");
        // Stored after the answer.
        turn(AiMessageType.TURN_DIGEST, "{}");

        suggester.suggest(1, model("""
                1. Which users failed most?
                - "Show the failures on fw1"
                * Which users failed most?
                Compare with yesterday
                And a fourth one"""));

        Mockito.verify(aiService).storeMessage(1, AiMessageType.SUGGESTIONS,
                "[\"Which users failed most?\",\"Show the failures on fw1\",\"Compare with yesterday\"]");
        // Only the question and answer are sent.
        assertThat(((UserMessage) requests.getFirst().messages().getLast()).singleText())
                .contains("How many failed logons were there today?")
                .contains("There were 42 failed logons");
    }

    @Test
    void anAttachedTabIsHintedSoSuggestionsActOnIt() {
        turn(AiMessageType.USER_MESSAGE, "What does this dashboard show?");
        turn(AiMessageType.AI_RESPONSE, "Failed logons by user.");
        final DocRef dashboard = new DocRef("Dashboard", "d1", "Logons");
        Mockito.when(tabSnapshots.forLatestQuestion(1)).thenReturn(Optional.of(new TabSnapshots.StoredTabSnapshot(
                5, 1, dashboard, "/", "Logons", null, false, null, "h", 0L, List.of(), List.of(), null)));

        suggester.suggest(1, model("Group by user"));

        final String prompt = ((UserMessage) requests.getFirst().messages().getLast()).singleText();
        assertThat(prompt).endsWith(AiSuggestions.tabHint(dashboard, null, false, List.of(), null));
        assertThat(FollowUpSuggester.SYSTEM_PROMPT).contains("strongly prefer follow-ups that act on them");
        // Without a tab, nothing is added.
        assertThat(FollowUpSuggester.prompt("q", "a", "")).endsWith("The assistant's answer:\na");
    }

    @Test
    void whatWasJustAttachedIsSuggestedForWithTheConversationSoFar() {
        turn(AiMessageType.USER_MESSAGE, "Onboard the ACME feed");
        turn(AiMessageType.AI_RESPONSE, "The ACME feed is set up, with a translation pipeline.");
        final DocRef feed = new DocRef("Feed", "f1", "ACME");
        final DocRef hidden = new DocRef("Feed", "f2", "SECRET");
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of(
                attachment(7, AiAttachmentType.DOCUMENT, feed, "Feed 'ACME'"),
                attachment(8, AiAttachmentType.DOCUMENT, hidden, "Feed 'SECRET'"),
                attachment(9, AiAttachmentType.DASHBOARD, null, "Dashboard 'Logons' -> Table 'By user'")));
        Mockito.when(documentAccess.canView(feed)).thenReturn(true);

        final List<String> suggested = suggester.suggestForAttached(1, model("Are there errors for ACME?"),
                List.of(9, 8, 7, 99), null);

        assertThat(suggested).containsExactly("Are there errors for ACME?");
        assertThat(requests.getFirst().messages().getFirst())
                .isEqualTo(SystemMessage.from(FollowUpSuggester.ATTACHED_SYSTEM_PROMPT));
        final String prompt = ((UserMessage) requests.getFirst().messages().getLast()).singleText();
        assertThat(prompt)
                .startsWith("The user's latest questions:\n- Onboard the ACME feed")
                .contains("The start of the assistant's latest answer:\nThe ACME feed is set up")
                .contains("Attached:\n" + AiSuggestions.tableHint("Dashboard 'Logons' -> Table 'By user'")
                          + "\n" + AiSuggestions.documentHint(feed))
                // Not one the user can't view, or that isn't the chat's.
                .doesNotContain("SECRET");
        // Nothing is stored: the chat shows them.
        Mockito.verify(aiService, Mockito.never()).storeMessage(Mockito.anyInt(), Mockito.any(),
                Mockito.anyString());
    }

    @Test
    void nothingIsSuggestedForOneThingAttachedWithoutAConversationOrWhenTurnedOff() {
        final DocRef feed = new DocRef("Feed", "f1", "ACME");
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of(
                attachment(7, AiAttachmentType.DOCUMENT, feed, "Feed 'ACME'")));
        Mockito.when(documentAccess.canView(feed)).thenReturn(true);

        assertThat(suggester.suggestForAttached(1, model("x"), List.of(7), null)).isEmpty();
        turn(AiMessageType.USER_MESSAGE, "q");
        assertThat(suggester.suggestForAttached(1, model("x"), List.of(), null)).isEmpty();
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, false, null);
        assertThat(suggester.suggestForAttached(1, model("x"), List.of(7), null)).isEmpty();
        assertThat(requests).isEmpty();
    }

    @Test
    void severalThingsAttachedAreSuggestedForTogetherEvenBeforeAnythingIsAsked() {
        final DocRef feed = new DocRef("Feed", "f1", "ACME");
        final DocRef pipeline = new DocRef("Pipeline", "p1", "ACME-Events");
        Mockito.when(aiService.getAttachmentsByChatId(1)).thenReturn(List.of(
                attachment(7, AiAttachmentType.DOCUMENT, feed, "Feed 'ACME'"),
                attachment(8, AiAttachmentType.DOCUMENT, pipeline, "Pipeline 'ACME-Events'")));
        Mockito.when(documentAccess.canView(Mockito.any())).thenReturn(true);

        assertThat(suggester.suggestForAttached(1, model("Does ACME-Events process ACME?"), List.of(8, 7), null))
                .containsExactly("Does ACME-Events process ACME?");
        final String prompt = ((UserMessage) requests.getFirst().messages().getLast()).singleText();
        assertThat(prompt)
                .startsWith("Nothing has been asked yet.\n\nAttached:\n" + AiSuggestions.documentHint(pipeline)
                            + "\n" + AiSuggestions.documentHint(feed));
        assertThat(FollowUpSuggester.ATTACHED_SYSTEM_PROMPT).contains("uses them together");
        // Seen in testing: offered "Apply these chart changes to the dashboard", which its button does.
        assertThat(FollowUpSuggester.ATTACHED_SYSTEM_PROMPT).contains("Never suggest applying, accepting or undoing");
    }

    private static AiChatAttachment attachment(final int id,
                                               final AiAttachmentType type,
                                               final DocRef docRef,
                                               final String description) {
        return new AiChatAttachment(id, 1, 0L, 0L, AiAttachmentStatus.READY, type, description, null, false, null,
                docRef, null);
    }

    @Test
    void earlierSuggestionsAreReplaced() {
        turn(AiMessageType.USER_MESSAGE, "first");
        turn(AiMessageType.AI_RESPONSE, "answer");
        turn(AiMessageType.SUGGESTIONS, "[\"old\"]");
        turn(AiMessageType.USER_MESSAGE, "second");
        turn(AiMessageType.AI_RESPONSE, "answer");

        suggester.suggest(1, model("next"));

        Mockito.verify(aiService).deleteMessage(3);
        Mockito.verify(aiService).storeMessage(1, AiMessageType.SUGGESTIONS, "[\"next\"]");
    }

    @Test
    void noSuggestionsWhenTheUserWasAskedForSomething() {
        for (final AiMessageType asked : List.of(AiMessageType.ELICITATION, AiMessageType.APPROVAL_REQUEST)) {
            messages.clear();
            turn(AiMessageType.USER_MESSAGE, "Onboard ACME");
            turn(asked, "{}");
            turn(AiMessageType.AI_RESPONSE, "Done.");

            suggester.suggest(1, model("anything"));
        }
        // Steps offered as buttons.
        messages.clear();
        turn(AiMessageType.USER_MESSAGE, "Onboard ACME");
        turn(AiMessageType.TOOL_RESULT, "{\"callId\":\"c\",\"userActions\":[{\"id\":\"enable\"}]}");
        turn(AiMessageType.AI_RESPONSE, "Click the button to enable the filter.");
        suggester.suggest(1, model("anything"));

        assertThat(requests).isEmpty();
        Mockito.verify(aiService, Mockito.never())
                .storeMessage(Mockito.anyInt(), Mockito.eq(AiMessageType.SUGGESTIONS), Mockito.anyString());
    }

    @Test
    void noSuggestionsWithoutAnAnswerOrWhenTurnedOff() {
        turn(AiMessageType.USER_MESSAGE, "question");
        turn(AiMessageType.ERROR, "The model failed");
        suggester.suggest(1, model("anything"));

        messages.clear();
        turn(AiMessageType.USER_MESSAGE, "question");
        turn(AiMessageType.AI_RESPONSE, "answer");
        aiConfig = new AiConfig(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, false, null);
        suggester.suggest(1, model("anything"));

        assertThat(requests).isEmpty();
    }

    @Test
    void aModelThatFailsOrHasNothingToSuggestStoresNothing() {
        turn(AiMessageType.USER_MESSAGE, "hello");
        turn(AiMessageType.AI_RESPONSE, "Hello! How can I help?");

        suggester.suggest(1, model("   "));
        suggester.suggest(1, new ChatModel() {
            @Override
            public ChatResponse doChat(final ChatRequest chatRequest) {
                throw new IllegalStateException("model down");
            }
        });

        Mockito.verify(aiService, Mockito.never())
                .storeMessage(Mockito.anyInt(), Mockito.eq(AiMessageType.SUGGESTIONS), Mockito.anyString());
    }

    @Test
    void overlongSuggestionsAreLeftOut() {
        assertThat(FollowUpSuggester.parse("x".repeat(FollowUpSuggester.MAX_SUGGESTION_CHARS + 1) + "\nshort"))
                .containsExactly("short");
        assertThat(FollowUpSuggester.parse(null)).isEmpty();
    }

    private void turn(final AiMessageType type, final String text) {
        final int id = messages.size() + 1;
        messages.add(new AiChatMessage(id, 1, 0L, type, null, text, id, null));
        Mockito.when(aiService.getMessages(1)).thenReturn(List.copyOf(messages));
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
