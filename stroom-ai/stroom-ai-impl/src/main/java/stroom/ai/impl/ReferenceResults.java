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

import stroom.util.shared.NullSafe;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// The tool results the model reads for reference, to work from for the rest of a question (e.g. the schema's
/// elements, a guide). When a run makes its conversation smaller (see [McpAgentChat#shrink]), they are kept as they
/// are for longer than other results, and a summary of earlier steps keeps them word for word, after what the
/// model wrote. Seen: an agent read an event type's elements, the result was shortened a few steps later, and it
/// mapped fields to another type it remembered better.
final class ReferenceResults {

    /// The tools whose results are read for reference.
    static final Set<String> TOOLS = Set.of(
            "read_document",
            "get_instructions",
            "get_field_conventions",
            "describe_template",
            "describe_feed",
            "describe_data_source");
    /// The most characters a result can have to be kept for reference: a larger one (e.g. a whole XSLT) is
    /// shortened as other results are.
    static final int MAX_CHARS = 6_000;
    /// The most characters of results a summary of earlier steps keeps for reference, the latest first.
    static final int KEPT_MAX_CHARS = 16_000;
    static final String KEPT_INTRO = "[What you read for reference in those steps, kept as it was, the latest "
                                     + "first:]";
    private static final String SEPARATOR = "\n\n---\n\n";

    private ReferenceResults() {
        // Static only.
    }

    /// @param result A tool's result.
    /// @return True if it is one the model reads for reference: from one of the [#TOOLS], not too large, and not
    /// already shortened.
    static boolean isReference(final ToolExecutionResultMessage result) {
        final String text = NullSafe.string(result.text());
        return TOOLS.contains(result.toolName())
               && text.length() <= MAX_CHARS
               && !text.equals(McpAgentChat.SHORTENED_RESULT);
    }

    /// @param conversation The conversation.
    /// @param keptFrom     The index of the first step a summary leaves as it is.
    /// @return The results before `keptFrom` read for reference, each with the call it answers, the latest first,
    /// then those an earlier summary kept; within [#KEPT_MAX_CHARS], the earliest left out.
    static List<String> before(final List<ChatMessage> conversation, final int keptFrom) {
        final Map<String, ToolExecutionRequest> calls = new HashMap<>();
        final List<String> found = new ArrayList<>();
        final List<String> keptBefore = new ArrayList<>();
        for (int i = 0; i < keptFrom; i++) {
            final ChatMessage message = conversation.get(i);
            if (message instanceof final AiMessage step && step.hasToolExecutionRequests()) {
                step.toolExecutionRequests().forEach(request -> calls.put(request.id(), request));
            } else if (message instanceof final ToolExecutionResultMessage result && isReference(result)) {
                final ToolExecutionRequest call = calls.get(result.id());
                final String arguments = call == null
                        ? ""
                        : McpAgentChat.oneLine(call.arguments(), McpAgentChat.COLLAPSED_LINE_CHARS);
                found.add("- " + result.toolName() + "(" + arguments + "):\n" + result.text().strip());
            } else if (McpAgentChat.isNote(message, McpAgentChat.TURN_SUMMARY_NOTE)) {
                keptBefore.addAll(keptIn(((UserMessage) message).singleText()));
            }
        }
        final List<String> all = new ArrayList<>(found.reversed());
        keptBefore.stream()
                .filter(entry -> !all.contains(entry))
                .forEach(all::add);
        final List<String> kept = new ArrayList<>();
        int length = 0;
        for (final String entry : all) {
            if (length + entry.length() > KEPT_MAX_CHARS) {
                break;
            }
            kept.add(entry);
            length += entry.length() + SEPARATOR.length();
        }
        return kept;
    }

    /// @param kept Results to keep, as [#before] gives them.
    /// @return What a summary's note ends with to keep them, or an empty string if there are none.
    static String toKeep(final List<String> kept) {
        return kept.isEmpty()
                ? ""
                : "\n\n" + KEPT_INTRO + "\n" + String.join(SEPARATOR, kept);
    }

    /// @param summaryNote What a summary's note says.
    /// @return It without the results it keeps for reference.
    static String withoutKept(final String summaryNote) {
        final int kept = summaryNote.indexOf(KEPT_INTRO);
        return (kept >= 0
                ? summaryNote.substring(0, kept)
                : summaryNote).strip();
    }

    // The results kept for reference in a summary's note, the latest first.
    private static List<String> keptIn(final String summaryNote) {
        final int kept = summaryNote.indexOf(KEPT_INTRO);
        if (kept < 0) {
            return List.of();
        }
        return Arrays.stream(summaryNote.substring(kept + KEPT_INTRO.length()).split(SEPARATOR))
                .map(String::strip)
                .filter(NullSafe::isNonBlankString)
                .toList();
    }
}
