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
import stroom.ai.shared.AiTabSnapshot;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Suggests up to three things the user might ask next, after an answer, for the chat to offer as links.
///
/// The chat's model is given the question and the start of the answer only, so this is one small call, with, when
/// a tab is attached to the chat, a line or two naming it and saying what the assistant can do with it, so the
/// suggestions lean towards acting on it. No
/// suggestions are made when, during the turn, the agent asked the user for something (a form, an approval, or
/// steps to carry out), as the user has something to do already.
public class FollowUpSuggester {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FollowUpSuggester.class);

    static final int MAX_SUGGESTIONS = 3;
    static final int MAX_SUGGESTION_CHARS = 120;
    static final int MAX_QUESTION_CHARS = 1_000;
    static final int MAX_ANSWER_CHARS = 3_000;
    /// The most documents attached to the chat named in the hint, the most recently attached.
    static final int MAX_DOCUMENT_HINTS = 3;
    /// The most things just attached named when suggesting what to ask about them.
    static final int MAX_ATTACHED_HINTS = 4;
    /// How much of the conversation is given when suggesting what to ask about something just attached: the
    /// latest questions, and the start of the latest answer.
    static final int MAX_RECENT_QUESTIONS = 2;
    static final int MAX_RECENT_ANSWER_CHARS = 1_000;

    static final String SYSTEM_PROMPT = """
            You suggest what the user of Stroom, a platform for processing and analysing event data, might ask \
            next in a chat with its AI assistant. Given their question and the assistant's answer, reply with up \
            to three short follow-up questions or requests the user would plausibly send next: one per line, in \
            the user's own voice and language, each under 15 words, each following from the answer. Reply with \
            the suggestions alone: no numbering, quotes or other text. Reply with nothing if no follow-up makes \
            sense, e.g. the answer was a greeting or the task is complete. If the user has a tab or documents \
            attached, strongly prefer follow-ups that act on them, using what the assistant can do with them.""";

    static final String ATTACHED_SYSTEM_PROMPT = """
            You suggest what the user of Stroom, a platform for processing and analysing event data, might ask \
            its AI assistant about what they have just attached to their chat (a tab, a document or a table). \
            Given what was attached, what the assistant can do with each, and the conversation so far, reply with \
            up to three short questions or requests the user would plausibly send next: one per line, in the \
            user's own voice, each under 15 words. Strongly prefer requests that act on what was just attached, \
            using what the assistant can do with it, and that fit what the user has been working on. Where several \
            things are attached, include a request that uses them together (e.g. whether an attached pipeline \
            processes an attached feed). Reply with the suggestions alone: no numbering, quotes or other text.""";

    private final AiService aiService;
    private final Provider<AiConfig> aiConfigProvider;
    private final TabSnapshots tabSnapshots;
    private final DocumentAccess documentAccess;

    @Inject
    FollowUpSuggester(final AiService aiService,
                      final Provider<AiConfig> aiConfigProvider,
                      final TabSnapshots tabSnapshots,
                      final DocumentAccess documentAccess) {
        this.aiService = aiService;
        this.aiConfigProvider = aiConfigProvider;
        this.tabSnapshots = tabSnapshots;
        this.documentAccess = documentAccess;
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
                    UserMessage.from(prompt(question, answer, attachedHint(chatId))))).aiMessage().text();
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

    /// Suggests what the user might ask about what is attached to the chat (the tab, documents and tables, all of
    /// them), given the conversation so far, for the chat to offer in place of the suggestions it made up
    /// itself. Nothing is stored. Any problem is only logged, as suggestions are a nicety.
    ///
    /// @param chatId        The chat, which the user owns.
    /// @param model         The chat's model.
    /// @param attachmentIds The chat's attachments not yet sent with a question, most recently attached first.
    ///                      Only the chat's own are used.
    /// @param tab           The attached tab, without its content, or null.
    /// @return Up to [#MAX_SUGGESTIONS] suggestions, or none if there is nothing attached, no conversation to go
    /// on, or suggestions are turned off.
    public List<String> suggestForAttached(final int chatId,
                                           final ChatModel model,
                                           final List<Integer> attachmentIds,
                                           final AiTabSnapshot tab) {
        try {
            if (model == null || !aiConfigProvider.get().isSuggestFollowUps()) {
                return List.of();
            }
            final List<String> hints = justAttachedHints(chatId, attachmentIds, tab);
            final String recent = recentConversation(aiService.getMessages(chatId));
            // With one thing attached and no conversation to go on, the chat's own suggestions for it are as good;
            // with several, the model can suggest using them together.
            if (hints.isEmpty() || (recent.isEmpty() && hints.size() < 2)) {
                return List.of();
            }
            final String reply = model.chat(List.of(
                    SystemMessage.from(ATTACHED_SYSTEM_PROMPT),
                    UserMessage.from(NullSafe.nonBlankStringElse(recent, "Nothing has been asked yet.")
                                     + "\n\nAttached:\n" + String.join("\n", hints))))
                    .aiMessage().text();
            return parse(reply);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to suggest what to ask about what was attached to chatId=" + chatId, e);
            return List.of();
        }
    }

    /// @return Hints of what was just attached that the user can view, most recently attached first, at most
    /// [#MAX_ATTACHED_HINTS].
    private List<String> justAttachedHints(final int chatId,
                                           final List<Integer> attachmentIds,
                                           final AiTabSnapshot tab) {
        final List<String> hints = new ArrayList<>();
        if (tab != null && documentAccess.canView(tab.getDocRef())) {
            hints.add(AiSuggestions.tabHint(tab.getDocRef(), tab.getInnerTab(), tab.isDirty(), tab.getSearches(),
                    tab.getStepping()));
        }
        final Map<Integer, AiChatAttachment> attachments = new HashMap<>();
        aiService.getAttachmentsByChatId(chatId).forEach(attachment -> attachments.put(attachment.getId(),
                attachment));
        for (final Integer id : NullSafe.list(attachmentIds)) {
            final AiChatAttachment attachment = attachments.get(id);
            if (attachment == null || hints.size() >= MAX_ATTACHED_HINTS) {
                continue;
            }
            final String hint = attachmentHint(attachment);
            if (hint != null) {
                hints.add(hint);
            }
        }
        return hints;
    }

    /// @return The hint of an attachment: a document the user can view, or a table of results; or null if it is
    /// neither.
    private String attachmentHint(final AiChatAttachment attachment) {
        final AiAttachmentType type = attachment.getAttachmentType();
        if (type == AiAttachmentType.DOCUMENT) {
            return documentAccess.canView(attachment.getDocRef())
                    ? AiSuggestions.documentHint(attachment.getDocRef())
                    : null;
        } else if (type == AiAttachmentType.DASHBOARD
                   || type == AiAttachmentType.QUERY
                   || type == AiAttachmentType.GENERAL) {
            return AiSuggestions.tableHint(attachment.getDescription());
        }
        return null;
    }

    /// @return The latest questions and the start of the latest answer, briefly, or an empty string if nothing
    /// has been asked yet.
    static String recentConversation(final List<AiChatMessage> messages) {
        final List<String> questions = new ArrayList<>();
        String answer = null;
        for (int i = messages.size() - 1; i >= 0 && questions.size() < MAX_RECENT_QUESTIONS; i--) {
            final AiChatMessage message = messages.get(i);
            if (message.getMessageType() == AiMessageType.USER_MESSAGE) {
                questions.addFirst(shorten(message.getMessage(), MAX_QUESTION_CHARS));
            } else if (answer == null && message.getMessageType() == AiMessageType.AI_RESPONSE) {
                answer = shorten(message.getMessage(), MAX_RECENT_ANSWER_CHARS);
            }
        }
        if (questions.isEmpty()) {
            return "";
        }
        return "The user's latest questions:\n- " + String.join("\n- ", questions)
               + (answer != null
                ? "\n\nThe start of the assistant's latest answer:\n" + answer
                : "");
    }

    /// @return Hints of the tab attached to the latest question and the documents attached to the chat (the most
    /// recently attached, at most [#MAX_DOCUMENT_HINTS]), and what can be done with them; or an empty string if
    /// there are none.
    private String attachedHint(final int chatId) {
        final List<String> hints = new ArrayList<>();
        tabSnapshots.forLatestQuestion(chatId)
                .map(tab -> AiSuggestions.tabHint(tab.docRef(), tab.innerTab(), tab.dirty(), tab.searches(),
                        tab.stepping()))
                .ifPresent(hints::add);
        aiService.getAttachmentsByChatId(chatId).stream()
                .filter(attachment -> attachment.getAttachmentType() == AiAttachmentType.DOCUMENT)
                .filter(attachment -> attachment.getStatus() == AiAttachmentStatus.READY)
                .sorted(Comparator.comparingLong(AiChatAttachment::getCreateTimeMs).reversed())
                .filter(attachment -> documentAccess.canView(attachment.getDocRef()))
                .limit(MAX_DOCUMENT_HINTS)
                .forEach(attachment -> hints.add(AiSuggestions.documentHint(attachment.getDocRef())));
        return String.join("\n", hints);
    }

    /// @return What the model is asked: the question, the start of the answer and the attached tab's hint, if any.
    static String prompt(final String question, final String answer, final String tabHint) {
        return "The user's question:\n" + shorten(question, MAX_QUESTION_CHARS)
               + "\n\nThe assistant's answer:\n" + shorten(answer, MAX_ANSWER_CHARS)
               + (NullSafe.isNonBlankString(tabHint)
                ? "\n\n" + tabHint
                : "");
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
