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
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Suggests up to three things the user might ask next, after an answer, for the chat to offer as links.
///
/// The chat's model is given the question and the start of the answer only, so this is one small call. No
/// suggestions are made when, during the turn, the agent asked the user for something (a form, an approval, or
/// steps to carry out), as the user has something to do already.
public class FollowUpSuggester {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FollowUpSuggester.class);

    static final int MAX_SUGGESTIONS = 3;
    static final int MAX_SUGGESTION_CHARS = 120;
    static final int MAX_QUESTION_CHARS = 1_000;
    static final int MAX_ANSWER_CHARS = 3_000;

    static final String SYSTEM_PROMPT = """
            You suggest what the user of Stroom, a platform for processing and analysing event data, might ask \
            next in a chat with its AI assistant. Given their question and the assistant's answer, reply with up \
            to three short follow-up questions or requests the user would plausibly send next: one per line, in \
            the user's own voice and language, each under 15 words, each following from the answer. Reply with \
            the suggestions alone: no numbering, quotes or other text. Reply with nothing if no follow-up makes \
            sense, e.g. the answer was a greeting or the task is complete.""";

    private final AiService aiService;
    private final Provider<AiConfig> aiConfigProvider;

    @Inject
    FollowUpSuggester(final AiService aiService, final Provider<AiConfig> aiConfigProvider) {
        this.aiService = aiService;
        this.aiConfigProvider = aiConfigProvider;
    }

    /// Suggests follow-ups to the chat's latest answer, if that fits. Any problem is only logged, as suggestions
    /// are a nicety.
    ///
    /// @param chatId The chat.
    /// @param model  The chat's model.
    public void suggest(final int chatId, final ChatModel model) {
        try {
            if (model == null || !aiConfigProvider.get().isSuggestFollowUps()) {
                return;
            }
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            final List<AiChatMessage> turn = latestTurn(messages);
            if (!isApt(turn)) {
                return;
            }
            final String question = turn.getFirst().getMessage();
            final String answer = conversation(turn).getLast().getMessage();
            final String reply = model.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(prompt(question, answer)))).aiMessage().text();
            final List<String> suggestions = parse(reply);
            // Only the latest suggestions are kept.
            messages.stream()
                    .filter(message -> message.getMessageType() == AiMessageType.SUGGESTIONS)
                    .forEach(message -> aiService.deleteMessage(message.getId()));
            if (!suggestions.isEmpty()) {
                aiService.storeMessage(chatId, AiMessageType.SUGGESTIONS,
                        JsonUtil.writeValueAsString(suggestions, false));
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to suggest follow-ups for chatId=" + chatId, e);
        }
    }

    /// @return The latest turn: its question and what followed, or empty if there is none.
    static List<AiChatMessage> latestTurn(final List<AiChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i).getMessageType() == AiMessageType.USER_MESSAGE) {
                return messages.subList(i, messages.size()).stream()
                        .filter(message -> message.getMessageType() != AiMessageType.SUGGESTIONS)
                        .toList();
            }
        }
        return List.of();
    }

    /// @return True if the turn ended with an answer, and the agent asked the user for nothing during it.
    static boolean isApt(final List<AiChatMessage> turn) {
        final List<AiChatMessage> conversation = conversation(turn);
        if (conversation.size() < 2 || conversation.getLast().getMessageType() != AiMessageType.AI_RESPONSE) {
            return false;
        }
        return turn.stream().noneMatch(message -> switch (message.getMessageType()) {
            case ELICITATION, APPROVAL_REQUEST -> true;
            case TOOL_RESULT -> offersUserActions(message);
            default -> false;
        });
    }

    /// @return The turn's question, what the user added, and the answer or error, without the records kept
    /// alongside (e.g. the turn's digest, stored after the answer).
    private static List<AiChatMessage> conversation(final List<AiChatMessage> turn) {
        return turn.stream()
                .filter(message -> switch (message.getMessageType()) {
                    case USER_MESSAGE, STEER_MESSAGE, AI_RESPONSE, ERROR -> true;
                    default -> false;
                })
                .toList();
    }

    private static boolean offersUserActions(final AiChatMessage message) {
        try {
            final ToolResultRecord result = JsonUtil.readValue(message.getMessage(), ToolResultRecord.class);
            return result != null && result.userActions() != null && !result.userActions().isEmpty();
        } catch (final RuntimeException e) {
            return false;
        }
    }

    /// @return What the model is asked: the question and the start of the answer.
    static String prompt(final String question, final String answer) {
        return "The user's question:\n" + shorten(question, MAX_QUESTION_CHARS)
               + "\n\nThe assistant's answer:\n" + shorten(answer, MAX_ANSWER_CHARS);
    }

    /// @return The suggestions in the model's reply: cleaned of numbering, bullets and quotes, without repeats or
    /// overlong lines, and no more than [#MAX_SUGGESTIONS].
    static List<String> parse(final String reply) {
        if (reply == null) {
            return List.of();
        }
        final Set<String> seen = new LinkedHashSet<>();
        final List<String> suggestions = new ArrayList<>();
        for (final String line : reply.strip().lines().toList()) {
            final String suggestion = line.strip()
                    .replaceFirst("^(?:[-*•]|\\d+[.)])\\s*", "")
                    .replaceAll("^[\"'`*_]+|[\"'`*_]+$", "")
                    .strip();
            if (suggestion.isEmpty()
                || suggestion.length() > MAX_SUGGESTION_CHARS
                || !seen.add(suggestion.toLowerCase(Locale.ROOT))) {
                continue;
            }
            suggestions.add(suggestion);
            if (suggestions.size() == MAX_SUGGESTIONS) {
                break;
            }
        }
        return suggestions;
    }

    private static String shorten(final String text, final int max) {
        final String value = text == null
                ? ""
                : text.strip();
        return value.length() > max
                ? value.substring(0, max) + "…"
                : value;
    }
}
