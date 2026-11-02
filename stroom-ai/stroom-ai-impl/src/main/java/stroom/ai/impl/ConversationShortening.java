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

import stroom.util.json.JsonUtil;
import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Makes a run's conversation smaller when it is getting too large for the model's context window: earlier tool
/// results and call arguments shortened, earlier steps put briefly in a note, or put in a note holding a summary
/// of them (the summary itself is written by the model, in [McpAgentChat]). Each changes the conversation in
/// place; every call and result stays stored, for read_turn.
final class ConversationShortening {

    /// How many of the latest tool results are kept as they are when the conversation is getting too large.
    static final int RECENT_RESULTS_KEPT = 2;

    /// Tool results shorter than this are never shortened.
    static final int SHORTENED_MIN_CHARS = 300;
    /// Put in place of the arguments of an earlier tool call that have been shortened, with how they started.
    static final String SHORTENED_ARGUMENTS_KEY = "shortenedToSaveSpace";
    static final int SHORTENED_PREVIEW_CHARS = 200;
    /// The most characters of each part of an earlier step put in a note.
    static final int COLLAPSED_LINE_CHARS = 160;
    static final String COLLAPSED_STEPS_NOTE = "[Earlier steps of your answer, put briefly to save space in the "
                                               + "context window. Their calls and results are stored: call "
                                               + "read_turn (with no turn id, for this turn) to read them again if "
                                               + "you need them.]";
    /// The most characters given to be summarised: the summary so far and the latest steps that fit, so a first
    /// summary of a very long question is neither slow nor too large for the model itself.
    static final int SUMMARY_INPUT_MAX_CHARS = 60_000;
    /// The most characters of each part of a step (what was said, a call's arguments, a result) given to be
    /// summarised.
    static final int SUMMARY_PART_CHARS = 1_500;
    // The summary is of the steps before the latest few, which follow it as they were. Seen: a summary said the
    // next step was to reprocess a stream, which a step kept after it had done, and the model did it again.
    static final String TURN_SUMMARY_NOTE = "[Your earlier progress on this question, summarised to save space "
                                            + "in the context window. The steps after this note came later and "
                                            + "are kept as they were: where they and the summary differ, they "
                                            + "are right, and what they did is done even if the summary says it "
                                            + "is still to do. Carry on from the latest of them, and don't redo "
                                            + "what they or the summary show is done. Every call and result is "
                                            + "stored: call read_turn (with no turn id, for this turn) to read "
                                            + "one again exactly if you need it.]";
    /// Put in place of a tool result that has been shortened.
    static final String SHORTENED_RESULT = "[This result has been removed to save space in the context window. "
                                           + "It is stored: call read_turn (with no turn id, for this turn) to "
                                           + "read it again if you need it.]";

    private ConversationShortening() {
        // Static only.
    }

    /// @param conversation The conversation.
    /// @param keptFrom     The index of the first step left as it is.
    /// @return What is given to be summarised: the summary so far, if the question has one, then each step before
    /// `keptFrom`, each part of it cut to [#SUMMARY_PART_CHARS], and only the latest steps that fit within
    /// [#SUMMARY_INPUT_MAX_CHARS]. What the summary so far kept for reference isn't given, as that is kept as it
    /// is.
    static String turnSummaryInput(final List<ChatMessage> conversation, final int keptFrom) {
        final StringBuilder soFar = new StringBuilder();
        final List<String> parts = new ArrayList<>();
        for (int i = 0; i < keptFrom; i++) {
            final ChatMessage message = conversation.get(i);
            if (message instanceof final AiMessage step && step.hasToolExecutionRequests()) {
                final StringBuilder part = new StringBuilder();
                if (NullSafe.isNonBlankString(step.text())) {
                    part.append("You said: ").append(truncate(step.text().strip(), SUMMARY_PART_CHARS))
                            .append('\n');
                }
                step.toolExecutionRequests().forEach(request -> part.append("Called ").append(request.name())
                        .append(" with ").append(truncate(NullSafe.string(request.arguments()), SUMMARY_PART_CHARS))
                        .append('\n'));
                parts.add(part.toString());
            } else if (message instanceof final ToolExecutionResultMessage result) {
                parts.add("Result of " + result.toolName() + ": "
                          + truncate(NullSafe.string(result.text()), SUMMARY_PART_CHARS) + "\n\n");
            } else if (isNote(message, TURN_SUMMARY_NOTE)) {
                soFar.append(ReferenceResults.withoutKept(
                        ((UserMessage) message).singleText().substring(TURN_SUMMARY_NOTE.length())));
            } else if (isNote(message, COLLAPSED_STEPS_NOTE)) {
                parts.add(((UserMessage) message).singleText().substring(COLLAPSED_STEPS_NOTE.length()).strip()
                          + "\n\n");
            }
        }
        final String start = soFar.isEmpty()
                ? ""
                : "Summary so far:\n" + soFar + "\n\n";
        // The latest steps that fit, as the summary so far stands for what came before them.
        final Deque<String> since = new ArrayDeque<>();
        int length = start.length();
        int left = parts.size();
        while (left > 0 && length + parts.get(left - 1).length() <= SUMMARY_INPUT_MAX_CHARS) {
            left--;
            since.addFirst(parts.get(left));
            length += parts.get(left).length();
        }
        final String omitted = left > 0
                ? "(The " + left + " earliest part(s) are left out, to keep this short.)\n\n"
                : "";
        return start + "Steps since:\n\n" + omitted + String.join("", since).strip();
    }

    /// Puts the steps before `keptFrom` (with their results, and any summary or note of steps before them) in
    /// one note holding the summary, and the results read for reference as they were (see [ReferenceResults]),
    /// where the first of them was. Everything else, e.g. what the user sent while the run went on, stays as it is.
    ///
    /// @param conversation The conversation, changed in place.
    /// @param keptFrom     The index of the first step left as it is.
    /// @param summary      The summary.
    /// @return How many steps were put in the summary.
    static int withTurnSummary(final List<ChatMessage> conversation, final int keptFrom, final String summary) {
        return withNote(conversation, keptFrom, turnSummaryNote(conversation, keptFrom, summary));
    }

    /// @param conversation The conversation.
    /// @param keptFrom     The index of the first step left as it is.
    /// @param summary      The summary.
    /// @return The note that holds the summary in place of the steps before `keptFrom`, with the results they read
    /// for reference.
    static String turnSummaryNote(final List<ChatMessage> conversation, final int keptFrom, final String summary) {
        return TURN_SUMMARY_NOTE + "\n" + summary
               + ReferenceResults.toKeep(ReferenceResults.before(conversation, keptFrom));
    }

    /// Puts the steps before `keptFrom` in a note, as [#withTurnSummary] does.
    ///
    /// @param conversation The conversation, changed in place.
    /// @param keptFrom     The index of the first step left as it is.
    /// @param note         The note, as [#turnSummaryNote] gives it.
    /// @return How many steps were put in the note.
    static int withNote(final List<ChatMessage> conversation, final int keptFrom, final String note) {
        final List<ChatMessage> result = new ArrayList<>();
        boolean noted = false;
        int summarised = 0;
        for (int i = 0; i < conversation.size(); i++) {
            final ChatMessage message = conversation.get(i);
            final boolean step = message instanceof final AiMessage ai && ai.hasToolExecutionRequests();
            final boolean inSummary = i < keptFrom
                                      && (step
                                          || message instanceof ToolExecutionResultMessage
                                          || isNote(message, TURN_SUMMARY_NOTE)
                                          || isNote(message, COLLAPSED_STEPS_NOTE));
            if (!inSummary) {
                result.add(message);
                continue;
            }
            if (!noted) {
                result.add(UserMessage.from(note));
                noted = true;
            }
            if (step) {
                summarised++;
            }
        }
        conversation.clear();
        conversation.addAll(result);
        return summarised;
    }

    /// @param message A message in the conversation.
    /// @param note    How a note starts.
    /// @return Whether the message is that note.
    static boolean isNote(final ChatMessage message, final String note) {
        return message instanceof final UserMessage user && user.hasSingleText()
               && user.singleText().startsWith(note);
    }

    /// Takes the first step that makes the conversation smaller, least lossy first:
    ///
    /// 1. earlier tool results, keeping the latest [#RECENT_RESULTS_KEPT] and those read for reference (see
    ///    [ReferenceResults#isReference]);
    /// 2. the arguments of earlier steps' tool calls (e.g. whole code proposed), keeping the latest step's;
    /// 3. earlier tool results, keeping only the latest and those read for reference;
    /// 4. earlier tool results read for reference, keeping only the latest result;
    /// 5. earlier steps, each put as a line or two in a note.
    ///
    /// The latest step, and its latest result, are always kept, so the model can carry on from it.
    ///
    /// @param conversation The conversation, changed in place.
    /// @return How many parts were shortened, or 0 if there was nothing more to shorten.
    static int shrink(final List<ChatMessage> conversation) {
        final int shortened = shrinkKeepingSteps(conversation);
        return shortened > 0
                ? shortened
                : collapseEarlierSteps(conversation, 1);
    }

    /// As [#shrink], without its last step: the earlier steps are kept, with their results shortened.
    ///
    /// @param conversation The conversation, changed in place.
    /// @return How many parts were shortened, or 0 if there was nothing more to shorten that way.
    static int shrinkKeepingSteps(final List<ChatMessage> conversation) {
        int shortened = shortenToolResults(conversation, RECENT_RESULTS_KEPT, true);
        if (shortened == 0) {
            shortened = shortenToolCallArguments(conversation, 1);
        }
        if (shortened == 0) {
            shortened = shortenToolResults(conversation, 1, true);
        }
        if (shortened == 0) {
            shortened = shortenToolResults(conversation, 1, false);
        }
        return shortened;
    }

    /// Replaces long arguments of earlier steps' tool calls with a short note of how they started. The arguments
    /// stay valid JSON, as some model servers read them.
    ///
    /// @param conversation The conversation, changed in place.
    /// @param keepSteps    How many of the latest steps to leave as they are.
    /// @return How many calls' arguments were shortened.
    static int shortenToolCallArguments(final List<ChatMessage> conversation, final int keepSteps) {
        final List<Integer> steps = stepIndexes(conversation);
        int shortened = 0;
        for (int i = 0; i < steps.size() - keepSteps; i++) {
            final int index = steps.get(i);
            final AiMessage step = (AiMessage) conversation.get(index);
            boolean changed = false;
            final List<ToolExecutionRequest> requests = new ArrayList<>();
            for (final ToolExecutionRequest request : step.toolExecutionRequests()) {
                final String arguments = NullSafe.string(request.arguments());
                if (arguments.length() > SHORTENED_MIN_CHARS && !arguments.contains(SHORTENED_ARGUMENTS_KEY)) {
                    requests.add(request.toBuilder()
                            .arguments(JsonUtil.writeValueAsString(Map.of(SHORTENED_ARGUMENTS_KEY,
                                    truncate(arguments, SHORTENED_PREVIEW_CHARS)), false))
                            .build());
                    changed = true;
                    shortened++;
                } else {
                    requests.add(request);
                }
            }
            if (changed) {
                conversation.set(index, AiMessage.builder()
                        .text(step.text())
                        .toolExecutionRequests(requests)
                        .build());
            }
        }
        return shortened;
    }

    /// Puts earlier steps (the model's tool calls and their results) as a line or two each in a note, in their
    /// place. Steps next to each other share a note.
    ///
    /// @param conversation The conversation, changed in place.
    /// @param keepSteps    How many of the latest steps to leave as they are.
    /// @return How many steps were put in notes.
    static int collapseEarlierSteps(final List<ChatMessage> conversation, final int keepSteps) {
        final List<Integer> steps = stepIndexes(conversation);
        if (steps.size() <= keepSteps) {
            return 0;
        }
        final Set<Integer> collapse = new HashSet<>(steps.subList(0, steps.size() - keepSteps));
        final List<ChatMessage> result = new ArrayList<>();
        StringBuilder note = null;
        int collapsed = 0;
        int i = 0;
        while (i < conversation.size()) {
            final ChatMessage message = conversation.get(i);
            if (!collapse.contains(i)) {
                if (note != null) {
                    result.add(UserMessage.from(note.toString().stripTrailing()));
                    note = null;
                }
                result.add(message);
                i++;
                continue;
            }
            if (note == null) {
                note = new StringBuilder(COLLAPSED_STEPS_NOTE).append('\n');
            }
            final AiMessage step = (AiMessage) message;
            if (NullSafe.isNonBlankString(step.text())) {
                note.append("- You said: ").append(oneLine(step.text(), COLLAPSED_LINE_CHARS)).append('\n');
            }
            final Map<String, String> results = new HashMap<>();
            i++;
            while (i < conversation.size() && conversation.get(i) instanceof final ToolExecutionResultMessage r) {
                results.put(r.id(), r.text());
                i++;
            }
            for (final ToolExecutionRequest request : step.toolExecutionRequests()) {
                note.append("- ").append(request.name()).append('(')
                        .append(oneLine(request.arguments(), COLLAPSED_LINE_CHARS)).append(") → ")
                        .append(oneLine(results.getOrDefault(request.id(), "no result"), COLLAPSED_LINE_CHARS))
                        .append('\n');
            }
            collapsed++;
        }
        if (note != null) {
            result.add(UserMessage.from(note.toString().stripTrailing()));
        }
        conversation.clear();
        conversation.addAll(result);
        return collapsed;
    }

    /// @param conversation The conversation.
    /// @return Where the model's steps (its messages with tool calls) are in the conversation, in order.
    static List<Integer> stepIndexes(final List<ChatMessage> conversation) {
        final List<Integer> steps = new ArrayList<>();
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i) instanceof final AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                steps.add(i);
            }
        }
        return steps;
    }

    /// @param text     Text, or null.
    /// @param maxChars The most characters to give.
    /// @return The text on one line, its white space collapsed, cut to `maxChars`.
    static String oneLine(final String text, final int maxChars) {
        return truncate(NullSafe.string(text).replaceAll("\\s+", " ").strip(), maxChars);
    }

    /// Replaces the text of the tool results in the conversation, except the latest ones, with a note saying
    /// how to read them again. Short results are left as they are.
    ///
    /// @param conversation   The conversation, changed in place.
    /// @param keep           How many of the latest tool results to leave as they are.
    /// @param spareReference True to leave the results read for reference as they are (see
    ///                       [ReferenceResults#isReference]).
    /// @return How many results were shortened.
    static int shortenToolResults(final List<ChatMessage> conversation,
                                  final int keep,
                                  final boolean spareReference) {
        final List<Integer> resultIndexes = new ArrayList<>();
        for (int i = 0; i < conversation.size(); i++) {
            if (conversation.get(i) instanceof ToolExecutionResultMessage) {
                resultIndexes.add(i);
            }
        }
        int shortened = 0;
        for (int i = 0; i < resultIndexes.size() - keep; i++) {
            final int index = resultIndexes.get(i);
            final ToolExecutionResultMessage result = (ToolExecutionResultMessage) conversation.get(index);
            if (NullSafe.string(result.text()).length() > SHORTENED_MIN_CHARS
                && !(spareReference && ReferenceResults.isReference(result))) {
                conversation.set(index, ToolExecutionResultMessage.from(result.id(), result.toolName(),
                        SHORTENED_RESULT));
                shortened++;
            }
        }
        return shortened;
    }

    private static String truncate(final String text, final int limit) {
        if (text == null || text.length() <= limit) {
            return text;
        }
        return text.substring(0, Math.max(0, limit - 1)).strip() + "…";
    }
}
