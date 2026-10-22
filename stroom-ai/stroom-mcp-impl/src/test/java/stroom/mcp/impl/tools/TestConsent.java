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
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCaller;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.UserQuestions;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import io.modelcontextprotocol.spec.McpSchema.ElicitResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Confirmations and approvals before a write (the Stroom MCP server's `tests/test_consent.py`, and
/// `tests/test_stateless.py` where it applies): a form where the session can ask, else a pending id.
class TestConsent {

    private static final ToolSession SESSION = new ToolSession(ToolSession.Kind.MCP, "s1",
            new ToolCaller(ToolCaller.Kind.EXTERNAL_CLIENT, "A test client"));
    private static final ToolCall CALL = new ToolCall("call-1", "create_feed", "{}");

    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Consent consent = new Consent(securityContext, () -> Mockito.mock(AiAuditLog.class), now::get);

    TestConsent() {
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("u1").build());
    }

    @Test
    void anIdIsBoundToTheExactRequest() {
        final Consent.Request promote = Consent.Request.approval("promote_build", "Promote",
                Map.of("plan", List.of("a")), null);
        final Map<String, Object> pending = consent.require(CALL, textOnly(), promote).orElseThrow();
        assertThat(pending.get("status")).isEqualTo("needs_approval");
        final String id = String.valueOf(pending.get("approval_id"));
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), Consent.Request.approval("promote_build",
                "Promote", Map.of("plan", List.of("b")), id)))
                .hasMessage("This approval id was issued for a different request; request the approval again");
        final String fresh = String.valueOf(consent.require(CALL, textOnly(), promote).orElseThrow()
                .get("approval_id"));
        assertThat(consent.require(CALL, textOnly(), Consent.Request.approval("promote_build", "Promote",
                Map.of("plan", List.of("a")), fresh))).isEmpty();
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), Consent.Request.approval("promote_build",
                "Promote", Map.of("plan", List.of("a")), fresh)))
                .hasMessage("This approval id was already used; request the approval again");
    }

    @Test
    void anIdPassedBackByCallsMadeAtOnceIsGrantedOnce() throws Exception {
        // Two calls with the same single-use id, made at once, must not both go ahead.
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 200; round++) {
                final String id = String.valueOf(consent.require(CALL, textOnly(), confirmation(null))
                        .orElseThrow().get("confirmation_id"));
                final CyclicBarrier start = new CyclicBarrier(8);
                final List<Future<Boolean>> granted = new ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    granted.add(executor.submit(() -> {
                        start.await();
                        try {
                            return consent.require(CALL, textOnly(), confirmation(id)).isEmpty();
                        } catch (final ToolRefusal e) {
                            return false;
                        }
                    }));
                }
                int grants = 0;
                for (final Future<Boolean> future : granted) {
                    grants += future.get()
                            ? 1
                            : 0;
                }
                assertThat(grants).as("round " + round).isEqualTo(1);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void anIdIsShortAndSaysWhatToDo() {
        // Short enough to pass back exactly: Haiku changed one character of a 281-character id, every time.
        final Map<String, Object> pending = consent.require(CALL, textOnly(), confirmation(null)).orElseThrow();
        final String id = String.valueOf(pending.get("confirmation_id"));
        assertThat(id).startsWith("conf-").contains(".").hasSize(46).isLowerCase();
        assertThat(pending.get("summary")).isEqualTo("Create feed A");
        assertThat(pending.get("details")).isEqualTo(Map.of("feed name", "A"));
        assertThat(pending.get("hint")).isEqualTo("Show the summary and details to the user. If they agree, call "
                                                  + "create_feed again with the same arguments plus "
                                                  + "confirmation_id='" + id + "'. If they change a detail, call "
                                                  + "again with the new values and no id to get a fresh "
                                                  + "confirmation.");
    }

    @Test
    void anIdNotGivenHereOrExpiredIsRefused() {
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), confirmation("conf-made.up")))
                .hasMessageContaining("not one the server issued: pass it back exactly as it was given");
        final String id = String.valueOf(consent.require(CALL, textOnly(), confirmation(null)).orElseThrow()
                .get("confirmation_id"));
        // One character changed.
        final String slip = id.substring(0, 10) + (id.charAt(10) == 'a'
                ? 'b'
                : 'a') + id.substring(11);
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), confirmation(slip)))
                .hasMessageContaining("pass it back exactly as it was given");
        // Given in another session.
        assertThatThrownBy(() -> consent.require(CALL, textOnly(new ToolSession(ToolSession.Kind.MCP, "s2",
                SESSION.caller())), confirmation(id))).hasMessageContaining("not one the server issued");
        now.addAndGet(Consent.TTL.toMillis() + 1);
        assertThatThrownBy(() -> consent.require(CALL, textOnly(), confirmation(id)))
                .hasMessage("This confirmation id has expired; request the confirmation again");
    }

    @Test
    void caseAndSpacesDontMatterAndAKeptIdSurvivesUntilDiscarded() {
        final String id = String.valueOf(consent.require(CALL, textOnly(), confirmation(null)).orElseThrow()
                .get("confirmation_id"));
        final ToolCallContext context = textOnly();
        assertThat(consent.require(CALL, context, confirmation(" " + id.toUpperCase() + " ").keepingId())).isEmpty();
        assertThat(consent.require(CALL, context, confirmation(id).keepingId())).isEmpty();
        consent.discard(context, id);
        for (final String again : List.of(id, id.toUpperCase())) {
            assertThatThrownBy(() -> consent.require(CALL, textOnly(), confirmation(again)))
                    .hasMessageContaining("already used");
        }
    }

    @Test
    void aFormIsUsedWhenTheSessionCanAsk() {
        final Questions accept = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", true)));
        assertThat(consent.require(CALL, accept.context(), Consent.Request.confirmation("create_feed",
                "Create feed X", Map.of("feed name", "X"), null))).isEmpty();
        assertThat(accept.intros.getFirst()).contains("feed name: X");
        final Map<?, ?> value = (Map<?, ?>) ((Map<?, ?>) accept.forms.getFirst().get("properties")).get("value");
        assertThat(value.get("type")).isEqualTo("boolean");
        assertThat(value.get("title")).isEqualTo("Create feed X");

        final Questions no = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT, Map.of("value", false)));
        assertThatThrownBy(() -> consent.require(CALL, no.context(), Consent.Request.confirmation("create_feed",
                "Create feed X", Map.of(), null))).hasMessage("The user did not agree to: Create feed X");

        final Questions decline = new Questions(new ElicitResult(ElicitResult.Action.DECLINE, null));
        assertThatThrownBy(() -> consent.require(CALL, decline.context(), Consent.Request.confirmation(
                "create_feed", "Create feed X", Map.of(), null))).hasMessageContaining("did not agree");

        // Closed or run out without an answer (Stroom answers cancel when its form expires): not a refusal.
        final Questions cancel = new Questions(new ElicitResult(ElicitResult.Action.CANCEL, null));
        assertThatThrownBy(() -> consent.require(CALL, cancel.context(), Consent.Request.confirmation(
                "create_feed", "Create feed X", Map.of(), null)))
                .hasMessageMatching("No answer yet to: Create feed X\\..*call create_feed again.*");
    }

    @Test
    void aSessionThatCantAskGetsAnId() {
        // Seen (stateless HTTP, a client whose form answer would have nowhere to land): the call answers at once
        // with an id, which a later call in the session passes back.
        final Map<String, Object> pending = consent.require(CALL, textOnly(), confirmation(null)).orElseThrow();
        assertThat(pending.get("status")).isEqualTo("needs_confirmation");
        assertThat(consent.require(CALL, textOnly(), confirmation(String.valueOf(pending.get("confirmation_id")))))
                .isEmpty();
    }

    @Test
    void aProposedNameCanBeCorrectedInTheForm() {
        final Consent.Request request = Consent.Request.confirmation("create_feed", "Create feed",
                Map.of("feed name", "FIREWALL-EDGE-V1.0"), null).withEditable("name", "Feed name",
                "FIREWALL-EDGE-V1.0");
        final Questions corrected = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("name", " ACME-FW-V1.0 ")));
        final ToolCallContext context = corrected.context();
        assertThat(consent.require(CALL, context, request)).isEmpty();
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
        assertThat(consent.require(CALL, same, request)).isEmpty();
        assertThat(consent.edited(same, "name", "FIREWALL-EDGE-V1.0")).isEqualTo("FIREWALL-EDGE-V1.0");
        assertThat(consent.changes(same)).isEmpty();

        // Declining the form stops it; a form that ran out is no answer yet, not a refusal.
        assertThatThrownBy(() -> consent.require(CALL, new Questions(new ElicitResult(ElicitResult.Action.DECLINE,
                null)).context(), request)).hasMessageContaining("did not agree");
        assertThatThrownBy(() -> consent.require(CALL, new Questions(new ElicitResult(ElicitResult.Action.CANCEL,
                null)).context(), request)).hasMessageMatching("No answer yet to: Create feed.*they didn't decline.*");
    }

    @Test
    void withAnIdTheProposalStands() {
        final Consent.Request request = Consent.Request.confirmation("create_feed", "Create feed", Map.of("n", 1),
                null).withEditable("name", "Feed name", "FIREWALL-EDGE-V1.0");
        final String id = String.valueOf(consent.require(CALL, textOnly(), request).orElseThrow()
                .get("confirmation_id"));
        final ToolCallContext context = textOnly();
        assertThat(consent.require(CALL, context, new Consent.Request(request.kind(), request.action(),
                request.summary(), request.details(), id, false, request.editable()))).isEmpty();
        assertThat(consent.edited(context, "name", "FIREWALL-EDGE-V1.0")).isEqualTo("FIREWALL-EDGE-V1.0");
    }

    @Test
    void aReissuedIdIsForTheSameRequest() {
        final ToolCallContext context = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("value", true))).context();
        assertThat(consent.require(CALL, context, confirmation(null))).isEmpty();
        final String id = consent.reissue(context, Consent.Kind.CONFIRMATION, "create_feed", Map.of("feed name", "A"));
        assertThat(consent.require(CALL, context, confirmation(id))).isEmpty();
    }

    @Test
    void eachFormACallAsksHasAnIdOfItsOwn() {
        // In a chat the call is made again once a form is answered: a confirmation and then a choice are told apart.
        final Questions questions = new Questions(new ElicitResult(ElicitResult.Action.ACCEPT,
                Map.of("value", true, "choice", "B")));
        final ToolCallContext context = questions.context();
        consent.require(CALL, context, confirmation(null));
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

    private static Consent.Request confirmation(final String id) {
        return Consent.Request.confirmation("create_feed", "Create feed A", Map.of("feed name", "A"), id);
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
