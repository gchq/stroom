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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    /// The most characters a shortened text runs past its limit to keep an identifier the cut falls in whole (a
    /// UUID's 36, with room).
    static final int MAX_ID_OVERRUN = 40;
    /// The most identifiers past the cut a call's shortened arguments name, whole.
    static final int MAX_IDS_AFTER_CUT = 4;

    private static final Pattern UUID = Pattern.compile(
            "[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}");
    private static final Pattern HEX_ID = Pattern.compile("(?=.*\\d)(?=.*[A-Fa-f])[0-9A-Fa-f]{12,}");
    private static final Pattern ALNUM_ID = Pattern.compile("(?=.*\\d)(?=.*[A-Za-z])[A-Za-z0-9_]{16,}");
    // The longest token taken for an identifier.
    private static final int MAX_ID_CHARS = 128;
    // A run of identifier characters, for the identifiers in a text.
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_-]+");
    // A JSON key, and the quote opening its value if it is a string: the argument a value after it belongs to.
    private static final Pattern KEY = Pattern.compile("\"([A-Za-z0-9_]+)\"\\s*:\\s*\"?");
    // How far before an identifier its argument's key is looked for.
    private static final int KEY_WINDOW = 80;

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
                    .append(arguments(call.arguments(), MAX_ARGUMENTS_CHARS)).append(')');
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

    /// A text on one line, shortened to about the limit. The cut never ends inside an identifier (a UUID, or a long
    /// hex or letters-and-digits id): one it would end in is kept whole where its rest is short, else left out.
    /// Seen in chat 194: a digest cut a UUID short, and after compaction the model called tools with the cut UUIDs.
    ///
    /// @param text  A text, or null.
    /// @param limit The most characters, but for an identifier kept whole (at most [#MAX_ID_OVERRUN] more).
    /// @return The text on one line, ending in `…` where it was shortened.
    static String oneLine(final String text, final int limit) {
        final String oneLine = NullSafe.string(text).replaceAll("\\s+", " ").strip();
        return oneLine.length() > limit
                ? oneLine.substring(0, cutAt(oneLine, limit - 1)) + "…"
                : oneLine;
    }

    /// A tool call's arguments on one line, shortened as [#oneLine] shortens a text, saying where they were cut: the
    /// argument cut, and the identifiers further on, whole (up to [#MAX_IDS_AFTER_CUT]), each with its argument's
    /// name where it has one.
    ///
    /// @param arguments The arguments, as the call gave them (JSON, usually), or null.
    /// @param limit     The most characters of the arguments kept, as [#oneLine]'s limit.
    /// @return The arguments on one line, shortened where they are longer than the limit.
    static String arguments(final String arguments, final int limit) {
        final String oneLine = NullSafe.string(arguments).replaceAll("\\s+", " ").strip();
        if (oneLine.length() <= limit) {
            return oneLine;
        }
        final int cut = cutAt(oneLine, limit - 1);
        final String kept = oneLine.substring(0, cut);
        final String cutArgument = lastKey(kept, false);
        final List<String> later = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final Matcher id = IDENTIFIER.matcher(oneLine);
        int count = 0;
        while (id.find()) {
            if (id.start() >= cut && isIdentifier(id.group()) && !kept.contains(id.group())
                && seen.add(id.group())) {
                count++;
                if (later.size() < MAX_IDS_AFTER_CUT) {
                    final String named = lastKey(oneLine.substring(Math.max(0, id.start() - KEY_WINDOW),
                            id.start()), true);
                    later.add(named == null
                            ? id.group()
                            : named + "=" + id.group());
                }
            }
        }
        final StringBuilder sb = new StringBuilder(kept).append("… (cut");
        if (cutArgument != null) {
            sb.append(" in ").append(cutArgument);
        }
        if (!later.isEmpty()) {
            sb.append("; ids further on, whole: ").append(String.join(", ", later));
            if (count > later.size()) {
                sb.append(" and ").append(count - later.size()).append(" more");
            }
        }
        return sb.append(')').toString();
    }

    // The name of the last JSON key in a text, or null: only one the text ends with (its value next), if asked.
    private static String lastKey(final String text, final boolean atEnd) {
        final Matcher key = KEY.matcher(text);
        String last = null;
        int end = -1;
        while (key.find()) {
            last = key.group(1);
            end = key.end();
        }
        return atEnd && end != text.length()
                ? null
                : last;
    }

    // Where to cut a text near an end: never inside an identifier, which is kept whole if its rest is short enough,
    // else left out.
    private static int cutAt(final String text, final int end) {
        int start = end;
        while (start > 0 && isIdChar(text.charAt(start - 1))) {
            start--;
        }
        int stop = end;
        while (stop < text.length() && isIdChar(text.charAt(stop))) {
            stop++;
        }
        if (start == end || stop == end || !isIdentifier(text.substring(start, stop))) {
            // Not inside a token, or inside one that is no identifier.
            return end;
        }
        return stop - end <= MAX_ID_OVERRUN
                ? stop
                : start;
    }

    private static boolean isIdChar(final char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_';
    }

    // Whether a token is an identifier: it holds a UUID, or is a long run of hex digits, or of letters and digits
    // (both), e.g. a review id or a tool call's id; no longer than MAX_ID_CHARS (a longer run is data, cut anywhere).
    private static boolean isIdentifier(final String token) {
        return token.length() <= MAX_ID_CHARS && (UUID.matcher(token).find() || HEX_ID.matcher(token).matches()
                                                  || ALNUM_ID.matcher(token).matches());
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
