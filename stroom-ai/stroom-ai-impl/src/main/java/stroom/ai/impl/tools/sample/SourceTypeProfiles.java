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

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.generation.FieldHomes;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// What the survey learns of each source type of an aggregated feed, counted over the records it reads, by the
/// source type field's value: size (records and bytes, per day, the streams it is in), shape (message formats and
/// how many failed to parse, fields and their fill, shapes and the commonest's share), composition (kinds of event,
/// levels) and signals of an audit trail or of operational traffic, each with what it rests on. Generic: field roles
/// ([FieldHomes]) and words any product uses, never one product's names.
///
/// Counted per call ([#add]) and kept in the survey state ([#toState], [#merge]); summed by the registry's rows
/// ([#summarise]), so a pattern's source type is profiled as one. Counts are of the records read; estimates scale
/// them to the streams read ([#scaled]), said as "about".
public final class SourceTypeProfiles {

    /// The survey state's key for the source types: `field`, `by`, `at`, `records`, `bytes`, `fields`,
    /// `values` (each value's profile), `stream_scale` and, once chosen, `scope`.
    public static final String STATE_KEY = "source_types";
    /// The state's key for the source type field (null when the user said the feed is not aggregated).
    public static final String FIELD = "field";
    /// Who chose the field: `user`, or `user (aggregated)`.
    public static final String BY = "by";
    /// The state's key for each value's profile.
    public static final String VALUES = "values";
    /// The state's key for the scope chosen: `source_types`, `build`, `by`, `at`.
    public static final String SCOPE = "scope";
    /// The state's key for each stream's scale: its parts' bytes over the bytes of its records read.
    public static final String STREAM_SCALE = "stream_scale";
    /// The suggested categories.
    public static final String SECURITY_AUDIT = "security audit";
    /// Operational traffic.
    public static final String OPERATIONAL = "operational";
    /// Network traffic.
    public static final String NETWORK = "network";
    /// Diagnostic noise.
    public static final String DIAGNOSTIC_NOISE = "diagnostic noise";
    /// Below this many records read, a profile says it rests on few.
    public static final int FEW_RECORDS = 20;

    // The most values profiled, fields, shapes, kinds and levels kept a value, and examples.
    static final int MAX_VALUES = 300;
    private static final int MAX_FIELDS = 80;
    private static final int MAX_SHAPES = 100;
    private static final int MAX_KIND_FIELDS = 4;
    private static final int MAX_KINDS = 25;
    private static final int MAX_LEVELS = 12;
    private static final int MAX_STREAMS = 50;
    private static final int EXAMPLES = 3;
    private static final String OTHER = "(other values)";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);
    private static final Pattern LEVEL_FIELD = Pattern.compile(
            "(?i)(^|[._-])(level|log_?level|severity|sev|priority|loglevel)$");
    private static final Pattern LEVEL_WORD = Pattern.compile(
            "(?<![\\w.])(TRACE|DEBUG|INFO|NOTICE|WARN|WARNING|ERROR|SEVERE|FATAL|CRITICAL|ALERT|EMERG)(?![\\w.])");
    private static final Pattern ACTOR_NAME = Pattern.compile(
            "(?i)(^|[._-])?(user|username|operator|actor|principal|account|owner|initiator|subject)(_?id|_?name)?$");
    private static final Pattern ACTOR_TEXT = Pattern.compile(
            "(?i)\\b(user|operator|actor|principal|account)(_?id|_?name)?\\s*[=:]\\s*\\S");
    private static final Pattern ADDRESS = Pattern.compile("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b");
    private static final Pattern OUTCOME = Pattern.compile(
            "(?i)(success|succeeded|fail|denied|rejected|refused|allowed|blocked|unauthori[sz]ed|forbidden)");
    private static final Pattern ACCESS = Pattern.compile(
            "(?i)(log[ _-]?on|log[ _-]?in|log[ _-]?off|log[ _-]?out|sign[ _-]?in|auth|password|permission|grant|"
            + "revoke|role|policy|delete|creat|modif|access|shar|download|upload|privilege|member|mailbox|inbox)");
    private static final Pattern SQL = Pattern.compile(
            "(?i)\\b(select\\s.+\\sfrom|insert\\s+into|update\\s+\\S+\\s+set|delete\\s+from|query|statement)\\b");
    private static final Pattern TIMING = Pattern.compile("(?i)(\\b\\d+(\\.\\d+)?\\s?(ms|msec|millis)\\b|\\btook\\b|"
                                                          + "\\bduration\\b|\\belapsed\\b)");
    private static final Pattern HEARTBEAT = Pattern.compile(
            "(?i)(heartbeat|health[ _-]?check|keep[ _-]?alive|\\bping\\b|liveness|readiness|is alive)");
    private static final Pattern NETWORK_WORDS = Pattern.compile(
            "(?i)(\\bconnect|disconnect|\\bport\\b|\\btcp\\b|\\budp\\b|\\bhttp/|\\b(GET|POST|PUT|HEAD) /|"
            + "\\bbytes\\b|\\bsrc\\b|\\bdst\\b|firewall|\\bnat\\b)");
    private static final Set<String> DEBUG_LEVELS = Set.of("TRACE", "DEBUG", "FINE", "FINER", "FINEST");
    // The signals counted, each with what it is a signal of.
    private static final List<String> AUDIT_SIGNALS = List.of("actor", "client_address", "outcome", "access_words");
    private static final List<String> OPERATIONAL_SIGNALS = List.of("sql", "timings", "heartbeats", "network_words");

    private final String field;
    private final Map<String, Map<String, Object>> values = new LinkedHashMap<>();
    private final Map<String, Long> fields = new LinkedHashMap<>();
    private final Map<Long, Long> created = new LinkedHashMap<>();
    private final Map<String, long[]> streamBytes = new LinkedHashMap<>();
    private long records;
    private long bytes;

    /// @param field The source type field, dotted.
    public SourceTypeProfiles(final String field) {
        this.field = field;
    }

    /// @return The source type field.
    public String field() {
        return field;
    }

    /// @param stream    A stream about to be read.
    /// @param createdMs When it was created, or 0 when it isn't known.
    public void streamCreated(final long stream, final long createdMs) {
        created.put(stream, createdMs);
    }

    /// Counts a record read.
    ///
    /// @param parsed The record's fields (flattened), or null for a record with none.
    /// @param text   Its text.
    /// @param stream The stream it is in.
    /// @param part   The part it is in.
    /// @param record Its index in the part.
    @SuppressWarnings("unchecked")
    public void add(final Map<String, Object> parsed,
                    final String text,
                    final long stream,
                    final int part,
                    final long record) {
        final long size = text == null
                ? 0
                : text.getBytes(StandardCharsets.UTF_8).length + 1L;
        records++;
        bytes += size;
        streamBytes.computeIfAbsent(stream + ":" + part, k -> new long[1])[0] += size;
        final Map<String, Object> fieldsOf = parsed == null
                ? Map.of()
                : parsed;
        fieldsOf.keySet().forEach(name -> countCapped(fields, name, 1, MAX_FIELDS * 2));
        final String found = SourceTypeFields.valueOf(parsed, field);
        final String value = found == null
                ? ""
                : found;
        final Map<String, Object> profile = values.containsKey(value) || values.size() < MAX_VALUES
                ? values.computeIfAbsent(value, k -> new LinkedHashMap<>())
                : values.computeIfAbsent(OTHER, k -> new LinkedHashMap<>());
        add(profile, "records", 1);
        add(profile, "bytes", size);
        count(profile, "streams", String.valueOf(stream), 1, MAX_STREAMS);
        final long createdMs = created.getOrDefault(stream, 0L);
        if (createdMs > 0) {
            count(profile, "days", DAY.format(Instant.ofEpochMilli(createdMs)), 1, 400);
        }
        final SourceTypeFields.Held held = SourceTypeFields.held(fieldsOf);
        count(profile, "formats", held.named(), 1, 20);
        if (held.failed()) {
            count(profile, "failed", held.named(), 1, 20);
        }
        final Map<String, Object> all = new LinkedHashMap<>(fieldsOf);
        final String messageField = SourceTypeFields.messageField(fieldsOf);
        held.fields().forEach((name, inner) -> all.put(messageField + "." + name, inner));
        all.keySet().stream().filter(name -> !name.equals(field))
                .forEach(name -> count(profile, "fields", name, 1, MAX_FIELDS));
        count(profile, "shapes", SourceTypeFields.shapeKey(fieldsOf, field), 1, MAX_SHAPES);
        final String message = messageField == null
                ? ""
                : String.valueOf(fieldsOf.get(messageField));
        kinds(profile, all, held, message);
        levels(profile, all, message);
        signals(profile, all, message);
        final List<Object> examples = (List<Object>) profile.computeIfAbsent("examples", k -> new ArrayList<>());
        if (examples.size() < EXAMPLES) {
            examples.add(SampleProfiler.map("stream", stream, "part", part, "record", record));
        }
    }

    /// @return Each part records were read from in this call, as `stream:part`.
    public Set<String> partsRead() {
        return Set.copyOf(streamBytes.keySet());
    }

    /// @param partSizes Each part read (`stream:part`) and its size in bytes, where it is known.
    /// @return This call's counts, as the survey state keeps them: each stream's scale is its parts' size over the
    /// bytes of the records read from them (1 for a part read whole).
    public Map<String, Object> toState(final Map<String, Long> partSizes) {
        final Map<String, Long> sizes = new LinkedHashMap<>();
        final Map<String, Long> read = new LinkedHashMap<>();
        streamBytes.forEach((key, counted) -> {
            final String stream = key.substring(0, key.indexOf(':'));
            read.merge(stream, counted[0], Long::sum);
            sizes.merge(stream, Math.max(counted[0], partSizes.getOrDefault(key, counted[0])), Long::sum);
        });
        final Map<String, Object> scale = new LinkedHashMap<>();
        read.forEach((stream, counted) -> scale.put(stream, counted == 0
                ? 1.0
                : ToolText.round((double) sizes.get(stream) / counted, 3)));
        final Map<String, Object> state = new LinkedHashMap<>();
        state.put(FIELD, field);
        state.put("records", records);
        state.put("bytes", bytes);
        state.put("fields", new LinkedHashMap<>(fields));
        state.put(VALUES, values);
        state.put(STREAM_SCALE, scale);
        return state;
    }

    /// Adds a call's counts to those kept.
    ///
    /// @param kept The survey state's source types, or null.
    /// @param call A call's counts ([#toState]).
    /// @return The kept counts, the call's added (the decision kept: field, who chose it, the scope).
    @SuppressWarnings("unchecked")
    public static Map<String, Object> merge(final Map<String, Object> kept, final Map<String, Object> call) {
        final Map<String, Object> out = kept == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(kept);
        call.forEach((key, value) -> {
            if (value instanceof final Map<?, ?> map && out.get(key) instanceof final Map<?, ?> before
                && !SCOPE.equals(key)) {
                out.put(key, mergeMaps((Map<String, Object>) before, (Map<String, Object>) map, key));
            } else if (value instanceof final Number number && out.get(key) instanceof final Number before) {
                out.put(key, before.longValue() + number.longValue());
            } else if (value != null) {
                out.put(key, value);
            }
        });
        return out;
    }

    // Two count maps added: numbers summed, maps merged, lists (examples) joined to the most kept; a stream's scale
    // is the later.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mergeMaps(final Map<String, Object> a,
                                                 final Map<String, Object> b,
                                                 final String key) {
        final Map<String, Object> out = new LinkedHashMap<>(a);
        b.forEach((name, value) -> {
            final Object before = out.get(name);
            if (STREAM_SCALE.equals(key)) {
                out.put(name, value);
            } else if (value instanceof final Number number && before instanceof final Number count) {
                out.put(name, count.longValue() + number.longValue());
            } else if (value instanceof final Map<?, ?> map && before instanceof final Map<?, ?> inner) {
                out.put(name, mergeMaps((Map<String, Object>) inner, (Map<String, Object>) map, name));
            } else if (value instanceof final List<?> list && before instanceof final List<?> inner) {
                final List<Object> joined = new ArrayList<>(inner);
                list.stream().filter(item -> !joined.contains(item)).forEach(joined::add);
                out.put(name, new ArrayList<>(joined.subList(0, Math.min(EXAMPLES, joined.size()))));
            } else if (value != null) {
                out.put(name, value);
            }
        });
        return out;
    }

    /// @param state The survey state's source types.
    /// @return True if the survey met more values than it keeps one by one: the rest counted together, so a value
    /// outside those kept may have been seen.
    public static boolean valuesCapped(final Map<String, Object> state) {
        return JsonValues.mapOrEmpty(state.get(VALUES)).containsKey(OTHER);
    }

    /// @param state The survey state's source types.
    /// @return The records seen with each value of the source type field.
    public static Map<String, Long> valueCounts(final Map<String, Object> state) {
        final Map<String, Long> out = new LinkedHashMap<>();
        JsonValues.mapOrEmpty(state.get(VALUES)).forEach((value, profile) -> {
            if (!OTHER.equals(value) && !"".equals(value)) {
                out.put(String.valueOf(value), number(JsonValues.mapOrEmpty(profile).get("records")));
            }
        });
        return out;
    }

    /// Each row's profile, its values' counts summed: the first row matching a value takes it.
    ///
    /// @param state The survey state's source types.
    /// @param rows  The registry's rows, in order.
    /// @return Each row's profile by name, in the rows' order, a row the survey met no record of included (its
    /// `records` 0); and, under `(unmatched)`, values no row matches, when there are any.
    @SuppressWarnings("unchecked")
    public static Map<String, Map<String, Object>> summarise(final Map<String, Object> state,
                                                             final List<SourceTypeRows.Row> rows) {
        final Map<String, Map<String, Object>> summed = new LinkedHashMap<>();
        rows.forEach(row -> summed.put(row.name(), new LinkedHashMap<>()));
        final Map<String, List<String>> covered = new LinkedHashMap<>();
        JsonValues.mapOrEmpty(state.get(VALUES)).forEach((value, profile) -> {
            final String name = SourceTypeRows.first(rows, String.valueOf(value))
                    .map(SourceTypeRows.Row::name)
                    .orElse("(unmatched)");
            summed.computeIfAbsent(name, k -> new LinkedHashMap<>());
            summed.put(name, mergeMaps(summed.get(name), (Map<String, Object>) profile, name));
            covered.computeIfAbsent(name, k -> new ArrayList<>()).add(String.valueOf(value));
        });
        final long total = Math.max(1, number(state.get("records")));
        final long totalBytes = Math.max(1, number(state.get("bytes")));
        final Map<String, Object> scale = JsonValues.mapOrEmpty(state.get(STREAM_SCALE));
        final Set<String> common = commonFields(state);
        final Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        summed.forEach((name, profile) -> {
            if ("(unmatched)".equals(name) && profile.isEmpty()) {
                return;
            }
            final Map<String, Object> summary = summary(profile, total, totalBytes, scale, common);
            if (covered.containsKey(name) && covered.get(name).size() > 1) {
                summary.put("values", covered.get(name).subList(0, Math.min(20, covered.get(name).size())));
            }
            out.put(name, summary);
        });
        return out;
    }

    /// @param state The survey state's source types.
    /// @return The fields nearly every record read has, whatever its source type (a wrapper's): left out of each
    /// profile's fields.
    public static Set<String> commonFields(final Map<String, Object> state) {
        final long total = Math.max(1, number(state.get("records")));
        final Set<String> out = new TreeSet<>();
        JsonValues.mapOrEmpty(state.get("fields")).forEach((name, count) -> {
            if (number(count) >= 0.95 * total) {
                out.add(String.valueOf(name));
            }
        });
        return out;
    }

    /// @param counts  A row's records in each stream read.
    /// @param scale   Each stream's scale.
    /// @return About how many records the streams read hold of it: each stream's records read times its scale.
    public static long scaled(final Map<String, Object> counts, final Map<String, Object> scale) {
        double sum = 0;
        for (final Map.Entry<String, Object> entry : counts.entrySet()) {
            final Object factor = scale.get(entry.getKey());
            sum += number(entry.getValue()) * (factor instanceof final Number n
                    ? Math.max(1.0, n.doubleValue())
                    : 1.0);
        }
        return Math.round(sum);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> summary(final Map<String, Object> profile,
                                               final long total,
                                               final long totalBytes,
                                               final Map<String, Object> scale,
                                               final Set<String> common) {
        final long count = number(profile.get("records"));
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("records_read", count);
        out.put("share_percent", ToolText.round(100.0 * count / total, 1));
        out.put("records_estimate", scaled(JsonValues.mapOrEmpty(profile.get("streams")), scale));
        out.put("bytes_read", number(profile.get("bytes")));
        out.put("bytes_share_percent", ToolText.round(100.0 * number(profile.get("bytes")) / totalBytes, 1));
        out.put("rests_on", count == 0
                ? "not seen in the " + total + " records read"
                : count < FEW_RECORDS
                        ? "from only " + count + " of the " + total + " records read"
                        : count + " of the " + total + " records read");
        if (count == 0) {
            return out;
        }
        out.put("per_day", JsonValues.mapOrEmpty(profile.get("days")));
        out.put("streams_seen", JsonValues.mapOrEmpty(profile.get("streams")).size());
        out.put("streams_read", scale.size());
        out.put("formats", byCount(JsonValues.mapOrEmpty(profile.get("formats")), 6));
        out.put("failed_to_parse", JsonValues.mapOrEmpty(profile.get("failed")).values().stream()
                .mapToLong(SourceTypeProfiles::number).sum());
        final List<Map<String, Object>> fieldFill = new ArrayList<>();
        byCount(JsonValues.mapOrEmpty(profile.get("fields")), 100).forEach((name, n) -> {
            if (!common.contains(name) && fieldFill.size() < 15) {
                fieldFill.add(SampleProfiler.map("field", name, "fill_percent",
                        ToolText.round(100.0 * number(n) / count, 1)));
            }
        });
        out.put("fields", fieldFill);
        final Map<String, Object> shapes = JsonValues.mapOrEmpty(profile.get("shapes"));
        out.put("shapes", shapes.size());
        out.put("commonest_shape_percent", ToolText.round(100.0 * shapes.values().stream()
                .mapToLong(SourceTypeProfiles::number).max().orElse(0) / count, 1));
        final Map.Entry<String, Map<String, Object>> kinds = kindField(profile);
        out.put("kind_field", kinds.getKey());
        final List<Map<String, Object>> top = new ArrayList<>();
        byCount(kinds.getValue(), 5).forEach((kind, n) -> top.add(SampleProfiler.map("kind", kind, "records",
                number(n))));
        out.put("top_kinds", top);
        final Map<String, Object> levels = JsonValues.mapOrEmpty(profile.get("levels"));
        if (!levels.isEmpty()) {
            out.put("levels", byCount(levels, MAX_LEVELS));
        }
        final Map<String, Object> signals = JsonValues.mapOrEmpty(profile.get("signals"));
        final List<Map<String, Object>> said = signalsSaid(signals, count, shapes, levels,
                100.0 * number(profile.get("bytes")) / totalBytes);
        out.put("signals", said);
        final Category category = category(signals, count, shapes, levels);
        out.put("suggested_category", category.name());
        out.put("confidence", category.confidence());
        out.put("examples", JsonValues.listOrEmpty(profile.get("examples")));
        return out;
    }

    // The kinds of event within a source type: the values of its commonest field named like a kind field (or its
    // message's), else its messages' templates.
    @SuppressWarnings("unchecked")
    private static Map.Entry<String, Map<String, Object>> kindField(final Map<String, Object> profile) {
        final Map<String, Object> naming = JsonValues.mapOrEmpty(profile.get("naming"));
        String best = null;
        long most = 0;
        for (final Map.Entry<String, Object> entry : naming.entrySet()) {
            final long n = JsonValues.mapOrEmpty(entry.getValue()).values().stream()
                    .mapToLong(SourceTypeProfiles::number).sum();
            if (n > most) {
                best = entry.getKey();
                most = n;
            }
        }
        return best != null
                ? Map.entry(best, (Map<String, Object>) naming.get(best))
                : Map.entry("(message template)", JsonValues.mapOrEmpty(profile.get("templates")));
    }

    // Each signal seen, with what it rests on: its share of the source type's records.
    private static List<Map<String, Object>> signalsSaid(final Map<String, Object> signals,
                                                         final long count,
                                                         final Map<String, Object> shapes,
                                                         final Map<String, Object> levels,
                                                         final double bytesShare) {
        final List<Map<String, Object>> out = new ArrayList<>();
        final Map<String, String> restsOn = Map.of(
                "actor", "a field naming who acted (a user home, or user/operator/account...)",
                "client_address", "an IP address in a field or the message",
                "outcome", "outcome words (success, failure, denied, blocked...)",
                "access_words", "access, change or authentication words (logon, delete, grant, password...)",
                "sql", "SQL or query text in the messages",
                "timings", "timings in the messages (ms, took, duration)",
                "heartbeats", "heartbeat or health-check words",
                "network_words", "network words (connect, port, GET /...)");
        for (final String signal : AUDIT_SIGNALS) {
            addSignal(out, signal, "audit trail", number(signals.get(signal)), count, restsOn.get(signal));
        }
        if (number(signals.get("actor")) == 0) {
            out.add(SampleProfiler.map("signal", "no_actor", "of", "operational traffic", "share_percent", 100.0,
                    "rests_on", "no field or message names who acted"));
        }
        for (final String signal : OPERATIONAL_SIGNALS) {
            addSignal(out, signal, "operational traffic", number(signals.get(signal)), count, restsOn.get(signal));
        }
        final double commonest = 100.0 * shapes.values().stream().mapToLong(SourceTypeProfiles::number).max()
                .orElse(0) / Math.max(1, count);
        if (commonest >= 90.0) {
            out.add(SampleProfiler.map("signal", "few_templates", "of", "operational traffic", "share_percent",
                    ToolText.round(commonest, 1), "rests_on", "one shape covers " + ToolText.round(commonest, 1)
                                                          + "% of its records"));
        }
        final double debug = debugShare(levels);
        if (debug >= 50.0) {
            out.add(SampleProfiler.map("signal", "debug_levels", "of", "operational traffic", "share_percent",
                    ToolText.round(debug, 1), "rests_on", "levels mostly debug or trace"));
        }
        if (bytesShare >= 40.0) {
            out.add(SampleProfiler.map("signal", "large_byte_share", "of", "operational traffic", "share_percent",
                    ToolText.round(bytesShare, 1), "rests_on", "its share of the feed's bytes"));
        }
        return out;
    }

    private static void addSignal(final List<Map<String, Object>> out,
                                  final String signal,
                                  final String of,
                                  final long n,
                                  final long count,
                                  final String restsOn) {
        if (n > 0) {
            out.add(SampleProfiler.map("signal", signal, "of", of, "share_percent",
                    ToolText.round(100.0 * n / Math.max(1, count), 1), "rests_on", restsOn));
        }
    }

    /// The category suggested for a source type, from its signals alone: a suggestion beside what it rests on,
    /// never a verdict.
    ///
    /// @param signals Each signal's records.
    /// @param count   The source type's records.
    /// @param shapes  Its shapes' records.
    /// @param levels  Its levels' records.
    /// @return The category and how sure.
    static Category category(final Map<String, Object> signals,
                             final long count,
                             final Map<String, Object> shapes,
                             final Map<String, Object> levels) {
        final double total = Math.max(1, count);
        final double actor = number(signals.get("actor")) / total;
        final double client = number(signals.get("client_address")) / total;
        final double outcome = number(signals.get("outcome")) / total;
        final double access = number(signals.get("access_words")) / total;
        final double sql = number(signals.get("sql")) / total;
        final double timings = number(signals.get("timings")) / total;
        final double heartbeats = number(signals.get("heartbeats")) / total;
        final double network = number(signals.get("network_words")) / total;
        final double debug = debugShare(levels) / 100.0;
        if (heartbeats >= 0.5) {
            return new Category(DIAGNOSTIC_NOISE, heartbeats >= 0.8
                    ? "high"
                    : "medium");
        }
        if (actor >= 0.5 && (outcome >= 0.2 || access >= 0.3)) {
            return new Category(SECURITY_AUDIT, client >= 0.5 && actor >= 0.8
                    ? "high"
                    : "medium");
        }
        if (actor >= 0.8 && client >= 0.8) {
            return new Category(SECURITY_AUDIT, "medium");
        }
        if ((outcome >= 0.3 || access >= 0.5) && (actor >= 0.2 || client >= 0.5)) {
            return new Category(SECURITY_AUDIT, "low");
        }
        if (sql >= 0.3 || timings >= 0.5) {
            return new Category(OPERATIONAL, sql >= 0.6 || timings >= 0.8
                    ? "high"
                    : "medium");
        }
        if (debug >= 0.6) {
            return new Category(DIAGNOSTIC_NOISE, "medium");
        }
        if (network >= 0.5 && client >= 0.5 && actor < 0.2) {
            return new Category(NETWORK, "medium");
        }
        return new Category(OPERATIONAL, "low");
    }

    private static double debugShare(final Map<String, Object> levels) {
        final long all = levels.values().stream().mapToLong(SourceTypeProfiles::number).sum();
        if (all == 0) {
            return 0.0;
        }
        final long debug = levels.entrySet().stream()
                .filter(entry -> DEBUG_LEVELS.contains(entry.getKey().toUpperCase(Locale.ROOT)))
                .mapToLong(entry -> number(entry.getValue()))
                .sum();
        return 100.0 * debug / all;
    }

    private void kinds(final Map<String, Object> profile,
                       final Map<String, Object> all,
                       final SourceTypeFields.Held held,
                       final String message) {
        boolean named = false;
        for (final Map.Entry<String, Object> entry : all.entrySet()) {
            final String name = entry.getKey();
            if (name.equals(field) || !KindFields.namedLikeKinds(ToolText.lastPart(name, '.'))) {
                continue;
            }
            final String value = SourceTypeFields.valueOf(all, name);
            if (value != null && value.length() <= 60) {
                final Map<String, Object> naming = map(profile, "naming");
                if (naming.containsKey(name) || naming.size() < MAX_KIND_FIELDS) {
                    count(naming, name, value, 1, MAX_KINDS);
                    named = true;
                }
            }
        }
        if (!named || !message.isEmpty()) {
            count(profile, "templates", held.format().equals(SourceTypeFields.XML)
                    ? held.named()
                    : held.template().isEmpty()
                            ? SourceTypeFields.template(message)
                            : held.template(), 1, MAX_KINDS);
        }
    }

    private static void levels(final Map<String, Object> profile, final Map<String, Object> all, final String message) {
        for (final String name : all.keySet()) {
            if (LEVEL_FIELD.matcher(name).find()) {
                final String value = SourceTypeFields.valueOf(all, name);
                if (value != null && value.length() <= 20) {
                    count(profile, "levels", value.toUpperCase(Locale.ROOT), 1, MAX_LEVELS);
                    return;
                }
            }
        }
        final Matcher word = LEVEL_WORD.matcher(ToolText.head(message, 200));
        if (word.find()) {
            count(profile, "levels", word.group(1), 1, MAX_LEVELS);
        }
    }

    private void signals(final Map<String, Object> profile, final Map<String, Object> all, final String message) {
        boolean actor = ACTOR_TEXT.matcher(message).find();
        boolean client = ADDRESS.matcher(message).find();
        final StringBuilder words = new StringBuilder(ToolText.head(message, 2000));
        for (final Map.Entry<String, Object> entry : all.entrySet()) {
            final String name = entry.getKey();
            final String value = SourceTypeFields.valueOf(all, name);
            if (value == null || name.equals(field)) {
                continue;
            }
            final String last = ToolText.lastPart(name, '.');
            actor = actor || ACTOR_NAME.matcher(last).find() || FieldHomes.HOMES.stream()
                    .anyMatch(home -> FieldHomes.USER_ID.equals(home.path()) && FieldHomes.matches(home.regex(),
                            name));
            client = client || ADDRESS.matcher(value).find();
            if (value.length() <= 200 && !Shapes.MESSAGE.matcher(last).find()) {
                words.append(' ').append(value);
            }
        }
        final String text = words.toString();
        final Map<String, Object> signals = map(profile, "signals");
        bump(signals, "actor", actor);
        bump(signals, "client_address", client);
        bump(signals, "outcome", OUTCOME.matcher(text).find());
        bump(signals, "access_words", ACCESS.matcher(text).find());
        bump(signals, "sql", SQL.matcher(message).find());
        bump(signals, "timings", TIMING.matcher(message).find());
        bump(signals, "heartbeats", HEARTBEAT.matcher(text).find());
        bump(signals, "network_words", NETWORK_WORDS.matcher(text).find());
    }

    private static void bump(final Map<String, Object> signals, final String signal, final boolean seen) {
        if (seen) {
            add(signals, signal, 1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(final Map<String, Object> profile, final String key) {
        return (Map<String, Object>) profile.computeIfAbsent(key, k -> new LinkedHashMap<String, Object>());
    }

    private static void add(final Map<String, Object> map, final String key, final long n) {
        map.merge(key, n, (a, b) -> number(a) + number(b));
    }

    // Counts a value under a key's map, new values past the cap left out.
    private static void count(final Map<String, Object> profile,
                              final String key,
                              final String value,
                              final long n,
                              final int cap) {
        final Map<String, Object> counts = map(profile, key);
        if (counts.containsKey(value) || counts.size() < cap) {
            add(counts, value, n);
        }
    }

    private static void countCapped(final Map<String, Long> counts, final String key, final long n, final int cap) {
        if (counts.containsKey(key) || counts.size() < cap) {
            counts.merge(key, n, Long::sum);
        }
    }

    // The commonest entries of a count map, at most so many.
    private static Map<String, Object> byCount(final Map<String, Object> counts, final int most) {
        final Map<String, Object> out = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(number(b.getValue()), number(a.getValue())))
                .limit(most)
                .forEach(entry -> out.put(entry.getKey(), number(entry.getValue())));
        return out;
    }

    private static long number(final Object value) {
        return value instanceof final Number n
                ? n.longValue()
                : 0L;
    }


    // --------------------------------------------------------------------------------


    /// A category suggested for a source type.
    ///
    /// @param name       [#SECURITY_AUDIT], [#OPERATIONAL], [#NETWORK] or [#DIAGNOSTIC_NOISE].
    /// @param confidence `high`, `medium` or `low`.
    public record Category(String name, String confidence) {

    }
}
