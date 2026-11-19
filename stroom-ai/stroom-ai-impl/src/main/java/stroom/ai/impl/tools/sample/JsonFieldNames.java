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

/// The one spelling of a JSON field's name: its keys from the top, dotted (`event.app`); a key that itself holds a
/// dot or a slash (an Elastic-style flat `"log.level"`, a label `app.kubernetes.io/name`), or starts with `[`, is
/// written in brackets and quotes, a quote in it doubled (`['log.level']`, `labels['app.kubernetes.io/name']`), so
/// the flat key is never misread as the nested `log.level`, nor its slash as a path into held XML.
public final class JsonFieldNames {

    private static final String OPEN = "['";
    private static final String CLOSE = "']";

    private JsonFieldNames() {
        // Static only.
    }

    /// @param parent A field's name, or "" for the top.
    /// @param key    A key of the object the field holds.
    /// @return The name of the key's field.
    public static String child(final String parent, final String key) {
        final String written = quoted(key)
                ? OPEN + key.replace("'", "''") + CLOSE
                : key;
        if (parent == null || parent.isEmpty()) {
            return written;
        }
        return written.startsWith(OPEN)
                ? parent + written
                : parent + "." + written;
    }

    /// @param parent A field's name, or "" for the top.
    /// @param name   A name of a field below it, spelt from there (as [#child(String, String)] spells it).
    /// @return The field's name from the top.
    public static String join(final String parent, final String name) {
        if (parent == null || parent.isEmpty()) {
            return name;
        }
        if (name == null || name.isEmpty()) {
            return parent;
        }
        return name.startsWith(OPEN)
                ? parent + name
                : parent + "." + name;
    }

    /// @param keys A field's keys from the top.
    /// @return Its name.
    public static String of(final List<String> keys) {
        String name = "";
        for (final String key : keys) {
            name = child(name, key);
        }
        return name;
    }

    /// @param name A field's name.
    /// @return Its keys from the top: split at its dots, a bracketed key read whole, its doubled quotes made one.
    public static List<String> keys(final String name) {
        final List<String> keys = new ArrayList<>();
        final StringBuilder key = new StringBuilder();
        int at = 0;
        while (at < name.length()) {
            if (name.startsWith(OPEN, at) && key.isEmpty()) {
                final int end = closing(name, at + OPEN.length());
                if (end >= 0) {
                    keys.add(name.substring(at + OPEN.length(), end).replace("''", "'"));
                    at = end + CLOSE.length();
                    if (at < name.length() && name.charAt(at) == '.') {
                        at++;
                    }
                    continue;
                }
            }
            final char c = name.charAt(at);
            if (c == '.') {
                keys.add(key.toString());
                key.setLength(0);
            } else if (name.startsWith(OPEN, at) && closing(name, at + OPEN.length()) >= 0) {
                keys.add(key.toString());
                key.setLength(0);
                continue;
            } else {
                key.append(c);
            }
            at++;
        }
        if (!key.isEmpty() || keys.isEmpty() || name.endsWith(".")) {
            keys.add(key.toString());
        }
        return keys;
    }

    /// @param parent A field's name.
    /// @param name   Another field's name.
    /// @return The other's name below the field (`app` of `event.app` below `event`, `['a.b']` of `event['a.b']`),
    /// or null when it isn't below it.
    public static String below(final String parent, final String name) {
        if (parent == null || parent.isEmpty() || name == null || !name.startsWith(parent)
            || name.length() == parent.length()) {
            return null;
        }
        final String rest = name.substring(parent.length());
        if (rest.startsWith(".") && rest.length() > 1) {
            return rest.substring(1);
        }
        return rest.startsWith(OPEN)
                ? rest
                : null;
    }

    // Whether a key is written in brackets: one holding a dot or slash, or that starts with a bracket.
    private static boolean quoted(final String key) {
        return key.indexOf('.') >= 0 || key.indexOf('/') >= 0 || key.startsWith("[");
    }

    // Where a bracketed key's closing quote and bracket are, a doubled quote passed over; -1 when there's none.
    private static int closing(final String name, final int from) {
        int at = from;
        while (at < name.length()) {
            if (name.charAt(at) == '\'') {
                if (name.startsWith("''", at)) {
                    at += 2;
                    continue;
                }
                return name.startsWith(CLOSE, at)
                        ? at
                        : -1;
            }
            at++;
        }
        return -1;
    }
}
