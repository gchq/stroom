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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Decides when a tool's output is too large to give the model in full, and what to give it instead: a short stub
/// saying how large the output is, where it has been saved in the chat's workspace, and how it starts. The whole
/// output stays in the workspace, so it is not lost and can be read in parts.
///
/// A result the user must be shown exactly as it is before the call is made again (an MCP server's
/// `needs_review`, `needs_confirmation` or `needs_approval` reply, see [#mustShowInFull(String)]) is given to the
/// model in full up to [#MAX_SHOWN_IN_FULL_CHARS]: a stub would leave the model nothing to show. Above that it is
/// stubbed too, and the stub says plainly what the user must be shown and how to get it.
///
/// A JSON result's `next` and `hint`, which say what to do next and which a tool often puts last, are repeated in
/// its stub: the preview, from its start, would not reach them.
final class ToolOutputs {

    /// Outputs longer than this are saved to the workspace and the model is given a stub.
    static final int MAX_INLINE_CHARS = 4_000;
    /// The most lines of a large output shown in its stub.
    static final int PREVIEW_LINES = 30;
    /// The most characters of a large output shown in its stub.
    static final int PREVIEW_CHARS = 1_500;
    /// The most field names of a large JSON object listed in its stub.
    static final int MAX_LISTED_FIELDS = 30;
    /// Results the user must be shown in full (see [#mustShowInFull(String)]) longer than this are stubbed too.
    static final int MAX_SHOWN_IN_FULL_CHARS = 64_000;
    /// The field of an MCP server's result that says what it is, e.g. `needs_review`.
    static final String STATUS_FIELD = "status";
    /// The statuses of results whose content the user must be shown, exactly as it is, before the agent calls
    /// the tool again with the result's id: a review (e.g. an index template in `dev_tools`, records in
    /// `show_user`, a layout in `layout`), or a confirmation or approval (its `summary` and `details`).
    static final Set<String> SHOWN_TO_USER_STATUSES = Set.of("needs_review", "needs_confirmation", "needs_approval");
    /// The fields of such a result that are for the agent, not to be shown to the user, in the order looked for.
    static final List<String> ID_FIELDS = List.of("review_id", "confirmation_id", "approval_id");
    static final String HINT_FIELD = "hint";
    /// The field of a result that gives the call to make next.
    static final String NEXT_FIELD = "next";
    /// The most characters of a result's hint repeated in its stub.
    static final int MAX_HINT_CHARS = 2_000;
    /// The most characters of a result's `next` repeated in its stub.
    static final int MAX_NEXT_CHARS = 1_500;
    // Numbers are kept as they were written.
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES)
            .build();

    private ToolOutputs() {
        // Static only.
    }

    /// @param output A tool's output.
    /// @return True if it is too large to give the model in full: longer than [#MAX_INLINE_CHARS], or, for a
    /// result the user must be shown in full ([#mustShowInFull(String)]), longer than
    /// [#MAX_SHOWN_IN_FULL_CHARS].
    static boolean isLarge(final String output) {
        if (output == null || output.length() <= MAX_INLINE_CHARS) {
            return false;
        }
        return output.length() > MAX_SHOWN_IN_FULL_CHARS || !mustShowInFull(output);
    }

    /// @param output A tool's output.
    /// @return True if it is a result whose content the user must be shown exactly as it is before the tool is
    /// called again with the result's id: a JSON object whose `status` is one of [#SHOWN_TO_USER_STATUSES]. The
    /// id is bound to exactly what was shown, so a preview of it is no use.
    static boolean mustShowInFull(final String output) {
        if (output == null) {
            return false;
        }
        final JsonNode json = readJson(output);
        return json != null && json.isObject() && isShownToUser(json);
    }

    /// @param turnId        The turn the call was made in.
    /// @param callMessageId The id of the TOOL_CALL message.
    /// @return The workspace path to save the call's output at.
    static String outputPath(final int turnId, final int callMessageId) {
        return "/outputs/turn-" + turnId + "/call-" + callMessageId + ".txt";
    }

    /// @param output A large tool output.
    /// @return The output as it is saved to the workspace. JSON, which tools often give on one line, is indented
    /// with a field or item per line, so that it can be read and searched in parts, as reading or searching a
    /// file is done by line.
    static String forSaving(final String output) {
        final JsonNode json = readJson(output);
        if (json == null) {
            return output;
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (final JsonProcessingException e) {
            return output;
        }
    }

    /// @param output The whole output, as saved (see [#forSaving(String)]).
    /// @param path   Where it has been saved in the workspace.
    /// @return What to give the model instead of the whole output.
    static String stub(final String output, final String path) {
        final String[] lines = output.split("\n", -1);
        final int lineCount = output.endsWith("\n")
                ? lines.length - 1
                : lines.length;
        final StringBuilder preview = new StringBuilder();
        int shown = 0;
        for (int i = 0; i < lineCount && shown < PREVIEW_LINES; i++) {
            if (preview.length() + lines[i].length() > PREVIEW_CHARS) {
                if (shown == 0) {
                    preview.append(lines[i], 0, PREVIEW_CHARS).append("…");
                    shown++;
                }
                break;
            }
            preview.append(lines[i]).append('\n');
            shown++;
        }
        final JsonNode json = readJson(output);
        return "The result was too large to show in full (" + output.length() + " characters, " + lineCount
               + " lines), so it has been saved to the workspace file " + path + ". "
               + (json != null && json.isObject() && isShownToUser(json)
                ? shownToUserNote(json, path)
                : "")
               + (json != null && json.isObject()
                ? nextSteps(json)
                : "")
               + "It starts with these " + shown + " line(s):\n\n"
               + preview.toString().stripTrailing()
               + "\n\n" + jsonShape(json)
               + "If you need more of it, read it from the workspace file with the workspace tools, if you "
               + "have them.";
    }

    private static boolean isShownToUser(final JsonNode json) {
        final JsonNode status = json.get(STATUS_FIELD);
        return status != null && status.isTextual() && SHOWN_TO_USER_STATUSES.contains(status.asText());
    }

    // Says that the user must be shown the result's content, what of it, and how to get it, so that the stub is not
    // taken for the content, or the content written out from the preview.
    private static String shownToUserNote(final JsonNode json, final String path) {
        final String status = json.get(STATUS_FIELD).asText();
        final List<String> shownFields = new ArrayList<>();
        json.fieldNames().forEachRemaining(field -> {
            if (!STATUS_FIELD.equals(field) && !HINT_FIELD.equals(field) && !ID_FIELDS.contains(field)) {
                shownFields.add(field);
            }
        });
        final String example = shownFields.isEmpty()
                ? "."
                : "." + shownFields.getFirst();
        final StringBuilder sb = new StringBuilder()
                .append("IMPORTANT: this is a ").append(status).append(" result. The user must be shown ")
                .append(shownFields.isEmpty()
                        ? "its content"
                        : "its " + String.join(", ", shownFields))
                .append(" exactly as it is before you go on, and it is too large (over ")
                .append(MAX_SHOWN_IN_FULL_CHARS)
                .append(" characters) to give you here, so do not write it out from the lines below. Get it from ")
                .append(path)
                .append(" with the jq tool (e.g. query `").append(example)
                .append("` with raw true and out set to a file in /outputs/), read that file in parts with the ")
                .append("read_file tool, and show it to the user exactly as it is; if it is too long to show in the ")
                .append("chat, tell the user plainly that it is to be reviewed and is in the workspace file ")
                .append(path)
                .append(", and ask them to read it there. ");
        for (final String idField : ID_FIELDS) {
            final JsonNode id = json.get(idField);
            if (id != null && id.isTextual()) {
                sb.append("Only once they have seen it, call again as its hint says, with its ").append(idField)
                        .append(" '").append(id.asText()).append("'. ");
                break;
            }
        }
        return sb.append("\n\n").toString();
    }

    // The result's next call and hint, which the preview may not reach (seen in Ask Stroom AI: a large draft's
    // next, its last field, was cut from the stub, and the agent asked the user what to do instead of doing it).
    private static String nextSteps(final JsonNode json) {
        final JsonNode next = json.get(NEXT_FIELD);
        final JsonNode hint = json.get(HINT_FIELD);
        final boolean hasNext = next != null && !next.isNull();
        final boolean hasHint = hint != null && hint.isTextual();
        if (!hasNext && !hasHint) {
            return "";
        }
        final StringBuilder sb = new StringBuilder("The result says what to do next, which the lines below may not "
                                                   + "reach. ");
        if (hasNext) {
            sb.append("Its next: ").append(capped(next.isTextual()
                    ? next.asText()
                    : next.toString(), MAX_NEXT_CHARS)).append(' ');
        }
        if (hasHint) {
            sb.append("Its hint: ").append(capped(hint.asText(), MAX_HINT_CHARS)).append(' ');
        }
        return sb.append("\n\n").toString();
    }

    private static String capped(final String text, final int maxChars) {
        return text.length() > maxChars
                ? text.substring(0, maxChars) + "…"
                : text;
    }

    // Says what JSON holds at its top level, so the parts needed can be picked out without reading it all.
    private static String jsonShape(final JsonNode json) {
        if (json == null) {
            return "";
        }
        if (json.isArray()) {
            return "It is a JSON array of " + json.size() + " items. Pick out the parts you need with the jq "
                   + "tool, e.g. `.[0]`, or `length`.\n\n";
        }
        final List<String> fields = new ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        final String listed = String.join(", ", fields.subList(0, Math.min(fields.size(), MAX_LISTED_FIELDS)))
                              + (fields.size() > MAX_LISTED_FIELDS
                ? " and " + (fields.size() - MAX_LISTED_FIELDS) + " more"
                : "");
        final String example = fields.isEmpty()
                ? "keys"
                : "." + fields.getLast();
        return "It is a JSON object with the fields " + listed + ". Pick out the parts you need with the jq tool, "
               + "e.g. `" + example + "`.\n\n";
    }

    // The output read as a JSON object or array, or null if it is something else.
    private static JsonNode readJson(final String output) {
        final String trimmed = output.strip();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return null;
        }
        try {
            final JsonNode json = MAPPER.readTree(trimmed);
            return json != null && json.isContainerNode()
                    ? json
                    : null;
        } catch (final JsonProcessingException e) {
            return null;
        }
    }
}
