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

package stroom.ai.impl.tools.fielddoc;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.PyText;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/// What each field means for this source, written by the agent from the field's name, where it comes from and its
/// sample values (asked for by the user: the schema's generic words, or 'the x value recorded in a Data element of
/// Send', add nothing a reader can't see). With `ask`, the fields not yet described are collected, to ask for
/// ([#wanted()]).
///
/// A Data name read from the same source under several elements is asked once, by its name
/// ([FieldDocs#dataKey(String)]): seen, 224 fields asked of the agent for one feed, most of them its 18 Data names
/// once per action element. A description given by full path still wins for that row. Ported from the Stroom MCP
/// server's `Descriptions` in `utils/fielddoc.py`.
///
/// Each description is kept with where its field came from when it was given ([#sources()]), and asked for again
/// when that changes: not in the Python server, where a description outlived its field's source (seen: TWITTER's
/// Description, "the tweet's text, including mentions and links", kept once both were taken out of it).
public final class Descriptions {

    /// What a field description's fallback is said to come from first: the vendor's documentation.
    public static final String VENDOR = "vendor documentation";
    /// The event-logging schema's words.
    public static final String EVENT_LOGGING = "event-logging";

    // Fields the server describes exactly itself: never asked of the agent.
    private static final Set<String> SELF_DESCRIBED = Set.of("@StreamId", "@EventId", "StreamId", "EventId");
    private static final Pattern DATA_PATH = Pattern.compile("^(.*)/Data\\[@Name='([^']*)'\\]/@Value$");
    private static final Pattern NAME = Pattern.compile("\\[@Name='([^']*)'\\]");
    private static final Pattern BARE = Pattern.compile("\\[[^\\]]*\\]|/@Value$");

    private final Map<String, String> given;
    // Where each given description's field came from when it was given, for those it was recorded of.
    private final Map<String, String> givenFrom;
    // Where each description used here was of, by its key.
    private final Map<String, String> used = new LinkedHashMap<>();
    private final boolean ask;
    private final List<Map<String, Object>> notes;
    private final Map<String, Map<String, Object>> asked = new LinkedHashMap<>();
    // Data key -> field -> its ask, with what it reads.
    private final Map<String, Map<String, DataAsk>> data = new LinkedHashMap<>();

    /// @param given The descriptions given (field to description); blank ones are left out.
    /// @param ask   True to collect the fields not yet described.
    /// @param notes The vendor documentation's field dictionary (`record_source_notes`): maps with `field`,
    ///              `meaning`, `event_logging_path` and `values`.
    public Descriptions(final Map<String, ?> given, final boolean ask, final List<Map<String, Object>> notes) {
        this(given, Map.of(), ask, notes);
    }

    /// @param given     The descriptions given (field to description); blank ones are left out.
    /// @param givenFrom Where each given description's field came from when it was given (field to source), for
    ///                  those recorded; one whose field comes from elsewhere now is asked for again.
    /// @param ask       True to collect the fields not yet described.
    /// @param notes     The vendor documentation's field dictionary (`record_source_notes`): maps with `field`,
    ///                  `meaning`, `event_logging_path` and `values`.
    public Descriptions(final Map<String, ?> given,
                        final Map<String, String> givenFrom,
                        final boolean ask,
                        final List<Map<String, Object>> notes) {
        this.givenFrom = Map.copyOf(Objects.requireNonNullElse(givenFrom, Map.of()));
        final Map<String, String> kept = new LinkedHashMap<>();
        if (given != null) {
            given.forEach((key, value) -> {
                if (value instanceof final String text && !PyText.strip(text).isEmpty()) {
                    kept.put(key, PyText.strip(text));
                }
            });
        }
        this.given = kept;
        this.ask = ask;
        this.notes = notes == null
                ? List.of()
                : List.copyOf(notes);
    }

    /// @param given The descriptions given.
    /// @param ask   True to collect the fields not yet described.
    public Descriptions(final Map<String, ?> given, final boolean ask) {
        this(given, ask, null);
    }

    /// No descriptions given, none asked for: every field gets its fallback.
    public Descriptions() {
        this(null, false, null);
    }

    /// @param ask True to collect the fields not yet described.
    /// @return Descriptions with none given.
    public static Descriptions asking(final boolean ask) {
        return new Descriptions(null, ask, null);
    }

    /// @return The descriptions given.
    public Map<String, String> given() {
        return given;
    }

    /// @return Where the field of each description given came from: as it was used here, else as it was given.
    public Map<String, String> sources() {
        final Map<String, String> out = new LinkedHashMap<>();
        given.keySet().forEach(key -> {
            final String from = used.getOrDefault(key, givenFrom.get(key));
            if (from != null) {
                out.put(key, from);
            }
        });
        return out;
    }

    /// @return True if the fields not yet described are collected.
    public boolean ask() {
        return ask;
    }

    /// @return The vendor documentation's field dictionary.
    public List<Map<String, Object>> notes() {
        return notes;
    }

    /// The fields to ask about. A Data name every element reads from the same source: once, by its name, with the
    /// elements it is under and their values together; else each by its full path.
    ///
    /// @return Field to what is asked of it (`field`, `from`, `values`, and `schemas_say` when a schema says
    /// anything; `under` for a Data name).
    public Map<String, Map<String, Object>> wanted() {
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>(asked);
        for (final Map.Entry<String, Map<String, DataAsk>> entry : data.entrySet()) {
            final Map<String, DataAsk> asks = entry.getValue();
            final Set<Set<String>> reads = asks.values().stream().map(DataAsk::reads).collect(Collectors.toSet());
            if (asks.size() > 1 && reads.size() == 1) {
                final List<String> values = new ArrayList<>(new LinkedHashSet<>(asks.values().stream()
                        .flatMap(a -> JsonValues.strings(a.asked().get("values")).stream())
                        .toList()));
                final Object said = asks.values().stream()
                        .map(a -> a.asked().get("schemas_say"))
                        .filter(JsonValues::truthy)
                        .findFirst()
                        .orElse(null);
                final Map<String, Object> merged = new LinkedHashMap<>();
                merged.put("field", entry.getKey());
                merged.put("under", asks.keySet().stream().map(f -> {
                    final Matcher matcher = DATA_PATH.matcher(f);
                    return matcher.find()
                            ? matcher.group(1)
                            : null;
                }).toList());
                merged.put("from", String.join("\n", new TreeSet<>(reads.iterator().next())));
                merged.put("values", values.subList(0, Math.min(3, values.size())));
                if (said != null) {
                    merged.put("schemas_say", said);
                }
                out.put(entry.getKey(), merged);
            } else {
                asks.forEach((field, a) -> out.put(field, a.asked()));
            }
        }
        return out;
    }

    /// `vendor`: what the vendor's documentation says of the source fields a field is read from; else of the
    /// source field it puts in this event-logging element (an index or CEF field knows only its path).
    ///
    /// @param names The source fields the field is read from.
    /// @param path  The field's event-logging path, or "".
    /// @return What the notes say, or "".
    public String vendor(final List<String> names, final String path) {
        List<Map<String, Object>> found = notes.stream()
                .filter(n -> names.contains(text(n.get("field"))))
                .toList();
        if (found.isEmpty() && path != null && !path.isEmpty()) {
            final Matcher name = NAME.matcher(path);
            final String wantedName = name.find()
                    ? name.group(1).toLowerCase(Locale.ROOT)
                    : null;
            final String bare = BARE.matcher(path).replaceAll("");
            found = notes.stream()
                    .filter(n -> JsonValues.truthy(n.get("event_logging_path")))
                    .filter(n -> BARE.matcher(text(n.get("event_logging_path"))).replaceAll("").equals(bare))
                    .filter(n -> wantedName == null || (text(n.get("event_logging_path")) + " " + text(n.get(
                            "field"))).toLowerCase(Locale.ROOT).contains(wantedName))
                    .toList();
        }
        return found.stream()
                .filter(n -> JsonValues.truthy(n.get("meaning")))
                .map(Descriptions::said)
                .collect(Collectors.joining(" "));
    }

    /// `of`: the field's description: the agent's, else (the fallback) the first that says anything of it: the
    /// vendor's documentation of the source fields it is read from (names) or of its element (path), then the
    /// schemas. The agent is shown them all, to build on (asked for by the user). A row of several event types is
    /// asked by its own path (`EventDetail/(Authenticate|Process)/Action`) and takes a description given by any of
    /// theirs (paths).
    ///
    /// @param field  The field (its index name, or its path).
    /// @param source Where it comes from, for a reader.
    /// @param values Its sampled values.
    /// @param says   What each schema says of it, by schema (e.g. `event-logging`, then `ECS`).
    /// @param path   Its event-logging path, or "".
    /// @param names  The source fields it is read from (nulls allowed, left out).
    /// @param paths  The paths of the event types a shared row is of.
    /// @param reads  What the row reads (the source of each), to tell a Data name meaning the same everywhere;
    ///               null for just the source.
    /// @return The description, or "" if nothing says anything of it.
    public String of(final String field,
                     final String source,
                     final List<String> values,
                     final Map<String, String> says,
                     final String path,
                     final List<String> names,
                     final List<String> paths,
                     final List<String> reads) {
        final Map<String, String> said = new LinkedHashMap<>();
        final List<String> named = names == null
                ? List.of()
                : names.stream().filter(JsonValues::truthy).toList();
        said.put(VENDOR, vendor(named, path));
        said.putAll(says);
        said.values().removeIf(v -> !JsonValues.truthy(v));
        final Matcher dataPath = DATA_PATH.matcher(field);
        final boolean isData = dataPath.find();
        final List<String> keys = new ArrayList<>();
        keys.add(field);
        keys.addAll(Objects.requireNonNullElse(paths, List.of()));
        if (isData) {
            keys.add(FieldDocs.dataKey(dataPath.group(2)));
        }
        String was = null;
        for (final String key : keys) {
            if (given.containsKey(key)) {
                if (givenFrom.containsKey(key) && !givenFrom.get(key).equals(source)) {
                    // Given of a field that came from elsewhere: asked again, with what it said.
                    was = given.get(key);
                    continue;
                }
                used.put(key, source);
                return given.get(key);
            }
        }
        if (ask && !SELF_DESCRIBED.contains(field)) {
            final Map<String, Object> asking = new LinkedHashMap<>();
            asking.put("field", field);
            asking.put("from", source);
            if (was != null) {
                asking.put("was", was);
            }
            asking.put("values", values.subList(0, Math.min(3, values.size())).stream()
                    .map(v -> PyText.head(v, 60))
                    .toList());
            if (!said.isEmpty()) {
                asking.put("schemas_say", said);
            }
            if (isData) {
                data.computeIfAbsent(FieldDocs.dataKey(dataPath.group(2)), k -> new LinkedHashMap<>())
                        .putIfAbsent(field, new DataAsk(asking, Set.copyOf(reads != null
                                ? reads
                                : List.of(source))));
            } else {
                asked.putIfAbsent(field, asking);
            }
        }
        return said.values().stream().findFirst().orElse("");
    }

    /// [#of(String, String, List, Map, String, List, List, List)] with the event-logging schema's words alone.
    ///
    /// @param field  The field.
    /// @param source Where it comes from.
    /// @param values Its sampled values.
    /// @param says   What the event-logging schema says of it.
    /// @return The description, or "".
    public String of(final String field, final String source, final List<String> values, final String says) {
        return of(field, source, values, eventLogging(says), "", List.of(), List.of(), null);
    }

    /// [#of(String, String, List, Map, String, List, List, List)] with no source fields or event types.
    ///
    /// @param field  The field.
    /// @param source Where it comes from.
    /// @param values Its sampled values.
    /// @param says   What each schema says of it.
    /// @param path   Its event-logging path.
    /// @return The description, or "".
    public String of(final String field,
                     final String source,
                     final List<String> values,
                     final Map<String, String> says,
                     final String path) {
        return of(field, source, values, says, path, List.of(), List.of(), null);
    }

    /// @param says What the event-logging schema says of a field.
    /// @return It as the schemas' words.
    public static Map<String, String> eventLogging(final String says) {
        final Map<String, String> map = new LinkedHashMap<>();
        map.put(EVENT_LOGGING, says);
        return map;
    }

    // One note's words: its meaning, and up to eight of its codes.
    private static String said(final Map<String, Object> note) {
        final String meaning = PyText.rstrip(text(note.get("meaning")), ".") + ".";
        if (!JsonValues.truthy(note.get("values")) || !(note.get("values") instanceof final Map<?, ?> codes)) {
            return meaning;
        }
        return meaning + " Codes: " + codes.entrySet().stream()
                .limit(8)
                .map(e -> PyText.str(e.getKey()) + ": " + PyText.str(e.getValue()))
                .collect(Collectors.joining("; ")) + ".";
    }

    private static String text(final Object value) {
        return value == null
                ? ""
                : PyText.str(value);
    }


    // --------------------------------------------------------------------------------


    // A Data field's ask, and the sources the row reads.
    private record DataAsk(Map<String, Object> asked, Set<String> reads) {

    }
}
