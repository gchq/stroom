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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import jakarta.inject.Singleton;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The latest summary of each chat's earlier steps on the question being answered (see
/// [McpAgentChat#withTurnSummary]), kept on this node so that a run carrying on with the question puts it back in
/// place of the steps it covers, rather than summarising them all again. Seen: a run stops for each form one of
/// Stroom AI's own tools asks the user to fill in, and carries on in a new run from the question's stored steps,
/// so each form was followed by a summary of every step so far, a minute or so each, and once they no longer fit
/// the summary's input, without the earliest of them.
///
/// A summary is found by the id of the last call it covers, so one for another question (or one the chat no
/// longer has, e.g. the question was edited or the chat cleared) is never put back, and is dropped when a run
/// doesn't find it. A run carrying on on another node summarises the steps again, as before.
@Singleton
class TurnSummaries {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TurnSummaries.class);
    // Each is a summary and the results it keeps for reference, so up to about 25k characters.
    static final int MAX_CHATS = 1_000;

    private final Map<Integer, Kept> keptByChat = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Integer, Kept> eldest) {
            return size() > MAX_CHATS;
        }
    };

    /// Keeps a chat's latest summary.
    ///
    /// @param chatId       The chat.
    /// @param conversation The conversation as it was before the summary was put in it.
    /// @param keptFrom     The index of the first step the summary leaves as it is.
    /// @param note         The note holding the summary, as it was put in the conversation.
    synchronized void remember(final int chatId,
                               final List<ChatMessage> conversation,
                               final int keptFrom,
                               final String note) {
        final String lastCallId = lastCallIdBefore(conversation, keptFrom);
        if (lastCallId == null) {
            keptByChat.remove(chatId);
        } else {
            keptByChat.put(chatId, new Kept(lastCallId, note));
        }
    }

    /// Puts a chat's latest summary back in a conversation built again from the question's stored steps, in place
    /// of the steps it covers.
    ///
    /// @param chatId       The chat.
    /// @param conversation The conversation, changed in place.
    /// @return How many steps the summary was put in place of, or 0 if there was none to put back.
    synchronized int putBack(final int chatId, final List<ChatMessage> conversation) {
        final Kept kept = keptByChat.get(chatId);
        if (kept == null) {
            return 0;
        }
        final int keptFrom = stepAfterCall(conversation, kept.lastCallId());
        if (keptFrom < 0) {
            // Not this question's, or its steps are gone.
            keptByChat.remove(chatId);
            return 0;
        }
        final int summarised = McpAgentChat.withNote(conversation, keptFrom, kept.note());
        LOGGER.info(() -> LogUtil.message("Put back the summary of {} earlier step(s) of chatId={}",
                summarised, chatId));
        return summarised;
    }

    // The id of the last call of the last step before keptFrom, or null if there is none.
    private static String lastCallIdBefore(final List<ChatMessage> conversation, final int keptFrom) {
        for (int i = Math.min(keptFrom, conversation.size()) - 1; i >= 0; i--) {
            if (conversation.get(i) instanceof final AiMessage step && step.hasToolExecutionRequests()) {
                return step.toolExecutionRequests().getLast().id();
            }
        }
        return null;
    }

    // The index of the first step after the one that made the call, or -1 if no step made it or none follows it,
    // as the steps a summary leaves as they are always follow it.
    private static int stepAfterCall(final List<ChatMessage> conversation, final String callId) {
        boolean found = false;
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i) instanceof final AiMessage step && step.hasToolExecutionRequests()) {
                if (found) {
                    return i;
                }
                found = step.toolExecutionRequests().stream()
                        .map(ToolExecutionRequest::id)
                        .anyMatch(id -> Objects.equals(id, callId));
            }
        }
        return -1;
    }


    // --------------------------------------------------------------------------------


    private record Kept(String lastCallId, String note) {

    }
}
