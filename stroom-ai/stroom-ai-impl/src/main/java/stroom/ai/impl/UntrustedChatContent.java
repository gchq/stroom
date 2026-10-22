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

import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/// Whether a stored chat already holds content that could hold instructions planted by someone other than the
/// user, so a run of it starts tainted (see [AiChatRunManager#markTainted(int)]).
///
/// It does when it holds an attachment or Stroom data put in the chat (a dashboard, query or table), or the
/// result of a tool that reads untrusted content. A result whose tool can't be told (an old row without the
/// tool's name, a row that can't be read, a tool since removed) counts. Results of tools that read only trusted
/// content (e.g. the time, or the chat's own task list) don't count.
final class UntrustedChatContent {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(UntrustedChatContent.class);

    // Stroom data or a user's file put in the chat, which could hold text written by anyone.
    private static final Set<AiMessageType> UNTRUSTED_DATA_TYPES = Set.of(
            AiMessageType.ATTACHMENT,
            AiMessageType.DASHBOARD_DATA,
            AiMessageType.QUERY_DATA,
            AiMessageType.TABLE_DATA);

    private UntrustedChatContent() {
        // Static methods only.
    }

    /// @param history        The chat's stored messages.
    /// @param readsUntrusted Whether a tool, by name, reads untrusted content: true for any tool that isn't one of
    ///                       Stroom's own (e.g. an MCP server's), or that Stroom no longer has.
    /// @return True if the chat holds content that could hold planted instructions.
    static boolean isIn(final List<AiChatMessage> history, final Predicate<String> readsUntrusted) {
        if (history.stream().anyMatch(message -> UNTRUSTED_DATA_TYPES.contains(message.getMessageType()))) {
            return true;
        }
        final Map<String, String> callTools = callTools(history);
        for (final AiChatMessage message : history) {
            if (message.getMessageType() == AiMessageType.TOOL_RESULT
                && isUntrustedResult(read(message, ToolResultRecord.class), callTools, readsUntrusted)) {
                LOGGER.debug(() -> "Message " + message.getId() + " of chatId=" + message.getChatId()
                                   + " is the result of a tool that reads untrusted content, or of an unknown tool");
                return true;
            }
        }
        // A call with no result (e.g. waiting for the user's approval, or for the answer to a form it asked) has
        // given the model nothing yet: when it is made again, it counts as any call does.
        return false;
    }

    // The tool named by each stored call, by the call's id.
    private static Map<String, String> callTools(final List<AiChatMessage> history) {
        final Map<String, String> callTools = new HashMap<>();
        for (final AiChatMessage message : history) {
            if (message.getMessageType() == AiMessageType.TOOL_CALL) {
                final ToolCallRecord call = read(message, ToolCallRecord.class);
                if (call != null && call.callId() != null) {
                    callTools.put(call.callId(), call.tool());
                }
            }
        }
        return callTools;
    }

    private static boolean isUntrustedResult(final ToolResultRecord result,
                                             final Map<String, String> callTools,
                                             final Predicate<String> readsUntrusted) {
        if (result == null) {
            // A row that can't be read: its tool can't be told.
            return true;
        }
        final String tool = result.tool() != null
                ? result.tool()
                : callTools.get(result.callId());
        return isUntrustedTool(tool, readsUntrusted);
    }

    private static boolean isUntrustedTool(final String tool, final Predicate<String> readsUntrusted) {
        return tool == null || tool.isBlank() || readsUntrusted.test(tool);
    }

    private static <T> T read(final AiChatMessage message, final Class<T> type) {
        try {
            return JsonUtil.readValue(message.getMessage(), type);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read message " + message.getId(), e);
            return null;
        }
    }
}
