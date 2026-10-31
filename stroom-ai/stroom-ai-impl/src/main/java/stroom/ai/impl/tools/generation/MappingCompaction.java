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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// A mapping (as JSON) made smaller to send, meaning the same, and messages made fewer, to shrink a draft's reply.
public final class MappingCompaction {

    private static final Set<String> PLAIN_DATA_KEYS = Set.of("path", "field", "data_name");
    private static final Set<String> PLAIN_EXTRACT_KEYS = Set.of("field", "regex", "names");
    private static final Pattern RULE = Pattern.compile("^\\[([^\\]]+)\\] (.*)$", Pattern.DOTALL);
    private static final Pattern LIST = Pattern.compile("\\[('[^\\]]*')\\]");
    private static final Pattern QUOTED = Pattern.compile("'([^']*)'");

    private MappingCompaction() {
        // Static only.
    }

    /// The mapping with each rule's plain Data entries (a field as Data of its action element, named after itself)
    /// folded into its data list: the same mapping, a third smaller, so a draft fits the client's reply inline.
    ///
    /// @param mapping A mapping's JSON.
    /// @return The mapping's JSON with the data lists.
    public static Map<String, Object> compactRules(final Map<String, Object> mapping) {
        final Map<String, Object> out = new LinkedHashMap<>(mapping);
        final List<Object> events = new ArrayList<>();
        out.put("events", events);
        for (final Map<String, Object> given : JsonValues.maps(mapping.get("events"))) {
            Map<String, Object> rule = given;
            final List<Map<String, Object>> fields = JsonValues.maps(rule.get("fields"));
            final List<String> paths = fields.stream()
                    .map(field -> text(field.get("path")))
                    .filter(path -> !path.endsWith("/Data"))
                    .toList();
            final String home = Objects.requireNonNullElse(MappingFacts.actionHome(paths), "");
            final List<Map<String, Object>> plain = new ArrayList<>();
            for (final Map<String, Object> field : fields) {
                final String name = field.get("field") instanceof final String text
                        ? text
                        : null;
                if (!home.isEmpty() && !home.endsWith("/Unknown") && (home + "/Data").equals(field.get("path"))
                    && name != null && !name.isEmpty() && name.equals(field.get("data_name"))
                    && PLAIN_DATA_KEYS.containsAll(field.keySet())) {
                    plain.add(field);
                }
            }
            if (!plain.isEmpty()) {
                rule = new LinkedHashMap<>(rule);
                rule.put("fields", fields.stream().filter(field -> !plain.contains(field)).toList());
                final List<Object> data = new ArrayList<>(JsonValues.listOrEmpty(given.get("data")));
                plain.forEach(field -> data.add(field.get("field")));
                rule.put("data", data);
            }
            events.add(rule);
        }
        return out;
    }

    /// The mapping with its extract entries that read a key's value as a pairs entry would ([Pairs]) folded into
    /// pairs entries, one a text field and style: the same mapping, its extract a fraction of the size (seen in
    /// testing: 60 such entries in a Fortigate draft). Entries reading another extraction's name, and any other,
    /// stay as they are.
    ///
    /// @param mapping A mapping's JSON.
    /// @return The mapping's JSON with pairs entries, or the mapping itself if none of its entries fold.
    public static Map<String, Object> compactPairs(final Map<String, Object> mapping) {
        final Set<Object> derived = new LinkedHashSet<>();
        JsonValues.maps(mapping.get("extract")).forEach(ex -> derived.addAll(JsonValues.listOrEmpty(ex.get("names"))));
        final Map<List<String>, Map<String, Object>> pairs = new LinkedHashMap<>();
        final List<Object> rest = new ArrayList<>();
        for (final Map<String, Object> ex : JsonValues.maps(mapping.get("extract"))) {
            final String field = ex.get("field") instanceof final String text && !text.isEmpty()
                    ? text
                    : null;
            final Optional<Pairs.StyleAndKey> found = PLAIN_EXTRACT_KEYS.containsAll(ex.keySet()) && field != null
                    ? Pairs.pairOf(text(ex.get("regex")))
                    : Optional.empty();
            final List<Object> allNames = JsonValues.listOrEmpty(ex.get("names"));
            final List<String> names = allNames.stream()
                    .filter(name -> name instanceof final String text && !text.isEmpty())
                    .map(String.class::cast)
                    .toList();
            if (found.isEmpty() || allNames.size() != 1 || names.isEmpty() || derived.contains(field)) {
                rest.add(ex);
                continue;
            }
            final String style = found.get().style();
            final String key = found.get().key();
            final Map<String, Object> entry = pairs.computeIfAbsent(List.of(field, style), ignored -> {
                final Map<String, Object> made = new LinkedHashMap<>();
                made.put("field", field);
                made.put("style", style);
                made.put("keys", new ArrayList<>());
                return made;
            });
            @SuppressWarnings("unchecked") final List<Object> keys = (List<Object>) entry.get("keys");
            if (keys.contains(key)) {        // the same key twice (two names): left as written
                rest.add(ex);
                continue;
            }
            keys.add(key);
            if (!names.getFirst().equals(Pairs.keyName(key))) {
                @SuppressWarnings("unchecked") final Map<String, Object> named = (Map<String, Object>) entry
                        .computeIfAbsent("names", ignored -> new LinkedHashMap<String, Object>());
                named.put(key, names.getFirst());
            }
        }
        if (pairs.isEmpty()) {
            return mapping;
        }
        final Map<String, Object> out = new LinkedHashMap<>();
        mapping.forEach((key, value) -> {
            if (!"extract".equals(key)) {
                out.put(key, value);
            }
        });
        final List<Object> all = new ArrayList<>(JsonValues.listOrEmpty(mapping.get("pairs")));
        all.addAll(pairs.values());
        out.put("pairs", all);
        if (!rest.isEmpty()) {
            out.put("extract", rest);
        }
        return out;
    }

    /// Messages the same but for their rule (and the list they name) as one, `[rule1, rule2] ...` with the lists
    /// merged: six rules' "required [...] are left out" warnings were a quarter of a draft's reply.
    ///
    /// @param messages Problems or warnings.
    /// @return Them grouped, in order of each group's first.
    public static List<String> grouped(final List<String> messages) {
        final Map<String, List<String[]>> groups = new LinkedHashMap<>();
        for (final String message : messages) {
            final Matcher found = RULE.matcher(message);
            final String rule;
            final String body;
            if (found.find()) {
                rule = found.group(1);
                body = found.group(2);
            } else {
                rule = "";
                body = message;
            }
            final String key = rule.isEmpty()
                    ? message
                    : LIST.matcher(body).replaceAll(Matcher.quoteReplacement("[…]"));
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new String[]{rule, body});
        }
        final List<String> out = new ArrayList<>();
        for (final List<String[]> members : groups.values()) {
            if (members.size() == 1 || members.getFirst()[0].isEmpty()) {
                members.forEach(member -> out.add(member[0].isEmpty()
                        ? member[1]
                        : "[" + member[0] + "] " + member[1]));
                continue;
            }
            final Set<String> items = new LinkedHashSet<>();
            for (final String[] member : members) {
                final Matcher list = LIST.matcher(member[1]);
                if (list.find()) {
                    final Matcher quoted = QUOTED.matcher(list.group(1));
                    while (quoted.find()) {
                        items.add(quoted.group(1));
                    }
                }
            }
            final String body = LIST.matcher(members.getFirst()[1])
                    .replaceFirst(Matcher.quoteReplacement(ToolText.list(List.copyOf(items))));
            out.add("[" + String.join(", ", members.stream().map(member -> member[0]).toList()) + "] " + body);
        }
        return out;
    }

    private static String text(final Object value) {
        return value instanceof final String text
                ? text
                : "";
    }
}
