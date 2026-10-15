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
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import jakarta.inject.Inject;

import java.util.List;
import java.util.stream.Stream;

/// Names chats after what they are about, and keeps the name up to date as the chat goes on, unless the user has
/// named the chat themselves.
///
/// A chat is first named after its first question. Then, after the answers to its 1st, 3rd, 6th and 10th
/// questions, and every 10th after that, the chat's model is asked for a short title. Only the user's questions
/// are sent, so this is cheap, and they are the user's own words, so carry no planted instructions.
public class ChatTitler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ChatTitler.class);

    static final int MAX_TITLE_CHARS = 60;
    static final int MAX_QUESTIONS = 10;
    static final int MAX_QUESTION_CHARS = 300;

    static final String SYSTEM_PROMPT = """
            You name chats. Reply with a title of at most six words that says what the chat is about, in the \
            user's language. Reply with the title alone: no quotes, labels or full stop.""";

    private final AiService aiService;
    private final AiDao aiDao;

    @Inject
    ChatTitler(final AiService aiService, final AiDao aiDao) {
        this.aiService = aiService;
        this.aiDao = aiDao;
    }

    /// Names a new chat after its first question, unless the user has named it.
    ///
    /// @param chatId   The chat.
    /// @param question The question just asked.
    public void nameFromQuestion(final int chatId, final String question) {
        if (countQuestions(chatId) == 1) {
            final String title = clean(question);
            if (!title.isEmpty()) {
                aiDao.setAutoTitle(chatId, title);
            }
        }
    }

    /// Asks the model for a title, if one is due, now that a question has been answered. Any problem is only
    /// logged, as the chat already has a title.
    ///
    /// @param chatId The chat.
    /// @param model  The chat's model.
    public void retitleIfDue(final int chatId, final ChatModel model) {
        try {
            final List<String> questions = questions(chatId);
            // Not if the user has named the chat themselves, which would only be ignored.
            if (model == null || !isDue(questions.size()) || aiDao.isTitleSetByUser(chatId)) {
                return;
            }
            final String reply = model.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(prompt(questions)))).aiMessage().text();
            final String title = clean(reply);
            if (!title.isEmpty()) {
                aiDao.setAutoTitle(chatId, title);
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to retitle chatId=" + chatId, e);
        }
    }

    /// @param questions How many questions the chat has.
    /// @return True if the chat is due a new title: after its 1st, 3rd, 6th and 10th questions, then every 10th.
    static boolean isDue(final int questions) {
        return questions == 1 || questions == 3 || questions == 6 || (questions > 0 && questions % 10 == 0);
    }

    /// @param questions The chat's questions, oldest first.
    /// @return What the model is asked: the first question and the latest ones, each shortened.
    static String prompt(final List<String> questions) {
        final StringBuilder sb = new StringBuilder("The user's questions in this chat, oldest first:\n");
        final List<String> chosen = questions.size() <= MAX_QUESTIONS
                ? questions
                : Stream.concat(
                        questions.stream().limit(1),
                        questions.stream().skip(questions.size() - (MAX_QUESTIONS - 1))).toList();
        for (final String question : chosen) {
            final String oneLine = question.replaceAll("\\s+", " ").strip();
            sb.append("- ").append(oneLine.length() > MAX_QUESTION_CHARS
                    ? oneLine.substring(0, MAX_QUESTION_CHARS) + "…"
                    : oneLine).append('\n');
        }
        return sb.toString();
    }

    /// @param text A question, or the model's reply.
    /// @return A title made from it: one line, without quotes, labels or a full stop, and not too long. Empty if
    /// there is nothing to make one from.
    static String clean(final String text) {
        if (text == null) {
            return "";
        }
        String title = text.strip().lines().findFirst().orElse("").strip();
        // Markup and quotes round it, and a label, in either order.
        title = unwrap(title).replaceFirst("(?i)^title\\s*:\\s*", "");
        title = unwrap(title);
        if (title.endsWith(".")) {
            title = title.substring(0, title.length() - 1).strip();
        }
        return title.length() > MAX_TITLE_CHARS
                ? title.substring(0, MAX_TITLE_CHARS - 1).strip() + "…"
                : title;
    }

    private static String unwrap(final String text) {
        return text.replaceAll("^[\"'`*_#\\s]+|[\"'`*_\\s]+$", "");
    }

    private int countQuestions(final int chatId) {
        return questions(chatId).size();
    }

    private List<String> questions(final int chatId) {
        return aiService.getMessages(chatId).stream()
                .filter(message -> message.getMessageType() == AiMessageType.USER_MESSAGE)
                .map(AiChatMessage::getMessage)
                .toList();
    }
}
