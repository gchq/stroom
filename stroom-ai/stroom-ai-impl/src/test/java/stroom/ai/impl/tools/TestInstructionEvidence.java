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

import stroom.ai.impl.ElicitationRecord;
import stroom.ai.impl.ElicitationRecord.Status;
import stroom.ai.impl.tools.InstructionEvidence.Answer;
import stroom.ai.impl.tools.InstructionEvidence.Chat;
import stroom.ai.impl.tools.InstructionEvidence.ChatRef;
import stroom.ai.impl.tools.InstructionEvidence.Evidence;
import stroom.ai.impl.tools.InstructionEvidence.Question;
import stroom.ai.shared.AiChat;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestInstructionEvidence {

    private static final String TOOL = "build_translation_xslt";
    private static final Question ZONE = new Question(TOOL, "time_zone", "Time zone");

    @Test
    void aChoiceMadeTheSameWayInEnoughChatsIsRepeated() {
        final Evidence evidence = InstructionEvidence.collect(List.of(
                chat(1, 100L, zone("Australia/Sydney")),
                chat(2, 300L, zone("Australia/Sydney")),
                chat(3, 200L, zone("Australia/Sydney"))), 2);

        assertThat(evidence.chats()).isEqualTo(3);
        assertThat(evidence.repeated()).singleElement().satisfies(repeated -> {
            assertThat(repeated.question()).isEqualTo(ZONE);
            assertThat(repeated.answer()).isEqualTo("Australia/Sydney");
            // Newest first.
            assertThat(repeated.chats()).extracting(ChatRef::id).containsExactly(2, 3, 1);
            assertThat(repeated.asked()).isEqualTo("Which time zone are the times in?");
        });
        assertThat(evidence.mixed()).isEmpty();
    }

    @Test
    void aChoiceMadeDifferentWaysIsMixedAndNoRule() {
        final Evidence evidence = InstructionEvidence.collect(List.of(
                chat(1, 100L, zone("UTC")),
                chat(2, 200L, zone("Australia/Sydney")),
                chat(3, 300L, zone("Australia/Sydney"))), 2);

        assertThat(evidence.repeated()).isEmpty();
        assertThat(evidence.mixed()).singleElement().satisfies(mixed -> {
            assertThat(mixed.question()).isEqualTo(ZONE);
            assertThat(mixed.answers()).containsExactly(Map.entry("Australia/Sydney", 2), Map.entry("UTC", 1));
        });
    }

    @Test
    void aChoiceMadeInTooFewChatsIsLeftOut() {
        // Answered twice in one chat: still one chat.
        final Evidence evidence = InstructionEvidence.collect(List.of(
                new Chat(1, "c1", 100L, List.of(zone("UTC"), zone("UTC")), List.of()),
                chat(2, 200L, new Answer("other_tool", "x", null, "?", "y"))), 2);

        assertThat(evidence.repeated()).isEmpty();
        assertThat(evidence.mixed()).isEmpty();
    }

    @Test
    void theSameFieldAskedByAnotherToolIsAnotherQuestion() {
        final Evidence evidence = InstructionEvidence.collect(List.of(
                chat(1, 100L, zone("UTC")),
                chat(2, 200L, new Answer("draft_cef_mapping", "time_zone", "Time zone", "?", "Europe/London"))), 1);

        assertThat(evidence.repeated()).extracting(r -> r.question().tool())
                .containsExactlyInAnyOrder(TOOL, "draft_cef_mapping");
    }

    @Test
    void whatTheUserWroteIsGivenNewestFirstCutShortAndCapped() {
        final String longMessage = "x".repeat(InstructionEvidence.MAX_MESSAGE + 50);
        final Evidence evidence = InstructionEvidence.collect(List.of(
                new Chat(1, "older", 100L, List.of(), List.of("always use UTC", " ")),
                new Chat(2, "newer", 200L, List.of(), List.of(longMessage))), 2);

        assertThat(evidence.said()).extracting(said -> said.chat().id()).containsExactly(2, 1);
        assertThat(evidence.said().getFirst().text()).hasSize(InstructionEvidence.MAX_MESSAGE).endsWith("...");
        assertThat(evidence.said().get(1).text()).isEqualTo("always use UTC");

        final List<String> many = IntStream.range(0, 100).mapToObj(i -> "m" + i).toList();
        assertThat(InstructionEvidence.collect(List.of(new Chat(1, "c", 1L, List.of(), many)), 2).said())
                .hasSize(InstructionEvidence.MAX_MESSAGES);
    }

    @Test
    void aChatsAcceptedFormsAndTheUsersMessagesAreItsEvidence() {
        final Map<String, Object> content = new LinkedHashMap<>();
        content.put("time_zone", "UTC");
        content.put("fields", List.of("user", "host"));
        content.put("note", "");
        final Map<String, Object> schema = Map.of("type", "object", "properties", Map.of(
                "time_zone", Map.of("type", "string", "title", "Time zone"),
                "fields", Map.of("type", "array")));
        final List<AiChatMessage> messages = List.of(
                message(1, AiMessageType.USER_MESSAGE, "onboard the Acme feed"),
                message(2, AiMessageType.ELICITATION, form(Status.ACCEPTED, schema, content)),
                message(3, AiMessageType.ELICITATION, form(Status.DECLINED, schema, null)),
                message(4, AiMessageType.AI_RESPONSE, "done"),
                message(5, AiMessageType.ELICITATION, "not a form"));

        final Chat chat = InstructionEvidenceTool.trace(new AiChat(7, 0L, 50L, "alice", "Acme"), messages);

        assertThat(chat.id()).isEqualTo(7);
        assertThat(chat.timeMs()).isEqualTo(50L);
        assertThat(chat.said()).containsExactly("onboard the Acme feed");
        assertThat(chat.answers()).containsExactly(
                new Answer(TOOL, "time_zone", "Time zone", "Which time zone?", "UTC"),
                new Answer(TOOL, "fields", null, "Which time zone?", "[\"user\",\"host\"]"));
    }

    private static Chat chat(final int id, final long timeMs, final Answer answer) {
        return new Chat(id, "chat " + id, timeMs, List.of(answer), List.of());
    }

    private static Answer zone(final String value) {
        return new Answer(TOOL, "time_zone", "Time zone", "Which time zone are the times in?", value);
    }

    private static String form(final Status status,
                               final Map<String, Object> schema,
                               final Map<String, Object> content) {
        return JsonUtil.writeValueAsString(new ElicitationRecord(ElicitationRecord.KIND, status, "Stroom AI", TOOL,
                "Which time zone?", schema, Long.MAX_VALUE, content, null), false);
    }

    private static AiChatMessage message(final int id, final AiMessageType type, final String text) {
        return new AiChatMessage(id, 7, 0L, type, null, text, null, 0);
    }
}
