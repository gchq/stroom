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
import stroom.ai.impl.ElicitationSchema;
import stroom.ai.impl.McpElicitations;
import stroom.ai.impl.tools.ChatUserQuestions;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.docref.DocRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Confirmations and approvals before a write (the Stroom MCP server's `tests/test_consent.py`, and
/// `tests/test_stateless.py` where it applies): always a form; where the session can't show one, a refusal, never a
/// pending id for the model to pass back.
class TestConsent {

    private static final ToolSession SESSION = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));
    private static final ToolCall CALL = new ToolCall("call-1", "create_feed", "{}");

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
    private static final class Questions implements UserQuestions {

        private final ElicitResult answer;
        private final List<String> intros = new ArrayList<>();
        private final List<String> callIds = new ArrayList<>();
        private final List<Map<String, Object>> forms = new ArrayList<>();

        private Questions(final ElicitResult answer) {
            this.answer = answer;
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
            intros.add(intro);
            callIds.add(callId);
            forms.add(form);
            return Optional.ofNullable(answer).orElseThrow();
        }
    }
}
