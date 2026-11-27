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

package stroom.ai.impl.tools.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// The longest string value in one JSON record, and the field holding it, found as the record's characters go by
/// without the record being held or parsed: what is still said of an event too long to sample, so a value Stroom's
/// JSONParser would cut ([JsonStringValues]) is not lost because its event was left out. A string's length is its
/// decoded length (an escape is one character). Fields are named as [JsonStringValues] names them: dotted keys,
/// `[]` after an array's.
final class LongestJsonString {

    // Keys longer than this aren't kept as a field's name.
    private static final int MOST_KEY_CHARS = 200;
    // Containers nested deeper than this are passed over (their strings still measured).
    private static final int MOST_DEPTH = 64;
    private static final String NO_FIELD = "(value)";

    // Each open container's kind ('{' or '[') and the path of the value it is.
    private final StringBuilder containers = new StringBuilder();
    private final List<String> paths = new ArrayList<>();
    private final StringBuilder key = new StringBuilder();
    private int deeper;
    private boolean inString;
    private boolean escape;
    private int unicodeLeft;
    private boolean stringIsKey;
    private boolean keyTooLong;
    private long length;
    private boolean expectKey;
    private String lastKey;
    private String valueKey;
    private long longest;
    private String field;

    /// Starts again, for the next record.
    void reset() {
        containers.setLength(0);
        paths.clear();
        key.setLength(0);
        deeper = 0;
        inString = false;
        escape = false;
        unicodeLeft = 0;
        stringIsKey = false;
        keyTooLong = false;
        length = 0;
        expectKey = false;
        lastKey = null;
        valueKey = null;
        longest = 0;
        field = null;
    }

    /// @param text More of the record's text.
    void accept(final CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            accept(text.charAt(i));
        }
    }

    /// @param c The record's next character.
    void accept(final char c) {
        if (inString) {
            inString(c);
            return;
        }
        switch (c) {
            case '"' -> {
                inString = true;
                length = 0;
                key.setLength(0);
                keyTooLong = false;
                stringIsKey = inObject() && expectKey;
            }
            case ':' -> {
                if (inObject()) {
                    valueKey = lastKey;
                    expectKey = false;
                }
            }
            case ',' -> {
                if (inObject()) {
                    expectKey = true;
                    valueKey = null;
                }
            }
            case '{', '[' -> open(c);
            case '}', ']' -> close();
            default -> {
                // Numbers, literals and white space say nothing of strings.
            }
        }
    }

    /// @return The decoded length of the longest string value read; 0 if none.
    long longest() {
        return longest;
    }

    /// @return The field holding it, or null if none was read (or it couldn't be told).
    String field() {
        return field;
    }

    private void inString(final char c) {
        if (unicodeLeft > 0) {
            unicodeLeft--;
            return;
        }
        if (escape) {
            escape = false;
            if (c == 'u') {
                unicodeLeft = 4;
            }
            length++;
            appendKey('?');
            return;
        }
        if (c == '\\') {
            escape = true;
            return;
        }
        if (c == '"') {
            inString = false;
            closeString();
            return;
        }
        length++;
        appendKey(c);
    }

    private void appendKey(final char c) {
        if (!stringIsKey) {
            return;
        }
        if (key.length() < MOST_KEY_CHARS) {
            key.append(c);
        } else {
            keyTooLong = true;
        }
    }

    private void closeString() {
        if (stringIsKey) {
            lastKey = keyTooLong
                    ? null
                    : key.toString();
            return;
        }
        if (length > longest) {
            longest = length;
            final String path = valuePath();
            field = path != null && path.isEmpty()
                    ? NO_FIELD
                    : path;
        }
    }

    private void open(final char c) {
        if (containers.length() >= MOST_DEPTH) {
            deeper++;
            return;
        }
        final String path = valuePath();
        containers.append(c);
        paths.add(path);
        expectKey = c == '{';
        valueKey = null;
    }

    private void close() {
        if (deeper > 0) {
            deeper--;
            return;
        }
        if (!containers.isEmpty()) {
            containers.setLength(containers.length() - 1);
            paths.removeLast();
        }
        expectKey = false;
        valueKey = null;
    }

    private boolean inObject() {
        return deeper == 0 && !containers.isEmpty() && containers.charAt(containers.length() - 1) == '{';
    }

    // The path of a value at this point: the record itself is "", its keys named from there.
    private String valuePath() {
        if (containers.isEmpty()) {
            return "";
        }
        final String base = Objects.requireNonNullElse(paths.getLast(), "");
        if (containers.charAt(containers.length() - 1) == '[') {
            return (base.isEmpty()
                    ? NO_FIELD
                    : base) + "[]";
        }
        if (valueKey == null) {
            return base.isEmpty()
                    ? null
                    : base;
        }
        return JsonFieldNames.child(base, valueKey);
    }
}
