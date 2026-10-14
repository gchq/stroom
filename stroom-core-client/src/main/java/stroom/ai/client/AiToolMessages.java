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

package stroom.ai.client;

import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

/**
 * Reads the JSON held by TOOL_CALL and TOOL_RESULT chat messages, for display.
 */
final class AiToolMessages {

    private AiToolMessages() {
        // Static only.
    }

    /**
     * @param json The message's JSON.
     * @param name A field name.
     * @return The field's value as a string, or null if it is missing or the JSON cannot be read.
     */
    static String getString(final String json, final String name) {
        final JSONValue value = getValue(json, name);
        if (value == null) {
            return null;
        }
        final JSONString string = value.isString();
        return string != null
                ? string.stringValue()
                : value.toString();
    }

    /**
     * @param json The message's JSON.
     * @param name A field name.
     * @return The field's value if it is a boolean, otherwise false.
     */
    static boolean getBoolean(final String json, final String name) {
        final JSONValue value = getValue(json, name);
        final JSONBoolean bool = value != null
                ? value.isBoolean()
                : null;
        return bool != null && bool.booleanValue();
    }

    private static JSONValue getValue(final String json, final String name) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            final JSONObject object = JSONParser.parseStrict(json).isObject();
            return object != null
                    ? object.get(name)
                    : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * @return Markdown showing a tool call's arguments.
     */
    static String callMarkdown(final String json) {
        final String arguments = getString(json, "arguments");
        return "Arguments:\n\n~~~json\n" + (arguments != null
                ? arguments
                : "{}") + "\n~~~";
    }

    /**
     * @return Markdown showing a tool call's result.
     */
    static String resultMarkdown(final String json) {
        final StringBuilder sb = new StringBuilder();
        final String path = getString(json, "path");
        if (path != null) {
            sb.append("The full result was saved to the workspace file `").append(path).append("`.\n\n");
        }
        sb.append("~~~\n").append(getString(json, "text") != null
                ? getString(json, "text")
                : "").append("\n~~~");
        return sb.toString();
    }

    /**
     * @return A one-line description of a compaction of the chat.
     */
    static String compactionTitle(final String json) {
        final String count = getString(json, "messageCount");
        final String before = getString(json, "tokensBefore");
        final String after = getString(json, "tokensAfter");
        final String title = "Earlier messages compacted" + (count != null
                ? ": " + count + " messages"
                : "");
        return getBoolean(json, "stale")
                ? title + " (no longer used, as a message it covered has changed)"
                : before != null && after != null
                        ? title + ", about " + kilo(before) + " → " + kilo(after) + " tokens"
                        : title;
    }

    /**
     * @return Markdown showing what a compaction of the chat did.
     */
    static String compactionMarkdown(final String json) {
        final String summary = getString(json, "summary");
        final StringBuilder sb = new StringBuilder("The model now sees earlier turns as short digests");
        if (summary != null) {
            sb.append(", and the oldest as this summary:\n\n").append(summary);
        } else {
            sb.append(". Everything is still stored, and the model can read any earlier turn again.");
        }
        return sb.toString();
    }

    private static String kilo(final String tokens) {
        try {
            final long value = Long.parseLong(tokens);
            return value >= 1000
                    ? (value / 1000) + "k"
                    : String.valueOf(value);
        } catch (final NumberFormatException e) {
            return tokens;
        }
    }
}
