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

/// Decides when a tool's output is too large to give the model in full, and what to give it instead: a short stub
/// saying how large the output is, where it has been saved in the chat's workspace, and how it starts. The whole
/// output stays in the workspace, so it is not lost and can be read in parts.
final class ToolOutputs {

    /// Outputs longer than this are saved to the workspace and the model is given a stub.
    static final int MAX_INLINE_CHARS = 4_000;
    /// The most lines of a large output shown in its stub.
    static final int PREVIEW_LINES = 30;
    /// The most characters of a large output shown in its stub.
    static final int PREVIEW_CHARS = 1_500;
    /// The most field names of a large JSON object listed in its stub.
    static final int MAX_LISTED_FIELDS = 30;
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
    /// @return True if it is too large to give the model in full.
    static boolean isLarge(final String output) {
        return output != null && output.length() > MAX_INLINE_CHARS;
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
        return "The result was too large to show in full (" + output.length() + " characters, " + lineCount
               + " lines), so it has been saved to the workspace file " + path + ". It starts with these "
               + shown + " line(s):\n\n"
               + preview.toString().stripTrailing()
               + "\n\n" + jsonShape(output)
               + "If you need more of it, read it from the workspace file with the workspace tools, if you "
               + "have them.";
    }

    // Says what JSON holds at its top level, so the parts needed can be picked out without reading it all.
    private static String jsonShape(final String output) {
        final JsonNode json = readJson(output);
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
