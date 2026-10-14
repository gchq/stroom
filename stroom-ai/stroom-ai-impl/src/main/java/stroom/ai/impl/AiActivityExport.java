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
import stroom.util.shared.NullSafe;

/// Writes a chat's reasoning and tool activity as markdown, for downloading the chat's history.
final class AiActivityExport {

    private static final String FENCE = "~~~~~~~~";

    private AiActivityExport() {
        // Static only.
    }

    /// @param type A message type.
    /// @return True if it is reasoning or tool activity.
    static boolean isActivity(final AiMessageType type) {
        return type == AiMessageType.THINKING
               || type == AiMessageType.TOOL_CALL
               || type == AiMessageType.TOOL_RESULT
               || type == AiMessageType.MEMORY_UPDATE;
    }

    /// @param msg A THINKING, TOOL_CALL, TOOL_RESULT or MEMORY_UPDATE message.
    /// @return The message as markdown.
    static String body(final AiChatMessage msg) {
        final String text = NullSafe.string(msg.getMessage());
        return switch (msg.getMessageType()) {
            case THINKING -> thinkingText(text);
            case TOOL_CALL -> {
                final ToolCallRecord call = read(text, ToolCallRecord.class);
                yield call == null
                        ? fenced(text)
                        : "`" + call.tool() + "`\n\n" + fenced(NullSafe.string(call.arguments()));
            }
            case TOOL_RESULT -> {
                final ToolResultRecord result = read(text, ToolResultRecord.class);
                if (result == null) {
                    yield fenced(text);
                }
                final String note = result.path() != null
                        ? "The full result was saved to `" + result.path() + "`.\n\n"
                        : "";
                yield "`" + result.tool() + "`" + (result.error()
                        ? " (failed)"
                        : "") + "\n\n" + note + fenced(NullSafe.string(result.text()));
            }
            default -> fenced(text);
        };
    }

    /// @param json A THINKING message: JSON (see [ThinkingRecord]), or plain text in older messages.
    /// @return The reasoning text.
    static String thinkingText(final String json) {
        if (json != null && json.startsWith("{")) {
            final ThinkingRecord thinking = read(json, ThinkingRecord.class);
            if (thinking != null && thinking.text() != null) {
                return thinking.text();
            }
        }
        return NullSafe.string(json);
    }

    // A long fence, so text holding a shorter one can't end the block early.
    private static String fenced(final String text) {
        return FENCE + "\n" + text.replace(FENCE, "~~~~~~~ ~") + "\n" + FENCE;
    }

    private static <T> T read(final String json, final Class<T> type) {
        try {
            return JsonUtil.readValue(json, type);
        } catch (final RuntimeException e) {
            return null;
        }
    }
}
