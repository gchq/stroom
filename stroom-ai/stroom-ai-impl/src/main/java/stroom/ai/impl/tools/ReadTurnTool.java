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

import stroom.ai.api.AiService;
import stroom.ai.impl.tools.ToolSchema.ObjectSchema;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * {@code read_turn} - reads a whole turn of the chat again (question, tool calls and results, and answer), e.g.
 * one the model only sees as a digest since the chat was compacted. Only the chat the call is made in can be
 * read.
 */
class ReadTurnTool extends AbstractBuiltInTool {

    static final String NAME = "read_turn";
    static final String TURN_ID_ARG = "turnId";

    private final AiService aiService;

    @Inject
    ReadTurnTool(final AiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description("Reads a whole turn of this conversation again: the question, every tool call and "
                             + "its result, and the answer. Use it when you need exact detail from an earlier turn "
                             + "that you only see as a digest or summary, or a tool result of this turn that has "
                             + "been removed to save space. Leave out the turn id to read the current turn.")
                .parameters(ObjectSchema.builder()
                        .addIntegerProperty(TURN_ID_ARG, "The turn's id, as shown in its digest (\"Turn 123\").")
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final OptionalInt chatId = context.session().chatId();
        if (chatId.isEmpty()) {
            return ToolResult.text(onlyInAChat(NAME));
        }
        final List<AiChatMessage> history = aiService.getMessages(chatId.getAsInt());
        final Integer requested = intArg(request, TURN_ID_ARG);
        final int turnId = requested != null
                ? requested
                : history.stream().mapToInt(AiChatMessage::getTurnId).max().orElse(0);
        final List<AiChatMessage> turn = history.stream()
                .filter(m -> m.getTurnId() == turnId)
                .toList();
        if (turn.stream().noneMatch(m -> m.getMessageType() == AiMessageType.USER_MESSAGE)) {
            return ToolResult.text(
                    "Error: there is no turn " + turnId + " in this chat. Turn ids are shown in the digests of "
                    + "earlier turns, or can be found with search_history.");
        }
        final StringBuilder sb = new StringBuilder("Turn ").append(turnId).append(":\n");
        for (final AiChatMessage message : turn) {
            appendMessage(sb, message);
        }
        return ToolResult.text(sb.toString().strip());
    }

    private static void appendMessage(final StringBuilder sb, final AiChatMessage message) {
        final String text = NullSafe.string(message.getMessage());
        switch (message.getMessageType()) {
            case USER_MESSAGE -> sb.append("\nQuestion:\n").append(text).append('\n');
            case STEER_MESSAGE -> sb.append("\nThe user added while you worked:\n").append(text).append('\n');
            case ATTACHMENT -> sb.append("\nThe user attached a table: ").append(text).append('\n');
            case AI_RESPONSE -> sb.append("\nAnswer:\n").append(text).append('\n');
            case TOOL_CALL -> {
                final Map<?, ?> call = readJson(text);
                sb.append("\nCalled tool ").append(call.get("tool")).append(" with ")
                        .append(Objects.requireNonNullElse(call.get("arguments"), "{}")).append('\n');
            }
            case TOOL_RESULT -> {
                final Map<?, ?> result = readJson(text);
                sb.append(Boolean.TRUE.equals(result.get("error"))
                        ? "Result (failed):\n"
                        : "Result:\n").append(Objects.requireNonNullElse(result.get("text"), "")).append('\n');
                if (result.get("path") != null) {
                    sb.append("(The full result is in the workspace file ").append(result.get("path"))
                            .append(")\n");
                }
            }
            default -> {
                // Not part of what the model saw.
            }
        }
    }

    private static Map<?, ?> readJson(final String json) {
        try {
            return Objects.requireNonNullElse(JsonUtil.readValue(json, Map.class), Map.of());
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }
}
