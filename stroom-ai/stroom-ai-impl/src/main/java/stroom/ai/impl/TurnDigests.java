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
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Builds the digest of a finished turn, in code (no model call), for the model to see in place of the turn once
/// the chat has been compacted. A digest keeps what later turns are most likely to need, exactly as it was:
///
/// - the question, shortened;
/// - anything the user added while the model worked;
/// - each tool used, with its result copied exactly if it is small, or where the result is stored if not;
/// - the workspace files created;
/// - the first paragraph of the answer.
///
/// The whole turn stays stored, and the model can read it again with the read_turn tool.
final class TurnDigests {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TurnDigests.class);

    static final int MAX_QUESTION_CHARS = 500;
    static final int MAX_ARGUMENTS_CHARS = 200;
    static final int MAX_EXACT_RESULT_CHARS = 2_000;
    static final int MAX_RESULT_PREVIEW_CHARS = 200;
    static final int MAX_ANSWER_CHARS = 1_000;

    private TurnDigests() {
        // Static only.
    }

    /// @param turnId       The turn.
    /// @param turnMessages The turn's messages, in order.
    /// @param maxRows      Tool results of up to this many lines are copied exactly.
    /// @return The turn's digest.
    static String build(final int turnId, final List<AiChatMessage> turnMessages, final int maxRows) {
        final StringBuilder sb = new StringBuilder("Turn ").append(turnId).append(":\n");
        final List<ToolCallRecord> calls = new ArrayList<>();
        final Map<String, ToolResultRecord> results = new HashMap<>();
        final List<String> files = new ArrayList<>();
        String question = null;
        final List<String> steers = new ArrayList<>();
        String answer = null;
        for (final AiChatMessage message : turnMessages) {
            switch (message.getMessageType()) {
                case USER_MESSAGE -> question = message.getMessage();
                case STEER_MESSAGE -> steers.add(message.getMessage());
                case AI_RESPONSE -> answer = message.getMessage();
                case TOOL_CALL -> read(message, ToolCallRecord.class).ifPresent(calls::add);
                case TOOL_RESULT -> read(message, ToolResultRecord.class).ifPresent(result -> {
                    results.put(result.callId(), result);
                    if (result.path() != null) {
                        files.add(result.path());
                    }
                });
                default -> {
                    // Not part of a digest.
                }
            }
        }

        sb.append("- Question: ").append(oneLine(question, MAX_QUESTION_CHARS)).append('\n');
        for (final String steer : steers) {
            sb.append("- The user added while you worked: ").append(oneLine(steer, MAX_QUESTION_CHARS)).append('\n');
        }
        for (final ToolCallRecord call : calls) {
            sb.append("- Tool ").append(call.tool()).append('(')
                    .append(oneLine(call.arguments(), MAX_ARGUMENTS_CHARS)).append(')');
            appendResult(sb, results.get(call.callId()), turnId, maxRows);
        }
        if (!files.isEmpty()) {
            sb.append("- Workspace files created: ").append(String.join(", ", files)).append('\n');
        }
        if (answer == null) {
            sb.append("- No answer was given (the run did not finish).\n");
        } else {
            sb.append("- Answer (start): ").append(firstParagraph(answer)).append('\n');
        }
        return sb.toString().strip();
    }

    private static void appendResult(final StringBuilder sb,
                                     final ToolResultRecord result,
                                     final int turnId,
                                     final int maxRows) {
        if (result == null) {
            sb.append(" -> no result\n");
            return;
        }
        final String failed = result.error()
                ? " (failed)"
                : "";
        final String text = NullSafe.string(result.text()).strip();
        if (result.path() != null) {
            sb.append(failed).append(" -> full result (").append(result.chars()).append(" characters) saved to ")
                    .append(result.path()).append('\n');
        } else if (lineCount(text) <= maxRows && text.length() <= MAX_EXACT_RESULT_CHARS) {
            sb.append(failed).append(" -> result:\n");
            text.lines().forEach(line -> sb.append("    ").append(line).append('\n'));
        } else {
            sb.append(failed).append(" -> ").append(oneLine(text, MAX_RESULT_PREVIEW_CHARS))
                    .append(" (").append(lineCount(text)).append(" lines; use read_turn ").append(turnId)
                    .append(" for all of it)\n");
        }
    }

    /// @param history All of a chat's messages, in order.
    /// @return The messages of each turn, by turn, in order.
    static Map<Integer, List<AiChatMessage>> byTurn(final List<AiChatMessage> history) {
        final Map<Integer, List<AiChatMessage>> byTurn = new LinkedHashMap<>();
        for (final AiChatMessage message : history) {
            byTurn.computeIfAbsent(message.getTurnId(), k -> new ArrayList<>()).add(message);
        }
        return byTurn;
    }

    /// @param history All of a chat's messages, in order.
    /// @param maxRows Tool results of up to this many lines are copied exactly.
    /// @return The digest of every turn: the stored one if there is one, otherwise built now.
    static Map<Integer, String> digests(final List<AiChatMessage> history, final int maxRows) {
        final Map<Integer, String> digests = new LinkedHashMap<>();
        byTurn(history).forEach((turnId, messages) -> {
            final String stored = messages.stream()
                    .filter(m -> m.getMessageType() == AiMessageType.TURN_DIGEST)
                    .map(AiChatMessage::getMessage)
                    .reduce((first, second) -> second)
                    .orElse(null);
            digests.put(turnId, stored != null
                    ? stored
                    : build(turnId, messages, maxRows));
        });
        return digests;
    }

    private static <T> Optional<T> read(final AiChatMessage message, final Class<T> type) {
        try {
            return Optional.ofNullable(JsonUtil.readValue(message.getMessage(), type));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read message " + message.getId(), e);
            return Optional.empty();
        }
    }

    static String oneLine(final String text, final int limit) {
        final String oneLine = NullSafe.string(text).replaceAll("\\s+", " ").strip();
        return oneLine.length() > limit
                ? oneLine.substring(0, limit - 1) + "…"
                : oneLine;
    }

    private static String firstParagraph(final String answer) {
        final String stripped = answer.strip();
        final int end = stripped.indexOf("\n\n");
        return oneLine(end > 0
                ? stripped.substring(0, end)
                : stripped, MAX_ANSWER_CHARS);
    }

    private static int lineCount(final String text) {
        return text.isEmpty()
                ? 0
                : (int) text.lines().count();
    }
}
