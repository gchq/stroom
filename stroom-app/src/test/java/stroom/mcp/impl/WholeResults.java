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

package stroom.mcp.impl;

import stroom.util.json.JsonUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/// A tool's result given in outline to a client outside Stroom (too large to give whole: `whole_result` says where
/// it is saved), read back whole from the workspace as an agent reads it: with jq, a part at a time, a list's entries
/// one a line (a read cuts a line past 2,000 characters, so an entry cut is read again a part at a time).
public final class WholeResults {

    private static final String WHOLE_RESULT = "whole_result";
    // How a workspace read marks a line it cut.
    private static final String CUT = " more characters)";
    private static final int SLICE = 100;
    private static final Pattern MORE = Pattern.compile("\\(\\d+ results in all; \\d+ not shown\\..*\\)");

    private WholeResults() {
        // Static only.
    }

    /// @param result A tool's result.
    /// @return True if it was given in outline, saved whole to the workspace.
    public static boolean outlined(final Map<String, Object> result) {
        return result.get(WHOLE_RESULT) instanceof Map<?, ?>;
    }

    /// @param client The client the result was given to.
    /// @param result A tool's result.
    /// @return The result whole: as it is unless given in outline, else its outlined and cut parts read back from the
    ///         saved file (a text saved as its lines joined again).
    public static Map<String, Object> of(final McpTestClient client, final Map<String, Object> result) {
        if (!(result.get(WHOLE_RESULT) instanceof final Map<?, ?> note)) {
            return result;
        }
        final String path = String.valueOf(note.get("saved"));
        final List<Object> parts = new ArrayList<>();
        parts.addAll(listOf(note.get("outlined")));
        parts.addAll(listOf(note.get("cut")));
        final List<Object> textLines = listOf(note.get("text_lines"));
        final Map<String, Object> whole = new LinkedHashMap<>(result);
        whole.remove(WHOLE_RESULT);
        for (final Object part : parts) {
            final Object value = read(client, path, ".[\"" + part + "\"]");
            whole.put(String.valueOf(part), textLines.contains(part) && value instanceof final List<?> lines
                    ? String.join("\n", lines.stream().map(String::valueOf).toList())
                    : value);
        }
        return whole;
    }

    /// @param client An MCP client.
    /// @param path   A JSON file in the workspace.
    /// @param at     A jq path in it.
    /// @return The value there, read as an agent reads it: a list's entries one a line (in slices), a map a key at
    ///         a time where it is too long for a line.
    public static Object read(final McpTestClient client, final String path, final String at) {
        final String type = jq(client, path, at + " | type").strip();
        if ("\"array\"".equals(type)) {
            final int length = Integer.parseInt(jq(client, path, at + " | length").strip());
            final List<Object> entries = new ArrayList<>();
            int from = 0;
            while (from < length) {
                final String text = jq(client, path, at + "[" + from + ":" + Math.min(length, from + SLICE) + "][]");
                // A read gives so many characters, then says how many more there are: read on from there.
                final List<String> lines = text.lines()
                        .filter(line -> !MORE.matcher(line).matches())
                        .toList();
                if (lines.isEmpty()) {
                    throw new AssertionError("Nothing read at " + at + "[" + from + "] in " + path + ": " + text);
                }
                for (int i = 0; i < lines.size(); i++) {
                    entries.add(lines.get(i).endsWith(CUT)
                            ? read(client, path, at + "[" + (from + i) + "]")
                            : parse(lines.get(i), at));
                }
                from += lines.size();
            }
            return entries;
        }
        final String text = jq(client, path, at);
        if (!text.endsWith(CUT)) {
            return parse(text, at);
        }
        if ("\"object\"".equals(type)) {
            final Map<String, Object> map = new LinkedHashMap<>();
            for (final Object key : (List<?>) read(client, path, "(" + at + " | keys)")) {
                map.put(String.valueOf(key), read(client, path, at + "[\"" + key + "\"]"));
            }
            return map;
        }
        throw new AssertionError("A value too long for a line, and not a list or map, at " + at + " in " + path);
    }

    private static Object parse(final String text, final String at) {
        try {
            return JsonUtil.readValue(text, Object.class);
        } catch (final RuntimeException e) {
            throw new AssertionError("Not JSON at " + at + " (" + text.length() + " characters): "
                                     + text.substring(Math.max(0, text.length() - 300)), e);
        }
    }

    private static List<Object> listOf(final Object value) {
        return value instanceof final List<?> list
                ? new ArrayList<>(list)
                : List.of();
    }

    private static String jq(final McpTestClient client, final String path, final String query) {
        return McpTestClient.text(client.call("jq", Map.of("path", path, "query", query)));
    }
}
