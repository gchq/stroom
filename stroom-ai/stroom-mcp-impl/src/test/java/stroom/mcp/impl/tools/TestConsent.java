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

package stroom.mcp.impl.tools;

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiChatRunManager;
import stroom.ai.impl.ChatForms;
import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.tools.ChatUserQuestions;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Confirmations and approvals before a write: always a form; where the session can't show one, a refusal, never a
/// pending id for the model to pass back.
class TestConsent {

    private static final ToolSession SESSION = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));
    private static final ToolCall CALL = new ToolCall("call-1", "create_feed", "{}");
    private static final String SOURCE_QUESTION = "Which field says which source a record came from?";

    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final Consent consent = new Consent(() -> auditLog);

    @Test
    void anMcpClientThatCantShowFormsIsRefusedAndGivenNoId() {
        // The model must never be handed an id it could pass back to approve its own call: a client without forms
        // (MCP elicitation) can't be asked, so nothing is done.
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), confirmation()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage(FormsRequired.cannotAsk(SESSION, "create_feed", "Create feed A"))
                .hasMessageStartingWith("Not done: Create feed A. This needs the user's agreement in a form that "
                                        + "their MCP client shows (MCP elicitation)")
                .hasMessageContaining("nothing was done", "Calls that need no agreement still work")
                .hasMessageNotContaining("_id");
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), Consent.Request.approval("promote_build",
                "Promote", Map.of("plan", List.of("a")))))
                .hasMessageStartingWith("Not done: Promote.")
                .hasMessageNotContaining("approval_id");
        Mockito.verify(auditLog, Mockito.times(2)).processed(Mockito.eq(SESSION), Mockito.isNull(),
                Mockito.anyString(), Mockito.anyString(), Mockito.eq("refused: no form can be shown"),
                Mockito.eq(false));
    }

    @Test
    void aChatTheUserIsAwayFromIsRefusedUntilTheyAreBack() {
        final ToolSession chat = ToolSession.chat(7);

        assertThatThrownBy(() -> consent.require(CALL, textOnly(chat), confirmation()))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("Not done: Create feed A. This needs the user's agreement in a form, and they are away "
                            + "from the chat, so they can't be asked now: nothing was done. Carry on with what "
                            + "doesn't need their agreement, and call create_feed again when they are back: they are "
                            + "asked then.");
    }

    @Test
    void aChatAsksThroughItsOwnForm() {
        // In the chat the form is an ask_user card: the run stops while it waits, and the same call, made again
        // with the answer, goes on.
        final McpElicitations elicitations = Mockito.mock(McpElicitations.class);
        final AiChatRunManager runManager = Mockito.mock(AiChatRunManager.class);
        Mockito.when(runManager.isUnattended(7)).thenReturn(false);
        Mockito.when(elicitations.askUser(Mockito.eq(7), Mockito.eq("create_feed"), Mockito.anyString(),
                        Mockito.anyString(), Mockito.anyMap()))
                .thenThrow(new AiChatRunManager.WaitingForUserException("waiting"))
                .thenReturn(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));
        final ToolCallContext context = ToolCallContext.builder(ToolSession.chat(7))
                .userQuestions(new ChatUserQuestions(7, () -> elicitations, () -> runManager))
                .build();

        assertThatThrownBy(() -> consent.require(CALL, context, confirmation()))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        consent.require(CALL, context, confirmation());

        Mockito.verify(elicitations, Mockito.times(2)).askUser(Mockito.eq(7), Mockito.eq("create_feed"),
                Mockito.startsWith("call-1" + UserQuestions.FORM_SEPARATOR + "confirmation-"),
                Mockito.eq("Create feed A\n\n- feed name: A"), Mockito.anyMap());
    }

    @Test
    void aFormIsUsedWhenTheSessionCanAsk() {
        final Questions accept = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));
        consent.require(CALL, accept.context(), Consent.Request.confirmation("create_feed",
                "Create feed X", Map.of("feed name", "X")));
        assertThat(accept.intros.getFirst()).contains("feed name: X");
        final Map<?, ?> value = (Map<?, ?>) ((Map<?, ?>) accept.forms.getFirst().get("properties")).get("value");
        assertThat(value.get("type")).isEqualTo("boolean");
        assertThat(value.get("title")).isEqualTo("Create feed X");

        final Questions no = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", false)));
        assertThatThrownBy(() -> consent.require(CALL, no.context(), Consent.Request.confirmation("create_feed",
                "Create feed X", Map.of()))).hasMessage("The user did not agree to: Create feed X");

        final Questions decline = new Questions(new ElicitResult(ElicitResult.Action.DECLINE, null));
        assertThatThrownBy(() -> consent.require(CALL, decline.context(), Consent.Request.confirmation(
                "create_feed", "Create feed X", Map.of()))).hasMessageContaining("did not agree");

        // Closed or run out without an answer (Stroom answers cancel when its form expires): not a refusal.
        final Questions cancel = new Questions(new ElicitResult(ElicitResult.Action.CANCEL, null));
        assertThatThrownBy(() -> consent.require(CALL, cancel.context(), Consent.Request.confirmation(
                "create_feed", "Create feed X", Map.of())))
                .hasMessageMatching("No answer yet to: Create feed X\\..*call create_feed again.*");
    }

    @Test
    void anApprovalOffersAChoiceTickedToStartThatTheUserCanUntick() {
        // Decided by the user (2026-11-17): promotion's tidy-up is a choice in its approval, on by default.
        final Consent.Request approval = Consent.Request.approval("promote_build", "Promote build 'bb'",
                Map.of("layout", "3 document(s)")).withOption("tidy_up", "Tidy up", true);
        final Questions ticked = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));
        final ToolCallContext tickedCall = ticked.context();
        consent.require(CALL, tickedCall, approval);

        final Map<String, Object> properties = asMap(ticked.forms.getFirst().get("properties"));
        assertThat(properties.keySet()).containsExactly("value", "tidy_up");
        assertThat(asMap(properties.get("tidy_up"))).containsEntry("type", "boolean")
                .containsEntry("title", "Tidy up").containsEntry("default", true);
        assertThat(ticked.forms.getFirst().get("required")).isEqualTo(List.of("value"));
        assertThat(ElicitationSchema.check(ticked.intros.getFirst(), ticked.forms.getFirst())).isNull();
        // Left as it started: ticked.
        assertThat(consent.option(tickedCall, "tidy_up", false)).isTrue();

        final Questions unticked = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true,
                "tidy_up", false)));
        final ToolCallContext untickedCall = unticked.context();
        consent.require(CALL, untickedCall, approval);
        assertThat(consent.option(untickedCall, "tidy_up", true)).isFalse();

        // A choice is part of what is agreed: the form's id differs from the same approval without it.
        final Questions plain = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));
        final ToolCallContext plainCall = plain.context();
        consent.require(CALL, plainCall, Consent.Request.approval("promote_build", "Promote build 'bb'",
                Map.of("layout", "3 document(s)")));
        assertThat(plain.callIds.getFirst()).isNotEqualTo(ticked.callIds.getFirst());
        assertThat(consent.option(plainCall, "tidy_up", true)).isTrue();
        assertThat(consent.option(plainCall, "tidy_up", false)).isFalse();

        // Declined: nothing agreed, so no choice either.
        final Questions declined = new Questions(new ElicitResult(ElicitResult.Action.DECLINE, null));
        final ToolCallContext declinedCall = declined.context();
        assertThatThrownBy(() -> consent.require(CALL, declinedCall, approval))
                .hasMessageContaining("did not agree");
        assertThat(consent.option(declinedCall, "tidy_up", false)).isFalse();
    }

    @Test
    void aProposedNameCanBeCorrectedInTheForm() {
        final Consent.Request request = Consent.Request.confirmation("create_feed", "Create feed",
                Map.of("feed name", "FIREWALL-EDGE-V1.0")).withEditable("name", "Feed name",
                "FIREWALL-EDGE-V1.0");
        final Questions corrected = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("name", " ACME-FW-V1.0 ")));
        final ToolCallContext context = corrected.context();
        consent.require(CALL, context, request);
        final Map<?, ?> properties = (Map<?, ?>) corrected.forms.getFirst().get("properties");
        // One prompt: accepting it confirms.
        assertThat(new ArrayList<Object>(properties.keySet())).containsExactly("name");
        final Map<?, ?> field = (Map<?, ?>) properties.get("name");
        assertThat(field.get("type")).isEqualTo("string");
        assertThat(field.get("default")).isEqualTo("FIREWALL-EDGE-V1.0");
        assertThat(field.get("title")).isEqualTo("Feed name");
        assertThat(consent.edited(context, "name", "FIREWALL-EDGE-V1.0")).isEqualTo("ACME-FW-V1.0");
        assertThat(consent.changes(context)).isEqualTo(Map.of("name",
                new Consent.Change("FIREWALL-EDGE-V1.0", "ACME-FW-V1.0")));

        // Left empty, the proposal stands.
        final Questions empty = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("name", "")));
        final ToolCallContext same = empty.context();
        consent.require(CALL, same, request);
        assertThat(consent.edited(same, "name", "FIREWALL-EDGE-V1.0")).isEqualTo("FIREWALL-EDGE-V1.0");
        assertThat(consent.changes(same)).isEmpty();

        // Declining the form stops it; a form that ran out is no answer yet, not a refusal.
        assertThatThrownBy(() -> consent.require(CALL, new Questions(new ElicitResult(ElicitResult.Action.DECLINE,
                null)).context(), request)).hasMessageContaining("did not agree");
        assertThatThrownBy(() -> consent.require(CALL, new Questions(new ElicitResult(ElicitResult.Action.CANCEL,
                null)).context(), request)).hasMessageMatching("No answer yet to: Create feed.*they didn't decline.*");
    }

    @Test
    void eachFormACallAsksHasAnIdOfItsOwn() {
        // In a chat the call is made again once a form is answered: a confirmation and then a choice are told apart.
        final Questions questions = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("value", true, "choice", "B")));
        final ToolCallContext context = questions.context();
        consent.require(CALL, context, confirmation());
        assertThat(consent.choose(CALL, context, "create_feed", "Which?", List.of("A", "B"), null)).contains("B");
        assertThat(questions.callIds).hasSize(2).doesNotHaveDuplicates()
                .allSatisfy(id -> assertThat(UserQuestions.toolCallId(id)).isEqualTo("call-1"));
    }

    @Test
    void aChoiceIsMadeInAForm() {
        final Questions picked = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("choice", "B")));
        assertThat(consent.choose(CALL, picked.context(), "create_feed", "Which feed?", List.of("A", "B"), null))
                .contains("B");
        final Map<?, ?> choice = (Map<?, ?>) ((Map<?, ?>) picked.forms.getFirst().get("properties")).get("choice");
        assertThat(choice.get("enum")).isEqualTo(List.of("A", "B"));
        assertThat(choice.get("title")).isEqualTo("Which feed?");
        // Cancelling the form stops the call, as declining a confirmation does.
        assertThatThrownBy(() -> consent.choose(CALL, new Questions(new ElicitResult(ElicitResult.Action.CANCEL,
                null)).context(), "create_feed", "Which feed?", List.of("A", "B"), null))
                .hasMessage("The user made no choice: Which feed?");
        // Without a form, the agent asks.
        assertThat(consent.choose(CALL, textOnly(), "create_feed", "Which feed?", List.of("A"), null)).isEmpty();
        assertThat(consent.choose(CALL, picked.context(), "create_feed", "Which feed?", List.of(), null)).isEmpty();
    }

    @Test
    void aQuestionIsAnsweredWithTheSuggestionOrInTheUsersWordsNotBoth() {
        // One field: the suggested answer as a button, the user's own words in the box under it.
        final Questions accepted = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("answer", " Accept all 3 ")));
        assertThat(consent.answer(CALL, accepted.context(), "classify_kinds", "Review them.", "Accept all 3, or "
                                                                                              + "type amendments",
                "Accept all 3")).contains("Accept all 3");
        final Map<?, ?> properties = (Map<?, ?>) accepted.forms.getFirst().get("properties");
        assertThat(List.copyOf(properties.keySet())).isEqualTo(List.of("answer"));
        assertThat(((Map<?, ?>) properties.get("answer")).get("examples")).isEqualTo(List.of("Accept all 3"));
        assertThat(accepted.forms.getFirst().get("required")).isEqualTo(List.of("answer"));
        // Declined, or sent empty: the call stops.
        assertThatThrownBy(() -> consent.answer(CALL, new Questions(new ElicitResult(ElicitResult.Action.DECLINE,
                null)).context(), "classify_kinds", "Review them.", "Accept?", "Accept"))
                .hasMessage("The user declined: Accept?");
        assertThatThrownBy(() -> consent.answer(CALL, new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("answer", "  "))).context(), "classify_kinds", "Review them.", "Accept?", "Accept"))
                .isInstanceOf(ToolRefusal.class);
        // Without a form, the agent asks.
        assertThat(consent.answer(CALL, textOnly(), "classify_kinds", "Review them.", "Accept?", "Accept")).isEmpty();
    }

    @Test
    void aQuestionPutAsideByAMessageTheUserTypedTellsTheAgentToReadTheMessageNotToAskAgain() {
        // Seen in testing: survey_feed's "Which field says which source a record came from?" form was shown, and
        // the user typed a question in the chat instead of answering it. The call, made again, was told the form
        // was closed or ran out, and to call survey_feed again with the same arguments: the form was shown again,
        // and the user's question never answered.
        final ChatForms chat = new ChatForms(7);
        final ToolCall call = new ToolCall("call-1", "survey_feed", "{\"feed\": \"ACME\"}");
        final ToolCallContext context = chat.context();
        final String intro = "Feed 'ACME' may aggregate several sources.";
        final List<String> options = List.of("event.app", "event.logger_name", "Not aggregated");
        assertThatThrownBy(() -> consent.answer(call, context, "survey_feed", intro, SOURCE_QUESTION, options))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        final String typed = "What's the difference between event.app and event.logger_name?";
        assertThat(chat.userSendsInstead(typed)).isEqualTo(1);

        final String told = toldTheAgent(() -> consent.answer(call, context, "survey_feed", intro,
                SOURCE_QUESTION, options));

        assertThat(told).as("what the re-made call tells the agent")
                .containsIgnoringCase("sent a message instead")
                .contains(typed)
                .doesNotContain("closed or ran out")
                .doesNotContain("again with the same arguments");
    }

    @Test
    void aConfirmationPutAsideByAMessageTheUserTypedTellsTheAgentToReadTheMessageNotToAskAgain() {
        // As for a question (above): a confirmation's form put aside by a message the user typed.
        final ChatForms chat = new ChatForms(7);
        final ToolCallContext context = chat.context();
        assertThatThrownBy(() -> consent.require(CALL, context, confirmation()))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        final String typed = "Hang on, why is it called A?";
        assertThat(chat.userSendsInstead(typed)).isEqualTo(1);

        final String told = toldTheAgent(() -> {
            consent.require(CALL, context, confirmation());
            return null;
        });

        assertThat(told).as("what the re-made call tells the agent")
                .containsIgnoringCase("sent a message instead")
                .contains(typed)
                .doesNotContain("closed or ran out")
                .doesNotContain("again with the same arguments");
    }

    @Test
    void aQuestionPutAsideByAMessageIsAnsweredInTheChatFromThenOnItsFormNotShownAgain() {
        // Decided by the user, 2026-11-19: once put aside, the question's answer is the one said in the chat; a
        // later call (its argument the user's answer, or without it) never shows the form again.
        final ChatForms chat = new ChatForms(7);
        final ToolCallContext context = chat.context();
        final ToolCall call = new ToolCall("call-1", "survey_feed", "{}");
        final List<String> options = List.of("event.app", "Not aggregated");
        assertThatThrownBy(() -> consent.answer(call, context, "survey_feed", "Intro", SOURCE_QUESTION, options))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        chat.userSendsInstead("Why do you ask?");
        assertThatThrownBy(() -> consent.answer(call, context, "survey_feed", "Intro", SOURCE_QUESTION, options))
                .isInstanceOf(ToolRefusal.class);
        final int formsShown = forms(chat);

        final ToolCall later = new ToolCall("call-2", "survey_feed", "{\"source_type_field\": \"event.app\"}");
        final Optional<String> asked = consent.answer(later, context, "survey_feed", "Intro", SOURCE_QUESTION,
                options);

        assertThat(asked).as("asked in a form again").isEmpty();
        assertThat(forms(chat)).as("forms shown").isEqualTo(formsShown);
        assertThat(consent.takesChatAnswer(context, "survey_feed", SOURCE_QUESTION))
                .as("the argument taken as the user's chat answer").isTrue();
        assertThat(consent.whyInChat(context, "survey_feed", SOURCE_QUESTION)).as("why it isn't in a form")
                .contains("put it aside");
        assertThat(consent.wasAskedInChat(context, "survey_feed", SOURCE_QUESTION))
                .as("the agent was told the answer is said in the chat").isTrue();
        // Another question of the tool is asked in its form as ever.
        assertThat(consent.takesChatAnswer(context, "survey_feed", "Another question?")).isFalse();
    }

    @Test
    void aConfirmationPutAsideByAMessageIsNeverTakenFromTheChatButAskedInItsFormAgain() {
        // A confirmation is only ever given in a form (the agent could otherwise agree for the user).
        final ChatForms chat = new ChatForms(7);
        final ToolCallContext context = chat.context();
        assertThatThrownBy(() -> consent.require(CALL, context, confirmation()))
                .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
        chat.userSendsInstead("Go on then");
        final String told = refusal(() -> consent.require(CALL, context, confirmation()));
        assertThat(told).contains("Go on then").contains("asked to agree in the form again");
        final int formsShown = forms(chat);

        assertThatThrownBy(() -> consent.require(new ToolCall("call-2", CALL.name(), "{}"), context,
                confirmation())).as("a later call").isInstanceOf(AiChatRunManager.WaitingForUserException.class);

        assertThat(forms(chat)).as("forms shown").isEqualTo(formsShown + 1);
        assertThat(consent.takesChatAnswer(context, CALL.name(), confirmation().summary())).isFalse();
    }

    @Test
    void eachFormHelperPutAsideByAMessageTellsTheAgentToReplyToIt() {
        // choose, chooseEach, answerEach, pick and review alike, as answer is (above).
        final Map<String, BiFunction<ToolCall, ToolCallContext, Object>> helpers = new LinkedHashMap<>();
        helpers.put("choose", (call, context) -> consent.choose(call, context, "a_tool", "Which one?",
                List.of("A", "B"), null));
        helpers.put("chooseEach", (call, context) -> consent.chooseEach(call, context, "a_tool", "Choose each",
                Map.of("x", new Consent.Question("Which x?", List.of("A", "B")))));
        helpers.put("answerEach", (call, context) -> consent.answerEach(call, context, "a_tool", "Answer each",
                Map.of("x", new Consent.Question("Which x?", List.of("A", "B")))));
        helpers.put("pick", (call, context) -> consent.pick(call, context, "a_tool", "Pick", "Which?",
                Map.of("A", "a", "B", "b"), "Name", null));
        helpers.put("review", (call, context) -> consent.review(call, context, "a_tool", "Review",
                Map.of("x", new Consent.Editable("X", "x")), "Anything else?"));
        int chatId = 10;
        for (final Map.Entry<String, BiFunction<ToolCall, ToolCallContext, Object>> helper : helpers.entrySet()) {
            final ChatForms chat = new ChatForms(chatId++);
            final ToolCallContext context = chat.context();
            final ToolCall call = new ToolCall("call-1", "a_tool", "{}");
            assertThatThrownBy(() -> helper.getValue().apply(call, context)).as("%s shows its form", helper.getKey())
                    .isInstanceOf(AiChatRunManager.WaitingForUserException.class);
            chat.userSendsInstead("A message to " + helper.getKey());

            final String told = toldTheAgent(() -> helper.getValue().apply(call, context));

            assertThat(told).as(helper.getKey()).contains("sent a message instead")
                    .contains("A message to " + helper.getKey()).contains("Don't call a_tool again")
                    .doesNotContain("made no choice").doesNotContain("declined").doesNotContain("closed or ran out");
        }
    }

    @Test
    void wordsInAFormAreTakenForAnAnswerOnlyWhenTheyAreOneWhole() {
        final List<String> options = List.of("Use these patterns", "One source type per value");

        assertThat(Consent.exactly("  use THESE patterns ", options)).contains("Use these patterns");
        assertThat(Consent.exactly("one source type per value", options)).contains("One source type per value");
        assertThat(Consent.exactly("which pattern would match?", options)).isEmpty();
        assertThat(Consent.exactly("Use these patterns, but why?", options)).isEmpty();
        assertThat(Consent.exactly("One", options)).isEmpty();
        assertThat(Consent.exactly("", options)).isEmpty();
        assertThat(Consent.exactly(null, options)).isEmpty();
    }

    @Test
    void wordsWrittenInsteadOfAnAnswerArePutToTheAgentAndTheQuestionAnsweredInTheChat() {
        final ChatForms chat = new ChatForms(7);
        final ToolCallContext context = chat.context();
        final String typed = "What's the difference?";

        final ToolRefusal refusal = consent.wroteInstead(context, "survey_feed", SOURCE_QUESTION, typed,
                "ask them in the chat which field it is.");

        assertThat(refusal.getMessage()).contains("\"" + typed + "\"").contains("If it's a question, answer it "
                                                                                    + "first")
                .contains("ask them in the chat which field it is.");
        assertThat(consent.takesChatAnswer(context, "survey_feed", SOURCE_QUESTION)).isTrue();
        assertThat(consent.answer(new ToolCall("call-2", "survey_feed", "{}"), context, "survey_feed", "Intro",
                SOURCE_QUESTION, List.of("event.app"))).as("asked in a form again").isEmpty();
        assertThat(forms(chat)).isZero();
    }

    // The forms shown in the chat so far.
    private static int forms(final ChatForms chat) {
        return (int) chat.messages().stream()
                .filter(message -> message.getMessageType() == AiMessageType.ELICITATION)
                .count();
    }

    @Test
    void aChoiceTooLargeForAFormIsLeftToTheAgentRatherThanRefused() {
        // Seen: a question naming every field of a wide mapping, too long for a form; refused, it stopped the call
        // where the user could never answer it.
        final Questions picked = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("choice", "A")));
        final String longQuestion = "Overwrite fields " + "attr_001, ".repeat(250) + "?";
        final List<String> manyOptions = IntStream.range(0, 400)
                .mapToObj(i -> "System/Indexes/Department " + i + "/Some long folder name/Index " + i)
                .toList();

        assertThat(consent.choose(CALL, picked.context(), "hand_edit", longQuestion, List.of("A", "B"), null))
                .isEmpty();
        assertThat(consent.choose(CALL, picked.context(), "get_field_conventions", "Which index?", manyOptions,
                null)).isEmpty();
        assertThat(consent.chooseEach(CALL, picked.context(), "build_translation_xslt", "Stand-ins?",
                IntStream.range(0, ElicitationSchema.MAX_FIELDS + 1).boxed().collect(Collectors.toMap(
                        i -> "v" + i, i -> new Consent.Question("v" + i, List.of("Keep", "Leave out")),
                        (a, b) -> a, LinkedHashMap::new)))).isEmpty();
        // Not asked at all: the agent asks in the chat, as when no form can be shown.
        assertThat(picked.forms).isEmpty();
        Mockito.verify(auditLog, Mockito.times(3)).processed(Mockito.any(), Mockito.isNull(), Mockito.eq("choice"),
                Mockito.anyString(), Mockito.startsWith("not asked in a form, as its"), Mockito.eq(false));
    }

    @Test
    void aFormMessageIsShortWhateverItsDetails() {
        // Seen in testing: a 2,000-character confirmation, which the client declined as too long.
        final Map<String, Object> details = new LinkedHashMap<>();
        for (int n = 0; n < 20; n++) {
            details.put("detail " + n, "x ".repeat(400));
        }
        final String message = Consent.formMessage("Use the index template\nAs shown in the chat.", details);
        assertThat(message.length()).isLessThanOrEqualTo(Consent.FORM_MESSAGE_LIMIT);
        assertThat(message).startsWith("Use the index template\nAs shown in the chat.")
                .endsWith("(the rest is in the chat)")
                .contains("…");
        assertThat(Consent.formMessage("Create feed X", Map.of("name", "X"))).isEqualTo("Create feed X\n\n- name: X");
    }

    @Test
    void theDocumentsAFormNamesAreLinkedByTheirUuids() {
        // Asked for by the user: the pipeline an approval names opens from its form, as the document it is.
        final DocRef pipeline = new DocRef("Pipeline", "a123a852-baaf-4ead-866f-96f756da1dea", "Twitter_Events");
        final String link = "[Twitter\\_Events](?action=open-doc&docType=Pipeline&docUuid="
                            + "a123a852-baaf-4ead-866f-96f756da1dea)";
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("pipeline", "Twitter_Events");
        details.put("streams", List.of(1));

        assertThat(Consent.formMessage("Reprocess\nReprocess 1 stream(s) with 'Twitter_Events'.", details,
                List.of(pipeline)))
                .isEqualTo("Reprocess\nReprocess 1 stream(s) with '" + link + "'.\n\n- pipeline: " + link
                           + "\n- streams: 1");
        // Not part of what is agreed to: the request is the same one without them.
        assertThat(Consent.Request.approval("t", "s", details).withDocuments(pipeline, null).documents())
                .containsExactly(pipeline);
        assertThat(Consent.formMessage("Create feed X", Map.of("name", "X"), List.of(pipeline)))
                .isEqualTo("Create feed X\n\n- name: X");
    }

    @Test
    void aReviewIdIsOfExactlyWhatWasShown() {
        final String id = Consent.reviewId("the layout");
        assertThat(id).hasSize(12);
        assertThat(Consent.wasReviewed(id.toUpperCase(), "the layout")).isTrue();
        assertThat(Consent.wasReviewed(true, "the layout")).isFalse();
        assertThat(Consent.wasReviewed(id, "another layout")).isFalse();
        assertThat(Consent.reviewNote(true)).endsWith("true is not one.");
        assertThat(Consent.reviewNote("abc")).contains("the one given was for something else");
        assertThat(Consent.reviewNote(null)).isEmpty();
    }

    @Test
    void anAnswerIsBoundToWhatWasShownWithoutTheFormShowingItsReviewId() {
        // Asked for by the user: "as shown in the chat: e998d10dadc5" meant nothing to them. The answer is still bound
        // to exactly what the chat showed: the same form over another table is another question.
        final Questions questions = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));
        final ToolCallContext context = questions.context();
        consent.require(CALL, context, confirmation().boundTo("the table shown"));
        consent.require(CALL, context, confirmation().boundTo("the table shown"));
        consent.require(CALL, context, confirmation().boundTo("another table"));

        assertThat(questions.intros).allSatisfy(intro -> assertThat(intro)
                .isEqualTo("Create feed A\n\n- feed name: A")
                .doesNotContain(Consent.reviewId("the table shown"), Consent.reviewId("another table")));
        assertThat(questions.callIds.get(0)).isEqualTo(questions.callIds.get(1));
        assertThat(questions.callIds.get(2)).isNotEqualTo(questions.callIds.get(0));
    }

    @Test
    void valuesToEditTooManyForAFormAreShownInTheChatThenConfirmedInOneShortForm() {
        // A new version's names, a field each: past what a form holds, the form was never shown and the call could
        // never go on.
        Consent.Request request = Consent.Request.confirmation("copy_pipeline", "Copy pipeline 'P' as 'P V2.0'",
                Map.of("build", "b"));
        for (int i = 1; i <= ElicitationSchema.MAX_FIELDS + 5; i++) {
            request = request.withEditable("doc" + i, "Copy of XSLT " + i, "XSLT " + i + " V2.0");
        }
        final Consent.Request many = request;
        final Questions user = Questions.strict(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));

        final String shown = refusal(() -> consent.require(CALL, user.context(), many));

        assertThat(user.forms).as("nothing asked before the values are shown").isEmpty();
        assertThat(shown).contains("Nothing was done yet", "too many to edit in one form",
                "Copy of XSLT 1: XSLT 1 V2.0", "Copy of XSLT 25: XSLT 25 V2.0", UserQuestions.SHOWN_ARG + "='");
        final String shownId = idAfter(shown, UserQuestions.SHOWN_ARG);
        // Another id (or none) is not what was shown: shown again.
        assertThat(refusal(() -> consent.require(withArg(UserQuestions.SHOWN_ARG, "abc"), user.context(), many)))
                .contains("too many to edit in one form");

        consent.require(withArg(UserQuestions.SHOWN_ARG, shownId), user.context(), many);

        assertThat(user.forms).singleElement().satisfies(form -> assertThat(asMap(form.get("properties")))
                .containsOnlyKeys("value"));
        assertThat(user.intros.getFirst()).contains("Copy pipeline 'P' as 'P V2.0'", "the 25 shown in the chat");
        // The values stand as proposed.
        assertThat(consent.changes(user.context())).isEmpty();
    }

    @Test
    void aConfirmationTheClientCantShowIsRefusedNotConfirmedInTheChat() {
        // A confirmation is given only in a form (decided by the user, 2026-11-18): the agent can't approve for the
        // user. The refusal says why the form wasn't shown, not that no form can be shown here.
        final Questions client = Questions.refusing();
        final Consent.Request request = Consent.Request.confirmation("create_feed", "Create feed A", Map.of(
                "feed name", "A")).withEditable("name", "Feed name", "A");

        final String refused = refusal(() -> consent.require(CALL, client.context(), request));

        assertThat(refused).startsWith("The client couldn't show the form, so the user wasn't asked to agree and "
                                       + "nothing was done")
                .contains("Create feed A", "given only in a form, not in the chat")
                .doesNotContain("confirmed_in_chat");
        // The same call again is refused again: nothing passed back grants it.
        assertThat(refusal(() -> consent.require(withArg("confirmed_in_chat", "anything"), client.context(),
                request))).contains("given only in a form");
        Mockito.verify(auditLog, Mockito.never()).processed(Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.anyString(), Mockito.anyString(), Mockito.eq(true));
    }

    @Test
    void aFormThatCantBeShownLetsTheNextCallTakeTheUsersChatAnswer() {
        // Seen: a scope form too large to show, the agent told to ask in the chat and pass the answer, and the
        // answer refused (forms can be shown in the session), so the form asked again, for ever.
        final Questions user = Questions.strict(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("choice", "A")));
        final List<String> manyOptions = IntStream.range(0, 400)
                .mapToObj(i -> "System/Indexes/Department " + i + "/Some long folder name/Index " + i)
                .toList();
        assertThat(consent.takesChatAnswer(user.context(), "get_field_conventions", "Which index?")).isFalse();

        assertThat(consent.choose(CALL, user.context(), "get_field_conventions", "Which index?", manyOptions,
                null)).isEmpty();

        assertThat(consent.takesChatAnswer(user.context(), "get_field_conventions", "Which index?")).isTrue();
        assertThat(consent.whyInChat(user.context(), "get_field_conventions", "Which index?"))
                .isEqualTo("The form was too large to show (its form is too large)");
        // Another question, tool or session isn't.
        assertThat(consent.takesChatAnswer(user.context(), "get_field_conventions", "Which backend?")).isFalse();
        assertThat(consent.takesChatAnswer(user.context(), "create_feed", "Which index?")).isFalse();
        assertThat(consent.takesChatAnswer(ToolCallContext.builder(ToolSession.chat(3)).userQuestions(user)
                .build(), "get_field_conventions", "Which index?")).isFalse();
        // Where no form can be shown at all, the chat's answer is the user's, as before.
        assertThat(consent.takesChatAnswer(textOnly(), "create_feed", "Which index?")).isTrue();
        assertThat(consent.whyInChat(textOnly(), "create_feed", "Which index?"))
                .isEqualTo("No form can be shown in this session now");
        // A form shown for the question since: asked in forms again.
        assertThat(consent.choose(CALL, user.context(), "get_field_conventions", "Which index?", List.of("A", "B"),
                null)).contains("A");
        assertThat(consent.takesChatAnswer(user.context(), "get_field_conventions", "Which index?")).isFalse();
    }

    @Test
    void aFormTheClientCantShowIsLeftToTheChatRatherThanRefusingTheCall() {
        // Seen over /mcp: the client answered a form with an error, the call was refused, and every call after
        // asked it again.
        final Questions client = Questions.refusing();

        assertThat(consent.choose(CALL, client.context(), "build_translation_xslt", "Where?", List.of("A", "B"),
                "Where do the Events go?")).isEmpty();

        assertThat(consent.takesChatAnswer(client.context(), "build_translation_xslt", "Where do the Events go?"))
                .isTrue();
        assertThat(consent.whyInChat(client.context(), "build_translation_xslt", "Where do the Events go?"))
                .isEqualTo("The client couldn't show the form");
    }

    @Test
    void manyOptionsArePickedInOneListNoneTickedToStart() {
        final Map<String, String> options = new LinkedHashMap<>();
        IntStream.rangeClosed(1, 65).forEach(i -> options.put("Source type " + i, "about 1% of the records "
                                                                                 + "read; json"));
        final Questions user = Questions.strict(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of(
                "picked", List.of("Source type 9", "Source type 2", "Not offered"), "text", " Group ")));

        final Optional<Consent.Picked> picked = consent.pick(CALL, user.context(), "record_source_types",
                "Which go in the group?", "Source types to put in the group", options, "What is it called?",
                "Suggested");

        assertThat(picked).contains(new Consent.Picked(List.of("Source type 2", "Source type 9"), "Group"));
        final Map<String, Object> properties = asMap(user.forms.getFirst().get("properties"));
        assertThat(properties).containsOnlyKeys("picked", "text");
        final Map<String, Object> list = asMap(properties.get("picked"));
        assertThat(list).containsEntry("type", "array").containsEntry("default", List.of());
        assertThat(asMap(list.get("items")).get("optionDescriptions")).asList().hasSize(65)
                .first().isEqualTo("about 1% of the records read; json");
        assertThat(asMap(properties.get("text"))).containsEntry("default", "Suggested");
    }

    @Test
    void aListInAFormsMessageIsItsFirstLinesAndHowManyMore() {
        final List<String> lines = IntStream.rangeClosed(1, 65).mapToObj(i -> "Row " + i).toList();

        assertThat(Consent.firstLines(lines, "all are in the table shown in the chat"))
                .isEqualTo("Row 1\nRow 2\nRow 3\nRow 4\nRow 5\nAnd 60 more (all are in the table shown in the "
                           + "chat).");
        assertThat(Consent.firstLines(lines.subList(0, 2), "x")).isEqualTo("Row 1\nRow 2");
    }

    // The refusal's message.
    private static String refusal(final Runnable call) {
        try {
            call.run();
        } catch (final ToolRefusal e) {
            return e.getMessage();
        }
        throw new AssertionError("Not refused");
    }

    // The id a reply says to pass back as the argument.
    private static String idAfter(final String text, final String argument) {
        final Matcher matcher = Pattern.compile(argument + "='([0-9a-f]+)'").matcher(text);
        assertThat(matcher.find()).as("%s in %s", argument, text).isTrue();
        return matcher.group(1);
    }

    // What the agent is told by a call that asks the user: the refusal it is given (as the tool's result), else
    // the answer.
    private static String toldTheAgent(final Supplier<Object> ask) {
        try {
            return "answered: " + ask.get();
        } catch (final ToolRefusal e) {
            return e.getMessage();
        }
    }

    private static ToolCall withArg(final String name, final String value) {
        return new ToolCall(CALL.id(), CALL.name(), "{\"" + name + "\": \"" + value + "\"}");
    }

    private static Consent.Request confirmation() {
        return Consent.Request.confirmation("create_feed", "Create feed A", Map.of("feed name", "A"));
    }

    private static ToolCallContext textOnly() {
        return textOnly(SESSION);
    }

    private static ToolCallContext textOnly(final ToolSession session) {
        return ToolCallContext.builder(session).userQuestions(UserQuestions.NONE).build();
    }

    // Answers every form with the same answer, and keeps what was asked.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(final Object value) {
        return (Map<String, Object>) value;
    }

    private static final class Questions implements UserQuestions {

        private final ElicitResult answer;
        // Fails a test given a form Stroom couldn't show.
        private final boolean strict;
        // Answers as a client that can't show the form does.
        private final boolean refuses;
        private final List<String> intros = new ArrayList<>();
        private final List<String> callIds = new ArrayList<>();
        private final List<Map<String, Object>> forms = new ArrayList<>();

        private Questions(final ElicitResult answer) {
            this(answer, false, false);
        }

        private Questions(final ElicitResult answer, final boolean strict, final boolean refuses) {
            this.answer = answer;
            this.strict = strict;
            this.refuses = refuses;
        }

        // Answers every form so, and fails the test given one too large to show.
        private static Questions strict(final ElicitResult answer) {
            return new Questions(answer, true, false);
        }

        // A client that answers every form with an error.
        private static Questions refusing() {
            return new Questions(null, false, true);
        }

        private ToolCallContext context() {
            return ToolCallContext.builder(SESSION).userQuestions(this).build();
        }

        @Override
        public boolean canAsk() {
            return true;
        }

        @Override
        public ElicitResult ask(final String toolName,
                                final String callId,
                                final String intro,
                                final Map<String, Object> form) {
            if (strict) {
                assertThat(ElicitationSchema.check(intro, form)).as("a form too large to show: %s", intro).isNull();
            }
            if (refuses) {
                throw new ToolRefusal(ElicitationSchema.cannotShow("the client said: no"));
            }
            intros.add(intro);
            callIds.add(callId);
            forms.add(form);
            return Optional.ofNullable(answer).orElseThrow();
        }
    }
}
