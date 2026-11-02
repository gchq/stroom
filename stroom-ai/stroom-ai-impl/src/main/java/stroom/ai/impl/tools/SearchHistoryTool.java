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
import stroom.ai.shared.AiToolCategory;
import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * {@code search_history} - finds the turns of the chat that mention some text. The text is matched literally
 * (not as a pattern), ignoring case. Only the chat the call is made in is searched.
 */
class SearchHistoryTool extends AbstractBuiltInTool {

    static final String NAME = "search_history";
    static final String TEXT_ARG = "text";
    static final String MAX_RESULTS_ARG = "maxResults";
    static final int DEFAULT_MAX_RESULTS = 10;
    static final int MAX_MAX_RESULTS = 50;
    static final int MAX_TEXT_LENGTH = 200;
    static final int SNIPPET_CONTEXT = 80;

    private final AiService aiService;

    @Inject
    SearchHistoryTool(final AiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public boolean isChatOnly() {
        // It searches the user's chats.
        return true;
    }

    @Override
    public AiToolCategory category() {
        return AiToolCategory.SEARCH;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.builder()
                .name(NAME)
                .description(ToolDescription.builder()
                        .general("Searches this conversation (questions, answers, tool calls and tool results) for "
                                 + "some text, ignoring case, and lists where it appears with the turn id.")
                        .chatOnly(" Read a whole turn with read_turn.")
                        .build())
                .parameters(ObjectSchema.builder()
                        .addStringProperty(TEXT_ARG, "The text to find. Matched literally, not as a pattern.")
                        .addIntegerProperty(MAX_RESULTS_ARG, "The most matches to list (default "
                                                             + DEFAULT_MAX_RESULTS + ", at most "
                                                             + MAX_MAX_RESULTS + ").")
                        .required(TEXT_ARG)
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(final ToolCall request, final ToolCallContext context) {
        final OptionalInt chatId = context.session().chatId();
        if (chatId.isEmpty()) {
            return ToolResult.text(onlyInAChat(NAME));
        }
        final String text = stringArg(request, TEXT_ARG);
        if (text == null) {
            return ToolResult.text("Error: the '" + TEXT_ARG + "' argument is required.");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            return ToolResult.text("Error: the text to find may be at most " + MAX_TEXT_LENGTH + " characters.");
        }
        final int maxResults = Math.clamp(Objects.requireNonNullElse(intArg(request, MAX_RESULTS_ARG),
                DEFAULT_MAX_RESULTS), 1, MAX_MAX_RESULTS);
        final String needle = text.toLowerCase(Locale.ROOT);

        final StringBuilder sb = new StringBuilder();
        int found = 0;
        for (final AiChatMessage message : aiService.getMessages(chatId.getAsInt())) {
            final String haystack = searchableText(message);
            if (haystack == null) {
                continue;
            }
            final int index = haystack.toLowerCase(Locale.ROOT).indexOf(needle);
            if (index >= 0) {
                found++;
                if (found <= maxResults) {
                    sb.append("- turn ").append(message.getTurnId()).append(", ")
                            .append(label(message.getMessageType())).append(": ")
                            .append(snippet(haystack, index, needle.length())).append('\n');
                }
            }
        }
        if (found == 0) {
            return ToolResult.text("No part of this conversation mentions \"" + text + "\".");
        }
        final String more = found > maxResults
                ? "\n(" + (found - maxResults) + " more not listed.)"
                : "";
        return ToolResult.text("Found \"" + text + "\" in " + found + " place(s):\n" + sb.toString().strip() + more);
    }

    private static String searchableText(final AiChatMessage message) {
        return switch (message.getMessageType()) {
            case USER_MESSAGE, STEER_MESSAGE, AI_RESPONSE -> message.getMessage();
            case TOOL_CALL -> {
                final Map<?, ?> call = readJson(message.getMessage());
                yield call.get("tool") + " " + Objects.requireNonNullElse(call.get("arguments"), "");
            }
            case TOOL_RESULT -> String.valueOf(Objects.requireNonNullElse(readJson(message.getMessage())
                    .get("text"), ""));
            default -> null;
        };
    }

    private static String label(final AiMessageType type) {
        return switch (type) {
            case USER_MESSAGE -> "question";
            case STEER_MESSAGE -> "added by the user";
            case AI_RESPONSE -> "answer";
            case TOOL_CALL -> "tool call";
            case TOOL_RESULT -> "tool result";
            default -> type.name();
        };
    }

    static String snippet(final String text, final int index, final int length) {
        final int start = Math.max(0, index - SNIPPET_CONTEXT);
        final int end = Math.min(text.length(), index + length + SNIPPET_CONTEXT);
        return (start > 0
                ? "…"
                : "")
               + text.substring(start, end).replaceAll("\\s+", " ").strip()
               + (end < text.length()
                ? "…"
                : "");
    }

    private static Map<?, ?> readJson(final String json) {
        try {
            return Objects.requireNonNullElse(JsonUtil.readValue(json, Map.class), Map.of());
        } catch (final RuntimeException e) {
            return Map.of();
        }
    }
}
