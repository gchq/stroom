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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// How the feeds of one build differ, read from each feed's survey (decided by the user, 2026-11-25: their shapes
/// are a union, the differences shown for the user to confirm; nothing merges silently): the format each survey
/// found, the fields some feeds' records have and others' lack, the kinds of event some feeds show and others
/// don't, and the time zones or offsets their records' times are written in.
///
/// Read from what the survey keeps of each kind of event (its signature, and its examples' text): a structured
/// record's signature lists its fields and the values of the fields naming its kind (`fields:a,b | action=x`); a
/// text record's is its message template. Format-level knowledge only: no source's own field names.
final class FeedShapeComparison {

    /// What the survey's signature of a structured record starts with.
    private static final String FIELDS = "fields:";
    // A time of day, then a zone: `Z`, or an offset (+10:00, -0500).
    private static final Pattern ZONED_TIME = Pattern.compile(
            "(?<![\\d:])\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(Z|[+-]\\d{2}:?\\d{2})(?![\\d:])");
    // A time of day with no zone after it.
    private static final Pattern BARE_TIME = Pattern.compile(
            "(?<![\\d:])\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?(?![\\d.,:]|Z|\\s?[+-]\\d{2}:?\\d{2})");
    /// How a feed's times with no zone or offset are said.
    static final String NO_ZONE = "no zone or offset";

    private FeedShapeComparison() {
        // Static only.
    }

    /// @param states Each feed's survey state, by the feed's name (as its survey doc keeps it).
    /// @return How they differ; nothing differs for fewer than two feeds.
    static Comparison compare(final Map<String, Map<String, Object>> states) {
        final Map<String, String> formats = new LinkedHashMap<>();
        final Map<String, Set<String>> fieldsByFeed = new LinkedHashMap<>();
        final Map<String, Set<String>> kindsByFeed = new LinkedHashMap<>();
        final Map<String, Set<String>> zonesByFeed = new LinkedHashMap<>();
        states.forEach((feed, state) -> {
            formats.put(feed, JsonValues.text(state.get("format")));
            final Set<String> fields = new TreeSet<>();
            final Set<String> kinds = new TreeSet<>();
            final Set<String> zones = new TreeSet<>();
            final Map<String, Object> shapes = JsonValues.mapOrEmpty(state.get("shapes"));
            shapes.forEach((signature, shape) -> {
                fields.addAll(fieldsOf(signature));
                kindOf(signature).ifPresent(kinds::add);
                for (final Map<String, Object> example : JsonValues.maps(JsonValues.mapOrEmpty(shape)
                        .get("examples"))) {
                    zones.addAll(zonesIn(JsonValues.text(example.get("text"))));
                }
            });
            fieldsByFeed.put(feed, fields);
            kindsByFeed.put(feed, kinds);
            zonesByFeed.put(feed, zones);
        });
        return new Comparison(List.copyOf(states.keySet()), formats, onlyIn(fieldsByFeed), onlyIn(kindsByFeed),
                zonesByFeed);
    }

    /// @param signature A kind of event's signature, as the survey keeps it.
    /// @return The fields a structured record of that kind has (its top level), in order; none for others.
    static List<String> fieldsOf(final String signature) {
        if (!signature.startsWith(FIELDS)) {
            return List.of();
        }
        String rest = signature.substring(FIELDS.length());
        for (final String end : List.of(" | ", " > ")) {
            final int at = rest.indexOf(end);
            if (at >= 0) {
                rest = rest.substring(0, at);
            }
        }
        final List<String> out = new ArrayList<>();
        for (final String field : rest.split(",")) {
            if (!field.isBlank()) {
                out.add(field.strip());
            }
        }
        return out;
    }

    /// @param signature A kind of event's signature.
    /// @return What names its kind: a structured record's naming values (`action=login`), else the signature
    /// itself (a delimited row's values, a text record's template, a CEF header); empty for a structured record that
    /// names no kind (its fields alone tell it, and they are compared as fields).
    static Optional<String> kindOf(final String signature) {
        if (!signature.startsWith(FIELDS)) {
            return Optional.of(signature);
        }
        final int at = signature.indexOf(" | ");
        if (at < 0) {
            return Optional.empty();
        }
        final int inner = signature.indexOf(" > ", at);
        return Optional.of(signature.substring(at + 3, inner < 0
                ? signature.length()
                : inner).strip());
    }

    /// @param text A record's text.
    /// @return The zones its times are written with: `Z` as `UTC`, each offset as it is written, and
    /// [#NO_ZONE] for a time with none.
    static Set<String> zonesIn(final String text) {
        final Set<String> out = new TreeSet<>();
        final Matcher zoned = ZONED_TIME.matcher(text);
        while (zoned.find()) {
            out.add("Z".equals(zoned.group(1))
                    ? "UTC"
                    : zoned.group(1));
        }
        if (BARE_TIME.matcher(text).find()) {
            out.add(NO_ZONE);
        }
        return out;
    }

    // Each item some feeds have and others lack, with the feeds that have it, in order.
    private static Map<String, List<String>> onlyIn(final Map<String, Set<String>> byFeed) {
        final Map<String, List<String>> held = new TreeMap<>();
        byFeed.forEach((feed, items) -> items.forEach(item -> held.computeIfAbsent(item, k -> new ArrayList<>())
                .add(feed)));
        final Map<String, List<String>> out = new LinkedHashMap<>();
        held.forEach((item, feeds) -> {
            if (feeds.size() < byFeed.size()) {
                out.put(item, List.copyOf(feeds));
            }
        });
        return out;
    }


    // --------------------------------------------------------------------------------


    /// How a build's feeds differ.
    ///
    /// @param feeds       The feeds compared, in order.
    /// @param formats     Each feed's format, as its survey found it (blank where none was).
    /// @param fieldsOnlyIn Each field some feeds' records have and others' lack, with the feeds that have it.
    /// @param kindsOnlyIn  Each kind of event some feeds show and others don't, with the feeds that show it.
    /// @param zones        The zones or offsets each feed's times are written in ([#zonesIn]).
    record Comparison(List<String> feeds,
                      Map<String, String> formats,
                      Map<String, List<String>> fieldsOnlyIn,
                      Map<String, List<String>> kindsOnlyIn,
                      Map<String, Set<String>> zones) {

        /// @return Whether the feeds' formats differ.
        boolean formatsDiffer() {
            return new LinkedHashSet<>(formats.values()).size() > 1;
        }

        /// @return Whether the feeds' times are written in different zones (or some with none).
        boolean zonesDiffer() {
            return new LinkedHashSet<>(zones.values()).size() > 1;
        }

        /// @return Whether anything differs between the feeds.
        boolean differs() {
            return formatsDiffer() || zonesDiffer() || !fieldsOnlyIn.isEmpty() || !kindsOnlyIn.isEmpty();
        }

        /// @return The feeds whose zone the user is asked, a feed at a time: those whose times have no zone or offset
        /// (their own zone, the user's to say, may differ from the others'); none for one feed, or where every
        /// time says its zone.
        List<String> zonesToAsk() {
            if (feeds.size() < 2) {
                return List.of();
            }
            return feeds.stream()
                    .filter(feed -> zones.getOrDefault(feed, Set.of()).contains(NO_ZONE))
                    .toList();
        }

        /// @return The differences in a few lines, for the chat and the form's message: formats, fields, kinds and
        /// zones, each item with the feeds that have it (at most 20 of each).
        List<String> said() {
            final List<String> out = new ArrayList<>();
            if (formatsDiffer()) {
                final List<String> each = new ArrayList<>();
                formats.forEach((feed, format) -> each.add(feed + ": " + orNotKnown(format)));
                out.add("Formats differ: " + String.join("; ", each) + ".");
            }
            if (!fieldsOnlyIn.isEmpty()) {
                out.add("Fields in some feeds only: " + listed(fieldsOnlyIn) + ".");
            }
            if (!kindsOnlyIn.isEmpty()) {
                out.add("Kinds of event in some feeds only: " + listed(kindsOnlyIn) + ".");
            }
            if (zonesDiffer()) {
                final List<String> each = new ArrayList<>();
                zones.forEach((feed, found) -> each.add(feed + ": " + (found.isEmpty()
                        ? "no times read"
                        : String.join(", ", found))));
                out.add("Times written in different zones: " + String.join("; ", each) + ".");
            }
            return out;
        }

        /// @return The differences as JSON, for the tool's result and the chat's table.
        Map<String, Object> json() {
            final Map<String, Object> out = new LinkedHashMap<>();
            out.put("feeds", feeds);
            out.put("formats", formats);
            out.put("fields_only_in", fieldsOnlyIn);
            out.put("kinds_only_in", kindsOnlyIn);
            final Map<String, Object> zoned = new LinkedHashMap<>();
            zones.forEach((feed, found) -> zoned.put(feed, List.copyOf(found)));
            out.put("time_zones_seen", zoned);
            out.put("differs", differs());
            return out;
        }

        /// @return A digest of what differs: a confirmation stands until a survey shows something new.
        String digest() {
            return XsltCode.sha256(String.valueOf(new TreeMap<>(formats)) + "\n" + fieldsOnlyIn + "\n"
                                   + kindsOnlyIn + "\n" + new TreeMap<>(zones)).substring(0, 16);
        }

        private static String orNotKnown(final String text) {
            return ToolText.isBlank(text)
                    ? "not known"
                    : text;
        }

        private static String listed(final Map<String, List<String>> onlyIn) {
            final List<String> each = new ArrayList<>();
            onlyIn.entrySet().stream()
                    .limit(20)
                    .forEach(entry -> each.add(entry.getKey() + " (" + String.join(", ", entry.getValue()) + ")"));
            return String.join("; ", each) + (onlyIn.size() > 20
                    ? "; and " + (onlyIn.size() - 20) + " more"
                    : "");
        }
    }
}
